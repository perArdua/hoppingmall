SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'withdrawal_restricted') = 0,
  'ALTER TABLE `order_items` ADD COLUMN `withdrawal_restricted` bit(1) NOT NULL DEFAULT b''0''',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND COLUMN_NAME = 'withdrawal_restriction_reason') = 0,
  'ALTER TABLE `order_items` ADD COLUMN `withdrawal_restriction_reason` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
