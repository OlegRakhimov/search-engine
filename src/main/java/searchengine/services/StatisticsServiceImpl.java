package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.config.AppConfig;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.model.Site;
import searchengine.model.Status;
import searchengine.repository.LemmaRepository;
import searchengine.repository.PageRepository;
import searchengine.repository.SiteRepository;

import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final SiteRepository siteRepository;
    private final PageRepository pageRepository;
    private final LemmaRepository lemmaRepository;
    private final AppConfig appConfig;

    @Override
    @Transactional(readOnly = true)
    public StatisticsResponse getStatistics() {
        List<Site> allSites = siteRepository.findAll();
        TotalStatistics total = new TotalStatistics();
        List<DetailedStatisticsItem> detailed = new ArrayList<>();

        total.setSites(appConfig.getSites().size());
        total.setPages(0);
        total.setLemmas(0);
        total.setIndexing(allSites.stream().anyMatch(s -> s.getStatus() == Status.INDEXING));

        for (var siteConfig : appConfig.getSites()) {
            Site site = allSites.stream()
                    .filter(s -> s.getUrl().equals(siteConfig.getUrl()))
                    .findFirst()
                    .orElse(null);

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setUrl(siteConfig.getUrl());
            item.setName(siteConfig.getName());

            if (site != null) {
                long pagesL = pageRepository.countBySite(site);
                long lemmasL = lemmaRepository.countBySiteId(site.getId());


                item.setStatus(site.getStatus().name());
                item.setStatusTime(site.getStatusTime().toInstant(ZoneOffset.UTC).toEpochMilli());
                item.setError(site.getLastError());
                item.setPages((int) pagesL);
                item.setLemmas((int) lemmasL);

                total.setPages(total.getPages() + (int) pagesL);
                total.setLemmas(total.getLemmas() + (int) lemmasL);
            } else {
                item.setStatus(Status.FAILED.name());
                item.setStatusTime(System.currentTimeMillis());
                item.setError("Сайт не индексировался");
                item.setPages(0);
                item.setLemmas(0);
            }
            detailed.add(item);
        }

        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);

        StatisticsResponse response = new StatisticsResponse();
        response.setResult(true);
        response.setStatistics(data);
        return response;
    }
}
