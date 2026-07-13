package com.stephanofer.networkboosters.command;

import com.stephanofer.networkboosters.api.booster.ActiveBooster;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.QueuedBooster;
import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.api.request.ActivationRequest;
import com.stephanofer.networkboosters.api.request.BoosterTransferRequest;
import com.stephanofer.networkboosters.api.request.ClaimRequest;
import com.stephanofer.networkboosters.api.request.DeactivationRequest;
import com.stephanofer.networkboosters.api.request.InventoryGrantRequest;
import com.stephanofer.networkboosters.api.request.InventoryRevokeRequest;
import com.stephanofer.networkboosters.api.request.InventorySetRequest;
import com.stephanofer.networkboosters.api.result.ActivationResult;
import com.stephanofer.networkboosters.api.result.ActivationStatus;
import com.stephanofer.networkboosters.api.result.ClaimResult;
import com.stephanofer.networkboosters.api.result.ClaimResultStatus;
import com.stephanofer.networkboosters.api.result.DeactivationResult;
import com.stephanofer.networkboosters.api.result.DeactivationStatus;
import com.stephanofer.networkboosters.api.result.InventoryMutationResult;
import com.stephanofer.networkboosters.api.result.InventoryMutationStatus;
import com.stephanofer.networkboosters.api.result.TransferResult;
import com.stephanofer.networkboosters.api.result.TransferStatus;
import com.stephanofer.networkboosters.api.source.ActivationSource;
import com.stephanofer.networkboosters.api.source.DeactivationReason;
import com.stephanofer.networkboosters.api.source.MutationSource;
import com.stephanofer.networkboosters.api.source.SourceReference;
import com.stephanofer.networkboosters.api.source.TransferSource;
import com.stephanofer.networkboosters.capacity.ResolvedInventoryCapacity;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import com.stephanofer.networkboosters.localization.DurationFormatter;
import com.stephanofer.networkboosters.localization.MessageArguments;
import com.stephanofer.networkboosters.localization.MessageKey;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.incendo.cloud.paper.PaperCommandManager;
import org.incendo.cloud.suggestion.Suggestion;
import org.incendo.cloud.suggestion.SuggestionProvider;

import static org.incendo.cloud.parser.standard.StringParser.stringParser;

public final class NetworkBoostersCommands {

    private static final int PAGE_SIZE = 8;
    private final NetworkBoostersCommandBridge bridge;
    private final List<String> roots;
    private final DurationFormatter durations = new DurationFormatter();

    public NetworkBoostersCommands(
        NetworkBoostersCommandBridge bridge,
        NetworkBoostersConfiguration.Commands commands
    ) {
        this.bridge = Objects.requireNonNull(bridge, "bridge");
        Objects.requireNonNull(commands, "commands");
        ArrayList<String> configuredRoots = new ArrayList<>();
        configuredRoots.add(commands.root());
        configuredRoots.addAll(commands.aliases());
        this.roots = List.copyOf(configuredRoots);
    }

    public void register(PaperCommandManager.Bootstrapped<CommandSourceStack> manager) {
        this.registerSuggestions(manager);
        for (String root : this.roots) {
            this.registerRoot(manager, root);
        }
    }

    private void registerSuggestions(PaperCommandManager.Bootstrapped<CommandSourceStack> manager) {
        manager.parserRegistry().registerSuggestionProvider("networkboosters-online-players", (context, input) ->
            suggestions(this.suggestOnlinePlayers(input.peekString())));
        manager.parserRegistry().registerSuggestionProvider("networkboosters-owned", (context, input) ->
            suggestions(this.suggestOwnedBoosters(context.sender().getSender(), input.peekString())));
        manager.parserRegistry().registerSuggestionProvider("networkboosters-transferable", (context, input) ->
            suggestions(this.suggestTransferableBoosters(context.sender().getSender(), input.peekString())));
        manager.parserRegistry().registerSuggestionProvider("networkboosters-definitions", (context, input) ->
            suggestions(this.suggestDefinitions(input.peekString())));
        manager.parserRegistry().registerSuggestionProvider("networkboosters-claims", (context, input) ->
            suggestions(this.suggestClaims(context.sender().getSender(), input.peekString())));
        manager.parserRegistry().registerSuggestionProvider("networkboosters-activations", (context, input) ->
            suggestions(this.suggestActivations(context.getOrDefault("player", ""), input.peekString())));
    }

    private static CompletableFuture<List<Suggestion>> suggestions(List<String> values) {
        return CompletableFuture.completedFuture(values.stream().map(Suggestion::suggestion).toList());
    }

    private void registerRoot(PaperCommandManager.Bootstrapped<CommandSourceStack> manager, String root) {
        manager.command(manager.commandBuilder(root).permission("networkboosters.command.open").handler(context -> this.openOrSummary(context.sender().getSender())));
        manager.command(manager.commandBuilder(root).literal("help").permission("networkboosters.command.open").handler(context -> this.help(context.sender().getSender())));
        manager.command(manager.commandBuilder(root).literal("menu").permission("networkboosters.command.open").handler(context -> this.openOrSummary(context.sender().getSender())));
        manager.command(manager.commandBuilder(root).literal("list").optional("page", stringParser(), this.listPages()).permission("networkboosters.command.list").handler(context -> this.list(context.sender().getSender(), context.getOrDefault("page", "1"))));
        manager.command(manager.commandBuilder(root).literal("active").permission("networkboosters.command.list").handler(context -> this.active(context.sender().getSender())));
        manager.command(manager.commandBuilder(root).literal("queue").optional("group", stringParser(), this.queueGroups()).permission("networkboosters.command.list").handler(context -> this.queue(context.sender().getSender(), context.getOrDefault("group", ""))));
        manager.command(manager.commandBuilder(root).literal("claims").optional("page", stringParser(), this.claimPages()).permission("networkboosters.command.claims").handler(context -> this.claims(context.sender().getSender(), context.getOrDefault("page", "1"))));
        manager.command(manager.commandBuilder(root).literal("claim").required("claim", stringParser(), this.claims()).permission("networkboosters.command.claims").handler(context -> this.claim(context.sender().getSender(), context.get("claim"))));
        manager.command(manager.commandBuilder(root).literal("activate").required("booster", stringParser(), this.ownedBoosters()).permission("networkboosters.command.activate").handler(context -> this.activate(context.sender().getSender(), context.get("booster"))));
        manager.command(manager.commandBuilder(root).literal("transfer").required("player", stringParser(), this.transferTargets()).required("booster", stringParser(), this.transferableBoosters()).optional("amount", stringParser(), this.transferAmounts()).permission("networkboosters.command.transfer").handler(context -> this.transfer(context.sender().getSender(), context.get("player"), context.get("booster"), context.getOrDefault("amount", "1"))));

        manager.command(manager.commandBuilder(root).literal("admin").literal("give").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).optional("amount", stringParser(), this.commonAmounts()).permission("networkboosters.admin.give").handler(context -> this.adminGive(context.sender().getSender(), context.get("player"), context.get("booster"), context.getOrDefault("amount", "1"), false)));
        manager.command(manager.commandBuilder(root).literal("admin").literal("give").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).required("amount", stringParser(), this.commonAmounts()).literal("--force").permission("networkboosters.admin.give.force").handler(context -> this.adminGive(context.sender().getSender(), context.get("player"), context.get("booster"), context.get("amount"), true)));
        manager.command(manager.commandBuilder(root).literal("admin").literal("take").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).optional("amount", stringParser(), this.adminTakeAmounts()).permission("networkboosters.admin.take").handler(context -> this.adminTake(context.sender().getSender(), context.get("player"), context.get("booster"), context.getOrDefault("amount", "1"))));
        manager.command(manager.commandBuilder(root).literal("admin").literal("set").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).required("amount", stringParser(), this.adminSetAmounts()).permission("networkboosters.admin.set").handler(context -> this.adminSet(context.sender().getSender(), context.get("player"), context.get("booster"), context.get("amount"), false)));
        manager.command(manager.commandBuilder(root).literal("admin").literal("set").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).required("amount", stringParser(), this.adminSetAmounts()).literal("--force").permission("networkboosters.admin.give.force").handler(context -> this.adminSet(context.sender().getSender(), context.get("player"), context.get("booster"), context.get("amount"), true)));
        manager.command(manager.commandBuilder(root).literal("admin").literal("activate").required("player", stringParser(), this.onlinePlayers()).required("booster", stringParser(), this.definitions()).permission("networkboosters.admin.activate").handler(context -> this.adminActivate(context.sender().getSender(), context.get("player"), context.get("booster"))));
        manager.command(manager.commandBuilder(root).literal("admin").literal("deactivate").required("player", stringParser(), this.onlinePlayers()).required("activation", stringParser(), this.activations()).permission("networkboosters.admin.deactivate").handler(context -> this.adminDeactivate(context.sender().getSender(), context.get("player"), context.get("activation"))));
        manager.command(manager.commandBuilder(root).literal("admin").literal("inspect").required("player", stringParser(), this.onlinePlayers()).permission("networkboosters.admin.inspect").handler(context -> this.inspect(context.sender().getSender(), context.get("player"))));
        manager.command(manager.commandBuilder(root).literal("admin").literal("reload").permission("networkboosters.admin.reload").handler(context -> this.reload(context.sender().getSender())));
    }

    private SuggestionProvider<CommandSourceStack> onlinePlayers() {
        return (context, input) -> suggestions(this.suggestOnlinePlayers(input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> transferTargets() {
        return (context, input) -> suggestions(this.suggestTransferTargets(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> definitions() {
        return (context, input) -> suggestions(this.suggestDefinitions(input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> ownedBoosters() {
        return (context, input) -> suggestions(this.suggestOwnedBoosters(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> transferableBoosters() {
        return (context, input) -> suggestions(this.suggestTransferableBoosters(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> claims() {
        return (context, input) -> suggestions(this.suggestClaims(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> activations() {
        return (context, input) -> suggestions(this.suggestActivations(context.getOrDefault("player", ""), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> listPages() {
        return (context, input) -> suggestions(this.suggestListPages(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> claimPages() {
        return (context, input) -> suggestions(this.suggestClaimPages(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> queueGroups() {
        return (context, input) -> suggestions(this.suggestQueueGroups(context.sender().getSender(), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> transferAmounts() {
        return (context, input) -> suggestions(this.suggestTransferAmounts(context.sender().getSender(), context.getOrDefault("booster", ""), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> adminTakeAmounts() {
        return (context, input) -> suggestions(this.suggestAdminTakeAmounts(context.getOrDefault("player", ""), context.getOrDefault("booster", ""), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> adminSetAmounts() {
        return (context, input) -> suggestions(this.suggestAdminSetAmounts(context.getOrDefault("player", ""), context.getOrDefault("booster", ""), input.peekString()));
    }

    private SuggestionProvider<CommandSourceStack> commonAmounts() {
        return (context, input) -> suggestions(suggestCommonAmounts(input.peekString()));
    }

    private List<String> suggestOnlinePlayers(String input) {
        return this.bridge.runtime()
            .map(runtime -> runtime.server().getOnlinePlayers().stream()
                .map(Player::getName)
                .filter(name -> startsWith(name, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestTransferTargets(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .map(runtime -> runtime.server().getOnlinePlayers().stream()
                .filter(target -> !target.getUniqueId().equals(player.getUniqueId()))
                .filter(target -> runtime.service().isReady(target.getUniqueId()))
                .map(Player::getName)
                .filter(name -> startsWith(name, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestDefinitions(String input) {
        return this.bridge.runtime()
            .map(runtime -> runtime.service().definitions().stream()
                .map(definition -> definition.id().value())
                .filter(id -> startsWith(id, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestOwnedBoosters(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> runtime.service().getCachedOrEmpty(player.getUniqueId()).inventory().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey().value())
                .filter(id -> startsWith(id, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestTransferableBoosters(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> runtime.service().getCachedOrEmpty(player.getUniqueId()).inventory().entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .map(entry -> entry.getKey())
                .filter(boosterId -> runtime.service().definition(boosterId)
                    .map(definition -> definition.transferPolicy().enabled())
                    .orElse(false))
                .map(BoosterId::value)
                .filter(id -> startsWith(id, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestClaims(CommandSender sender, String input) {
        List<String> suggestions = new ArrayList<>();
        if (startsWith("all", input)) {
            suggestions.add("all");
        }
        if (!(sender instanceof Player player)) {
            return suggestions;
        }
        this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .ifPresent(runtime -> runtime.service().getCachedOrEmpty(player.getUniqueId()).pendingClaims().stream()
                .map(claim -> claim.claimId().toString())
                .filter(id -> startsWith(id, input))
                .forEach(suggestions::add));
        return List.copyOf(suggestions);
    }

    private List<String> suggestListPages(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> pageSuggestions(runtime.service().getCachedOrEmpty(player.getUniqueId()).inventory().size(), input))
            .orElse(List.of());
    }

    private List<String> suggestClaimPages(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> pageSuggestions(runtime.service().getCachedOrEmpty(player.getUniqueId()).pendingClaims().size(), input))
            .orElse(List.of());
    }

    private List<String> suggestQueueGroups(CommandSender sender, String input) {
        if (!(sender instanceof Player player)) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> runtime.service().getCachedOrEmpty(player.getUniqueId()).queuedBoosters().keySet().stream()
                .map(group -> group.value())
                .filter(group -> startsWith(group, input))
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList())
            .orElse(List.of());
    }

    private List<String> suggestTransferAmounts(CommandSender sender, String rawBooster, String input) {
        if (!(sender instanceof Player player) || rawBooster == null || rawBooster.isBlank()) {
            return List.of();
        }
        return this.bridge.runtime()
            .filter(runtime -> runtime.service().isReady(player.getUniqueId()))
            .map(runtime -> {
                BoosterId boosterId = parseSuggestionBooster(rawBooster).orElse(null);
                if (boosterId == null) {
                    return List.<String>of();
                }
                long owned = runtime.service().getCachedOrEmpty(player.getUniqueId()).ownedAmount(boosterId);
                long maximum = runtime.service().definition(boosterId)
                    .map(definition -> Math.min(owned, definition.transferPolicy().maximumAmount()))
                    .orElse(0L);
                return amountSuggestions(maximum, input);
            })
            .orElse(List.of());
    }

    private List<String> suggestAdminTakeAmounts(String targetName, String rawBooster, String input) {
        if (targetName == null || targetName.isBlank() || rawBooster == null || rawBooster.isBlank()) {
            return List.of();
        }
        return this.bridge.runtime()
            .map(runtime -> {
                Player target = runtime.server().getPlayerExact(targetName);
                BoosterId boosterId = parseSuggestionBooster(rawBooster).orElse(null);
                if (target == null || boosterId == null || !runtime.service().isReady(target.getUniqueId())) {
                    return List.<String>of();
                }
                return amountSuggestions(runtime.service().getCachedOrEmpty(target.getUniqueId()).ownedAmount(boosterId), input);
            })
            .orElse(List.of());
    }

    private List<String> suggestAdminSetAmounts(String targetName, String rawBooster, String input) {
        ArrayList<String> suggestions = new ArrayList<>();
        if (startsWith("0", input)) {
            suggestions.add("0");
        }
        suggestions.addAll(suggestCommonAmounts(input));
        if (targetName != null && !targetName.isBlank() && rawBooster != null && !rawBooster.isBlank()) {
            this.bridge.runtime().ifPresent(runtime -> {
                Player target = runtime.server().getPlayerExact(targetName);
                BoosterId boosterId = parseSuggestionBooster(rawBooster).orElse(null);
                if (target != null && boosterId != null && runtime.service().isReady(target.getUniqueId())) {
                    String current = String.valueOf(runtime.service().getCachedOrEmpty(target.getUniqueId()).ownedAmount(boosterId));
                    if (!suggestions.contains(current) && startsWith(current, input)) {
                        suggestions.add(current);
                    }
                }
            });
        }
        return suggestions.stream().distinct().toList();
    }

    private static List<String> pageSuggestions(int entries, String input) {
        int pages = Math.max(1, (int) Math.ceil(entries / (double) PAGE_SIZE));
        ArrayList<String> suggestions = new ArrayList<>();
        for (int page = 1; page <= pages; page++) {
            String value = String.valueOf(page);
            if (startsWith(value, input)) {
                suggestions.add(value);
            }
        }
        return suggestions;
    }

    private static List<String> suggestCommonAmounts(String input) {
        return List.of("1", "5", "10", "32", "64").stream()
            .filter(value -> startsWith(value, input))
            .toList();
    }

    private static List<String> amountSuggestions(long maximum, String input) {
        if (maximum < 1) {
            return List.of();
        }
        ArrayList<String> suggestions = new ArrayList<>();
        for (long candidate : List.of(1L, 2L, 5L, 10L, 32L, 64L, maximum)) {
            long value = Math.min(candidate, maximum);
            String text = String.valueOf(value);
            if (!suggestions.contains(text) && startsWith(text, input)) {
                suggestions.add(text);
            }
        }
        return suggestions;
    }

    private static Optional<BoosterId> parseSuggestionBooster(String rawBooster) {
        try {
            return Optional.of(BoosterId.of(rawBooster));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private List<String> suggestActivations(String targetName, String input) {
        List<String> suggestions = new ArrayList<>();
        if (startsWith("all", input)) {
            suggestions.add("all");
        }
        this.bridge.runtime().ifPresent(runtime -> {
            Player target = runtime.server().getPlayerExact(targetName);
            if (target == null || !runtime.service().isReady(target.getUniqueId())) {
                return;
            }
            runtime.service().getCachedOrEmpty(target.getUniqueId()).activeBoosters().values().stream()
                .map(active -> active.activationId().toString())
                .filter(id -> startsWith(id, input))
                .forEach(suggestions::add);
        });
        return List.copyOf(suggestions);
    }

    private static boolean startsWith(String value, String input) {
        String normalizedInput = input == null ? "" : input.toLowerCase(java.util.Locale.ROOT);
        return value.toLowerCase(java.util.Locale.ROOT).startsWith(normalizedInput);
    }

    private void summary(CommandSender sender) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(player.getUniqueId());
            ResolvedInventoryCapacity capacity = runtime.capacity(player);
            long queued = snapshot.queuedBoosters().values().stream().mapToLong(List::size).sum();
            send(sender, runtime, MessageKey.SUMMARY_HEADER);
            send(sender, runtime, MessageKey.SUMMARY_INVENTORY, MessageArguments.text("used", snapshot.ownedTotal()), MessageArguments.text("capacity", capacity.maximum()));
            send(sender, runtime, MessageKey.SUMMARY_ACTIVE, MessageArguments.text("amount", snapshot.activeBoosters().size()));
            send(sender, runtime, MessageKey.SUMMARY_QUEUED, MessageArguments.text("amount", queued));
            send(sender, runtime, MessageKey.SUMMARY_CLAIMS, MessageArguments.text("amount", snapshot.pendingClaims().size()));
            send(sender, runtime, MessageKey.SUMMARY_HINT);
        });
    }

    private void openOrSummary(CommandSender sender) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            if (!runtime.openMenu(player)) {
                this.summary(sender);
            }
        });
    }

    private void help(CommandSender sender) {
        this.withRuntime(sender, runtime -> {
            String root = runtime.configurationStore().requireCurrent().configuration().commands().root();
            send(sender, runtime, MessageKey.HELP_HEADER);
            if (sender.hasPermission("networkboosters.command.open")) {
                helpEntry(sender, runtime, root, MessageKey.HELP_SUMMARY);
            }
            if (sender.hasPermission("networkboosters.command.list")) {
                helpEntry(sender, runtime, root + " list [page]", MessageKey.HELP_LIST);
                helpEntry(sender, runtime, root + " active", MessageKey.HELP_ACTIVE);
                helpEntry(sender, runtime, root + " queue [group]", MessageKey.HELP_QUEUE);
            }
            if (sender.hasPermission("networkboosters.command.claims")) {
                helpEntry(sender, runtime, root + " claims [page]", MessageKey.HELP_CLAIMS);
            }
            if (sender.hasPermission("networkboosters.command.activate")) {
                helpEntry(sender, runtime, root + " activate <booster>", MessageKey.HELP_ACTIVATE);
            }
            if (sender.hasPermission("networkboosters.command.transfer")) {
                helpEntry(sender, runtime, root + " transfer <player> <booster> [amount]", MessageKey.HELP_TRANSFER);
            }
            send(sender, runtime, MessageKey.HELP_FOOTER);
        });
    }

    private static void helpEntry(CommandSender sender, NetworkBoostersCommandRuntime runtime, String command, MessageKey description) {
        send(sender, runtime, MessageKey.HELP_ENTRY,
            MessageArguments.text("command", command),
            MessageArguments.component("description", runtime.localization().message(sender, description)));
    }

    private void list(CommandSender sender, String rawPage) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            int page = parsePage(sender, runtime, rawPage);
            if (page < 1) {
                return;
            }
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(player.getUniqueId());
            List<BoosterId> boosters = snapshot.inventory().keySet().stream()
                .sorted(Comparator.comparing(id -> runtime.service().definition(id).map(BoosterDefinition::displayOrder).orElse(Integer.MAX_VALUE)))
                .toList();
            if (boosters.isEmpty()) {
                send(sender, runtime, MessageKey.LIST_EMPTY);
                return;
            }
            int pages = Math.max(1, (int) Math.ceil(boosters.size() / (double) PAGE_SIZE));
            if (page > pages) {
                send(sender, runtime, MessageKey.COMMON_PAGE_OUT_OF_RANGE);
                return;
            }
            send(sender, runtime, MessageKey.LIST_HEADER, MessageArguments.text("page", page), MessageArguments.text("pages", pages));
            int from = (page - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, boosters.size());
            for (BoosterId boosterId : boosters.subList(from, to)) {
                BoosterDefinition definition = runtime.service().definition(boosterId).orElse(null);
                String duration = definition == null ? "?" : this.duration(runtime, sender, definition.duration());
                String multiplier = definition == null ? "?" : definition.multiplier().toPlainString();
                send(sender, runtime, MessageKey.LIST_ENTRY,
                    MessageArguments.component("name", runtime.localization().boosterName(sender, boosterId.value())),
                    MessageArguments.text("id", boosterId.value()),
                    MessageArguments.text("amount", snapshot.ownedAmount(boosterId)),
                    MessageArguments.text("multiplier", multiplier),
                    MessageArguments.text("duration", duration));
            }
        });
    }

    private void active(CommandSender sender) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(player.getUniqueId());
            List<ActiveBooster> active = snapshot.activeBoosters().values().stream()
                .filter(booster -> booster.isActiveAt(runtime.clock().instant()))
                .toList();
            if (active.isEmpty()) {
                send(sender, runtime, MessageKey.ACTIVE_EMPTY);
                return;
            }
            send(sender, runtime, MessageKey.ACTIVE_HEADER);
            for (ActiveBooster booster : active) {
                send(sender, runtime, MessageKey.ACTIVE_ENTRY,
                    MessageArguments.component("name", runtime.localization().boosterName(sender, booster.boosterId().value())),
                    MessageArguments.text("group", booster.activationGroup().value()),
                    MessageArguments.text("multiplier", booster.multiplier().toPlainString()),
                    MessageArguments.text("remaining", this.duration(runtime, sender, Duration.between(runtime.clock().instant(), booster.expiresAt()))));
            }
        });
    }

    private void queue(CommandSender sender, String rawGroup) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(player.getUniqueId());
            List<QueuedBooster> queued = snapshot.queuedBoosters().entrySet().stream()
                .filter(entry -> rawGroup.isBlank() || entry.getKey().value().equalsIgnoreCase(rawGroup))
                .flatMap(entry -> entry.getValue().stream())
                .toList();
            if (queued.isEmpty()) {
                send(sender, runtime, MessageKey.QUEUE_EMPTY);
                return;
            }
            send(sender, runtime, MessageKey.QUEUE_HEADER);
            for (QueuedBooster booster : queued) {
                send(sender, runtime, MessageKey.QUEUE_ENTRY,
                    MessageArguments.component("name", runtime.localization().boosterName(sender, booster.boosterId().value())),
                    MessageArguments.text("group", booster.activationGroup().value()),
                    MessageArguments.text("position", booster.position()),
                    MessageArguments.text("duration", this.duration(runtime, sender, booster.duration())));
            }
        });
    }

    private void claims(CommandSender sender, String rawPage) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(player.getUniqueId());
            if (snapshot.pendingClaims().isEmpty()) {
                send(sender, runtime, MessageKey.CLAIMS_EMPTY);
                return;
            }
            int page = parsePage(sender, runtime, rawPage);
            if (page < 1) {
                return;
            }
            int pages = Math.max(1, (int) Math.ceil(snapshot.pendingClaims().size() / (double) PAGE_SIZE));
            if (page > pages) {
                send(sender, runtime, MessageKey.COMMON_PAGE_OUT_OF_RANGE);
                return;
            }
            send(sender, runtime, MessageKey.CLAIMS_HEADER);
            int from = (page - 1) * PAGE_SIZE;
            int to = Math.min(from + PAGE_SIZE, snapshot.pendingClaims().size());
            for (BoosterClaim claim : snapshot.pendingClaims().subList(from, to)) {
                Component entry = runtime.localization().message(sender, MessageKey.CLAIMS_ENTRY,
                    MessageArguments.text("claim", claim.claimId()),
                    MessageArguments.component("name", runtime.localization().boosterName(sender, claim.boosterId().value())),
                    MessageArguments.text("amount", claim.amount()));
                String root = runtime.configurationStore().requireCurrent().configuration().commands().root();
                sender.sendMessage(entry.clickEvent(ClickEvent.suggestCommand("/" + root + " claim " + claim.claimId())));
            }
        });
    }

    private void claim(CommandSender sender, String rawClaim) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            if (rawClaim.equalsIgnoreCase("all")) {
                this.claimAll(sender, runtime, player);
                return;
            }
            UUID claimId = parseUuid(sender, runtime, rawClaim);
            if (claimId == null) {
                return;
            }
            runtime.service().claim(new ClaimRequest(player.getUniqueId(), claimId))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendClaimResult(sender, runtime, result, failure)));
        });
    }

    private void claimAll(CommandSender sender, NetworkBoostersCommandRuntime runtime, Player player) {
        List<UUID> claims = runtime.service().getCachedOrEmpty(player.getUniqueId()).pendingClaims().stream().map(BoosterClaim::claimId).toList();
        if (claims.isEmpty()) {
            send(sender, runtime, MessageKey.CLAIMS_EMPTY);
            return;
        }
        List<CompletableFuture<ClaimResult>> futures = claims.stream()
            .map(claimId -> runtime.service().claim(new ClaimRequest(player.getUniqueId(), claimId))
                .exceptionally(ignored -> new ClaimResult(ClaimResultStatus.SERVICE_UNAVAILABLE, Optional.empty(), 0)))
            .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> sync(runtime, sender, () -> {
            long claimed = futures.stream()
                .map(future -> future.getNow(new ClaimResult(ClaimResultStatus.SERVICE_UNAVAILABLE, Optional.empty(), 0)))
                .filter(result -> result.status() == ClaimResultStatus.CLAIMED)
                .count();
            send(sender, runtime, MessageKey.CLAIMS_CLAIM_ALL_SUMMARY, MessageArguments.text("claimed", claimed), MessageArguments.text("failed", futures.size() - claimed));
        }));
    }

    private void activate(CommandSender sender, String rawBooster) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            if (boosterId == null) {
                return;
            }
            runtime.service().activate(new ActivationRequest(player.getUniqueId(), boosterId, ActivationSource.PLAYER_COMMAND, reference(runtime, player, "activate")))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendActivationResult(sender, runtime, result, failure, boosterId)));
        });
    }

    private void transfer(CommandSender sender, String targetName, String rawBooster, String rawAmount) {
        this.withPlayerRuntime(sender, (runtime, player) -> {
            Player recipient = runtime.server().getPlayerExact(targetName);
            if (recipient == null) {
                send(sender, runtime, MessageKey.COMMON_UNKNOWN_PLAYER);
                return;
            }
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            long amount = parsePositiveAmount(sender, runtime, rawAmount);
            if (boosterId == null || amount < 1) {
                return;
            }
            runtime.service().transfer(new BoosterTransferRequest(player.getUniqueId(), recipient.getUniqueId(), boosterId, amount, TransferSource.PLAYER_COMMAND, reference(runtime, player, "transfer")))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendTransferResult(sender, runtime, result, failure, recipient)));
        });
    }

    private void adminGive(CommandSender sender, String targetName, String rawBooster, String rawAmount, boolean force) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, force ? "networkboosters.admin.give.force" : "networkboosters.admin.give")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            long amount = parsePositiveAmount(sender, runtime, rawAmount);
            if (target == null || boosterId == null || amount < 1) {
                return;
            }
            runtime.service().grant(new InventoryGrantRequest(target.getUniqueId(), boosterId, amount, MutationSource.ADMIN_COMMAND, reference(runtime, sender, "admin give"), force))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendInventoryResult(sender, runtime, result, failure, amount)));
        });
    }

    private void adminTake(CommandSender sender, String targetName, String rawBooster, String rawAmount) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, "networkboosters.admin.take")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            long amount = parsePositiveAmount(sender, runtime, rawAmount);
            if (target == null || boosterId == null || amount < 1) {
                return;
            }
            runtime.service().revoke(new InventoryRevokeRequest(target.getUniqueId(), boosterId, amount, MutationSource.ADMIN_COMMAND, reference(runtime, sender, "admin take")))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendInventoryResult(sender, runtime, result, failure, amount)));
        });
    }

    private void adminSet(CommandSender sender, String targetName, String rawBooster, String rawAmount, boolean force) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, force ? "networkboosters.admin.give.force" : "networkboosters.admin.set")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            Long amount = parseNonNegativeAmount(sender, runtime, rawAmount);
            if (target == null || boosterId == null || amount == null) {
                return;
            }
            runtime.service().setInventoryAmount(new InventorySetRequest(target.getUniqueId(), boosterId, amount, MutationSource.ADMIN_COMMAND, reference(runtime, sender, "admin set"), force))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendInventoryResult(sender, runtime, result, failure, amount)));
        });
    }

    private void adminActivate(CommandSender sender, String targetName, String rawBooster) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, "networkboosters.admin.activate")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            BoosterId boosterId = parseBooster(sender, runtime, rawBooster);
            if (target == null || boosterId == null) {
                return;
            }
            runtime.service().activate(new ActivationRequest(target.getUniqueId(), boosterId, ActivationSource.ADMIN_COMMAND, reference(runtime, sender, "admin activate")))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendActivationResult(sender, runtime, result, failure, boosterId)));
        });
    }

    private void adminDeactivate(CommandSender sender, String targetName, String rawActivation) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, "networkboosters.admin.deactivate")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            if (target == null) {
                return;
            }
            if (rawActivation.equalsIgnoreCase("all")) {
                List<UUID> activations = runtime.service().getCachedOrEmpty(target.getUniqueId()).activeBoosters().values().stream().map(ActiveBooster::activationId).toList();
                List<CompletableFuture<DeactivationResult>> futures = activations.stream()
                    .map(id -> runtime.service().deactivate(new DeactivationRequest(id, DeactivationReason.ADMIN, reference(runtime, sender, "admin deactivate all")))
                        .exceptionally(ignored -> new DeactivationResult(DeactivationStatus.SERVICE_UNAVAILABLE, Optional.empty(), Optional.empty())))
                    .toList();
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenComplete((ignored, failure) -> sync(runtime, sender, () -> {
                    long deactivated = futures.stream()
                        .map(future -> future.getNow(new DeactivationResult(DeactivationStatus.SERVICE_UNAVAILABLE, Optional.empty(), Optional.empty())))
                        .filter(result -> result.status() == DeactivationStatus.DEACTIVATED || result.status() == DeactivationStatus.EXPIRED)
                        .count();
                    send(sender, runtime, MessageKey.ADMIN_DEACTIVATED_ALL, MessageArguments.text("amount", deactivated));
                }));
                return;
            }
            UUID activationId = parseUuid(sender, runtime, rawActivation);
            if (activationId == null) {
                return;
            }
            boolean belongsToTarget = runtime.service().getCachedOrEmpty(target.getUniqueId()).activeBoosters().values().stream()
                .anyMatch(active -> active.activationId().equals(activationId));
            if (!belongsToTarget) {
                send(sender, runtime, MessageKey.DEACTIVATION_NOT_FOUND);
                return;
            }
            runtime.service().deactivate(new DeactivationRequest(activationId, DeactivationReason.ADMIN, reference(runtime, sender, "admin deactivate")))
                .whenComplete((result, failure) -> sync(runtime, sender, () -> sendDeactivationResult(sender, runtime, result, failure, activationId)));
        });
    }

    private void inspect(CommandSender sender, String targetName) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, "networkboosters.admin.inspect")) {
                return;
            }
            Player target = target(sender, runtime, targetName);
            if (target == null) {
                return;
            }
            PlayerBoostSnapshot snapshot = runtime.service().getCachedOrEmpty(target.getUniqueId());
            ResolvedInventoryCapacity capacity = runtime.capacity(target);
            runtime.persistedRevision(target.getUniqueId()).whenComplete((persisted, failure) -> sync(runtime, sender, () -> {
                send(sender, runtime, MessageKey.ADMIN_INSPECT_HEADER, MessageArguments.text("player", target.getName()));
                String persistedText = failure == null ? String.valueOf(persisted) : "unavailable";
                String revisionState = failure == null && persisted == snapshot.revision() ? "synced" : "stale";
                if (failure != null) {
                    revisionState = "unavailable";
                }
                long queued = snapshot.queuedBoosters().values().stream().mapToLong(List::size).sum();
                String inventory = snapshot.inventory().isEmpty()
                    ? "empty"
                    : snapshot.inventory().entrySet().stream()
                        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().value()))
                        .map(entry -> entry.getKey().value() + " x" + entry.getValue())
                        .collect(java.util.stream.Collectors.joining(", "));
                String active = snapshot.activeBoosters().isEmpty()
                    ? "empty"
                    : snapshot.activeBoosters().values().stream()
                        .sorted(java.util.Comparator.comparing(activeBooster -> activeBooster.activationGroup().value()))
                        .map(activeBooster -> activeBooster.boosterId().value()
                            + "@" + activeBooster.activationGroup().value()
                            + " x" + activeBooster.multiplier().toPlainString()
                            + " expires=" + activeBooster.expiresAt())
                        .collect(java.util.stream.Collectors.joining(", "));
                String queue = snapshot.queuedBoosters().isEmpty()
                    ? "empty"
                    : snapshot.queuedBoosters().entrySet().stream()
                        .sorted(java.util.Comparator.comparing(entry -> entry.getKey().value()))
                        .map(entry -> entry.getKey().value() + "[" + entry.getValue().stream()
                            .map(queuedBooster -> "#" + queuedBooster.position() + " " + queuedBooster.boosterId().value() + " " + queuedBooster.duration().toMillis() + "ms")
                            .collect(java.util.stream.Collectors.joining(" -> ")) + "]")
                        .collect(java.util.stream.Collectors.joining(", "));
                for (Component line : runtime.localization().lines(sender, MessageKey.ADMIN_INSPECT_BODY,
                    MessageArguments.text("online", target.isOnline()),
                    MessageArguments.text("settings_ready", runtime.playerSettings().isReady(target.getUniqueId())),
                    MessageArguments.text("boosters_ready", runtime.service().isReady(target.getUniqueId())),
                    MessageArguments.text("local_revision", snapshot.revision()),
                    MessageArguments.text("persisted_revision", persistedText),
                    MessageArguments.text("revision_state", revisionState),
                    MessageArguments.text("used", snapshot.ownedTotal()),
                    MessageArguments.text("capacity", capacity.maximum()),
                    MessageArguments.text("capacity_rule", capacity.ruleId().orElse("fallback")),
                    MessageArguments.text("active", snapshot.activeBoosters().size()),
                    MessageArguments.text("queued", queued),
                    MessageArguments.text("claims", snapshot.pendingClaims().size()),
                    MessageArguments.text("inventory_breakdown", inventory),
                    MessageArguments.text("active_breakdown", active),
                    MessageArguments.text("queue_breakdown", queue),
                    MessageArguments.text("redis", runtime.redisStatus()))) {
                    sender.sendMessage(line);
                }
            }));
        });
    }

    private void reload(CommandSender sender) {
        this.withRuntime(sender, runtime -> {
            if (!requirePermission(sender, runtime, "networkboosters.admin.reload")) {
                return;
            }
            runtime.reload().whenComplete((report, failure) -> sync(runtime, sender, () -> {
                if (failure != null || report == null) {
                    send(sender, runtime, MessageKey.ADMIN_RELOAD_FAILED, MessageArguments.text("reason", failure == null ? "unknown" : failure.getMessage()));
                    return;
                }
                if (report.restartRequired()) {
                    send(sender, runtime, MessageKey.ADMIN_RELOAD_RESTART_REQUIRED, MessageArguments.text("paths", report.detail()));
                    return;
                }
                if (!report.success()) {
                    send(sender, runtime, MessageKey.ADMIN_RELOAD_FAILED, MessageArguments.text("reason", report.detail()));
                    return;
                }
                send(sender, runtime, MessageKey.ADMIN_RELOAD_SUCCESS, MessageArguments.text("definitions", report.definitions()), MessageArguments.text("warnings", report.warnings()));
            }));
        });
    }

    private void withRuntime(CommandSender sender, RuntimeConsumer consumer) {
        Optional<NetworkBoostersCommandRuntime> optional = this.bridge.runtime();
        if (optional.isEmpty()) {
            sender.sendMessage(Component.text("NetworkBoosters is not available."));
            return;
        }
        consumer.accept(optional.orElseThrow());
    }

    private void withPlayerRuntime(CommandSender sender, PlayerRuntimeConsumer consumer) {
        this.withRuntime(sender, runtime -> {
            if (!(sender instanceof Player player)) {
                send(sender, runtime, MessageKey.COMMON_PLAYER_ONLY);
                return;
            }
            if (!runtime.playerSettings().isReady(player.getUniqueId()) || !runtime.service().isReady(player.getUniqueId())) {
                send(sender, runtime, MessageKey.COMMON_PLAYER_NOT_READY);
                return;
            }
            consumer.accept(runtime, player);
        });
    }

    private Player target(CommandSender sender, NetworkBoostersCommandRuntime runtime, String name) {
        Player target = runtime.server().getPlayerExact(name);
        if (target == null) {
            send(sender, runtime, MessageKey.COMMON_UNKNOWN_PLAYER);
            return null;
        }
        if (!runtime.playerSettings().isReady(target.getUniqueId()) || !runtime.service().isReady(target.getUniqueId())) {
            send(sender, runtime, MessageKey.ADMIN_TARGET_NOT_READY);
            return null;
        }
        return target;
    }

    private BoosterId parseBooster(CommandSender sender, NetworkBoostersCommandRuntime runtime, String raw) {
        try {
            BoosterId boosterId = new BoosterId(raw);
            if (runtime.service().definition(boosterId).isEmpty()) {
                send(sender, runtime, MessageKey.COMMON_UNKNOWN_BOOSTER, MessageArguments.text("booster", raw));
                return null;
            }
            return boosterId;
        } catch (IllegalArgumentException exception) {
            send(sender, runtime, MessageKey.COMMON_UNKNOWN_BOOSTER, MessageArguments.text("booster", raw));
            return null;
        }
    }

    private int parsePage(CommandSender sender, NetworkBoostersCommandRuntime runtime, String raw) {
        long value = parsePositiveAmount(sender, runtime, raw);
        return value > Integer.MAX_VALUE ? -1 : (int) value;
    }

    private long parsePositiveAmount(CommandSender sender, NetworkBoostersCommandRuntime runtime, String raw) {
        Long amount = parseNonNegativeAmount(sender, runtime, raw);
        if (amount == null || amount < 1) {
            send(sender, runtime, MessageKey.COMMON_INVALID_AMOUNT);
            return -1;
        }
        return amount;
    }

    private Long parseNonNegativeAmount(CommandSender sender, NetworkBoostersCommandRuntime runtime, String raw) {
        try {
            long amount = Long.parseLong(raw);
            if (amount < 0) {
                send(sender, runtime, MessageKey.COMMON_INVALID_AMOUNT);
                return null;
            }
            return amount;
        } catch (NumberFormatException exception) {
            send(sender, runtime, MessageKey.COMMON_INVALID_AMOUNT);
            return null;
        }
    }

    private UUID parseUuid(CommandSender sender, NetworkBoostersCommandRuntime runtime, String raw) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException exception) {
            send(sender, runtime, MessageKey.COMMON_INVALID_UUID);
            return null;
        }
    }

    private static boolean requirePermission(CommandSender sender, NetworkBoostersCommandRuntime runtime, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        send(sender, runtime, MessageKey.COMMON_NO_PERMISSION);
        return false;
    }

    private String duration(NetworkBoostersCommandRuntime runtime, CommandSender sender, Duration duration) {
        return this.durations.format(duration, runtime.configurationStore().requireCurrent().localization(), runtime.localization().language(sender));
    }

    private static SourceReference reference(NetworkBoostersCommandRuntime runtime, CommandSender sender, String command) {
        Optional<UUID> actor = sender instanceof Player player ? Optional.of(player.getUniqueId()) : Optional.empty();
        return new SourceReference(actor, Optional.empty(), Optional.of(runtime.configurationStore().requireCurrent().configuration().serverId()));
    }

    private static void sendActivationResult(CommandSender sender, NetworkBoostersCommandRuntime runtime, ActivationResult result, Throwable failure, BoosterId boosterId) {
        if (failure != null || result == null) {
            send(sender, runtime, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        send(sender, runtime, activationKey(result.status()), MessageArguments.component("name", runtime.localization().boosterName(sender, boosterId.value())), MessageArguments.text("amount", result.remainingInventoryAmount()));
    }

    private static MessageKey activationKey(ActivationStatus status) {
        return switch (status) {
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
    }

    private static void sendInventoryResult(CommandSender sender, NetworkBoostersCommandRuntime runtime, InventoryMutationResult result, Throwable failure, long amount) {
        if (failure != null || result == null) {
            send(sender, runtime, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        send(sender, runtime, inventoryKey(result.status()),
            MessageArguments.component("name", runtime.localization().boosterName(sender, result.boosterId().value())),
            MessageArguments.text("amount", amount),
            MessageArguments.text("new", result.newAmount()));
    }

    private static MessageKey inventoryKey(InventoryMutationStatus status) {
        return switch (status) {
            case GRANTED -> MessageKey.INVENTORY_GRANTED;
            case GRANTED_FORCED -> MessageKey.INVENTORY_GRANTED_FORCED;
            case REVOKED -> MessageKey.INVENTORY_REVOKED;
            case SET -> MessageKey.INVENTORY_SET;
            case UNCHANGED -> MessageKey.INVENTORY_UNCHANGED;
            case CLAIM_CREATED -> MessageKey.INVENTORY_CLAIM_CREATED;
            case DUPLICATE_REQUEST -> MessageKey.INVENTORY_DUPLICATE_REQUEST;
            case IDEMPOTENCY_CONFLICT -> MessageKey.INVENTORY_IDEMPOTENCY_CONFLICT;
            case DEFINITION_NOT_FOUND -> MessageKey.INVENTORY_DEFINITION_NOT_FOUND;
            case INSUFFICIENT_AMOUNT -> MessageKey.INVENTORY_INSUFFICIENT;
            case INVENTORY_LIMIT_REACHED -> MessageKey.INVENTORY_LIMIT_REACHED;
            case PLAYER_NOT_READY -> MessageKey.INVENTORY_PLAYER_NOT_READY;
            case PERMISSION_DENIED -> MessageKey.INVENTORY_PERMISSION_DENIED;
            case SERVICE_UNAVAILABLE -> MessageKey.COMMON_SERVICE_UNAVAILABLE;
        };
    }

    private static void sendTransferResult(CommandSender sender, NetworkBoostersCommandRuntime runtime, TransferResult result, Throwable failure, Player recipient) {
        if (failure != null || result == null) {
            send(sender, runtime, MessageKey.COMMON_SERVICE_UNAVAILABLE);
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
        send(sender, runtime, key,
            MessageArguments.component("name", runtime.localization().boosterName(sender, result.boosterId().value())),
            MessageArguments.text("amount", result.amount()),
            MessageArguments.text("recipient", recipient.getName()),
            MessageArguments.text("remaining", cooldown(runtime, sender, result.retryAt())));
        if (result.status() == TransferStatus.TRANSFERRED && recipient.isOnline()) {
            send(recipient, runtime, MessageKey.TRANSFER_TRANSFERRED_RECIPIENT,
                MessageArguments.component("name", runtime.localization().boosterName(recipient, result.boosterId().value())),
                MessageArguments.text("amount", result.amount()),
                MessageArguments.text("sender", sender.getName()));
        }
    }

    private static String cooldown(NetworkBoostersCommandRuntime runtime, CommandSender sender, Optional<Instant> retryAt) {
        if (retryAt.isEmpty()) {
            return "";
        }
        return new DurationFormatter().format(Duration.between(runtime.clock().instant(), retryAt.orElseThrow()), runtime.configurationStore().requireCurrent().localization(), runtime.localization().language(sender));
    }

    private static void sendClaimResult(CommandSender sender, NetworkBoostersCommandRuntime runtime, ClaimResult result, Throwable failure) {
        if (failure != null || result == null) {
            send(sender, runtime, MessageKey.COMMON_SERVICE_UNAVAILABLE);
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
        send(sender, runtime, key,
            MessageArguments.component("name", claim.map(BoosterClaim::boosterId).map(id -> runtime.localization().boosterName(sender, id.value())).orElse(Component.text("?"))),
            MessageArguments.text("amount", claim.map(BoosterClaim::amount).orElse(0L)));
    }

    private static void sendDeactivationResult(CommandSender sender, NetworkBoostersCommandRuntime runtime, DeactivationResult result, Throwable failure, UUID activationId) {
        if (failure != null || result == null) {
            send(sender, runtime, MessageKey.COMMON_SERVICE_UNAVAILABLE);
            return;
        }
        MessageKey key = switch (result.status()) {
            case DEACTIVATED -> MessageKey.DEACTIVATION_DEACTIVATED;
            case EXPIRED -> MessageKey.DEACTIVATION_EXPIRED;
            case NOT_FOUND -> MessageKey.DEACTIVATION_NOT_FOUND;
            case ALREADY_INACTIVE -> MessageKey.DEACTIVATION_ALREADY_INACTIVE;
            case PLAYER_NOT_READY -> MessageKey.DEACTIVATION_PLAYER_NOT_READY;
            case SERVICE_UNAVAILABLE -> MessageKey.COMMON_SERVICE_UNAVAILABLE;
        };
        send(sender, runtime, key, MessageArguments.text("activation", activationId));
    }

    private static void sync(NetworkBoostersCommandRuntime runtime, CommandSender sender, Runnable runnable) {
        if (!runtime.isRunning()) {
            return;
        }
        runtime.server().getScheduler().runTask(runtime.plugin(), () -> {
            if (!runtime.isRunning() || sender instanceof Player player && !player.isOnline()) {
                return;
            }
            runnable.run();
        });
    }

    private static void send(CommandSender sender, NetworkBoostersCommandRuntime runtime, MessageKey key, TagResolver... resolvers) {
        sender.sendMessage(runtime.localization().message(sender, key, resolvers));
    }

    @FunctionalInterface
    private interface RuntimeConsumer {
        void accept(NetworkBoostersCommandRuntime runtime);
    }

    @FunctionalInterface
    private interface PlayerRuntimeConsumer {
        void accept(NetworkBoostersCommandRuntime runtime, Player player);
    }
}
