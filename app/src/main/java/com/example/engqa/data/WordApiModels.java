package com.example.engqa.data;

import java.util.List;

/** Gson DTOs for the free dictionary response used by {@link WordRepository}. */
public final class WordApiModels {

    private WordApiModels() {
    }

    public static class Entry {
        public String word;
        public String phonetic;
        public List<Phonetic> phonetics;
        public List<Meaning> meanings;
    }

    public static class Phonetic {
        public String text;
        public String audio;
    }

    public static class Meaning {
        public String partOfSpeech;
        public List<Definition> definitions;
    }

    public static class Definition {
        public String definition;
        public String example;
    }
}
