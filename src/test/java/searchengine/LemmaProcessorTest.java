package searchengine;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import searchengine.morphology.LemmaProcessor;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LemmaProcessorTest {

    @Autowired
    private LemmaProcessor lemmaProcessor;

    @Test
    void russianLemmaExtraction() {
        Map<String, Integer> m = lemmaProcessor.collectLemmas("Лошади бегут быстро, а лошадь красива.");
        assertThat(m).containsKeys("лошадь");         // есть лемма
        assertThat(m.get("лошадь")).isGreaterThanOrEqualTo(2); // встречается >= 2 раз
    }
}
