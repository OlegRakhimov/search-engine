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

import javax.transaction.Transactional;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

@Controller
@RequestMapping("/api")
public class LemmaProcessor {

    private final LuceneMorphology luceneMorphology;

    @Autowired
    private PageRepository pageRepository;

    @Autowired
    private LemmaRepository lemmaRepository;

    @Autowired
    private IndexRepository indexRepository;

    @Autowired
    private SiteRepository siteRepository;

    public LemmaProcessor() throws Exception {
        this.luceneMorphology = new RussianLuceneMorphology();
    }

    public Map<String, Integer> collectLemmas(String text) {
        Map<String, Integer> lemmaCount = new HashMap<>();
        String cleanText = Jsoup.parse(text).text().toLowerCase();

        String[] words = cleanText.split("[^а-яё]+");
        for (String word : words) {
            if (word.isBlank()) continue;
            if (!luceneMorphology.checkString(word)) continue;

            List<String> morphInfo = luceneMorphology.getMorphInfo(word);
            if (isServicePart(morphInfo)) continue;

            List<String> normalForms = luceneMorphology.getNormalForms(word);
            String lemma = normalForms.get(0);

            lemmaCount.put(lemma, lemmaCount.getOrDefault(lemma, 0) + 1);
        }
        return lemmaCount;
    }

    private boolean isServicePart(List<String> morphInfo) {
        for (String info : morphInfo) {
            if (info.contains("СОЮЗ") ||
                    info.contains("ПРЕДЛ") ||
                    info.contains("МЕЖД") ||
                    info.contains("ЧАСТ")) {
                return true;
            }
        }
        return false;
    }

    public String cleanHtml(String html) {
        return Jsoup.parse(html).text();
    }

    public String getSiteUrl(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }
}
