package searchengine.dto.search;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Setter
@Getter
public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResultItem> data;
    private String error;
}
