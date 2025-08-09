package searchengine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.dto.search.SearchResponse;
import searchengine.dto.search.SearchResultItem;
import searchengine.services.SearchService;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SearchServiceTest {

    @Autowired
    private SearchService searchService;

    @Test
    void searchEnvelopeIsValid_inAnyState() {
        int limit = 5;
        SearchResponse resp = searchService.search("тест", null, 0, limit);

        assertThat(resp).isNotNull();
        assertThat(resp.getData()).isNotNull();

        if (resp.getCount() == 0) {
            assertThat(resp.isResult()).isTrue();
            assertThat(resp.getData()).isEmpty();
        } else {
            assertThat(resp.isResult()).isTrue();
            assertThat(resp.getData()).isNotEmpty();
            assertThat(resp.getData().size()).isLessThanOrEqualTo(limit);

            for (SearchResultItem it : resp.getData()) {
                assertThat(it.getSite()).isNotBlank();
                assertThat(it.getUri()).isNotBlank();
                assertThat(it.getRelevance()).isBetween(0.0, 1.0);
            }
        }
    }
}
