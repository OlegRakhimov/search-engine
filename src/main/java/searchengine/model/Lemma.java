package searchengine.model;

import lombok.Getter;
import lombok.Setter;
import searchengine.model.Site;

import javax.persistence.*;

@Getter
@Setter
@Entity
public class Lemma {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Column(nullable = false)
    private String lemma;

    @Column(nullable = false)
    private int frequency;

    @ManyToOne
    private Site site;
}
