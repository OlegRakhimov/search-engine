package searchengine.morphology;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.util.List;

public class TestLemmas {
    public static void main(String[] args) throws Exception {
        LuceneMorphology luceneMorph = new RussianLuceneMorphology();
        List<String> lemmas = luceneMorph.getNormalForms("лошадей");
        lemmas.forEach(System.out::println);
    }
}
