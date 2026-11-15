SET @ddl = IF(
  (SELECT COUNT(*) FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refunds' AND COLUMN_NAME = 'point_refund_amount') = 0,
  'ALTER TABLE `refunds` ADD COLUMN `point_refund_amount` decimal(10,2) NOT NULL DEFAULT 0 AFTER `return_shipping_fee`',
  'DO 0'
);
PREPARE stmt FROM @ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE `refunds` ALTER COLUMN `point_refund_amount` DROP DEFAULT;
