package com.stephanofer.networkboosters.synchronization;

import com.hera.craftkit.redis.RedisClient;
import com.hera.craftkit.redis.RedisSubscription;
import com.stephanofer.networkboosters.api.event.BoosterEventOrigin;
import java.util.Objects;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;

public final class BoosterInvalidationSubscriber implements AutoCloseable {

    private final RedisSubscription subscription;

    public BoosterInvalidationSubscriber(
        RedisClient redis,
        String channel,
        String serverId,
        BoosterInvalidationCodec codec,
        PlayerInvalidationCoordinator coordinator,
        ComponentLogger logger
    ) {
        Objects.requireNonNull(redis, "redis");
        Objects.requireNonNull(channel, "channel");
        Objects.requireNonNull(serverId, "serverId");
        Objects.requireNonNull(codec, "codec");
        Objects.requireNonNull(coordinator, "coordinator");
        Objects.requireNonNull(logger, "logger");
        this.subscription = redis.subscriber().subscribe(channel, message -> codec.decode(message.payload()).ifPresentOrElse(invalidation -> {
            if (serverId.equals(invalidation.sourceServerId())) {
                return;
            }
            coordinator.accept(invalidation, BoosterEventOrigin.REMOTE);
        }, () -> logger.warn("Ignored invalid NetworkBoosters Redis invalidation on {}", message.channel())));
        this.subscription.initialRegistration().whenComplete((ignored, failure) -> {
            if (failure != null) {
                logger.warn("NetworkBoosters Redis invalidation subscription is degraded", failure);
            }
        });
    }

    @Override
    public void close() {
        this.subscription.close();
    }
}
