package searchengine.task;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repository.PageRepository;
import searchengine.morphology.LemmaProcessor;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.RecursiveAction;

public class SiteIndexerTask extends RecursiveAction {

    private final String url;
    private final Site site;
    private final PageRepository pageRepository;
    private final Set<String> visited;
    private final LemmaProcessor lemmaProcessor;

    public SiteIndexerTask(String url, Site site, PageRepository pageRepository, Set<String> visited, LemmaProcessor lemmaProcessor) {
        this.url = url;
        this.site = site;
        this.pageRepository = pageRepository;
        this.visited = visited;
        this.lemmaProcessor = lemmaProcessor;
    }

    @Override
    protected void compute() {
        try {
            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            if (!visited.add(url)) {
                return;
            }

            Thread.sleep(500 + new Random().nextInt(1500));

            if (Thread.currentThread().isInterrupted()) {
                return;
            }

            Document doc = Jsoup.connect(url)
                    .userAgent("HeliontSearchBot")
                    .referrer("https://www.google.com")
                    .timeout(10000)
                    .get();

            String path = url.replace(site.getUrl(), "");
            if (path.isEmpty()) path = "/";

            Page page = new Page();
            page.setSite(site);
            page.setPath(path);
            page.setCode(200);
            page.setContent(doc.html());
            pageRepository.save(page);

            lemmaProcessor.processAndSaveLemmas(page.getContent(), site, page);

            Elements links = doc.select("a[href]");
            List<SiteIndexerTask> tasks = new ArrayList<>();

            for (Element element : links) {
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }

                String link = element.absUrl("href");

                if (isValidLink(link)) {
                    tasks.add(new SiteIndexerTask(link, site, pageRepository, visited, lemmaProcessor)); // <--- передаем lemmaProcessor
                }
            }

            invokeAll(tasks);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            throw new RuntimeException("Ошибка при обходе " + url + ": " + e.getMessage(), e);
        }
    }

    private boolean isValidLink(String link) {
        return link.startsWith(site.getUrl()) &&
                !visited.contains(link) &&
                !link.contains("#") &&
                !link.contains("?") &&
                !link.matches(".*(\\.jpg|\\.png|\\.gif|\\.pdf|\\.doc|\\.css|\\.js)$") &&
                !link.startsWith("mailto:") &&
                !link.startsWith("tel:");
    }
}
