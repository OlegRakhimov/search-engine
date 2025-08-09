package searchengine.model;

import lombok.Getter;

import javax.persistence.*;

@Getter
@Entity
@Table(name = "page_index")
public class PageIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "page_id", nullable = false)
    private Page page;

    @ManyToOne
    @JoinColumn(name = "lemma_id", nullable = false)
    private Lemma lemma;

    @Column(name = "page_rank", nullable = false)
    private float pageRank;

    public PageIndex() {
    }

    public PageIndex(Page page, Lemma lemma, float pageRank) {
        this.page = page;
        this.lemma = lemma;
        this.pageRank = pageRank;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public void setLemma(Lemma lemma) {
        this.lemma = lemma;
    }

    public void setPageRank(float pageRank) {
        this.pageRank = pageRank;
    }

    public float getPageRank() {
        return pageRank;
    }
}
