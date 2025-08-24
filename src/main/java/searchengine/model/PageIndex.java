package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import javax.persistence.*;

@Entity
@Table(
        name = "page_index",
        uniqueConstraints = @UniqueConstraint(name = "uq_page_lemma", columnNames = {"page_id","lemma_id"})
)
@Getter @Setter @NoArgsConstructor
public class PageIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "page_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_index_page"))
    private Page page;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "lemma_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_index_lemma"))
    private Lemma lemma;

    @Column(name = "rank_value", nullable = false)
    private float rank;
}
