CREATE TABLE `settlement_items` (
  `quantity` int NOT NULL,
  `sales_amount` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `order_item_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_name` varchar(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_items_order_item_id` (`order_item_id`),
  KEY `idx_settlement_items_settlement_id` (`settlement_id`),
  KEY `idx_settlement_items_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `settlement_seller_locks` (
  `seller_id` bigint NOT NULL,
  PRIMARY KEY (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `settlement_summaries` (
  `commission_amount` decimal(12,2) NOT NULL,
  `commission_rate` decimal(5,4) NOT NULL,
  `period_end` date NOT NULL,
  `period_start` date NOT NULL,
  `settlement_amount` decimal(12,2) NOT NULL,
  `total_refund_amount` decimal(12,2) NOT NULL,
  `total_sales_amount` decimal(12,2) NOT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paid_at` datetime(6) DEFAULT NULL,
  `seller_id` bigint NOT NULL,
  `settlement_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlement_summaries_seller_period` (`seller_id`,`period_start`,`period_end`),
  UNIQUE KEY `uk_settlement_summaries_settlement_id` (`settlement_id`),
  KEY `idx_settlement_summaries_seller_id` (`seller_id`),
  KEY `idx_settlement_summaries_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `settlements` (
  `commission_amount` decimal(12,2) NOT NULL,
  `commission_rate` decimal(5,4) NOT NULL,
  `period_end` date NOT NULL,
  `period_start` date NOT NULL,
  `settlement_amount` decimal(12,2) NOT NULL,
  `total_refund_amount` decimal(12,2) NOT NULL,
  `total_sales_amount` decimal(12,2) NOT NULL,
  `confirmed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `paid_at` datetime(6) DEFAULT NULL,
  `seller_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_settlements_seller_period` (`seller_id`,`period_start`,`period_end`),
  KEY `idx_settlements_seller_id` (`seller_id`),
  KEY `idx_settlements_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
