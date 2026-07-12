ALTER TABLE `${tablePrefix}audit_log`
    ADD COLUMN claim_id BINARY(16) NULL AFTER new_value,
    ADD INDEX idx_audit_claim (claim_id);

CREATE TABLE IF NOT EXISTS `${tablePrefix}mutation_receipts` (
    receipt_id BINARY(16) NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    source_type VARCHAR(32) NOT NULL,
    external_reference VARCHAR(255) NOT NULL,

    player_uuid BINARY(16) NOT NULL,
    booster_id VARCHAR(64) NOT NULL,
    amount BIGINT UNSIGNED NOT NULL,

    result VARCHAR(32) NOT NULL,
    claim_id BINARY(16) NULL,
    created_at TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),

    PRIMARY KEY (receipt_id),
    UNIQUE KEY uq_mutation_receipt_reference (operation_type, source_type, external_reference),
    KEY idx_mutation_receipts_player_created (player_uuid, created_at),

    CONSTRAINT chk_mutation_receipt_amount_positive CHECK (amount > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
