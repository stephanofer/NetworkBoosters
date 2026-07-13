package com.stephanofer.networkboosters.inventory;

import com.stephanofer.networkboosters.api.source.ClaimSource;
import com.stephanofer.networkboosters.api.source.MutationSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InventoryMutationPolicyTest {

    @Test
    void onlyPurchasesAndCompensationsOverflowIntoClaims() {
        assertEquals(ClaimSource.PURCHASE, InventoryMutationService.claimSource(MutationSource.PURCHASE).orElseThrow());
        assertEquals(ClaimSource.COMPENSATION, InventoryMutationService.claimSource(MutationSource.COMPENSATION).orElseThrow());
        assertTrue(InventoryMutationService.claimSource(MutationSource.CRATE).isEmpty());
        assertTrue(InventoryMutationService.claimSource(MutationSource.BATTLE_PASS).isEmpty());
        assertTrue(InventoryMutationService.claimSource(MutationSource.EVENT).isEmpty());
        assertTrue(InventoryMutationService.claimSource(MutationSource.DAILY_REWARD).isEmpty());
        assertTrue(InventoryMutationService.claimSource(MutationSource.SYSTEM).isEmpty());
        assertTrue(InventoryMutationService.claimSource(MutationSource.ADMIN_COMMAND).isEmpty());
    }
}
