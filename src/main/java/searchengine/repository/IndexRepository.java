package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import searchengine.model.Index;
import searchengine.model.Page;

public interface IndexRepository extends JpaRepository<Index, Integer> {
    void deleteAllByPage(Page page);
}
