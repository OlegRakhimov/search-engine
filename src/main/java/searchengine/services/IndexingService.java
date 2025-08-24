package searchengine.services;

import lombok.Getter;
import org.jsoup.Connection;
import org.jsoup.HttpStatusException;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import searchengine.config.AppConfig;
import searchengine.config.SiteConfig;
import searchengine.dto.SimpleResponse;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.morphology.LemmaProcessor;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
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
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final AppConfig appConfig;
    private final LemmaProcessor lemmaProcessor;
    private final IndexingService self;

    @Getter
    private volatile boolean indexing = false;
    private ForkJoinPool pool;

    public IndexingService(SiteRepository siteRepository,
                           PageRepository pageRepository,
                           LemmaRepository lemmaRepository,
                           PageIndexRepository pageIndexRepository,
                           AppConfig appConfig,
                           LemmaProcessor lemmaProcessor,
                           @Lazy IndexingService self) {
        this.siteRepository = siteRepository;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageIndexRepository = pageIndexRepository;
        this.appConfig = appConfig;
        this.lemmaProcessor = lemmaProcessor;
        this.self = self;
    }

    private void failSite(Site site, String message) {
        if (site == null) return;
        site.setStatus(Status.FAILED);
        site.setLastError(message);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);
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
            if (site != null) {
                List<Page> pages = pageRepository.findAllBySite(site);
                for (Page page : pages) {
                    pageIndexRepository.deleteAllByPage(page);
                }
                lemmaRepository.deleteAllBySite(site);
                pageRepository.deleteAllBySite(site);
            } else {
                site = new Site();
                site.setUrl(siteUrl);
                site.setName(siteName);
            }

            site.setStatus(Status.INDEXING);
            site.setStatusTime(LocalDateTime.now());
            site.setLastError(null);
            siteRepository.save(site);

            Set<String> visited = new HashSet<>();
            pool.submit(new SiteIndexerTask(
                    siteUrl,
                    site,
                    pageRepository,
                    lemmaRepository,
                    pageIndexRepository,
                    siteRepository,
                    visited,
                    lemmaProcessor
            ));
        }
        return new SimpleResponse(true, null);
    }

    @Transactional
    public synchronized SimpleResponse stopIndexing() {
        if (!indexing) {
            return new SimpleResponse(false, "Индексация не запущена");
        }
        pool.shutdownNow();
        indexing = false;

        List<Site> sitesInProgress = siteRepository.findAllByStatus(Status.INDEXING);
        for (Site site : sitesInProgress) {
            site.setStatus(Status.FAILED);
            site.setLastError("Индексация остановлена пользователем");
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
        return new SimpleResponse(true, null);
    }

    @Transactional(Transactional.TxType.SUPPORTS)
    public SimpleResponse indexPage(String url) {
        if (url == null || url.isBlank()) {
            return new SimpleResponse(false, "Пустой URL страницы");
        }

        final URL parsedInput;
        final String originNoWww;
        try {
            parsedInput = new URL(url.trim());
            originNoWww = normalizeOriginNoWww(parsedInput);
        } catch (MalformedURLException e) {
            return new SimpleResponse(false, "Некорректный URL");
        }

        boolean allowed = appConfig.getSites().stream().anyMatch(sc -> {
            try {
                return normalizeOriginNoWww(new URL(sc.getUrl())).equalsIgnoreCase(originNoWww);
            } catch (MalformedURLException ignored) {
                return false;
            }
        });
        if (!allowed) {
            return new SimpleResponse(false, "Данная страница находится за пределами сайтов, указанных в конфигурации");
        }

        self.indexPageJob(url.trim());
        return new SimpleResponse(true, null);
    }

    @Async
    @Transactional
    public void indexPageJob(String url) {
        Optional<SiteConfig> optionalConfig = appConfig.getSites().stream()
                .filter(sc -> {
                    try {
                        return normalizeOriginNoWww(new URL(sc.getUrl()))
                                .equalsIgnoreCase(normalizeOriginNoWww(new URL(url)));
                    } catch (MalformedURLException ignored) {
                        return false;
                    }
                })
                .findFirst();

        if (optionalConfig.isEmpty()) {
            return;
        }

        SiteConfig config = optionalConfig.get();

        Site site = siteRepository.findByUrl(config.getUrl()).orElseGet(() -> {
            Site s = new Site();
            s.setUrl(config.getUrl());
            s.setName(config.getName());
            return siteRepository.save(s);
        });
        site.setStatus(Status.INDEXING);
        site.setLastError(null);
        site.setStatusTime(LocalDateTime.now());
        siteRepository.save(site);

        try {
            final URL parsedInput = new URL(url);
            Connection.Response resp;
            try {
                resp = httpGet(parsedInput.toString());
            } catch (UnknownHostException uh) {
                URL alt = toggleWww(parsedInput);
                if (alt == null) throw uh;
                resp = httpGet(alt.toString());
            }

            int code = resp.statusCode();
            String contentType = Optional.ofNullable(resp.contentType())
                    .orElse("")
                    .toLowerCase(Locale.ROOT);

            if (!contentType.contains("text/html")) {
                failSite(site, "Некорректный Content-Type");
                return;
            }

            URL usedUrl = new URL(resp.url().toString());
            String path = buildPathFromUrl(usedUrl);

            deleteAllByPathAndSiteSafe(site, path);

            String body = resp.body();
            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(code);
            page.setContent(body);
            Page savedPage = pageRepository.save(page);

            if (code == 200 && contentType.contains("text/html")) {
                Document doc = resp.parse();
                try {
                    lemmaProcessor.processAndSaveLemmas(doc.html(), site, savedPage);
                } catch (RuntimeException ignored) {
                }
            }

            site.setStatus(Status.INDEXED);
            site.setLastError(code == 200 ? null : ("HTTP " + code + " при индексации " + path));
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);

        } catch (SocketTimeoutException e) {
            failSite(site, "Таймаут соединения");
        } catch (UnknownHostException e) {
            failSite(site, "Хост не найден");
        } catch (SSLHandshakeException e) {
            failSite(site, "Ошибка SSL-соединения");
        } catch (HttpStatusException e) {
            failSite(site, "HTTP " + e.getStatusCode());
        } catch (DataIntegrityViolationException e) {
            failSite(site, "Нарушение ограничений БД (возможен слишком длинный путь или дубли)");
        } catch (IOException e) {
            failSite(site, "Ошибка ввода/вывода: " + e.getMessage());
        } catch (Exception e) {
            failSite(site, "Не удалось проиндексировать: " + e.getClass().getSimpleName());
        }
    }

    private String normalizeRoot(String url) {
        try {
            URL u = new URL(url);
            return u.getProtocol() + "://" + u.getHost() + (u.getPort() > 0 ? ":" + u.getPort() : "") + "/";
        } catch (Exception e) {
            return url;
        }
    }

    private String normalizeOriginNoWww(URL u) {
        String scheme = u.getProtocol().toLowerCase(Locale.ROOT);
        String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
        host = stripWww(host);
        int port = u.getPort();
        String portPart = (port > 0 && port != u.getDefaultPort()) ? (":" + port) : "";
        return scheme + "://" + host + portPart + "/";
    }

    private String stripWww(String host) {
        if (host == null) return "";
        host = host.toLowerCase(Locale.ROOT);
        return host.startsWith("www.") ? host.substring(4) : host;
    }

    private URL toggleWww(URL u) {
        try {
            String host = u.getHost();
            if (host == null || host.isEmpty()) return null;
            String altHost = host.toLowerCase(Locale.ROOT).startsWith("www.")
                    ? host.substring(4)
                    : "www." + host;
            return new URL(u.getProtocol(), altHost, u.getPort(), u.getFile());
        } catch (MalformedURLException e) {
            return null;
        }
    }

    private Connection.Response httpGet(String url) throws IOException {
        return Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (HTML, like Gecko) Chrome/127.0.0.0 Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8")
                .header("Accept-Language", "ru,en;q=0.9")
                .referrer("https://www.google.com")
                .timeout(30000)
                .ignoreHttpErrors(true)
                .followRedirects(true)
                .execute();
    }

    private String buildPathFromUrl(URL u) {
        String path = (u.getPath() == null || u.getPath().isBlank()) ? "/" : u.getPath();
        if (!path.startsWith("/")) path = "/" + path;
        String query = u.getQuery();
        if (query != null && !query.isEmpty()) path = path + "?" + query;
        if (path.length() > 512) path = path.substring(0, 512);
        return path;
    }

    private void deleteAllByPathAndSiteSafe(Site site, String path) {
        pageRepository.findByPathAndSite(path, site).ifPresent(oldPage -> {
            var indices = pageIndexRepository.findAllByPage(oldPage);

            indices.forEach(idx -> {
                var lemma = idx.getLemma();
                int newFreq = Math.max(0, lemma.getFrequency() - 1);
                lemma.setFrequency(newFreq);
                lemmaRepository.save(lemma);
            });
            pageIndexRepository.deleteAllByPage(oldPage);
            pageRepository.delete(oldPage);
        });
    }
}
