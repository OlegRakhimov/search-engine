package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.List;

import java.util.Map;

public class TestLemmas {
    public static void main(String[] args) throws Exception {
        LemmaProcessor processor = new LemmaProcessor();

        String input = "Повторное появление леопарда в Осетии позволяет предположить, что леопард постоянно обитает в некоторых районах Северного Кавказа.";
        Map<String, Integer> lemmas = processor.collectLemmas(input);

        lemmas.forEach((lemma, count) -> System.out.println(lemma + " — " + count));
    }
}