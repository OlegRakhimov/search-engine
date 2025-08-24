package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Page;
import searchengine.model.PageIndex;

import java.util.List;
import java.util.Optional;

public interface PageIndexRepository extends JpaRepository<PageIndex, Integer> {
    void deleteAllByPage(Page page);
    List<PageIndex> findByLemmaId(int lemmaId);
    Optional<PageIndex> findByPageIdAndLemmaId(int pageId, int lemmaId);
    List<PageIndex> findAllByPage(Page page);
}
