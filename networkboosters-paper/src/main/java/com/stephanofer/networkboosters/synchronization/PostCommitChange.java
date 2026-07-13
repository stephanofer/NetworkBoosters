package com.stephanofer.networkboosters.synchronization;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.event.InventoryChangeCause;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.result.TransferResult;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public sealed interface PostCommitChange permits
    PostCommitChange.ActivationStarted,
    PostCommitChange.ActivationExtended,
    PostCommitChange.BoosterQueued,
    PostCommitChange.ActivationDeactivated,
    PostCommitChange.ActivationExpired,
    PostCommitChange.InventoryChanged,
    PostCommitChange.ClaimCreated,
    PostCommitChange.ClaimCompleted,
    PostCommitChange.TransferCompleted,
    PostCommitChange.TransferObserved,
    PostCommitChange.StateChanged {

    List<PlayerBoostSnapshot> snapshots();

    List<BoosterInvalidation.PlayerChange> invalidations();

    record ActivationStarted(
        PlayerBoostSnapshot snapshot,
        ActiveBooster activeBooster,
        Optional<QueuedBooster> consumedQueueEntry
    ) implements PostCommitChange {
        public ActivationStarted {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(activeBooster, "activeBooster");
            consumedQueueEntry = Objects.requireNonNull(consumedQueueEntry, "consumedQueueEntry");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.ACTIVATED,
                Optional.of(this.activeBooster.activationId())
            ));
        }
    }

    record ActivationExtended(PlayerBoostSnapshot snapshot, ActiveBooster activeBooster) implements PostCommitChange {
        public ActivationExtended {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(activeBooster, "activeBooster");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.EXTENDED,
                Optional.of(this.activeBooster.activationId())
            ));
        }
    }

    record BoosterQueued(PlayerBoostSnapshot snapshot, QueuedBooster queuedBooster, boolean merged) implements PostCommitChange {
        public BoosterQueued {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(queuedBooster, "queuedBooster");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.QUEUED,
                Optional.of(this.queuedBooster.queueId())
            ));
        }
    }

    record ActivationDeactivated(
        PlayerBoostSnapshot snapshot,
        ActiveBooster deactivatedBooster,
        Optional<ActiveBooster> promotedBooster
    ) implements PostCommitChange {
        public ActivationDeactivated {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(deactivatedBooster, "deactivatedBooster");
            promotedBooster = Objects.requireNonNull(promotedBooster, "promotedBooster");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.DEACTIVATED,
                Optional.of(this.deactivatedBooster.activationId())
            ));
        }
    }

    record ActivationExpired(
        PlayerBoostSnapshot snapshot,
        Optional<ActiveBooster> expiredActiveBooster,
        Optional<QueuedBooster> expiredQueuedBooster
    ) implements PostCommitChange {
        public ActivationExpired {
            Objects.requireNonNull(snapshot, "snapshot");
            expiredActiveBooster = Objects.requireNonNull(expiredActiveBooster, "expiredActiveBooster");
            expiredQueuedBooster = Objects.requireNonNull(expiredQueuedBooster, "expiredQueuedBooster");
            if (expiredActiveBooster.isEmpty() && expiredQueuedBooster.isEmpty()) {
                throw new IllegalArgumentException("an expired active or queued booster is required");
            }
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            Optional<java.util.UUID> reference = this.expiredActiveBooster.map(ActiveBooster::activationId)
                .or(() -> this.expiredQueuedBooster.map(QueuedBooster::queueId));
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.EXPIRED,
                reference
            ));
        }
    }

    record InventoryChanged(
        PlayerBoostSnapshot snapshot,
        BoosterId boosterId,
        long previousAmount,
        long newAmount,
        InventoryChangeCause cause,
        Optional<java.util.UUID> referenceId
    ) implements PostCommitChange {
        public InventoryChanged {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(boosterId, "boosterId");
            if (previousAmount < 0 || newAmount < 0) {
                throw new IllegalArgumentException("amounts cannot be negative");
            }
            Objects.requireNonNull(cause, "cause");
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.INVENTORY_CHANGED,
                this.referenceId
            ));
        }
    }

    record ClaimCompleted(PlayerBoostSnapshot snapshot, BoosterClaim claim, long inventoryAmount) implements PostCommitChange {
        public ClaimCompleted {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(claim, "claim");
            if (inventoryAmount < 0) {
                throw new IllegalArgumentException("inventoryAmount cannot be negative");
            }
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.CLAIMED,
                Optional.of(this.claim.claimId())
            ));
        }
    }

    record ClaimCreated(PlayerBoostSnapshot snapshot, BoosterClaim claim) implements PostCommitChange {
        public ClaimCreated {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(claim, "claim");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(
                this.snapshot.playerId(),
                this.snapshot.revision(),
                BoosterChangeType.CLAIM_CREATED,
                Optional.of(this.claim.claimId())
            ));
        }
    }

    record TransferCompleted(
        TransferResult result,
        PlayerBoostSnapshot senderSnapshot,
        PlayerBoostSnapshot recipientSnapshot
    ) implements PostCommitChange {
        public TransferCompleted {
            Objects.requireNonNull(result, "result");
            Objects.requireNonNull(senderSnapshot, "senderSnapshot");
            Objects.requireNonNull(recipientSnapshot, "recipientSnapshot");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.senderSnapshot, this.recipientSnapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            java.util.UUID transferId = this.result.transferId().orElseThrow();
            BoosterInvalidation.TransferDetails details = new BoosterInvalidation.TransferDetails(
                transferId,
                this.result.senderId(),
                this.result.recipientId(),
                this.result.boosterId(),
                this.result.amount(),
                this.senderSnapshot.revision(),
                this.recipientSnapshot.revision()
            );
            return List.of(
                new BoosterInvalidation.PlayerChange(this.senderSnapshot.playerId(), this.senderSnapshot.revision(), BoosterChangeType.TRANSFERRED, Optional.of(transferId), Optional.of(details)),
                new BoosterInvalidation.PlayerChange(this.recipientSnapshot.playerId(), this.recipientSnapshot.revision(), BoosterChangeType.TRANSFERRED, Optional.of(transferId), Optional.of(details))
            );
        }
    }

    record TransferObserved(
        BoosterInvalidation.TransferDetails details,
        Optional<PlayerBoostSnapshot> senderSnapshot,
        Optional<PlayerBoostSnapshot> recipientSnapshot
    ) implements PostCommitChange {
        public TransferObserved {
            Objects.requireNonNull(details, "details");
            senderSnapshot = Objects.requireNonNull(senderSnapshot, "senderSnapshot");
            recipientSnapshot = Objects.requireNonNull(recipientSnapshot, "recipientSnapshot");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of();
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of();
        }
    }

    record StateChanged(PlayerBoostSnapshot snapshot, BoosterChangeType type, Optional<java.util.UUID> referenceId) implements PostCommitChange {
        public StateChanged {
            Objects.requireNonNull(snapshot, "snapshot");
            Objects.requireNonNull(type, "type");
            referenceId = Objects.requireNonNull(referenceId, "referenceId");
        }

        @Override
        public List<PlayerBoostSnapshot> snapshots() {
            return List.of(this.snapshot);
        }

        @Override
        public List<BoosterInvalidation.PlayerChange> invalidations() {
            return List.of(new BoosterInvalidation.PlayerChange(this.snapshot.playerId(), this.snapshot.revision(), this.type, this.referenceId));
        }
    }
}
