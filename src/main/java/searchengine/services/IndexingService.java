package searchengine.services;

import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.SiteConfig;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.task.SiteIndexerTask;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ForkJoinPool;

@Service
public class IndexingService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final AppConfig appConfig;

    private volatile boolean indexing = false;
    private ForkJoinPool pool;

    public IndexingService(SiteRepository siteRepository, PageRepository pageRepository, AppConfig appConfig) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.appConfig = appConfig;
    }

    public synchronized void startIndexing() {
        if (indexing) {
            throw new RuntimeException("Индексация уже запущена");
        }

        indexing = true;
        pool = new ForkJoinPool();

        for (SiteConfig siteConfig : appConfig.getSites()) {
            String siteUrl = siteConfig.getUrl();
            String siteName = siteConfig.getName();

            Site site = siteRepository.findByUrl(siteUrl).orElseGet(() -> {
                Site newSite = new Site();
                newSite.setUrl(siteUrl);
                newSite.setName(siteName);
                newSite.setStatus(Status.INDEXING);
                newSite.setStatusTime(LocalDateTime.now());
                newSite.setLastError(null);
                return siteRepository.save(newSite);
            });

            pageRepository.deleteAllBySite(site);

            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            Set<String> visited = new HashSet<>();

            // Асинхронный запуск задачи
            pool.submit(new SiteIndexerTask(siteUrl, site, pageRepository, visited));
        }
    }

    public synchronized void stopIndexing() {
        if (!indexing) {
            throw new RuntimeException("Индексация не запущена");
        }

        pool.shutdownNow();
        indexing = false;
    }

    public boolean isIndexing() {
        return indexing;
    }
}
