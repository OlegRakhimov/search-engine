package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.PageIndex;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class LemmaProcessor {

    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final LuceneMorphology russianMorph;
    private final LuceneMorphology englishMorph;

    private static final Pattern CYR = Pattern.compile("\\p{IsCyrillic}");

    public LemmaProcessor(LemmaRepository lemmaRepository,
                          PageIndexRepository pageIndexRepository) throws Exception {
        this.lemmaRepository = lemmaRepository;
        this.pageIndexRepository = pageIndexRepository;
        this.russianMorph = new RussianLuceneMorphology();
        this.englishMorph = new EnglishLuceneMorphology();
    }

    private String toPlainText(String html) {
        String text = Jsoup.parse(html == null ? "" : html).text();
        return text.replaceAll("\\s+", " ").trim();
    }

    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String safe = text == null ? "" : text;

        String[] words = safe.toLowerCase(Locale.ROOT).split("[^\\p{L}]+");

        for (String word : words) {
            if (word.isBlank()) continue;
            if (word.length() < 2) continue;

            LuceneMorphology morph = isRussian(word) ? russianMorph : englishMorph;

            try {
                if (isServiceWord(morph, word)) continue;

                List<String> normalForms = morph.getNormalForms(word);
                if (!normalForms.isEmpty()) {
                    String lemma = normalForms.get(0).toLowerCase(Locale.ROOT);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            } catch (RuntimeException ex) {
                System.out.println("Не удалось обработать слово: " + word + " (" + ex.getMessage() + ")");
            }
        }
        return lemmas;
    }

    private boolean isRussian(String word) {
        return CYR.matcher(word).find();
    }

    private boolean isServiceWord(LuceneMorphology morph, String word) {
        List<String> info;
        try {
            info = morph.getMorphInfo(word);
        } catch (RuntimeException e) {
            return true;
        }

        boolean ruStop = info.stream().anyMatch(p ->
                p.contains("СОЮЗ") || p.contains("МЕЖД") || p.contains("ПРЕДЛ") || p.contains("ЧАСТ"));
        boolean enStop = info.stream().anyMatch(p ->
                p.contains("CONJ") || p.contains("PREP") || p.contains("PART") ||
                        p.contains("ARTICLE") || p.contains("PRON") || p.contains("INT"));

        return ruStop || enStop;
    }

    public void processAndSaveLemmas(String html, Site site, Page page) {
        String plain = toPlainText(html);
        Map<String, Integer> lemmasFromPage = collectLemmas(plain);

        for (Map.Entry<String, Integer> entry : lemmasFromPage.entrySet()) {
            String lemmaStr = entry.getKey();
            int freq = entry.getValue();

            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaStr, site)
                    .orElseGet(() -> {
                        Lemma l = new Lemma();
                        l.setSite(site);
                        l.setLemma(lemmaStr);
                        l.setFrequency(0);
                        return lemmaRepository.save(l);
                    });

            lemma.setFrequency(lemma.getFrequency() + 1);
            lemmaRepository.save(lemma);

            PageIndex idx = pageIndexRepository.findByPageIdAndLemmaId(page.getId(), lemma.getId())
                    .orElseGet(() -> {
                        PageIndex pi = new PageIndex();
                        pi.setPage(page);
                        pi.setLemma(lemma);
                        pi.setRank(0f);
                        return pi;
                    });
            idx.setRank((float) freq);
            pageIndexRepository.save(idx);
        }
    }
}
