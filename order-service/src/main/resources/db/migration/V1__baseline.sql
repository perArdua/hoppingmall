CREATE TABLE `cart_items` (
  `product_price` decimal(10,2) DEFAULT NULL,
  `quantity` int DEFAULT NULL,
  `total_price` decimal(19,2) DEFAULT NULL,
  `buyer_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `product_id` bigint DEFAULT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_image_url` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cart_items_buyer_product` (`buyer_id`,`product_id`),
  KEY `idx_cart_items_buyer_id` (`buyer_id`),
  KEY `idx_cart_items_product_id` (`product_id`)
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

CREATE TABLE `order_items` (
  `product_price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL,
  `total_price` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `reservation_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_order_items_order_id` (`order_id`),
  KEY `idx_order_items_product_id` (`product_id`),
  KEY `idx_order_items_seller_id` (`seller_id`),
  KEY `idx_order_items_reservation_id` (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `orders` (
  `cancel_retry_count` int NOT NULL,
  `total_amount` decimal(10,2) NOT NULL,
  `shipping_zip_code` varchar(5) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `buyer_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `delivered_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `shipping_recipient_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `shipping_recipient_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_address_detail` varchar(90) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_address` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `shipping_delivery_memo` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_orders_buyer_id` (`buyer_id`),
  KEY `idx_orders_status_updated_at` (`status`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `outbox_events` (
  `processed` bit(1) NOT NULL,
  `retry_count` int NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `processed_at` datetime(6) DEFAULT NULL,
  `updated_at` datetime(6) NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `aggregate_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `partition_key` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `topic` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `error_message` text COLLATE utf8mb4_unicode_ci,
  `event_data` text COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_outbox_processed_created` (`processed`,`created_at`),
  KEY `idx_outbox_aggregate_id` (`aggregate_id`),
  KEY `idx_outbox_status_retry` (`processed`,`status`,`retry_count`),
  KEY `idx_outbox_processed_processed_at` (`processed`,`processed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refund_event_logs` (
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `refund_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `event_id` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_type` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK1iaoxnff3qqkit2fy0ytogop8` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refund_items` (
  `product_price` decimal(10,2) NOT NULL,
  `quantity` int NOT NULL,
  `refund_price` decimal(10,2) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_item_id` bigint NOT NULL,
  `product_id` bigint NOT NULL,
  `refund_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `product_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_refund_items_refund_id` (`refund_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `refunds` (
  `is_full_refund` bit(1) NOT NULL,
  `refund_amount` decimal(10,2) NOT NULL,
  `return_shipping_fee` decimal(10,2) NOT NULL,
  `approved_by` bigint DEFAULT NULL,
  `buyer_id` bigint NOT NULL,
  `completed_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `payment_id` bigint NOT NULL,
  `seller_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `reason` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reason_detail` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `rejection_reason` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_refunds_order_id` (`order_id`),
  KEY `idx_refunds_payment_id` (`payment_id`),
  KEY `idx_refunds_buyer_id` (`buyer_id`),
  KEY `idx_refunds_seller_id` (`seller_id`),
  KEY `idx_refunds_seller_status_completed` (`seller_id`,`status`,`completed_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `reservation_fence` (
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `decision` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL,
  `reservation_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`reservation_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `saga_event_logs` (
  `payment_amount` decimal(10,2) DEFAULT NULL,
  `remote_completed` bit(1) NOT NULL DEFAULT b'0',
  `timed_out` bit(1) NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `payment_id` bigint DEFAULT NULL,
  `timeout_at` datetime(6) NOT NULL,
  `event_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `event_id` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `idx_saga_event_id` (`event_id`),
  KEY `idx_saga_open_timeout_scan` (`timed_out`,`remote_completed`,`event_type`,`created_at`),
  KEY `idx_saga_order_id` (`order_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `saga_event_log_steps` (
  `saga_event_log_id` bigint NOT NULL,
  `step` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  KEY `FKffn3rrvvvhcmpl2jj0gjr4rb2` (`saga_event_log_id`),
  CONSTRAINT `FKffn3rrvvvhcmpl2jj0gjr4rb2` FOREIGN KEY (`saga_event_log_id`) REFERENCES `saga_event_logs` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `shippings` (
  `attempt` int NOT NULL DEFAULT '1',
  `buyer_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `order_id` bigint NOT NULL,
  `updated_at` datetime(6) DEFAULT NULL,
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL,
  `carrier_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_address` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `recipient_phone` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  `tracking_number` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_shippings_order_id` (`order_id`),
  KEY `idx_shippings_buyer_id` (`buyer_id`),
  KEY `idx_shippings_tracking_number` (`tracking_number`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
