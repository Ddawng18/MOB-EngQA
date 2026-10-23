package com.example.engqa.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ImageRefsTest {

    @Test
    public void toRef_formatsIdCorrectly() {
        assertEquals("fsimg:img_12345", ImageRefs.toRef("img_12345"));
        assertEquals("fsimg:abc_xyz", ImageRefs.toRef("fsimg:abc_xyz"));
        assertNull(ImageRefs.toRef(null));
    }

    @Test
    public void isRef_identifiesRefStrings() {
        assertTrue(ImageRefs.isRef("fsimg:img_12345"));
        assertFalse(ImageRefs.isRef(null));
        assertFalse(ImageRefs.isRef(""));
        assertFalse(ImageRefs.isRef("http://example.com/image.jpg"));
        assertFalse(ImageRefs.isRef("https://firebasestorage.googleapis.com/v0/b/app/o/img.jpg"));
    }

    @Test
    public void idOf_extractsIdFromRef() {
        assertEquals("img_12345", ImageRefs.idOf("fsimg:img_12345"));
        assertNull(ImageRefs.idOf("http://example.com/image.jpg"));
        assertNull(ImageRefs.idOf(null));
        assertNull(ImageRefs.idOf(""));
    }
}
