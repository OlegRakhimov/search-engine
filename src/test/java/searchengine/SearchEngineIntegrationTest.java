package searchengine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import searchengine.services.IndexingService;
import searchengine.services.SearchService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SearchEngineIntegrationTest {

    @MockBean
    private IndexingService indexingService;

    @Autowired
    private SearchService searchService;

    @Test
    void contextLoads_andIndexingServiceWired() {
        assertThat(searchService).isNotNull();
        assertThat(indexingService).isNotNull();
    }

    @Test
    void startAndStopIndexing_invokesServiceMethods() {
        indexingService.startIndexing();
        indexingService.stopIndexing();

        verify(indexingService, times(1)).startIndexing();
        verify(indexingService, times(1)).stopIndexing();
        verifyNoMoreInteractions(indexingService);
    }
}
