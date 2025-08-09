package searchengine.services;

import lombok.RequiredArgsConstructor;
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

    private static final Set<String> STOP_LEMMAS = Set.of(
            "и", "в", "во", "не", "на", "я", "с", "со", "как", "а", "то", "все", "она", "так", "его", "но"
    );

    @Override
    @Transactional(readOnly = true)
    public SearchResponse search(String query, String site, int offset, int limit) {
        SearchResponse response = new SearchResponse();
        if (query == null || query.trim().isEmpty()) {
            response.setResult(false);
            response.setError("Задан пустой поисковый запрос");
            return response;
        }

        List<String> lemmaList = lemmaProcessor.collectLemmas(query)
                .keySet()
                .stream()
                .filter(l -> !STOP_LEMMAS.contains(l))
                .collect(Collectors.toList());

        if (lemmaList.isEmpty()) {
            response.setResult(false);
            response.setError("Нет значимых слов для поиска");
            return response;
        }

        List<Lemma> lemmaEntities = getLemmaEntities(lemmaList, site);
        if (lemmaEntities.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(List.of());
            return response;
        }

        long totalPages = getTotalPages(site);
        double tooCommonThreshold = totalPages * 0.6; // 60%
        List<Lemma> filteredLemmas = lemmaEntities.stream()
                .filter(l -> l.getFrequency() < tooCommonThreshold)
                .sorted(Comparator.comparingInt(Lemma::getFrequency))
                .collect(Collectors.toList());

        if (filteredLemmas.isEmpty()) {
            response.setResult(false);
            response.setError("Нет подходящих лемм для поиска");
            return response;
        }

        Set<Page> pages = findPagesByLemmas(filteredLemmas, site);
        if (pages.isEmpty()) {
            response.setResult(true);
            response.setCount(0);
            response.setData(List.of());
            return response;
        }

        Map<Page, Float> relevanceMap = calculateRelevance(pages, filteredLemmas);
        float maxAbsRel = relevanceMap.values().stream().max(Float::compare).orElse(1f);

        List<Map.Entry<Page, Float>> sorted =
                relevanceMap.entrySet().stream()
                        .sorted((a, b) -> Float.compare(b.getValue(), a.getValue()))
                        .skip(offset)
                        .limit(limit)
                        .collect(Collectors.toList());

        List<SearchResultItem> resultItems = sorted.stream()
                .map(entry -> pageToResult(entry.getKey(), entry.getValue(), maxAbsRel, filteredLemmas, query))
                .collect(Collectors.toList());

        response.setResult(true);
        response.setCount(relevanceMap.size());
        response.setData(resultItems);
        return response;
    }

    private List<Lemma> getLemmaEntities(List<String> lemmas, String siteUrl) {
        if (siteUrl != null) {
            Optional<Site> siteOpt = siteRepository.findByUrl(siteUrl);
            if (siteOpt.isEmpty()) return List.of();
            int siteId = siteOpt.get().getId();
            return lemmaRepository.findByLemmaInAndSiteId(lemmas, siteId);
        }
        return lemmaRepository.findByLemmaIn(lemmas);
    }

    private long getTotalPages(String siteUrl) {
        if (siteUrl != null) {
            Optional<Site> siteOpt = siteRepository.findByUrl(siteUrl);
            if (siteOpt.isEmpty()) return 1;
            return pageRepository.countBySite(siteOpt.get());
        }
        return pageRepository.count();
    }

    private Set<Page> findPagesByLemmas(List<Lemma> lemmas, String siteUrl) {
        Integer requiredSiteId = resolveSiteId(siteUrl);

        List<Set<Page>> lemmaPages = new ArrayList<>();
        for (Lemma lemma : lemmas) {
            List<PageIndex> indexes = pageIndexRepository.findByLemmaId(lemma.getId());
            Set<Page> pages = indexes.stream()
                    .map(PageIndex::getPage)
                    .filter(p -> requiredSiteId == null || p.getSite().getId() == requiredSiteId)
                    .collect(Collectors.toSet());
            lemmaPages.add(pages);
        }

        if (lemmaPages.isEmpty()) {
            return Collections.emptySet();
        }

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
            float sum = 0;
            for (Lemma lemma : lemmas) {
                Optional<PageIndex> idxOpt =
                        pageIndexRepository.findByPageIdAndLemmaId(page.getId(), lemma.getId());
                sum += idxOpt.map(PageIndex::getPageRank).orElse(0f);
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
        String text = html.replaceAll("<[^>]+>", " ");
        String lower = text.toLowerCase();

        Set<String> terms = new LinkedHashSet<>();
        for (String w : query.split("\\s+")) {
            String t = w.trim().toLowerCase();
            if (!t.isEmpty()) terms.add(t);
        }
        for (Lemma l : lemmas) {
            String t = l.getLemma().trim().toLowerCase();
            if (!t.isEmpty()) terms.add(t);
        }
        if (terms.isEmpty()) {
            return text.substring(0, Math.min(160, text.length())) + "...";
        }

        String alternation = terms.stream()
                .map(Pattern::quote)
                .collect(Collectors.joining("|"));
        Pattern p = Pattern.compile("(?iu)(" + alternation + ")");

        final int HALF = 60;
        List<int[]> windows = new ArrayList<>();
        Matcher m = p.matcher(lower);
        while (m.find()) {
            int from = Math.max(0, m.start() - HALF);
            int to   = Math.min(text.length(), m.end() + HALF);
            windows.add(new int[]{from, to});
        }
        if (windows.isEmpty()) {
            return text.substring(0, Math.min(160, text.length())) + "...";
        }

        windows.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> merged = new ArrayList<>();
        int[] cur = windows.get(0);
        for (int i = 1; i < windows.size(); i++) {
            int[] nxt = windows.get(i);
            if (nxt[0] <= cur[1] + 20) {
                cur[1] = Math.max(cur[1], nxt[1]);
            } else {
                merged.add(cur);
                cur = nxt;
            }
        }
        merged.add(cur);

        final int MAX_FRAGMENTS = 3;
        List<String> fragments = new ArrayList<>();
        for (int i = 0; i < Math.min(MAX_FRAGMENTS, merged.size()); i++) {
            int from = merged.get(i)[0];
            int to   = merged.get(i)[1];
            String frag = text.substring(from, to);

            Matcher hl = p.matcher(frag);
            StringBuffer sb = new StringBuffer();
            while (hl.find()) {
                hl.appendReplacement(sb, "<b>" + hl.group(0) + "</b>");
            }
            hl.appendTail(sb);

            String cleaned = sb.toString().replaceAll("\\s{2,}", " ").trim();
            String withDots = (from > 0 ? "..." : "") + cleaned + (to < text.length() ? "..." : "");
            fragments.add(withDots);
        }

        String result = String.join(" ", fragments);
        int MAX_LEN = 600;
        if (result.length() > MAX_LEN) {
            result = result.substring(0, MAX_LEN).replaceAll("<b([^>]*)?$", "") + "...";
        }
        return result;
    }
}
