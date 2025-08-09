package searchengine.dto.search;

import java.util.List;

public class SearchResponse {
    private boolean result;
    private int count;
    private List<SearchResultItem> data;
    private String error;

    public boolean isResult() {
        return result;
    }

    public void setResult(boolean result) {
        this.result = result;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<SearchResultItem> getData() {
        return data;
    }

    public void setData(List<SearchResultItem> data) {
        this.data = data;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
