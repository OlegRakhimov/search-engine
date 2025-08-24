package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.Site;

import java.util.List;
import java.util.Optional;

public interface PageRepository extends JpaRepository<Page, Integer> {
    List<Page> findAllBySite(Site site);
    void deleteAllBySite(Site site);
    Optional<Page> findByPathAndSite(String path, Site site);
    long countBySite(Site site);
}
