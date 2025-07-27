package searchengine.model;

import lombok.Getter;

import javax.persistence.*;

@Getter
@Entity
@Table(
        name = "page",
        indexes = {@javax.persistence.Index(columnList = "path")}
)
public class Page {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @ManyToOne
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    // Исправлено: используем length = 255 вместо columnDefinition = "TEXT"
    @Column(name = "path", length = 255, nullable = false)
    private String path;

    @Column(name = "code", nullable = false)
    private int code;

    // content может быть длинным, поэтому MEDIUMTEXT оставляем
    @Column(name = "content", columnDefinition = "MEDIUMTEXT", nullable = false)
    private String content;

    public void setId(int id) {
        this.id = id; }

    public void setSite(Site site) {
        this.site = site; }

    public void setPath(String path) {
        this.path = path; }

    public void setCode(int code) {
        this.code = code; }

    public void setContent(String content) {
        this.content = content; }
}
