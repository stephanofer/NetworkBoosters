package com.stephanofer.networkboosters.event;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.event.BoosterActivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterClaimEvent;
import com.stephanofer.networkboosters.api.event.BoosterClaimCreatedEvent;
import com.stephanofer.networkboosters.api.event.BoosterDeactivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterEventOrigin;
import com.stephanofer.networkboosters.api.event.BoosterExpireEvent;
import com.stephanofer.networkboosters.api.event.BoosterExtendEvent;
import com.stephanofer.networkboosters.api.event.BoosterInventoryChangeEvent;
import com.stephanofer.networkboosters.api.event.BoosterPreActivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterQueueEvent;
import com.stephanofer.networkboosters.api.event.BoosterTransferEvent;
import com.stephanofer.networkboosters.api.event.PlayerBoostersReadyEvent;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.synchronization.PostCommitChange;
import com.stephanofer.networkboosters.synchronization.PostCommitMutation;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.logger.slf4j.ComponentLogger;
import org.bukkit.Bukkit;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.Plugin;

public final class BoosterEventDispatcher implements AutoCloseable {

    private final Plugin plugin;
    private final Server server;
    private final ComponentLogger logger;
    private final PaperThreadExecutor paperThread;
    private final String serverId;
    private final AtomicBoolean accepting = new AtomicBoolean(true);

    public BoosterEventDispatcher(Plugin plugin, ComponentLogger logger, String serverId) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.server = plugin.getServer();
        this.logger = Objects.requireNonNull(logger, "logger");
        this.serverId = Objects.requireNonNull(serverId, "serverId");
        this.paperThread = new PaperThreadExecutor(plugin);
    }

    public CompletableFuture<Boolean> callPreActivate(
        ActivationRequest request,
        BoosterDefinition definition,
        PlayerBoostSnapshot snapshot
    ) {
        Objects.requireNonNull(request, "request");
        Objects.requireNonNull(definition, "definition");
        Objects.requireNonNull(snapshot, "snapshot");
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        boolean scheduled = this.paperThread.execute(() -> {
            if (!this.accepting.get()) {
                result.complete(false);
                return;
            }
            Player player = this.server.getPlayer(request.playerId());
            if (player == null || !player.isOnline()) {
                result.complete(false);
                return;
            }
            BoosterPreActivateEvent event = new BoosterPreActivateEvent(player, request, definition, snapshot);
            this.call(event);
            result.complete(!event.isCancelled());
        });
        if (!scheduled) {
            result.complete(false);
        }
        return result;
    }

    public void callReady(Player player, PlayerBoostSnapshot snapshot) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(snapshot, "snapshot");
        this.paperThread.execute(() -> {
            if (this.accepting.get() && player.isOnline()) {
                this.call(new PlayerBoostersReadyEvent(player, snapshot, this.serverId));
            }
        });
    }

    public void dispatch(PostCommitMutation<?> mutation, BoosterEventOrigin origin, String sourceServerId) {
        Objects.requireNonNull(mutation, "mutation");
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(sourceServerId, "sourceServerId");
        if (mutation.changes().isEmpty()) {
            return;
        }
        this.dispatch(mutation.changes(), origin, sourceServerId);
    }

    public void dispatch(List<PostCommitChange> changes, BoosterEventOrigin origin, String sourceServerId) {
        List<PostCommitChange> copied = List.copyOf(Objects.requireNonNull(changes, "changes"));
        if (copied.isEmpty()) {
            return;
        }
        this.paperThread.execute(() -> {
            if (!this.accepting.get()) {
                return;
            }
            for (PostCommitChange change : copied) {
                this.dispatchOne(change, origin, sourceServerId);
            }
        });
    }

    private void dispatchOne(PostCommitChange change, BoosterEventOrigin origin, String sourceServerId) {
        switch (change) {
            case PostCommitChange.ActivationStarted started -> this.call(new BoosterActivateEvent(
                started.snapshot(), origin, sourceServerId, started.activeBooster(), started.consumedQueueEntry()
            ));
            case PostCommitChange.ActivationExtended extended -> this.call(new BoosterExtendEvent(
                extended.snapshot(), origin, sourceServerId, extended.activeBooster()
            ));
            case PostCommitChange.BoosterQueued queued -> this.call(new BoosterQueueEvent(
                queued.snapshot(), origin, sourceServerId, queued.queuedBooster(), queued.merged()
            ));
            case PostCommitChange.ActivationDeactivated deactivated -> {
                this.call(new BoosterDeactivateEvent(
                    deactivated.snapshot(), origin, sourceServerId, deactivated.deactivatedBooster(), deactivated.promotedBooster()
                ));
                deactivated.promotedBooster().ifPresent(promoted -> this.call(new BoosterActivateEvent(
                    deactivated.snapshot(), origin, sourceServerId, promoted, Optional.empty()
                )));
            }
            case PostCommitChange.ActivationExpired expired -> this.call(new BoosterExpireEvent(
                expired.snapshot(), origin, sourceServerId, expired.expiredActiveBooster(), expired.expiredQueuedBooster()
            ));
            case PostCommitChange.InventoryChanged inventory -> this.call(new BoosterInventoryChangeEvent(
                inventory.snapshot(),
                origin,
                sourceServerId,
                inventory.boosterId(),
                inventory.previousAmount(),
                inventory.newAmount(),
                inventory.cause(),
                inventory.referenceId()
            ));
            case PostCommitChange.ClaimCompleted claim -> this.call(new BoosterClaimEvent(
                claim.snapshot(), origin, sourceServerId, claim.claim(), claim.inventoryAmount()
            ));
            case PostCommitChange.ClaimCreated claim -> this.call(new BoosterClaimCreatedEvent(
                claim.snapshot(), origin, sourceServerId, claim.claim()
            ));
            case PostCommitChange.TransferCompleted transfer -> this.call(new BoosterTransferEvent(
                transfer.result().transferId().orElseThrow(),
                transfer.result().senderId(),
                transfer.result().recipientId(),
                transfer.result().boosterId(),
                transfer.result().amount(),
                transfer.senderSnapshot().revision(),
                transfer.recipientSnapshot().revision(),
                Optional.of(transfer.senderSnapshot()),
                Optional.of(transfer.recipientSnapshot()),
                origin,
                sourceServerId
            ));
            case PostCommitChange.TransferObserved transfer -> this.call(new BoosterTransferEvent(
                transfer.details().transferId(),
                transfer.details().senderId(),
                transfer.details().recipientId(),
                transfer.details().boosterId(),
                transfer.details().amount(),
                transfer.details().senderRevision(),
                transfer.details().recipientRevision(),
                transfer.senderSnapshot(),
                transfer.recipientSnapshot(),
                origin,
                sourceServerId
            ));
            case PostCommitChange.StateChanged ignored -> {
            }
        }
    }

    private void call(Event event) {
        try {
            Bukkit.getPluginManager().callEvent(event);
        } catch (RuntimeException exception) {
            this.logger.error("A NetworkBoosters event listener failed for {}", event.getEventName(), exception);
        }
    }

    @Override
    public void close() {
        this.accepting.set(false);
    }
}
