UPDATE `point_policies` p
JOIN (SELECT MAX(`id`) AS keep_id FROM `point_policies` WHERE `is_active` = 1) k
SET p.`is_active` = 0
WHERE p.`is_active` = 1 AND p.`id` <> k.keep_id;

SET @add_active_key = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `point_policies` ADD COLUMN `active_key` TINYINT GENERATED ALWAYS AS (CASE WHEN is_active THEN 1 END)',
    'DO 0')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'point_policies' AND COLUMN_NAME = 'active_key'
);
PREPARE add_active_key FROM @add_active_key;
EXECUTE add_active_key;
DEALLOCATE PREPARE add_active_key;

SET @add_active_key_unique = (
  SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `point_policies` ADD CONSTRAINT `uk_point_policies_active_key` UNIQUE (`active_key`)',
    'DO 0')
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'point_policies' AND INDEX_NAME = 'uk_point_policies_active_key'
);
PREPARE add_active_key_unique FROM @add_active_key_unique;
EXECUTE add_active_key_unique;
DEALLOCATE PREPARE add_active_key_unique;
