package searchengine.model;

import javax.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int id;

    @Enumerated(EnumType.STRING)
    @Column(columnDefinition = "ENUM('INDEXING', 'INDEXED', 'FAILED')", nullable = false)
    private Status status;

    @Column(name = "status_time", nullable = false)
    private LocalDateTime statusTime;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "url", columnDefinition = "VARCHAR(255)", nullable = false, unique = true)
    private String url;

    @Column(name = "name", columnDefinition = "VARCHAR(255)", nullable = false)
    private String name;

    public int getId() {
        return id; }
    public void setId(int id) {
        this.id = id; }

    public Status getStatus() {
        return status; }
    public void setStatus(Status status) {
        this.status = status; }

    public LocalDateTime getStatusTime() {
        return statusTime; }
    public void setStatusTime(LocalDateTime statusTime) {
        this.statusTime = statusTime; }

    public String getLastError() {
        return lastError; }
    public void setLastError(String lastError) {
        this.lastError = lastError; }

    public String getUrl() {
        return url; }
    public void setUrl(String url) {
        this.url = url; }

    public String getName() {
        return name; }
    public void setName(String name) {
        this.name = name; }
}
