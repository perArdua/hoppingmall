ALTER TABLE `users` MODIFY `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `sellers` MODIFY `approval_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `memberships` MODIFY `grade` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
ALTER TABLE `membership_event_logs` MODIFY `type` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
ALTER TABLE `user_sessions` MODIFY `revoke_reason` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
ALTER TABLE `dlq_messages` MODIFY `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL;
