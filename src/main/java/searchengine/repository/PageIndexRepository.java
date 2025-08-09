package searchengine.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import searchengine.model.PageIndex;
import searchengine.model.Page;

import java.util.List;
import java.util.Optional;

public interface PageIndexRepository extends JpaRepository<PageIndex, Integer> {
    void deleteAllByPage(Page page);

    List<PageIndex> findByLemmaId(int lemmaId);

    Optional<PageIndex> findByPageIdAndLemmaId(int pageId, int lemmaId);

    List<PageIndex> findByPageId(int pageId);

    @Query("""
           SELECT pi
           FROM PageIndex pi
           WHERE pi.lemma.id = :lemmaId
             AND pi.page.site.id = :siteId
           """)
    List<PageIndex> findByLemmaIdAndSiteId(@Param("lemmaId") int lemmaId,
                                           @Param("siteId") int siteId);

    @Query("""
           SELECT pi.page.id
           FROM PageIndex pi
           WHERE pi.lemma.id = :lemmaId
             AND pi.page.site.id = :siteId
           """)
    List<Integer> findPageIdsByLemmaIdAndSiteId(@Param("lemmaId") int lemmaId,
                                                @Param("siteId") int siteId);
}
