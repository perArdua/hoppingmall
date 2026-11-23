ALTER TABLE `products` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `inventory_reservations` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `bulk_import_jobs` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `dlq_messages` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
