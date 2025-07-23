package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.IndexRepository;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import javax.annotation.PostConstruct;
import javax.transaction.Transactional;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Controller
@RequestMapping("/api")
public class LemmaProcessor {

    private LuceneMorphology luceneMorphology;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private SiteRepository siteRepository;

    @PostConstruct
    public void init() throws Exception {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    /**
     * Очищает HTML и сохраняет леммы в таблицы lemma и index.
     */
    @Transactional
    public void processAndSaveLemmas(String html, Site site, Page page) {
        String text = Jsoup.parse(html).text(); // удаляем HTML-теги
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

            Index index = new Index();
            index.setPage(page);
            index.setLemma(lemma);
            index.setRank((float) count);
            indexRepository.save(index);
        }
    }

    /**
     * Разбивает текст на слова, фильтрует служебные части речи и возвращает леммы с частотами.
     */
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
            } catch (Exception ignored) {
                // игнорируем слова, которые не удаётся распознать
            }
        }
        return lemmas;
    }

    /**
     * Проверяет, является ли слово служебной частью речи.
     */
    private boolean isServicePartOfSpeech(String morph) {
        return morph.contains("СОЮЗ") || morph.contains("МЕЖД") ||
                morph.contains("ПРЕДЛ") || morph.contains("ЧАСТ");
    }
}