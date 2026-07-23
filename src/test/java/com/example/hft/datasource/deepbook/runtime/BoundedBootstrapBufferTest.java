package com.example.hft.datasource.deepbook.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;


class BoundedBootstrapBufferTest {
    @Test
    void enforcesBothEntryAndByteLimitsWithoutLosingAccounting() {
        BoundedBootstrapBuffer<String> buffer = new BoundedBootstrapBuffer<>(2, 5, String::length);

        assertTrue(buffer.offer("ab"));
        assertTrue(buffer.offer("cde"));
        assertFalse(buffer.offer("x"));
        assertEquals(2, buffer.size());
        assertEquals(5L, buffer.bytes());
        assertEquals(1L, buffer.overflows());

        assertEquals("ab", buffer.poll());
        assertEquals(3L, buffer.bytes());
        assertTrue(buffer.offer("xy"));
        assertEquals("cde", buffer.poll());
        assertEquals("xy", buffer.poll());
        assertNull(buffer.poll());
        assertEquals(0L, buffer.bytes());
    }
}