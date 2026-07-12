package com.stephanofer.networkboosters.persistence;

import java.nio.ByteBuffer;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;
import java.util.UUID;

public final class JdbcUuid {

    private static final int UUID_BYTES = 16;

    private JdbcUuid() {
    }

    public static byte[] toBytes(UUID uuid) {
        Objects.requireNonNull(uuid, "uuid");
        ByteBuffer buffer = ByteBuffer.allocate(UUID_BYTES);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    public static UUID fromBytes(byte[] bytes, String label) {
        Objects.requireNonNull(bytes, label);
        if (bytes.length != UUID_BYTES) {
            throw new PersistenceException(label + " must contain exactly 16 bytes, got " + bytes.length);
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    public static void set(PreparedStatement statement, int index, UUID uuid) throws SQLException {
        statement.setBytes(index, toBytes(uuid));
    }

    public static void setNullable(PreparedStatement statement, int index, UUID uuid) throws SQLException {
        if (uuid == null) {
            statement.setBytes(index, null);
            return;
        }
        set(statement, index, uuid);
    }

    public static UUID get(ResultSet result, String column) throws SQLException {
        return fromBytes(result.getBytes(column), column);
    }

    public static UUID getNullable(ResultSet result, String column) throws SQLException {
        byte[] bytes = result.getBytes(column);
        return bytes == null ? null : fromBytes(bytes, column);
    }
}
