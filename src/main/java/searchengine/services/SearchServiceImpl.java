package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResultItem;
import searchengine.model.*;
import searchengine.morphology.LemmaProcessor;
import searchengine.repository.*;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final LemmaRepository lemmaRepository;
    private final PageRepository pageRepository;
    private final PageIndexRepository pageIndexRepository;
    private final SiteRepository siteRepository;
    private final LemmaProcessor lemmaProcessor;

    @Value("${search.tooCommonPercent:0.6}")
    private double tooCommonPercent;

    private static final Set<String> STOP_LEMMAS = Set.of(
            "и", "в", "во", "не", "на", "я", "с", "со", "как", "а",
            "то", "все", "она", "так", "его", "но",
            "the","and","to","of","in","a","is","it","for","on","that",
            "with","as","at","by","an","be","this","from","or"
    );

    private static final int SNIPPET_LEN = 160;
    private static final int HALF = SNIPPET_LEN / 2;
    private static final String ELLIPSIS = "...";

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String siteUrl, int offset, int limit) {
        siteUrl = (siteUrl == null || siteUrl.isBlank()) ? null : siteUrl.trim();
        SearchResponse response = new SearchResponse();
        if (query == null || query.trim().isEmpty()) {
            return errorResponse(response, "Задан пустой поисковый запрос");
        }

        List<String> lemmaList = lemmaProcessor.collectLemmas(query)
                .keySet()
                .stream()
                .filter(l -> !STOP_LEMMAS.contains(l))
                .collect(Collectors.toList());

        if (lemmaList.isEmpty()) {
            return errorResponse(response, "Нет значимых слов для поиска");
        }

        List<Lemma> lemmaEntities = getLemmaEntities(lemmaList, siteUrl);
        if (lemmaEntities.isEmpty()) {
            return emptyResponse(response);
        }

        long totalPages = getTotalPages(siteUrl);
        double tooCommonThreshold = totalPages * tooCommonPercent;

        List<Lemma> filteredLemmas = lemmaEntities.stream()
                .filter(l -> l.getFrequency() <= tooCommonThreshold)
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());

        if (filteredLemmas.isEmpty()) {
            return errorResponse(response, "Нет подходящих лемм для поиска");
        }

        Set<Page> pages = findPagesByLemmas(filteredLemmas, siteUrl);
        if (pages.isEmpty()) {
            return emptyResponse(response);
        }

        Map<Page, Float> relevanceMap = calculateRelevance(pages, filteredLemmas);
        float maxAbsRel = relevanceMap.values().stream().max(Float::compare).orElse(1f);

        List<SearchResultItem> resultItems = relevanceMap.entrySet().stream()
                .sorted((a, b) -> Float.compare(b.getValue(),
                        a.getValue()))
                .skip(offset)
                .limit(limit)
                .map(entry -> pageToResult(entry.getKey(), entry.getValue(),
                        maxAbsRel, filteredLemmas, query))
                .collect(Collectors.toList());

        response.setResult(true);
        response.setCount(relevanceMap.size());
        response.setData(resultItems);
        return response;
    }

    private SearchResponse errorResponse(SearchResponse resp, String error) {
        resp.setResult(false);
        resp.setError(error);
        return resp;
    }

    private SearchResponse emptyResponse(SearchResponse resp) {
        resp.setResult(true);
        resp.setCount(0);
        resp.setData(List.of());
        return resp;
    }

    private List<Lemma> getLemmaEntities(List<String> lemmas, String siteUrl) {
        if (siteUrl != null) {
            return siteRepository.findByUrl(siteUrl)
                    .map(site -> lemmaRepository.findByLemmaInAndSiteId(lemmas,
                            site.getId()))
                    .orElse(List.of());
        }
        return lemmaRepository.findByLemmaIn(lemmas);
    }

    private long getTotalPages(String siteUrl) {
        if (siteUrl != null) {
            return siteRepository.findByUrl(siteUrl)
                    .map(pageRepository::countBySite)
                    .orElse(1L);
        }
        return pageRepository.count();
    }

    private Set<Page> findPagesByLemmas(List<Lemma> lemmas, String siteUrl) {
        Integer requiredSiteId = resolveSiteId(siteUrl);
        if (requiredSiteId != null) {
            return intersectPagesForSite(lemmas.stream()
                    .filter(l -> l.getSite().getId() == requiredSiteId)
                    .collect(Collectors.toList()));
        }

        Map<Integer, List<Lemma>> bySite = lemmas.stream()
                .collect(Collectors.groupingBy(l -> l.getSite().getId()));

        Set<Page> union = new HashSet<>();
        for (List<Lemma> siteLemmas : bySite.values()) {
            Set<Page> pagesForSite = intersectPagesForSite(siteLemmas);
            union.addAll(pagesForSite);
        }
        return union;
    }

    private Set<Page> intersectPagesForSite(List<Lemma> siteLemmas) {
        if (siteLemmas.isEmpty()) return Collections.emptySet();
        List<Set<Page>> lemmaPages = new ArrayList<>();
        for (Lemma lemma : siteLemmas) {
            Set<Page> pages = pageIndexRepository.findByLemmaId(lemma.getId()).stream()
                    .map(PageIndex::getPage)
                    .collect(Collectors.toSet());
            if (!pages.isEmpty()) {
                lemmaPages.add(pages);
            }
        }
        if (lemmaPages.isEmpty()) return Collections.emptySet();
        Set<Page> result = new HashSet<>(lemmaPages.get(0));
        for (int i = 1; i < lemmaPages.size(); i++) {
            result.retainAll(lemmaPages.get(i));
            if (result.isEmpty()) break;
        }
        return result;
    }

    private Integer resolveSiteId(String siteUrl) {
        if (siteUrl == null) return null;
        return siteRepository.findByUrl(siteUrl).map(Site::getId).orElse(null);
    }

    private Map<Page, Float> calculateRelevance(Set<Page> pages, List<Lemma> lemmas) {
        Map<Page, Float> map = new HashMap<>();
        for (Page page : pages) {
            float sum = 0f;
            for (Lemma lemma : lemmas) {
                sum += pageIndexRepository.findByPageIdAndLemmaId(page.getId(),
                                lemma.getId())
                        .map(PageIndex::getRank)
                        .orElse(0f);
            }
            map.put(page, sum);
        }
        return map;
    }

    private SearchResultItem pageToResult(Page page, float absRel, float maxAbsRel,
                                          List<Lemma> lemmas, String query) {
        SearchResultItem item = new SearchResultItem();
        Site site = page.getSite();
        item.setSite(site.getUrl());
        item.setSiteName(site.getName());
        item.setUri(page.getPath());
        item.setTitle(extractTitle(page.getContent()));
        item.setSnippet(makeSnippet(page.getContent(), lemmas, query));
        item.setRelevance(absRel / maxAbsRel);
        return item;
    }

    private String extractTitle(String html) {
        int start = html.indexOf("<title>");
        int end = html.indexOf("</title>");
        if (start != -1 && end != -1 && end > start) {
            return html.substring(start + 7, end);
        }
        return "";
    }

    private String makeSnippet(String html, List<Lemma> lemmas, String query) {
        String text = normalizeHtml(html);
        String lower = text.toLowerCase(Locale.ROOT);

        Set<String> terms = collectTerms(lemmas, query);
        if (terms.isEmpty()) {
            return shorten(text, SNIPPET_LEN);
        }

        Pattern p = buildWordBoundaryPattern(terms);
        Matcher m = p.matcher(lower);

        List<int[]> windows = new ArrayList<>();
        while (m.find()) {
            int from = Math.max(0, m.start() - HALF);
            int to   = Math.min(text.length(), m.end() + HALF);
            windows.add(new int[]{from, to});
        }
        if (windows.isEmpty()) {
            return shorten(text, SNIPPET_LEN);
        }

        List<int[]> merged = mergeOverlaps(windows);
        if (merged.size() > 2) {
            merged = merged.subList(0, 2);
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < merged.size(); i++) {
            int[] w = merged.get(i);
            String frag = text.substring(w[0], w[1]).trim();
            if (w[0] > 0) frag = ELLIPSIS + " " + frag;
            if (w[1] < text.length()) frag = frag + " " + ELLIPSIS;
            if (i > 0) sb.append(" ").append(ELLIPSIS).append(" ");
            sb.append(frag);
        }

        String snippet = highlight(sb.toString(), p);
        return shorten(snippet, SNIPPET_LEN * 2);
    }

    private String normalizeHtml(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text();
        return text.replaceAll("\\s+", " ").trim();
    }

    private Set<String> collectTerms(List<Lemma> lemmas, String query) {
        Set<String> terms = new LinkedHashSet<>();
        if (lemmas != null) {
            for (Lemma l : lemmas) {
                if (l == null || l.getLemma() == null) continue;
                String t = l.getLemma().trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) terms.add(t);
            }
        }
        if (query != null) {
            Arrays.stream(query.toLowerCase(Locale.ROOT).split("\\s+"))
                    .map(String::trim).filter(s -> s.length() > 1)
                    .forEach(terms::add);
        }
        return terms;
    }

    private Pattern buildWordBoundaryPattern(Set<String> terms) {
        List<String> escaped = new ArrayList<>(terms.size());
        for (String t : terms) {
            if (!t.isEmpty()) escaped.add(Pattern.quote(t));
        }
        if (escaped.isEmpty()) {
            return Pattern.compile("(?!x)x");
        }
        String alternation = String.join("|", escaped);
        return Pattern.compile("\\b(" + alternation + ")\\b",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    private List<int[]> mergeOverlaps(List<int[]> windows) {
        if (windows.size() <= 1) return windows;
        windows.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> res = new ArrayList<>();
        int[] cur = Arrays.copyOf(windows.get(0), 2);
        for (int i = 1; i < windows.size(); i++) {
            int[] w = windows.get(i);
            if (w[0] <= cur[1]) {
                cur[1] = Math.max(cur[1], w[1]);
            } else {
                res.add(cur);
                cur = Arrays.copyOf(w, 2);
            }
        }
        res.add(cur);
        return res;
    }

    private String highlight(String text, Pattern termPattern) {
        Matcher m = termPattern.matcher(text);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(text, last, m.start());
            out.append("<b>").append(text, m.start(), m.end()).append("</b>");
            last = m.end();
        }
        out.append(text, last, text.length());
        return out.toString();
    }

    private String shorten(String text, int n) {
        if (text == null) return "";
        if (text.length() <= n) return text;
        return text.substring(0, n) + ELLIPSIS;
    }
}