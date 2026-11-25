CREATE TABLE IF NOT EXISTS `inventory_runtime_mode` (
  `bucket_count` int DEFAULT NULL,
  `id` int NOT NULL,
  `updated_at` datetime(6) NOT NULL,
  `mode` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
