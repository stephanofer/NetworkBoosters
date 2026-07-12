package com.stephanofer.networkboosters.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Set;
import org.junit.jupiter.api.Test;

class SnapshotJsonCodecTest {

    private final SnapshotJsonCodec codec = new SnapshotJsonCodec();

    @Test
    void writesStringArraysDeterministically() {
        assertEquals("[\"a\",\"b\",\"c\"]", this.codec.writeStringArray(Set.of("c", "a", "b")));
        assertEquals("[\"a\\\\b\",\"quoted\\\"\"]", this.codec.writeStringArray(Set.of("quoted\"", "a\\b")));
    }

    @Test
    void readsValidStringArrays() {
        assertEquals(Set.of("*"), this.codec.readStringArray("[\"*\"]", "scope"));
        assertEquals(Set.of("skywars", "bedwars"), this.codec.readStringArray("[\"skywars\", \"bedwars\"]", "scope"));
        assertEquals(Set.of("a\nb"), this.codec.readStringArray("[\"a\\nb\"]", "scope"));
    }

    @Test
    void rejectsUnsupportedJsonShapes() {
        assertThrows(PersistenceException.class, () -> this.codec.readStringArray("{}", "scope"));
        assertThrows(PersistenceException.class, () -> this.codec.readStringArray("[1]", "scope"));
        assertThrows(PersistenceException.class, () -> this.codec.readStringArray("[\"a\",\"a\"]", "scope"));
        assertThrows(PersistenceException.class, () -> this.codec.readStringArray("[\"a\"] true", "scope"));
    }
}
