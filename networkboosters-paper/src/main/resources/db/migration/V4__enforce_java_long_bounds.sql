ALTER TABLE `${tablePrefix}inventory`
    ADD CONSTRAINT chk_inventory_amount_signed_long CHECK (amount <= 9223372036854775807);

ALTER TABLE `${tablePrefix}queue`
    ADD CONSTRAINT chk_queue_position_signed_long CHECK (position <= 9223372036854775807),
    ADD CONSTRAINT chk_queue_duration_signed_long CHECK (duration_millis <= 9223372036854775807);

ALTER TABLE `${tablePrefix}player_revision`
    ADD CONSTRAINT chk_player_revision_signed_long CHECK (revision <= 9223372036854775807);
