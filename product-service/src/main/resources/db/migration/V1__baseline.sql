CREATE TABLE `applied_request_keys` (
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `operation` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_applied_request_keys_operation_key` (`operation`,`request_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `bulk_import_jobs` (
  `fail_count` int NOT NULL,
  `processed_rows` int NOT NULL,
  `success_count` int NOT NULL,
  `total_rows` int NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `seller_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_details` text COLLATE utf8mb4_unicode_ci,
  `file_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_bulk_import_jobs_seller_id` (`seller_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `categories` (
  `depth` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `parent_category_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKt8o6pivur7nn124jehx7cygw5` (`name`),
  KEY `idx_categories_parent_id` (`parent_category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `dlq_messages` (
  `original_partition` int NOT NULL,
  `retry_count` int NOT NULL,
  `archived_at` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `error_timestamp` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_retry_at` bigint DEFAULT NULL,
  `next_retry_at` bigint DEFAULT NULL,
  `original_offset` bigint NOT NULL,
  `processed_at` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `original_key` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `consumer_group` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `exception_message` text COLLATE utf8mb4_unicode_ci,
  `notes` text COLLATE utf8mb4_unicode_ci,
  `original_topic` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `original_value` text COLLATE utf8mb4_unicode_ci,
  PRIMARY KEY (`id`),
  KEY `idx_dlq_topic` (`original_topic`),
  KEY `idx_dlq_status` (`status`),
  KEY `idx_dlq_created_at` (`created_at`),
  KEY `idx_dlq_next_retry_at` (`status`,`next_retry_at`),
  KEY `idx_dlq_topic_content_hash` (`original_topic`,`content_hash`),
  KEY `idx_dlq_status_archived_at` (`status`,`archived_at`),
  KEY `idx_dlq_status_processed_at` (`status`,`processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `idempotency_records` (
  `response_status` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `http_method` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `idempotency_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_fingerprint` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `endpoint` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `response_body` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKol0gjg0uap11mq1y9ug506f1i` (`idempotency_key`),
  KEY `idx_idempotency_records_expires_at` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventories` (
  `stock_quantity` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK3bms153u88bt0daekfy4qpj6k` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventory_buckets` (
  `bucket_no` int NOT NULL,
  `stock_quantity` int NOT NULL,
  `product_id` bigint NOT NULL,
  PRIMARY KEY (`bucket_no`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `inventory_reservations` (
  `quantity` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reservation_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  `request_key` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_reservation_id` (`reservation_id`),
  UNIQUE KEY `uk_reservation_request_product` (`request_key`,`product_id`),
  KEY `idx_reservation_product_status` (`product_id`,`status`),
  KEY `idx_reservation_expires_status` (`expires_at`,`status`),
  KEY `idx_reservation_product_status_expires` (`product_id`,`status`,`expires_at`),
  KEY `idx_reservation_status_expires` (`status`,`expires_at`),
  KEY `idx_reservation_status_created` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_daily_statistics` (
  `daily_refund_amount` decimal(15,2) NOT NULL,
  `daily_sales_amount` decimal(15,2) NOT NULL,
  `end_of_day_stock` int NOT NULL,
  `statistics_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `daily_order_count` bigint NOT NULL,
  `daily_refund_quantity` bigint NOT NULL,
  `daily_sales_quantity` bigint NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKqojjhjujdsbspwp9er00y0cql` (`product_id`,`statistics_date`),
  KEY `idx_daily_stats_date` (`statistics_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_hourly_statistics` (
  `hour` int NOT NULL,
  `hourly_refund_amount` decimal(15,2) NOT NULL,
  `hourly_sales_amount` decimal(15,2) NOT NULL,
  `statistics_date` date NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `hourly_order_count` bigint NOT NULL,
  `hourly_refund_quantity` bigint NOT NULL,
  `hourly_sales_quantity` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKhyd1ukchirgkicuvs84btvr03` (`product_id`,`statistics_date`,`hour`),
  KEY `idx_hourly_stats_date_hour` (`statistics_date`,`hour`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_images` (
  `sort_order` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_product_images_product_id` (`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `product_statistics` (
  `average_order_amount` decimal(15,2) NOT NULL,
  `average_rating` decimal(3,2) NOT NULL,
  `current_stock` int NOT NULL,
  `last30days_sales_amount` decimal(15,2) NOT NULL,
  `last7days_sales_amount` decimal(15,2) NOT NULL,
  `net_revenue` decimal(15,2) NOT NULL,
  `refund_rate` decimal(5,4) NOT NULL,
  `sales_growth_rate` decimal(10,4) NOT NULL,
  `stock_turnover_rate` decimal(10,4) NOT NULL,
  `today_refund_amount` decimal(15,2) NOT NULL,
  `today_sales_amount` decimal(15,2) NOT NULL,
  `total_refund_amount` decimal(15,2) NOT NULL,
  `total_sales_amount` decimal(15,2) NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `current_cart_count` bigint NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `last_aggregated_at` datetime(6) NOT NULL,
  `order_count` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `review_count` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `today_order_count` bigint NOT NULL,
  `today_refund_quantity` bigint NOT NULL,
  `today_sales_quantity` bigint NOT NULL,
  `total_refund_quantity` bigint NOT NULL,
  `total_sales_quantity` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UKwqgwb7sadf7p71peljf317tm` (`product_id`),
  KEY `idx_product_statistics_seller_id` (`seller_id`),
  KEY `idx_product_statistics_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `products` (
  `average_rating` decimal(3,2) NOT NULL DEFAULT '0.00',
  `price` decimal(10,2) NOT NULL,
  `category_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `review_count` bigint NOT NULL DEFAULT '0',
  `seller_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `description` text COLLATE utf8mb4_unicode_ci,
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_products_seller_id` (`seller_id`),
  KEY `idx_products_category_id` (`category_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reviews` (
  `rating` int NOT NULL,
  `buyer_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_item_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL,
  `image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK96f6ovfc9wn4579incehx4gra` (`order_item_id`),
  KEY `idx_reviews_product_id` (`product_id`),
  KEY `idx_reviews_buyer_id` (`buyer_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `statistics_event_logs` (
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK2tws3a07ylt38v191unxsc5ag` (`event_id`),
  KEY `idx_statistics_event_logs_order_id_event_type` (`order_id`,`event_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `wishlists` (
  `buyer_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK4hyr166pev4gem2saafwdkclt` (`buyer_id`,`product_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
