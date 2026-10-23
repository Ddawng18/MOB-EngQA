package com.example.engqa.media;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Unit tests for the Firestore media budget. */
public class MediaBudgetTest {

    @Test
    public void base64Length_includesPadding() {
        assertEquals(0, MediaBudget.base64Length(0));
        assertEquals(4, MediaBudget.base64Length(1));
        assertEquals(4, MediaBudget.base64Length(3));
        assertEquals(8, MediaBudget.base64Length(4));
    }

    @Test
    public void fitsDocument_usesTheBase64Budget() {
        assertTrue(MediaBudget.fitsDocument(MediaBudget.MAX_BINARY_BYTES));
        assertTrue(MediaBudget.fitsDocument(10));
        assertFalse(MediaBudget.fitsDocument(MediaBudget.MAX_BINARY_BYTES + 1));
        assertFalse(MediaBudget.fitsDocument(0));
    }

    @Test
    public void maxBinaryBudget_staysBelowSixHundredThousandChars() {
        assertTrue(MediaBudget.base64Length(MediaBudget.MAX_BINARY_BYTES) <= 600_000);
        assertTrue(MediaBudget.base64Length(MediaBudget.MAX_BINARY_BYTES + 1) > 600_000);
    }

    @Test
    public void audioBudget_isSmallerThanTheDocumentLimit() {
        assertTrue(MediaBudget.isAudioWithinBudget(MediaBudget.MAX_AUDIO_BYTES));
        assertFalse(MediaBudget.isAudioWithinBudget(MediaBudget.MAX_AUDIO_BYTES + 1));
        assertFalse(MediaBudget.isAudioWithinBudget(0));
        assertTrue(MediaBudget.base64Length(MediaBudget.MAX_AUDIO_BYTES) <= 600_000);
    }
}
