package com.stephanofer.networkboosters.synchronization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import org.junit.jupiter.api.Test;

final class BoosterInvalidationCodecTest {

    private final BoosterInvalidationCodec codec = new BoosterInvalidationCodec();

    @Test
    void roundTripsValidInvalidation() {
        UUID eventId = UUID.randomUUID();
        UUID playerId = UUID.randomUUID();
        UUID activationId = UUID.randomUUID();
        BoosterInvalidation invalidation = new BoosterInvalidation(
            BoosterInvalidation.CURRENT_SCHEMA,
            eventId,
            "skywars-01",
            Instant.parse("2026-07-12T18:30:20.123Z"),
            List.of(new BoosterInvalidation.PlayerChange(playerId, 42, BoosterChangeType.ACTIVATED, Optional.of(activationId)))
        );

        Optional<BoosterInvalidation> decoded = this.codec.decode(this.codec.encode(invalidation));

        assertTrue(decoded.isPresent());
        assertEquals(invalidation, decoded.orElseThrow());
    }

    @Test
    void rejectsUnknownSchema() {
        String payload = "{\"schemaVersion\":2,\"eventId\":\"" + UUID.randomUUID()
            + "\",\"sourceServerId\":\"lobby-01\",\"occurredAt\":\"2026-07-12T18:30:20Z\",\"changes\":[]}";

        assertTrue(this.codec.decode(payload).isEmpty());
    }

    @Test
    void rejectsMalformedUuid() {
        String payload = "{\"schemaVersion\":1,\"eventId\":\"not-a-uuid\",\"sourceServerId\":\"lobby-01\","
            + "\"occurredAt\":\"2026-07-12T18:30:20Z\",\"changes\":[]}";

        assertTrue(this.codec.decode(payload).isEmpty());
    }

    @Test
    void rejectsEmptyChanges() {
        String payload = "{\"schemaVersion\":1,\"eventId\":\"" + UUID.randomUUID()
            + "\",\"sourceServerId\":\"lobby-01\",\"occurredAt\":\"2026-07-12T18:30:20Z\",\"changes\":[]}";

        assertTrue(this.codec.decode(payload).isEmpty());
    }

    @Test
    void preservesLongRevisionAndTransferMetadata() {
        UUID sender = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        UUID transferId = UUID.randomUUID();
        long senderRevision = (long) Integer.MAX_VALUE + 4L;
        BoosterInvalidation.TransferDetails transfer = new BoosterInvalidation.TransferDetails(
            transferId,
            sender,
            recipient,
            BoosterId.of("personal_points_x2"),
            5,
            senderRevision,
            senderRevision + 1
        );
        BoosterInvalidation invalidation = new BoosterInvalidation(
            BoosterInvalidation.CURRENT_SCHEMA,
            UUID.randomUUID(),
            "lobby-01",
            Instant.now(),
            List.of(new BoosterInvalidation.PlayerChange(
                sender,
                senderRevision,
                BoosterChangeType.TRANSFERRED,
                Optional.of(transferId),
                Optional.of(transfer)
            ))
        );

        BoosterInvalidation decoded = this.codec.decode(this.codec.encode(invalidation)).orElseThrow();

        assertEquals(invalidation, decoded);
    }
}
