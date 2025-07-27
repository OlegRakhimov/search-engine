package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.HashMap;
import java.util.List;

import java.util.Map;

public class TestLemmas {
    public static void main(String[] args) throws Exception {
        RussianLuceneMorphology morphology = new RussianLuceneMorphology();

        String input = "Повторное появление леопарда в Осетии позволяет предположить...";
        String[] words = input.toLowerCase().split("[^а-яА-Яa-zA-Z]+");

        Map<String, Integer> lemmas = new HashMap<>();

        for (String word : words) {
            if (word.isBlank()) continue;

            List<String> morphInfo = morphology.getMorphInfo(word);
            if (morphInfo.stream().anyMatch(m -> m.contains("СОЮЗ") || m.contains("МЕЖД") || m.contains("ПРЕДЛ") || m.contains("ЧАСТ")))
                continue;

            List<String> normalForms = morphology.getNormalForms(word);
            if (!normalForms.isEmpty()) {
                String lemma = normalForms.get(0);
                lemmas.put(lemma, lemmas.getOrDefault(lemma, 0) + 1);
            }
        }

        lemmas.forEach((lemma, count) -> System.out.println(lemma + " — " + count));
    }
}
