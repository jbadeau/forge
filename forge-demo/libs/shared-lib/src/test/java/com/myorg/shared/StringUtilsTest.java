package com.myorg.shared;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest {

    @Test
    public void testIsEmpty() {
        assertTrue(StringUtils.isEmpty(null));
        assertTrue(StringUtils.isEmpty(""));
        assertTrue(StringUtils.isEmpty("   "));
        assertFalse(StringUtils.isEmpty("hello"));
        assertFalse(StringUtils.isEmpty(" hello "));
    }

    @Test
    public void testCapitalize() {
        assertEquals("Hello", StringUtils.capitalize("hello"));
        assertEquals("World", StringUtils.capitalize("WORLD"));
        assertEquals("", StringUtils.capitalize(""));
        assertNull(StringUtils.capitalize(null));
        assertEquals("A", StringUtils.capitalize("a"));
    }

    @Test
    public void testReverse() {
        assertEquals("olleh", StringUtils.reverse("hello"));
        assertEquals("dlrow", StringUtils.reverse("world"));
        assertEquals("", StringUtils.reverse(""));
        assertNull(StringUtils.reverse(null));
        assertEquals("a", StringUtils.reverse("a"));
    }

    @Test
    public void testTruncate() {
        assertEquals("hello", StringUtils.truncate("hello", 10));
        assertEquals("hel...", StringUtils.truncate("hello", 3));
        assertEquals("", StringUtils.truncate("", 5));
        assertNull(StringUtils.truncate(null, 5));
        assertEquals("hello world...", StringUtils.truncate("hello world test", 11));
    }
}