package com.example.engqa.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Domain model for the "Word of the Day" card and detail screen.
 *
 * <p>Gson serialises this class for the small SharedPreferences cache, so the same fields are used
 * for the API mapping and the cached copy.
 */
public class WordEntry {

    private String word;
    private String phonetic;
    private String audioUrl;
    /** yyyy-MM-dd of the day this entry was fetched. */
    private String date;
    private List<Meaning> meanings = new ArrayList<>();

    public static class Meaning {
        private String partOfSpeech;
        private List<Definition> definitions = new ArrayList<>();

        public String getPartOfSpeech() {
            return partOfSpeech;
        }

        public void setPartOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
        }

        public List<Definition> getDefinitions() {
            return definitions;
        }

        public void setDefinitions(List<Definition> definitions) {
            this.definitions = definitions;
        }
    }

    public static class Definition {
        private String text;
        private String example;

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }

        public String getExample() {
            return example;
        }

        public void setExample(String example) {
            this.example = example;
        }
    }

    public String getWord() {
        return word;
    }

    public void setWord(String word) {
        this.word = word;
    }

    public String getPhonetic() {
        return phonetic;
    }

    public void setPhonetic(String phonetic) {
        this.phonetic = phonetic;
    }

    public String getAudioUrl() {
        return audioUrl;
    }

    public void setAudioUrl(String audioUrl) {
        this.audioUrl = audioUrl;
    }

    public String getDate() {
        return date;
    }

    public void setDate(String date) {
        this.date = date;
    }

    public List<Meaning> getMeanings() {
        return meanings;
    }

    public void setMeanings(List<Meaning> meanings) {
        this.meanings = meanings;
    }

    /** First definition's text, used by the compact feed card. */
    public String primaryDefinition() {
        for (Meaning meaning : meanings) {
            if (meaning.getDefinitions() == null) {
                continue;
            }
            for (Definition definition : meaning.getDefinitions()) {
                if (definition.getText() != null && !definition.getText().isEmpty()) {
                    return definition.getText();
                }
            }
        }
        return "";
    }

    public String primaryPartOfSpeech() {
        for (Meaning meaning : meanings) {
            if (meaning.getPartOfSpeech() != null && !meaning.getPartOfSpeech().isEmpty()) {
                return meaning.getPartOfSpeech();
            }
        }
        return "";
    }
}
