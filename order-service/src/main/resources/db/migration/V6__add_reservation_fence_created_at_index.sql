SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reservation_fence' AND INDEX_NAME = 'idx_reservation_fence_created_at') = 0,
  'CREATE INDEX `idx_reservation_fence_created_at` ON `reservation_fence` (`created_at`)',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
