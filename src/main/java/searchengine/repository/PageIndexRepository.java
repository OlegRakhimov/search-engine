package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.PageIndex;
import searchengine.model.Page;

public interface PageIndexRepository extends JpaRepository<PageIndex, Integer> {
    void deleteAllByPage(Page page);
}
