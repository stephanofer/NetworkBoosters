package com.stephanofer.networkboosters.localization;

public enum MessageKey {
    COMMON_PREFIX("common.prefix"),
    COMMON_LOADING("common.loading"),
    COMMON_SERVICE_UNAVAILABLE("common.service-unavailable"),
    COMMON_PLAYER_NOT_READY("common.player-not-ready"),
    COMMON_PLAYER_ONLY("common.player-only"),
    COMMON_NO_PERMISSION("common.no-permission"),
    COMMON_INVALID_SYNTAX("common.invalid-syntax"),
    COMMON_INVALID_AMOUNT("common.invalid-amount"),
    COMMON_INVALID_UUID("common.invalid-uuid"),
    COMMON_UNKNOWN_PLAYER("common.unknown-player"),
    COMMON_UNKNOWN_BOOSTER("common.unknown-booster"),
    COMMON_PAGE_OUT_OF_RANGE("common.page-out-of-range"),

    HELP_HEADER("help.header"),
    HELP_ENTRY("help.entry"),
    HELP_FOOTER("help.footer"),
    HELP_SUMMARY("help.summary"),
    HELP_LIST("help.list"),
    HELP_ACTIVE("help.active"),
    HELP_QUEUE("help.queue"),
    HELP_CLAIMS("help.claims"),
    HELP_ACTIVATE("help.activate"),
    HELP_TRANSFER("help.transfer"),

    SUMMARY_HEADER("summary.header"),
    SUMMARY_INVENTORY("summary.inventory"),
    SUMMARY_ACTIVE("summary.active"),
    SUMMARY_QUEUED("summary.queued"),
    SUMMARY_CLAIMS("summary.claims"),
    SUMMARY_HINT("summary.hint"),

    LIST_HEADER("list.header"),
    LIST_ENTRY("list.entry"),
    LIST_EMPTY("list.empty"),

    ACTIVE_HEADER("active.header"),
    ACTIVE_ENTRY("active.entry"),
    ACTIVE_EMPTY("active.empty"),

    QUEUE_HEADER("queue.header"),
    QUEUE_ENTRY("queue.entry"),
    QUEUE_EMPTY("queue.empty"),

    CLAIMS_HEADER("claims.header"),
    CLAIMS_ENTRY("claims.entry"),
    CLAIMS_EMPTY("claims.empty"),
    CLAIMS_CLAIMED("claims.claimed"),
    CLAIMS_CLAIM_ALL_SUMMARY("claims.claim-all-summary"),
    CLAIMS_NOT_FOUND("claims.not-found"),
    CLAIMS_ALREADY_CLAIMED("claims.already-claimed"),
    CLAIMS_INVENTORY_FULL("claims.inventory-full"),

    ACTIVATION_ACTIVATED("activation.activated"),
    ACTIVATION_EXTENDED("activation.extended"),
    ACTIVATION_QUEUED("activation.queued"),
    ACTIVATION_QUEUE_MERGED("activation.queue-merged"),
    ACTIVATION_REPLACED("activation.replaced"),
    ACTIVATION_NOT_OWNED("activation.not-owned"),
    ACTIVATION_DEFINITION_NOT_FOUND("activation.definition-not-found"),
    ACTIVATION_DEFINITION_CHANGED("activation.definition-changed"),
    ACTIVATION_DEFINITION_DISABLED("activation.definition-disabled"),
    ACTIVATION_PERMISSION_DENIED("activation.permission-denied"),
    ACTIVATION_CANCELLED("activation.cancelled"),
    ACTIVATION_GROUP_OCCUPIED("activation.group-occupied"),
    ACTIVATION_QUEUE_LIMIT("activation.queue-limit"),
    ACTIVATION_DURATION_LIMIT("activation.duration-limit"),
    ACTIVATION_PLAYER_NOT_READY("activation.player-not-ready"),
    ACTIVATION_EXPIRY_WARNING("activation.expiry-warning"),
    ACTIVATION_EXPIRED("activation.expired"),

    INVENTORY_GRANTED("inventory.granted"),
    INVENTORY_GRANTED_FORCED("inventory.granted-forced"),
    INVENTORY_REVOKED("inventory.revoked"),
    INVENTORY_SET("inventory.set"),
    INVENTORY_UNCHANGED("inventory.unchanged"),
    INVENTORY_CLAIM_CREATED("inventory.claim-created"),
    INVENTORY_DUPLICATE_REQUEST("inventory.duplicate-request"),
    INVENTORY_IDEMPOTENCY_CONFLICT("inventory.idempotency-conflict"),
    INVENTORY_DEFINITION_NOT_FOUND("inventory.definition-not-found"),
    INVENTORY_INSUFFICIENT("inventory.insufficient"),
    INVENTORY_LIMIT_REACHED("inventory.limit-reached"),
    INVENTORY_PLAYER_NOT_READY("inventory.player-not-ready"),
    INVENTORY_PERMISSION_DENIED("inventory.permission-denied"),

    TRANSFER_TRANSFERRED_SENDER("transfer.transferred-sender"),
    TRANSFER_TRANSFERRED_RECIPIENT("transfer.transferred-recipient"),
    TRANSFER_SAME_PLAYER("transfer.same-player"),
    TRANSFER_RECIPIENT_NOT_ONLINE("transfer.recipient-not-online"),
    TRANSFER_NOT_TRANSFERABLE("transfer.not-transferable"),
    TRANSFER_INVALID_AMOUNT("transfer.invalid-amount"),
    TRANSFER_INSUFFICIENT("transfer.insufficient"),
    TRANSFER_RECIPIENT_FULL("transfer.recipient-full"),
    TRANSFER_COOLDOWN("transfer.cooldown"),
    TRANSFER_PERMISSION_DENIED("transfer.permission-denied"),
    TRANSFER_PLAYER_NOT_READY("transfer.player-not-ready"),

    DEACTIVATION_DEACTIVATED("deactivation.deactivated"),
    DEACTIVATION_EXPIRED("deactivation.expired"),
    DEACTIVATION_NOT_FOUND("deactivation.not-found"),
    DEACTIVATION_ALREADY_INACTIVE("deactivation.already-inactive"),
    DEACTIVATION_PLAYER_NOT_READY("deactivation.player-not-ready"),

    ADMIN_RELOAD_SUCCESS("admin.reload-success"),
    ADMIN_RELOAD_FAILED("admin.reload-failed"),
    ADMIN_RELOAD_RESTART_REQUIRED("admin.reload-restart-required"),
    ADMIN_INSPECT_HEADER("admin.inspect-header"),
    ADMIN_INSPECT_BODY("admin.inspect-body"),
    ADMIN_DEACTIVATED_ALL("admin.deactivated-all"),
    ADMIN_TARGET_NOT_READY("admin.target-not-ready"),

    PLACEHOLDER_TRUE("placeholder.true"),
    PLACEHOLDER_FALSE("placeholder.false"),

    TIME_NOW("time.now"),
    TIME_DAY("time.day"),
    TIME_DAYS("time.days"),
    TIME_HOUR("time.hour"),
    TIME_HOURS("time.hours"),
    TIME_MINUTE("time.minute"),
    TIME_MINUTES("time.minutes"),
    TIME_SECOND("time.second"),
    TIME_SECONDS("time.seconds");

    private final String path;

    MessageKey(String path) {
        this.path = path;
    }

    public String path() {
        return this.path;
    }
}
