CREATE TABLE IF NOT EXISTS `${tablePrefix}inventory` (
    player_uuid BINARY(16) NOT NULL,
    booster_id VARCHAR(64) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (player_uuid, booster_id),
    CONSTRAINT chk_inventory_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}activations` (
    activation_id BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    booster_id VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    multiplier DECIMAL(19, 6) NOT NULL,
    activation_group VARCHAR(64) NOT NULL,
    conflict_policy VARCHAR(16) NOT NULL,

    scope_type VARCHAR(16) NOT NULL,
    game_scopes JSON NOT NULL,
    server_scopes JSON NOT NULL,
    requirement_mode VARCHAR(16) NOT NULL,
    requirement_permissions JSON NOT NULL,

    activated_at TIMESTAMP(3) NOT NULL,
    expires_at TIMESTAMP(3) NOT NULL,
    status VARCHAR(16) NOT NULL,

    source_type VARCHAR(32) NOT NULL,
    actor_uuid BINARY(16) NULL,
    source_reference VARCHAR(255) NULL,
    source_server_id VARCHAR(64) NULL,

    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    active_group_key VARCHAR(64) GENERATED ALWAYS AS (
        CASE WHEN status = 'ACTIVE' THEN activation_group ELSE NULL END
    ) STORED,

    PRIMARY KEY (activation_id),
    UNIQUE KEY uq_active_player_group (player_uuid, active_group_key),
    KEY idx_activations_player_status_expiry (player_uuid, status, expires_at),
    KEY idx_activations_status_expiry (status, expires_at),

    CONSTRAINT chk_activation_multiplier_positive CHECK (multiplier > 0),
    CONSTRAINT chk_activation_time CHECK (expires_at > activated_at),
    CONSTRAINT chk_activation_status CHECK (status IN ('ACTIVE', 'EXPIRED', 'DEACTIVATED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}queue` (
    queue_id BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    activation_group VARCHAR(64) NOT NULL,
    position BIGINT UNSIGNED NOT NULL,

    booster_id VARCHAR(64) NOT NULL,
    target_key VARCHAR(128) NOT NULL,
    multiplier DECIMAL(19, 6) NOT NULL,
    conflict_policy VARCHAR(16) NOT NULL,

    scope_type VARCHAR(16) NOT NULL,
    game_scopes JSON NOT NULL,
    server_scopes JSON NOT NULL,
    requirement_mode VARCHAR(16) NOT NULL,
    requirement_permissions JSON NOT NULL,

    duration_millis BIGINT UNSIGNED NOT NULL,
    queued_at TIMESTAMP(3) NOT NULL,

    source_type VARCHAR(32) NOT NULL,
    actor_uuid BINARY(16) NULL,
    source_reference VARCHAR(255) NULL,
    source_server_id VARCHAR(64) NULL,

    PRIMARY KEY (queue_id),
    UNIQUE KEY uq_queue_position (player_uuid, activation_group, position),
    KEY idx_queue_player_group_position (player_uuid, activation_group, position),

    CONSTRAINT chk_queue_multiplier_positive CHECK (multiplier > 0),
    CONSTRAINT chk_queue_duration_positive CHECK (duration_millis > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}claims` (
    claim_id BINARY(16) NOT NULL,
    player_uuid BINARY(16) NOT NULL,
    booster_id VARCHAR(64) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,

    source_type VARCHAR(32) NOT NULL,
    actor_uuid BINARY(16) NULL,
    source_reference VARCHAR(255) NULL,
    source_server_id VARCHAR(64) NULL,

    created_at TIMESTAMP(3) NOT NULL,
    claimed_at TIMESTAMP(3) NULL,
    status VARCHAR(16) NOT NULL,

    PRIMARY KEY (claim_id),
    KEY idx_claims_player_status_created (player_uuid, status, created_at),

    CONSTRAINT chk_claim_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_claim_status CHECK (status IN ('PENDING', 'CLAIMED')),
    CONSTRAINT chk_claim_timestamp CHECK (
        (status = 'PENDING' AND claimed_at IS NULL)
        OR
        (status = 'CLAIMED' AND claimed_at IS NOT NULL)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}transfers` (
    transfer_id BINARY(16) NOT NULL,
    sender_uuid BINARY(16) NOT NULL,
    recipient_uuid BINARY(16) NOT NULL,
    booster_id VARCHAR(64) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,

    source_type VARCHAR(32) NOT NULL,
    actor_uuid BINARY(16) NULL,
    source_reference VARCHAR(255) NULL,
    source_server_id VARCHAR(64) NULL,

    created_at TIMESTAMP(3) NOT NULL,
    status VARCHAR(24) NOT NULL,

    PRIMARY KEY (transfer_id),
    KEY idx_transfers_sender_created (sender_uuid, created_at),
    KEY idx_transfers_recipient_created (recipient_uuid, created_at),

    CONSTRAINT chk_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT chk_transfer_distinct_players CHECK (sender_uuid <> recipient_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}audit_log` (
    audit_id BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    operation_id BINARY(16) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,

    actor_type VARCHAR(16) NOT NULL,
    actor_uuid BINARY(16) NULL,
    affected_player_uuid BINARY(16) NOT NULL,

    booster_id VARCHAR(64) NULL,
    amount BIGINT NULL,
    previous_value JSON NULL,
    new_value JSON NULL,

    activation_id BINARY(16) NULL,
    transfer_id BINARY(16) NULL,

    source_type VARCHAR(32) NOT NULL,
    source_reference VARCHAR(255) NULL,
    source_server_id VARCHAR(64) NULL,

    result VARCHAR(32) NOT NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (audit_id),
    KEY idx_audit_player_created (affected_player_uuid, created_at),
    KEY idx_audit_operation (operation_id),
    KEY idx_audit_activation (activation_id),
    KEY idx_audit_transfer (transfer_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `${tablePrefix}player_revision` (
    player_uuid BINARY(16) NOT NULL,
    revision BIGINT UNSIGNED NOT NULL,
    updated_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),

    PRIMARY KEY (player_uuid)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
