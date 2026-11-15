ALTER TABLE `settlements`
  ADD COLUMN `carried_in_amount` decimal(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN `carry_over_amount` decimal(12,2) NOT NULL DEFAULT 0;

ALTER TABLE `settlement_summaries`
  ADD COLUMN `carried_in_amount` decimal(12,2) NOT NULL DEFAULT 0,
  ADD COLUMN `carry_over_amount` decimal(12,2) NOT NULL DEFAULT 0;

CREATE TABLE `settlement_refunds` (
  `refund_amount` decimal(12,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `refund_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_refunds_refund_id` (`refund_id`),
  KEY `idx_settlement_refunds_settlement_id` (`settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE `settlement_carry_overs` (
  `amount` decimal(12,2) NOT NULL,
  `applied_settlement_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `source_settlement_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_carry_overs_source_settlement_id` (`source_settlement_id`),
  KEY `idx_settlement_carry_overs_seller_applied` (`seller_id`,`applied_settlement_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO `settlement_carry_overs` (`amount`, `created_at`, `seller_id`, `source_settlement_id`)
SELECT -`settlement_amount`, NOW(6), `seller_id`, `id`
FROM `settlements`
WHERE `settlement_amount` < 0 AND `status` <> 'PAID';

UPDATE `settlement_summaries` ss
JOIN `settlements` s ON s.`id` = ss.`settlement_id`
SET ss.`carry_over_amount` = -s.`settlement_amount`, ss.`settlement_amount` = 0
WHERE s.`settlement_amount` < 0 AND s.`status` <> 'PAID';

UPDATE `settlements`
SET `carry_over_amount` = -`settlement_amount`, `settlement_amount` = 0
WHERE `settlement_amount` < 0 AND `status` <> 'PAID';
