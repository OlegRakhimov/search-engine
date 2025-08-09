package searchengine.services;

import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.SiteConfig;
import searchengine.dto.SimpleResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.morphology.LemmaProcessor;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;
import searchengine.task.SiteIndexerTask;

import javax.net.ssl.SSLHandshakeException;
import javax.transaction.Transactional;
import java.io.IOException;
import java.net.*;
import java.time.LocalDateTime;
import java.util.*;
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
    public synchronized SimpleResponse startIndexing() {
        if (indexing) {
            return new SimpleResponse(false, "Индексация уже запущена");
        }

        indexing = true;
        pool = new ForkJoinPool();

        Set<String> uniqueRoots = new HashSet<>();
        List<SiteConfig> uniqueSites = new ArrayList<>();
        for (SiteConfig sc : appConfig.getSites()) {
            String root = normalizeRoot(sc.getUrl());
            if (uniqueRoots.add(root)) {
                uniqueSites.add(sc);
            }
        }

        for (SiteConfig siteConfig : uniqueSites) {
            String siteUrl = siteConfig.getUrl();
            String siteName = siteConfig.getName();

            Site site = siteRepository.findByUrl(siteUrl).orElse(null);
            if (site == null) {
                site = new Site();
                site.setUrl(siteUrl);
                site.setName(siteName);
            } else {
                pageRepository.deleteAllBySite(site);
            }

            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            Set<String> visited = new HashSet<>();
            pool.submit(new SiteIndexerTask(siteUrl, site, pageRepository, visited, lemmaProcessor));
        }

        return new SimpleResponse(true, null);
    }

    public synchronized SimpleResponse stopIndexing() {
        if (!indexing) {
            return new SimpleResponse(false, "Индексация не запущена");
        }
        pool.shutdownNow();
        indexing = false;
        return new SimpleResponse(true, null);
    }

    public SimpleResponse indexPage(String url) {
        if (url == null || url.isBlank()) {
            return new SimpleResponse(false, "Пустой URL страницы");
        }
        final String normalized;
        final String siteRoot;
        try {
            URL u = new URL(url);
            siteRoot = u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "/";
            String path = (u.getPath() == null || u.getPath().isEmpty()) ? "/" : u.getPath();
            String query = (u.getQuery() == null) ? "" : "?" + u.getQuery();
            normalized = u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + path + query;
        } catch (MalformedURLException e) {
            return new SimpleResponse(false, "Некорректный URL");
        }

        Optional<SiteConfig> optionalConfig = appConfig.getSites().stream()
                .filter(sc -> normalizeRoot(sc.getUrl()).equalsIgnoreCase(siteRoot))
                .findFirst();

        if (optionalConfig.isEmpty()) {
            return new SimpleResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурации");
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
            Connection.Response resp = Jsoup.connect(normalized)
                    .userAgent("HeliontSearchBot/1.0")
                    .referrer("https://www.google.com")
                    .timeout(15_000)
                    .ignoreHttpErrors(true)
                    .execute();

            int code = resp.statusCode();
            if (code >= 400) {
                String msg = "HTTP " + code + " при загрузке страницы";
                failSite(site, msg);
                return new SimpleResponse(false, msg);
            }

            String contentType = resp.contentType() == null ? "" : resp.contentType().toLowerCase();
            if (!contentType.contains("text/html")) {
                String msg = "Контент не является HTML (" + contentType + ")";
                failSite(site, msg);
                return new SimpleResponse(false, msg);
            }

            Document doc = resp.parse();
            String html = doc.html();

            String path = normalized.replaceFirst("^" + java.util.regex.Pattern.quote(config.getUrl()), "");
            if (path.isEmpty()) path = "/";

            pageRepository.deleteByPathAndSite(path, site);

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(code);
            page.setContent(html);
            Page savedPage = pageRepository.save(page);

            lemmaProcessor.processAndSaveLemmas(html, site, savedPage);

            site.setStatus(Status.INDEXED);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            return new SimpleResponse(true, null);

        } catch (SocketTimeoutException e) {
            String msg = "Таймаут соединения при загрузке страницы";
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        } catch (UnknownHostException e) {
            String msg = "Не удалось разрешить имя хоста";
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        } catch (SSLHandshakeException e) {
            String msg = "Ошибка SSL-соединения";
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        } catch (HttpStatusException e) {
            String msg = "HTTP " + e.getStatusCode() + " при получении страницы";
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        } catch (IOException e) {
            String msg = "Ошибка ввода-вывода: " + e.getMessage();
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        } catch (Exception e) {
            String msg = "Не удалось проиндексировать страницу: " + e.getClass().getSimpleName();
            failSite(site, msg);
            return new SimpleResponse(false, msg);
        }
    }

    private void failSite(Site site, String msg) {
        site.setStatus(Status.FAILED);
        site.setStatusTime(LocalDateTime.now());
        site.setLastError(msg);
        siteRepository.save(site);
    }

    private String normalizeRoot(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "/";
        } catch (Exception e) {
            return url;
        }
    }
}
