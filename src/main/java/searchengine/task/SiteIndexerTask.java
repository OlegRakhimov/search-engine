package searchengine.task;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.morphology.LemmaProcessor;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageIndexRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.net.URL;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

public class SiteIndexerTask extends RecursiveAction {

    private final String url;
    private final Site site;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final PageIndexRepository pageIndexRepository;
    private final SiteRepository siteRepository;
    private final Set<String> visited;
    private final LemmaProcessor lemmaProcessor;

    public SiteIndexerTask(String url,
                           Site site,
                           PageRepository pageRepository,
                           LemmaRepository lemmaRepository,
                           PageIndexRepository pageIndexRepository,
                           SiteRepository siteRepository,
                           Set<String> visited,
                           LemmaProcessor lemmaProcessor) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageIndexRepository = pageIndexRepository;
        this.siteRepository = siteRepository;
        this.visited = visited;
        this.lemmaProcessor = lemmaProcessor;
    }

    @Override
    protected void compute() {
        if (!visited.add(url)) {
            return;
        }

        try {
            Document doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36" +
                            " (HTML, like Gecko) Chrome/115.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9," +
                            "image/webp,*/*;q=0.8")
                    .header("Accept-Language", "ru,en;q=0.9")
                    .referrer("https://www.google.com")
                    .timeout(30000)
                    .ignoreHttpErrors(true)
                    .get();


            String path = getPathFromUrl(url);
            if (path.isEmpty()) path = "/";

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(200);
            page.setContent(doc.html());

            Page savedPage = pageRepository.save(page);
            lemmaProcessor.processAndSaveLemmas(doc.html(), site, savedPage);

            String siteOrigin = originNoWww(new URL(site.getUrl()));

            Elements links = doc.select("a[href]");
            for (Element link : links) {
                String absHref = link.absUrl("href");
                if (absHref.isBlank()) continue;
                if (absHref.contains("#")) continue;
                if (absHref.matches("(?i).+\\.(jpg|jpeg|png|gif|webp|svg|pdf|docx?" +
                        "|xlsx?|pptx?|zip|rar)$")) continue;

                URL linkUrl;
                try {
                    linkUrl = new URL(absHref);
                } catch (Exception ignored) {
                    continue;
                }

                if (!originNoWww(linkUrl).equals(siteOrigin)) continue;

                invokeAll(new SiteIndexerTask(
                        absHref,
                        site,
                        pageRepository,
                        lemmaRepository,
                        pageIndexRepository,
                        siteRepository,
                        visited,
                        lemmaProcessor
                ));
            }

        } catch (Exception e) {
            site.setStatus(Status.FAILED);
            site.setLastError(e.getClass().getSimpleName() + ": " + (e.getMessage() == null ? "" :
                    e.getMessage()));
            site.setStatusTime(LocalDateTime.now());
            siteRepository.save(site);
        }
    }

    private String getPathFromUrl(String fullUrl) {
        try {
            URL urlObj = new URL(fullUrl);
            String path = urlObj.getPath();
            return (path == null || path.isEmpty()) ? "/" : path;
        } catch (Exception e) {
            return "/";
        }
    }
    private static String originNoWww(URL u) {
        String host = (u.getHost() == null ? "" : u.getHost()).toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        int port = u.getPort();
        String portPart = (port > 0 && port != u.getDefaultPort()) ? (":" + port) : "";
        return u.getProtocol().toLowerCase(Locale.ROOT) + "://" + host + portPart + "/";
    }
}
