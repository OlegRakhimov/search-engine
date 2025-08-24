package searchengine.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import javax.persistence.*;

@Entity
@Table(name = "page", indexes = @Index(name = "idx_page_path", columnList = "path"))
@Getter
@Setter
@NoArgsConstructor
public class Page {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", foreignKey = @ForeignKey(name = "fk_page_site"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private Site site;

    @Column(name = "path", nullable = false, length = 512)
    private String path;

    @Column(nullable = false)
    private int code;

    @Column(columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;
}
