package com.stephanofer.networkboosters.command;

import com.stephanofer.networkboosters.api.NetworkBoostersService;
import com.stephanofer.networkboosters.api.booster.ActivationGroup;
import com.stephanofer.networkboosters.api.booster.ActivationRequirements;
import com.stephanofer.networkboosters.api.booster.BoosterCategory;
import com.stephanofer.networkboosters.api.booster.BoosterDefinition;
import com.stephanofer.networkboosters.api.booster.BoosterId;
import com.stephanofer.networkboosters.api.booster.BoosterScope;
import com.stephanofer.networkboosters.api.booster.BoosterTarget;
import com.stephanofer.networkboosters.api.booster.ConflictPolicy;
import com.stephanofer.networkboosters.api.booster.TransferPolicy;
import com.stephanofer.networkboosters.api.player.PlayerBoostSnapshot;
import com.stephanofer.networkboosters.config.NetworkBoostersConfiguration;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class NetworkBoostersCommandsTest {

    @Test
    void amountSuggestionsRespectConfiguredMinimumMaximumAndInput() {
        assertEquals(
            List.of("3", "5", "10", "12"),
            NetworkBoostersCommands.amountSuggestions(3, 12, "")
        );
        assertEquals(
            List.of("10", "12"),
            NetworkBoostersCommands.amountSuggestions(3, 12, "1")
        );
    }

    @Test
    void amountSuggestionsAreEmptyWhenOwnedAmountIsBelowMinimum() {
        assertTrue(NetworkBoostersCommands.amountSuggestions(3, 2, "").isEmpty());
    }

    @Test
    void amountSuggestionsIncludeTheExactAvailableMaximum() {
        assertEquals(
            List.of("1", "2", "5", "7"),
            NetworkBoostersCommands.amountSuggestions(7, "")
        );
    }

    @Test
    void onlinePlayerSuggestionsAreRealSortedNamesFilteredByPrefix() {
        TestCommands test = testCommands();
        Player alpha = player("Alpha");
        Player beta = player("beta");
        doReturn(List.of(beta, alpha)).when(test.server()).getOnlinePlayers();

        assertEquals(List.of("Alpha"), test.commands().suggestOnlinePlayers("a"));
        assertEquals(List.of("Alpha", "beta"), test.commands().suggestOnlinePlayers(""));
    }

    @Test
    void transferTargetsExcludeSenderAndPlayersWhoseStateIsNotReady() {
        TestCommands test = testCommands();
        Player sender = player("Sender");
        Player ready = player("Ready");
        Player loading = player("Loading");
        doReturn(List.of(sender, loading, ready)).when(test.server()).getOnlinePlayers();
        when(test.service().isReady(ready.getUniqueId())).thenReturn(true);

        assertEquals(List.of("Ready"), test.commands().suggestTransferTargets(sender, ""));
    }

    @Test
    void ownedAndTransferableSuggestionsUseCachedInventoryAndPolicy() {
        TestCommands test = testCommands();
        Player sender = player("Sender");
        BoosterId transferable = BoosterId.of("transferable");
        BoosterId locked = BoosterId.of("locked");
        BoosterId disabled = BoosterId.of("disabled");
        PlayerBoostSnapshot snapshot = new PlayerBoostSnapshot(
            sender.getUniqueId(),
            1,
            Map.of(transferable, 4L, locked, 2L, disabled, 1L),
            Map.of(),
            Map.of(),
            List.of()
        );
        when(test.service().isReady(sender.getUniqueId())).thenReturn(true);
        when(test.service().getCachedOrEmpty(sender.getUniqueId())).thenReturn(snapshot);
        when(test.service().definition(transferable)).thenReturn(Optional.of(definition(transferable, true, Optional.empty(), 2, 10)));
        when(test.service().definition(locked)).thenReturn(Optional.of(definition(locked, true, Optional.of("networkboosters.transfer.locked"), 1, 10)));
        when(test.service().definition(disabled)).thenReturn(Optional.of(definition(disabled, false, Optional.empty(), 1, 10)));

        assertEquals(List.of("disabled", "locked", "transferable"), test.commands().suggestOwnedBoosters(sender, ""));
        assertEquals(List.of("transferable"), test.commands().suggestTransferableBoosters(sender, ""));
        assertEquals(List.of("2", "4"), test.commands().suggestTransferAmounts(sender, "transferable", ""));
    }

    private static TestCommands testCommands() {
        NetworkBoostersCommandBridge bridge = new NetworkBoostersCommandBridge();
        NetworkBoostersCommandRuntime runtime = mock(NetworkBoostersCommandRuntime.class);
        Server server = mock(Server.class);
        NetworkBoostersService service = mock(NetworkBoostersService.class);
        when(runtime.isRunning()).thenReturn(true);
        when(runtime.server()).thenReturn(server);
        when(runtime.service()).thenReturn(service);
        bridge.bind(runtime);
        return new TestCommands(
            new NetworkBoostersCommands(bridge, new NetworkBoostersConfiguration.Commands("booster", List.of("boosters"))),
            server,
            service
        );
    }

    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        return player;
    }

    private static BoosterDefinition definition(
        BoosterId id,
        boolean enabled,
        Optional<String> permission,
        long minimum,
        long maximum
    ) {
        return new BoosterDefinition(
            id,
            BoosterTarget.NETWORK_PROGRESSION_POINTS,
            BigDecimal.valueOf(2),
            Duration.ofHours(1),
            BoosterScope.personalGlobal(),
            ActivationGroup.of("personal-points"),
            ConflictPolicy.QUEUE,
            ActivationRequirements.NONE,
            new TransferPolicy(true, minimum, maximum, Duration.ZERO, permission),
            enabled,
            0,
            BoosterCategory.of("points")
        );
    }

    private record TestCommands(NetworkBoostersCommands commands, Server server, NetworkBoostersService service) {
    }
}
