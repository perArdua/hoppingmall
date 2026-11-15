ALTER TABLE `orders` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `refunds` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `refunds` MODIFY `reason` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `shippings` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `outbox_events` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `dlq_messages` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
