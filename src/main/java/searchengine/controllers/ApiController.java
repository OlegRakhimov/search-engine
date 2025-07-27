package searchengine.controllers;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.config.IndexingSettings;
import searchengine.config.SiteConfig;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.services.IndexingService;
import searchengine.services.StatisticsService;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final IndexingService indexingService;
    private final StatisticsService statisticsService;

    private final IndexingSettings indexingSettings;

    @Autowired
    public ApiController(IndexingService indexingService,
                         StatisticsService statisticsService,
                         IndexingSettings indexingSettings) {
        this.indexingService = indexingService;
        this.statisticsService = statisticsService;
        this.indexingSettings = indexingSettings;
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Map<String, Object>> startIndexing() {
        Map<String, Object> response = new HashMap<>();
        try {
            indexingService.startIndexing();
            response.put("result", true);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Map<String, Object>> stopIndexing() {
        Map<String, Object> response = new HashMap<>();
        try {
            indexingService.stopIndexing();
            response.put("result", true);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Map<String, Object>> indexPage(@RequestParam String url) {
        Map<String, Object> response = new HashMap<>();
        try {
            indexingService.indexPage(url);
            response.put("result", true);
        } catch (Exception e) {
            response.put("result", false);
            response.put("error", e.getMessage());
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/printSites")
    public ResponseEntity<List<SiteConfig>> printSites() {
        List<SiteConfig> sites = indexingSettings.getSites();
        sites.forEach(site -> System.out.println(site.getUrl() + " — " + site.getName()));
        return ResponseEntity.ok(sites); // Можно вернуть список как JSON
    }
}
