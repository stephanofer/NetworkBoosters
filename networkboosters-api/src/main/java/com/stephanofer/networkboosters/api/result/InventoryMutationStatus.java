package com.stephanofer.networkboosters.api.result;

public enum InventoryMutationStatus {
    GRANTED,
    GRANTED_FORCED,
    REVOKED,
    SET,
    CLAIM_CREATED,
    DEFINITION_NOT_FOUND,
    INVALID_AMOUNT,
    INSUFFICIENT_AMOUNT,
    INVENTORY_LIMIT_REACHED,
    PLAYER_NOT_READY,
    PERMISSION_DENIED,
    SERVICE_UNAVAILABLE
}
