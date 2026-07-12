package com.stephanofer.networkboosters.api.result;

import com.stephanofer.networkboosters.api.player.BoosterClaim;
import com.stephanofer.networkboosters.api.player.ClaimStatus;
import java.util.Objects;
import java.util.Optional;

public record ClaimResult(ClaimResultStatus status, Optional<BoosterClaim> claim, long inventoryAmount) {

    public ClaimResult {
        Objects.requireNonNull(status, "status");
        claim = Objects.requireNonNull(claim, "claim");
        if (inventoryAmount < 0) {
            throw new IllegalArgumentException("inventoryAmount cannot be negative");
        }
        if (status == ClaimResultStatus.CLAIMED) {
            BoosterClaim claimed = claim.orElseThrow(() -> new IllegalArgumentException("claimed result requires claim"));
            if (claimed.status() != ClaimStatus.CLAIMED) {
                throw new IllegalArgumentException("claimed result requires a claimed claim");
            }
        } else if (claim.isPresent()) {
            throw new IllegalArgumentException("non-claimed result cannot contain a claim");
        }
    }
}
