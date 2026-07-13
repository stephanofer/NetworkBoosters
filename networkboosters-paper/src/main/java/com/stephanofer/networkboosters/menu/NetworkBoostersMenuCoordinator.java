package com.stephanofer.networkboosters.menu;

import com.hera.craftkit.zmenu.ZMenuIntegration;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.event.BoosterActivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterClaimEvent;
import com.stephanofer.networkboosters.api.event.BoosterDeactivateEvent;
import com.stephanofer.networkboosters.api.event.BoosterExpireEvent;
import com.stephanofer.networkboosters.api.event.BoosterExtendEvent;
import com.stephanofer.networkboosters.api.event.BoosterInventoryChangeEvent;
import com.stephanofer.networkboosters.api.event.BoosterQueueEvent;
import com.stephanofer.networkboosters.api.event.BoosterTransferEvent;
import com.stephanofer.networkboosters.api.event.PlayerBoostersReadyEvent;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.api.request.BoosterTransferRequest;
import com.stephanofer.networkboosters.api.request.ClaimRequest;
import com.stephanofer.networkboosters.api.result.ActivationResult;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.result.ClaimResult;
import com.stephanofer.networkboosters.api.result.TransferResult;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.command.NetworkBoostersCommandRuntime;
import com.stephanofer.networkboosters.config.booster.BoosterDisplay;
import com.stephanofer.networkboosters.localization.DurationFormatter;
import com.stephanofer.networkboosters.localization.MessageArguments;
import com.stephanofer.networkboosters.localization.MessageKey;
import fr.maxlego08.menu.api.utils.Placeholders;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class NetworkBoostersMenuCoordinator implements Listener, AutoCloseable {

    public static final String MAIN_MENU = "boosters";
    public static final String ACTIVATION_MENU = "booster-confirm";
    public static final String TRANSFER_TARGET_MENU = "booster-transfer-target";
    public static final String TRANSFER_MENU = "booster-transfer";
    public static final String CLAIMS_MENU = "booster-claims";

    private static final Duration ACTIVATION_TTL = Duration.ofSeconds(30);
    private static final Duration TRANSFER_TTL = Duration.ofSeconds(45);

    private final NetworkBoostersCommandRuntime runtime;
    private final ZMenuIntegration zmenu;
    private final MenuSessionStore sessions = new MenuSessionStore();
    private final ConcurrentHashMap<UUID, Boolean> activeOperations = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Boolean> activeClaims = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final DurationFormatter durations = new DurationFormatter();

    public NetworkBoostersMenuCoordinator(NetworkBoostersCommandRuntime runtime, ZMenuIntegration zmenu) {
        this.runtime = Objects.requireNonNull(runtime, "runtime");
        this.zmenu = Objects.requireNonNull(zmenu, "zmenu");
    }

    public MenuSession session(Player player) {
        return this.sessions.get(player.getUniqueId());
    }

    public List<OwnedBoosterView> views(Player player) {
        MenuSession session = this.session(player);
        PlayerBoostSnapshot snapshot = this.runtime.service().getCachedOrEmpty(player.getUniqueId());
        return BoosterMenuViews.ownedViews(
            snapshot,
            this.runtime.configurationStore().requireCurrent().definitions().asMap(),
            player::hasPermission,
            this.runtime.configurationStore().requireCurrent().configuration().gameId(),
            this.runtime.configurationStore().requireCurrent().configuration().serverId(),
            session.filter(),
            session.sort()
        );
    }

    public void openMain(Player player) {
        if (!this.canTouchUi(player)) {
            return;
        }
        this.sessions.get(player.getUniqueId());
        if (!this.runtime.service().isReady(player.getUniqueId())) {
            this.runtime.service().load(player.getUniqueId()).whenComplete((ignored, failure) -> this.sync(player, () -> {
                if (failure != null) {
                    this.send(player, MessageKey.COMMON_PLAYER_NOT_READY);
                    return;
                }
                this.zmenu.open(player, MAIN_MENU, this.session(player).page());
            }));
            return;
        }
        this.zmenu.open(player, MAIN_MENU, this.session(player).page());
    }

    public void openClaims(Player player) {
        if (this.canTouchUi(player)) {
            this.zmenu.open(player, CLAIMS_MENU, 1);
        }
    }

    public void cycleFilter(Player player) {
        this.sessions.update(player.getUniqueId(), session -> session.withFilter(session.filter().next()));
        this.update(player);
    }

    public void cycleSort(Player player) {
        this.sessions.update(player.getUniqueId(), session -> session.withSort(session.sort().next()));
        this.update(player);
    }

    public void beginActivation(Player player, BoosterId boosterId, int page) {
        if (!this.canTouchUi(player)) {
            return;
        }
        PlayerBoostSnapshot snapshot = this.runtime.service().getCachedOrEmpty(player.getUniqueId());
        MenuSession session = this.sessions.update(player.getUniqueId(), current -> current.withPage(page).withActivation(new PendingActivation(
            UUID.randomUUID(),
            player.getUniqueId(),
            boosterId,
            snapshot.revision(),
            Math.max(1, page),
            current.filter(),
            current.sort(),
            this.runtime.clock().instant().plus(ACTIVATION_TTL)
        )));
        if (session.activation().isPresent()) {
            this.zmenu.openWithHistory(player, ACTIVATION_MENU, 1);
        }
    }

    public Optional<PendingActivation> pendingActivation(Player player) {
        Instant now = this.runtime.clock().instant();
        Optional<PendingActivation> pending = this.session(player).activation().filter(value -> !value.expired(now));
        if (pending.isEmpty()) {
            this.sessions.update(player.getUniqueId(), MenuSession::clearPending);
        }
        return pending;
    }

    public void confirmActivation(Player player) {
        Optional<PendingActivation> pending = this.pendingActivation(player);
        if (pending.isEmpty()) {
            this.send(player, MessageKey.ACTIVATION_DEFINITION_CHANGED);
            this.openMain(player);
            return;
        }
        PendingActivation activation = pending.orElseThrow();
        if (this.activeOperations.putIfAbsent(player.getUniqueId(), true) != null) {
            return;
        }
        this.sessions.update(player.getUniqueId(), MenuSession::clearPending);
        this.runtime.service().activate(new ActivationRequest(player.getUniqueId(), activation.boosterId(), ActivationSource.PLAYER_MENU, this.reference(player, "menu-activate")))
            .whenComplete((result, failure) -> this.sync(player, () -> {
                this.activeOperations.remove(player.getUniqueId());
                this.sendActivation(player, activation.boosterId(), result, failure);
                this.runtime.service().refresh(player.getUniqueId());
                this.sessions.update(player.getUniqueId(), session -> session.withFilter(activation.returnFilter()).withSort(activation.returnSort()).withPage(activation.returnPage()));
                this.openMain(player);
            }));
    }

    public void beginTransfer(Player player, BoosterId boosterId, int page) {
        PlayerBoostSnapshot snapshot = this.runtime.service().getCachedOrEmpty(player.getUniqueId());
        this.sessions.update(player.getUniqueId(), current -> current.withPage(page).withTransfer(new PendingTransfer(
            UUID.randomUUID(),
            player.getUniqueId(),
            player.getUniqueId(),
            boosterId,
            1,
            snapshot.revision(),
            snapshot.revision(),
            Math.max(1, page),
            current.filter(),
            current.sort(),
            this.runtime.clock().instant().plus(TRANSFER_TTL)
        )));
        this.zmenu.openWithHistory(player, TRANSFER_TARGET_MENU, 1);
    }

    public List<Player> transferTargets(Player sender) {
        return this.runtime.server().getOnlinePlayers().stream()
            .map(Player.class::cast)
            .filter(player -> !player.getUniqueId().equals(sender.getUniqueId()))
            .filter(player -> this.runtime.service().isReady(player.getUniqueId()))
            .sorted(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER).thenComparing(Player::getUniqueId))
            .toList();
    }

    public List<BoosterClaim> claims(Player player) {
        return this.runtime.service().getCachedOrEmpty(player.getUniqueId()).pendingClaims();
    }

    public void selectTransferTarget(Player sender, Player recipient) {
        PendingTransfer current = this.pendingTransfer(sender).orElse(null);
        if (current == null || !recipient.isOnline()) {
            this.send(sender, MessageKey.TRANSFER_RECIPIENT_NOT_ONLINE);
            this.openMain(sender);
            return;
        }
        PlayerBoostSnapshot recipientSnapshot = this.runtime.service().getCachedOrEmpty(recipient.getUniqueId());
        this.sessions.update(sender.getUniqueId(), ignored -> ignored.withTransfer(new PendingTransfer(
            UUID.randomUUID(),
            sender.getUniqueId(),
            recipient.getUniqueId(),
            current.boosterId(),
            current.amount(),
            current.senderRevision(),
            recipientSnapshot.revision(),
            current.returnPage(),
            current.returnFilter(),
            current.returnSort(),
            this.runtime.clock().instant().plus(TRANSFER_TTL)
        )));
        this.zmenu.openWithHistory(sender, TRANSFER_MENU, 1);
    }

    public Optional<PendingTransfer> pendingTransfer(Player player) {
        Instant now = this.runtime.clock().instant();
        Optional<PendingTransfer> pending = this.session(player).transfer().filter(value -> !value.expired(now));
        if (pending.isEmpty()) {
            this.sessions.update(player.getUniqueId(), MenuSession::clearPending);
        }
        return pending;
    }

    public void changeTransferAmount(Player player, long delta) {
        Optional<PendingTransfer> pending = this.pendingTransfer(player);
        if (pending.isEmpty()) {
            this.openMain(player);
            return;
        }
        PendingTransfer transfer = pending.orElseThrow();
        long owned = this.runtime.service().getCachedOrEmpty(player.getUniqueId()).ownedAmount(transfer.boosterId());
        long max = this.runtime.service().definition(transfer.boosterId()).map(definition -> definition.transferPolicy().maximumAmount()).orElse(owned);
        long next = Math.max(1, Math.min(Math.min(owned, max), transfer.amount() + delta));
        this.sessions.update(player.getUniqueId(), session -> session.withTransfer(transfer.withAmount(next)));
        this.update(player);
    }

    public void confirmTransfer(Player sender) {
        Optional<PendingTransfer> pending = this.pendingTransfer(sender);
        if (pending.isEmpty()) {
            this.send(sender, MessageKey.ACTIVATION_DEFINITION_CHANGED);
            this.openMain(sender);
            return;
        }
        PendingTransfer transfer = pending.orElseThrow();
        Player recipient = this.runtime.server().getPlayer(transfer.recipientId());
        if (recipient == null) {
            this.send(sender, MessageKey.TRANSFER_RECIPIENT_NOT_ONLINE);
            this.openMain(sender);
            return;
        }
        if (this.activeOperations.putIfAbsent(sender.getUniqueId(), true) != null) {
            return;
        }
        this.sessions.update(sender.getUniqueId(), MenuSession::clearPending);
        this.runtime.service().transfer(new BoosterTransferRequest(sender.getUniqueId(), recipient.getUniqueId(), transfer.boosterId(), transfer.amount(), TransferSource.PLAYER_MENU, this.reference(sender, "menu-transfer")))
            .whenComplete((result, failure) -> this.sync(sender, () -> {
                this.activeOperations.remove(sender.getUniqueId());
                this.sendTransfer(sender, recipient, result, failure);
                this.sessions.update(sender.getUniqueId(), session -> session.withFilter(transfer.returnFilter()).withSort(transfer.returnSort()).withPage(transfer.returnPage()));
                this.openMain(sender);
            }));
    }

    public void claim(Player player, BoosterClaim claim) {
        if (this.activeClaims.putIfAbsent(claim.claimId(), true) != null) {
            return;
        }
        this.runtime.service().claim(new ClaimRequest(player.getUniqueId(), claim.claimId()))
            .whenComplete((result, failure) -> this.sync(player, () -> {
                this.activeClaims.remove(claim.claimId());
                this.sendClaim(player, result, failure);
                this.update(player);
            }));
    }

    public void update(Player player) {
        if (this.canTouchUi(player)) {
            this.zmenu.inventories().updateInventory(player);
        }
    }

    public Placeholders basePlaceholders(Player player) {
        MenuSession session = this.session(player);
        PlayerBoostSnapshot snapshot = this.runtime.service().getCachedOrEmpty(player.getUniqueId());
        Placeholders placeholders = new Placeholders();
        placeholders.register("filter", session.filter().name().toLowerCase(java.util.Locale.ROOT));
        placeholders.register("sort", session.sort().name().toLowerCase(java.util.Locale.ROOT));
        placeholders.register("owned_total", String.valueOf(snapshot.ownedTotal()));
        placeholders.register("active_count", String.valueOf(snapshot.activeBoosters().size()));
        placeholders.register("queue_count", String.valueOf(snapshot.queuedBoosters().values().stream().mapToLong(List::size).sum()));
        placeholders.register("claims_count", String.valueOf(snapshot.pendingClaims().size()));
        placeholders.register("capacity", String.valueOf(this.runtime.capacity(player).maximum()));
        return placeholders;
    }

    public Placeholders boosterPlaceholders(Player player, OwnedBoosterView view) {
        Placeholders placeholders = this.basePlaceholders(player);
        placeholders.register("booster_id", view.boosterId().value());
        placeholders.register("amount", String.valueOf(view.amount()));
        placeholders.register("state", view.state().name().toLowerCase(java.util.Locale.ROOT));
        placeholders.register("transferable", String.valueOf(view.transferable()));
        view.definition().ifPresentOrElse(definition -> {
            placeholders.register("multiplier", definition.multiplier().toPlainString());
            placeholders.register("duration", this.format(player, definition.duration()));
            placeholders.register("target", definition.target().key());
            placeholders.register("category", definition.category().value());
            placeholders.register("queue_size", String.valueOf(view.queue().size()));
            placeholders.register("requirements", definition.requirements().permissions().isEmpty() ? "none" : String.join(", ", definition.requirements().permissions()));
        }, () -> {
            placeholders.register("multiplier", "?");
            placeholders.register("duration", "?");
            placeholders.register("target", "?");
            placeholders.register("category", "?");
            placeholders.register("queue_size", "0");
            placeholders.register("requirements", "?");
        });
        return placeholders;
    }

    public ItemStack applyDisplay(OwnedBoosterView view, ItemStack itemStack) {
        BoosterDisplay display = this.runtime.configurationStore().requireCurrent().definitions().display(view.boosterId());
        String materialName = switch (view.state()) {
            case BLOCKED_PERMISSION -> display.lockedMaterial();
            case ACTIVE, EXTENDABLE, QUEUED -> display.activeMaterial();
            default -> display.material();
        };
        Material material = Material.matchMaterial(materialName);
        if (material != null && material.isItem()) {
            itemStack.setType(material);
        }
        if (display.customModelData().isPresent()) {
            ItemMeta meta = itemStack.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(display.customModelData().getAsInt());
                itemStack.setItemMeta(meta);
            }
        }
        return itemStack;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.sessions.remove(event.getPlayer().getUniqueId());
        this.activeOperations.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler public void onReady(PlayerBoostersReadyEvent event) { this.refresh(event.player().getUniqueId()); }
    @EventHandler public void onInventory(BoosterInventoryChangeEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onActivate(BoosterActivateEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onExtend(BoosterExtendEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onQueue(BoosterQueueEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onExpire(BoosterExpireEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onDeactivate(BoosterDeactivateEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onClaim(BoosterClaimEvent event) { this.refresh(event.playerId()); }
    @EventHandler public void onTransfer(BoosterTransferEvent event) { this.refresh(event.senderId()); this.refresh(event.recipientId()); }

    private void refresh(UUID playerId) {
        Player player = this.runtime.server().getPlayer(playerId);
        if (player != null) {
            this.update(player);
        }
    }

    private boolean canTouchUi(Player player) {
        return !this.closed.get() && this.runtime.isRunning() && player.isOnline();
    }

    private void sync(Player player, Runnable runnable) {
        this.runtime.server().getScheduler().runTask(this.runtime.plugin(), () -> {
            if (this.canTouchUi(player)) {
                runnable.run();
            }
        });
    }

    private String format(Player player, Duration duration) {
        return this.durations.format(duration, this.runtime.configurationStore().requireCurrent().localization(), this.runtime.localization().language(player));
    }

    private SourceReference reference(Player player, String operation) {
        return new SourceReference(Optional.of(player.getUniqueId()), Optional.of(operation), Optional.of(this.runtime.configurationStore().requireCurrent().configuration().serverId()));
    }

    private void send(Player player, MessageKey key) {
        player.sendMessage(this.runtime.localization().message(player, key));
    }

    private void sendActivation(Player player, BoosterId boosterId, ActivationResult result, Throwable failure) {
        if (failure != null || result == null) {
            this.send(player, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        MessageKey key = switch (result.status()) {
            case ACTIVATED -> MessageKey.ACTIVATION_ACTIVATED;
            case EXTENDED -> MessageKey.ACTIVATION_EXTENDED;
            case QUEUED -> MessageKey.ACTIVATION_QUEUED;
            case QUEUE_MERGED -> MessageKey.ACTIVATION_QUEUE_MERGED;
            case REPLACED -> MessageKey.ACTIVATION_REPLACED;
            case NOT_OWNED -> MessageKey.ACTIVATION_NOT_OWNED;
            case DEFINITION_NOT_FOUND -> MessageKey.ACTIVATION_DEFINITION_NOT_FOUND;
            case DEFINITION_CHANGED -> MessageKey.ACTIVATION_DEFINITION_CHANGED;
            case DEFINITION_DISABLED -> MessageKey.ACTIVATION_DEFINITION_DISABLED;
            case PERMISSION_DENIED -> MessageKey.ACTIVATION_PERMISSION_DENIED;
            case PRE_ACTIVATION_CANCELLED -> MessageKey.ACTIVATION_CANCELLED;
            case GROUP_OCCUPIED -> MessageKey.ACTIVATION_GROUP_OCCUPIED;
            case QUEUE_LIMIT_REACHED -> MessageKey.ACTIVATION_QUEUE_LIMIT;
            case DURATION_LIMIT_REACHED -> MessageKey.ACTIVATION_DURATION_LIMIT;
            case PLAYER_NOT_READY -> MessageKey.ACTIVATION_PLAYER_NOT_READY;
            case SERVICE_UNAVAILABLE -> MessageKey.COMMON_SERVICE_UNAVAILABLE;
        };
        player.sendMessage(this.runtime.localization().message(player, key, MessageArguments.component("name", this.runtime.localization().boosterName(player, boosterId.value())), MessageArguments.text("amount", result.remainingInventoryAmount())));
    }

    private void sendTransfer(Player sender, Player recipient, TransferResult result, Throwable failure) {
        if (failure != null || result == null) {
            this.send(sender, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        MessageKey key = switch (result.status()) {
            case TRANSFERRED -> MessageKey.TRANSFER_TRANSFERRED_SENDER;
            case SAME_PLAYER -> MessageKey.TRANSFER_SAME_PLAYER;
            case RECIPIENT_NOT_ONLINE -> MessageKey.TRANSFER_RECIPIENT_NOT_ONLINE;
            case NOT_TRANSFERABLE -> MessageKey.TRANSFER_NOT_TRANSFERABLE;
            case INVALID_AMOUNT -> MessageKey.TRANSFER_INVALID_AMOUNT;
            case INSUFFICIENT_AMOUNT -> MessageKey.TRANSFER_INSUFFICIENT;
            case RECIPIENT_LIMIT_REACHED -> MessageKey.TRANSFER_RECIPIENT_FULL;
            case COOLDOWN -> MessageKey.TRANSFER_COOLDOWN;
            case PERMISSION_DENIED -> MessageKey.TRANSFER_PERMISSION_DENIED;
            case PLAYER_NOT_READY -> MessageKey.TRANSFER_PLAYER_NOT_READY;
            case SERVICE_UNAVAILABLE -> MessageKey.COMMON_SERVICE_UNAVAILABLE;
        };
        sender.sendMessage(this.runtime.localization().message(sender, key,
            MessageArguments.component("name", this.runtime.localization().boosterName(sender, result.boosterId().value())),
            MessageArguments.text("amount", result.amount()),
            MessageArguments.text("recipient", recipient.getName()),
            MessageArguments.text("remaining", result.retryAt().map(instant -> this.format(sender, Duration.between(this.runtime.clock().instant(), instant))).orElse(""))));
        if (result.status() == TransferStatus.TRANSFERRED && recipient.isOnline()) {
            recipient.sendMessage(this.runtime.localization().message(recipient, MessageKey.TRANSFER_TRANSFERRED_RECIPIENT,
                MessageArguments.component("name", this.runtime.localization().boosterName(recipient, result.boosterId().value())),
                MessageArguments.text("amount", result.amount()),
                MessageArguments.text("sender", sender.getName())));
        }
    }

    private void sendClaim(Player player, ClaimResult result, Throwable failure) {
        if (failure != null || result == null) {
            this.send(player, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        MessageKey key = switch (result.status()) {
            case CLAIMED -> MessageKey.CLAIMS_CLAIMED;
            case NOT_FOUND -> MessageKey.CLAIMS_NOT_FOUND;
            case ALREADY_CLAIMED -> MessageKey.CLAIMS_ALREADY_CLAIMED;
            case INVENTORY_LIMIT_REACHED -> MessageKey.CLAIMS_INVENTORY_FULL;
            case PLAYER_NOT_READY -> MessageKey.COMMON_PLAYER_NOT_READY;
            case SERVICE_UNAVAILABLE -> MessageKey.COMMON_SERVICE_UNAVAILABLE;
        };
        Optional<BoosterClaim> claim = result.claim();
        player.sendMessage(this.runtime.localization().message(player, key,
            MessageArguments.component("name", claim.map(BoosterClaim::boosterId).map(id -> this.runtime.localization().boosterName(player, id.value())).orElse(Component.text("?"))),
            MessageArguments.text("amount", claim.map(BoosterClaim::amount).orElse(0L))));
    }

    @Override
    public void close() {
        this.closed.set(true);
        this.sessions.clear();
        this.activeOperations.clear();
        this.activeClaims.clear();
    }
}
