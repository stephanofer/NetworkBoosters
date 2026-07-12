package com.stephanofer.networkboosters.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class JdbcUuidTest {

    @Test
    void uuidRoundTripPreservesAllBits() {
        UUID uuid = new UUID(Long.MIN_VALUE, Long.MAX_VALUE);

        assertEquals(uuid, JdbcUuid.fromBytes(JdbcUuid.toBytes(uuid), "uuid"));
        assertArrayEquals(new byte[16], JdbcUuid.toBytes(new UUID(0, 0)));
    }

    @Test
    void invalidBinaryLengthIsRejected() {
        assertThrows(PersistenceException.class, () -> JdbcUuid.fromBytes(new byte[15], "player_uuid"));
        assertThrows(PersistenceException.class, () -> JdbcUuid.fromBytes(new byte[17], "player_uuid"));
    }
}
