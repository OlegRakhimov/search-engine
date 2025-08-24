package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.AppConfig;
import searchengine.config.SiteConfig;
import searchengine.dto.SimpleResponse;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;
import searchengine.services.StatisticsService;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final IndexingService indexingService;
    private final StatisticsService statisticsService;
    private final SearchService searchService;
    private final AppConfig appConfig;

    @Autowired
    public ApiController(IndexingService indexingService,
                         StatisticsService statisticsService,
                         SearchService searchService,
                         AppConfig appConfig) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.appConfig = appConfig;
    }

    @RequestMapping(value = "/startIndexing", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<SimpleResponse> startIndexing()
    {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @RequestMapping(value = "/stopIndexing", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<SimpleResponse> stopIndexing()
    {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @RequestMapping(value = "/indexPage", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<SimpleResponse> indexPage(@RequestParam String url) {
        SimpleResponse resp = indexingService.indexPage(url);
        if (!resp.isResult()) {
            return ResponseEntity.badRequest().body(resp);
        }
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/printSites")
    public ResponseEntity<List<SiteConfig>> printSites() {
        List<SiteConfig> sites = appConfig.getSites();
        sites.forEach(site -> System.out.println(site.getUrl() + " — " + site.getName()));
        return ResponseEntity.ok(sites);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {
        if (site != null && site.isBlank()) {
            site = null;
        }
        var searchResponse = searchService.search(query, site, offset, limit);

        if (!searchResponse.isResult()) {
            return ResponseEntity.badRequest().body(searchResponse);
        }
        return ResponseEntity.ok(searchResponse);
    }
}
