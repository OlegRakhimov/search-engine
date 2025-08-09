package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {

    void deleteAllBySite(Site site);

    void deleteByPathAndSite(String path, Site site);


    Optional<Page> findByPathAndSite(String path, Site site);

    long countBySiteId(int siteId);

    long countBySite(Site site);


}