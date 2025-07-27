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

    @Column(nullable = false)
    private float rank;

    public PageIndex() {
    }

    public PageIndex(Page page, Lemma lemma, float rank) {
        this.page = page;
        this.lemma = lemma;
        this.rank = rank;
    }

    public void setPage(Page page) {
        this.page = page;
    }

    public void setLemma(Lemma lemma) {

        this.lemma = lemma;
    }

    public void setRank(float rank) {

        this.rank = rank;
    }
}
