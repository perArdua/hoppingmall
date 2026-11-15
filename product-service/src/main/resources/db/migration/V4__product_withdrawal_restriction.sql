ALTER TABLE `products`
  ADD COLUMN `withdrawal_restricted` bit(1) NOT NULL DEFAULT b'0',
  ADD COLUMN `withdrawal_restriction_reason` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL;
