package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class SearchResultItem {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private double relevance;
}
