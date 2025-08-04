package searchengine.services;

import lombok.Getter;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.SiteConfig;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.morphology.LemmaProcessor;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.task.SiteIndexerTask;
import searchengine.morphology.LemmaProcessor;
import searchengine.task.SiteIndexerTask;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final AppConfig appConfig;
    private final LemmaProcessor lemmaProcessor;

    @Getter
    private volatile boolean indexing = false;
    private ForkJoinPool pool;

    public IndexingService(SiteRepository siteRepository,
                           PageRepository pageRepository,
                           AppConfig appConfig,
                           LemmaProcessor lemmaProcessor) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.appConfig = appConfig;
        this.lemmaProcessor = lemmaProcessor;
    }

    @Transactional
    public synchronized void startIndexing() {
        if (indexing) {
            throw new RuntimeException("Индексация уже запущена");
        }

        indexing = true;
        pool = new ForkJoinPool();

        for (SiteConfig siteConfig : appConfig.getSites()) {
            String siteUrl = siteConfig.getUrl();
            String siteName = siteConfig.getName();

            siteRepository.findByUrl(siteUrl).ifPresent(existingSite -> {
                pageRepository.deleteAllBySite(existingSite);
                siteRepository.delete(existingSite);
            });

            Site newSite = new Site();
            newSite.setUrl(siteUrl);
            newSite.setName(siteName);
            newSite.setStatus(Status.INDEXING);
            newSite.setStatusTime(LocalDateTime.now());

            Site savedSite = siteRepository.save(newSite);

            Set<String> visited = new HashSet<>();
            pool.submit(new SiteIndexerTask(siteUrl, savedSite, pageRepository, visited, lemmaProcessor));
        }
    }

    public synchronized void stopIndexing() {
        if (!indexing) {
            throw new RuntimeException("Индексация не запущена");
        }
        pool.shutdownNow();
        indexing = false;
    }

    public void indexPage(String url) {
        Optional<SiteConfig> optionalConfig = appConfig.getSites().stream()
                .filter(site -> url.startsWith(site.getUrl()))
                .findFirst();

        if (optionalConfig.isEmpty()) {
            throw new IllegalArgumentException("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }

        SiteConfig config = optionalConfig.get();
        Site site = siteRepository.findByUrl(config.getUrl()).orElseGet(() -> {
            Site s = new Site();
            s.setUrl(config.getUrl());
            s.setName(config.getName());
            s.setStatus(Status.INDEXING);
            s.setStatusTime(LocalDateTime.now());
            return siteRepository.save(s);
        });

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("http://www.google.com")
                    .get();

            String html = doc.html();
            String path = url.replace(site.getUrl(), "");
            if (path.isEmpty()) path = "/";

            pageRepository.deleteByPathAndSite(path, site);

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(doc.connection().response().statusCode());
            page.setContent(html);

            Page savedPage = pageRepository.save(page);

            lemmaProcessor.processAndSaveLemmas(html, site, savedPage);

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

        } catch (IOException e) {
            site.setStatus(Status.FAILED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError("Ошибка загрузки страницы: " + e.getMessage());
            siteRepository.save(site);
            throw new RuntimeException("Ошибка при индексации страницы: " + e.getMessage());
        }
    }
}
