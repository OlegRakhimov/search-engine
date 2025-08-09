package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.IndexingSettings;
import searchengine.config.SiteConfig;
import searchengine.dto.SimpleResponse;
import searchengine.dto.search.SearchResponse;
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
    private final IndexingSettings indexingSettings;

    @Autowired
    public ApiController(IndexingService indexingService,
                         StatisticsService statisticsService,
                         SearchService searchService,
                         IndexingSettings indexingSettings) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
        this.searchService = searchService;
        this.indexingSettings = indexingSettings;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<SimpleResponse> startIndexing() {
        return ResponseEntity.ok(indexingService.startIndexing());
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<SimpleResponse> stopIndexing() {
        return ResponseEntity.ok(indexingService.stopIndexing());
    }

    @PostMapping("/indexPage")
    public ResponseEntity<SimpleResponse> indexPage(@RequestParam String url) {
        return ResponseEntity.ok(indexingService.indexPage(url));
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/printSites")
    public ResponseEntity<List<SiteConfig>> printSites() {
        List<SiteConfig> sites = indexingSettings.getSites();
        sites.forEach(site -> System.out.println(site.getUrl() + " — " + site.getName()));
        return ResponseEntity.ok(sites);
    }

    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestParam String query,
            @RequestParam(required = false) String site,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "20") int limit) {

        SearchResponse searchResponse = searchService.search(query, site, offset, limit);

        if (!searchResponse.isResult()) {
            return ResponseEntity.badRequest().body(searchResponse);
        }
        return ResponseEntity.ok(searchResponse);
    }
}
