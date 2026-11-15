CREATE TABLE `uploaded_files` (
  `attached_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL,
  `deleted_at` datetime(6) DEFAULT NULL,
  `file_size` bigint NOT NULL,
  `id` bigint NOT NULL AUTO_INCREMENT,
  `updated_at` datetime(6) DEFAULT NULL,
  `uploader_id` bigint NOT NULL,
  `uploader_role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `content_type` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL,
  `object_key` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_uploaded_files_object_key` (`object_key`),
  KEY `idx_uploaded_files_status_created_at` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
