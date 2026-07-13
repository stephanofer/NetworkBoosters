package com.stephanofer.networkboosters.synchronization;

import com.hera.craftkit.redis.RedisClient;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class BoosterInvalidationPublisher {

    private final RedisClient redis;
    private final String channel;
    private final String serverId;
    private final Clock clock;
    private final BoosterInvalidationCodec codec;
    private final ComponentLogger logger;

    public BoosterInvalidationPublisher(
        RedisClient redis,
        String channel,
        String serverId,
        Clock clock,
        BoosterInvalidationCodec codec,
        ComponentLogger logger
    ) {
        this.redis = redis;
        this.channel = Objects.requireNonNull(channel, "channel");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    public void publish(PostCommitMutation<?> mutation) {
        if (this.redis == null || mutation.changes().isEmpty()) {
            return;
        }
        ArrayList<BoosterInvalidation.PlayerChange> changes = new ArrayList<>();
        for (PostCommitChange change : mutation.changes()) {
            changes.addAll(change.invalidations());
        }
        if (changes.isEmpty()) {
            return;
        }
        BoosterInvalidation invalidation = new BoosterInvalidation(
            BoosterInvalidation.CURRENT_SCHEMA,
            UUID.randomUUID(),
            this.serverId,
            this.clock.instant(),
            changes
        );
        String payload = this.codec.encode(invalidation);
        this.redis.publisher().publish(this.channel, payload).whenComplete((subscribers, failure) -> {
            if (failure != null) {
                this.logger.warn(
                    "Failed to publish NetworkBoosters invalidation {} with {} player change(s)",
                    invalidation.eventId(),
                    invalidation.changes().size(),
                    failure
                );
                return;
            }
            this.logger.debug(
                "Published NetworkBoosters invalidation {} to {} subscriber(s)",
                invalidation.eventId(),
                subscribers
            );
        });
    }
}
