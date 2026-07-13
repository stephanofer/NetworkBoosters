CREATE INDEX idx_transfers_sender_booster_status_created
    ON `${tablePrefix}transfers` (sender_uuid, booster_id, status, created_at);
