package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.PageIndex;
import searchengine.model.Site;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.util.*;

@Controller
@RequestMapping("/api")
public class LemmaProcessor {

    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private LuceneMorphology luceneMorphology;

    public LemmaProcessor(LemmaRepository lemmaRepository,
                          PageIndexRepository pageIndexRepository) {
        this.lemmaRepository = lemmaRepository;
        this.pageIndexRepository = pageIndexRepository;
    }

    @PostConstruct
    public void init() throws Exception {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    @Transactional
    public void processAndSaveLemmas(String html, Site site, Page page) {
        String text = Jsoup.parse(html).text();
        Map<String, Integer> lemmaMap = collectLemmas(text);

        for (Map.Entry<String, Integer> entry : lemmaMap.entrySet()) {
            String lemmaStr = entry.getKey();
            int count = entry.getValue();

            Lemma lemma = lemmaRepository.findByLemmaAndSite(lemmaStr, site)
                    .orElseGet(() -> {
                        Lemma newLemma = new Lemma();
                        newLemma.setLemma(lemmaStr);
                        newLemma.setSite(site);
                        newLemma.setFrequency(0);
                        return newLemma;
                    });

            lemma.setFrequency(lemma.getFrequency() + 1);
            lemma = lemmaRepository.save(lemma);

            pageIndexRepository.save(new PageIndex(page, lemma, (float) count));
        }
    }

    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmas = new HashMap<>();
        String[] words = text.toLowerCase().split("[^а-яА-Яa-zA-Z]+");

        for (String word : words) {
            if (word.isBlank()) continue;

            try {
                List<String> morphInfo = luceneMorphology.getMorphInfo(word);
                if (morphInfo.stream().anyMatch(this::isServicePartOfSpeech)) continue;

                List<String> normalForms = luceneMorphology.getNormalForms(word);
                if (!normalForms.isEmpty()) {
                    String lemma = normalForms.get(0);
                    lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
                }
            } catch (Exception e) {
                System.out.println("Ошибка при обработке слова \"" + word + "\": " + e.getMessage());
            }

        }
        return lemmas;
    }

    private boolean isServicePartOfSpeech(String morph) {
        return morph.contains("СОЮЗ") || morph.contains("МЕЖД") ||
                morph.contains("ПРЕДЛ") || morph.contains("ЧАСТ");
    }
}
