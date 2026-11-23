UPDATE `orders` o
JOIN `shippings` s ON s.`order_id` = o.`id` AND s.`status` = 'DELIVERED' AND s.`deleted_at` IS NULL
SET o.`delivered_at` = COALESCE(s.`updated_at`, s.`created_at`)
WHERE o.`delivered_at` IS NULL;

UPDATE `orders`
SET `delivered_at` = COALESCE(`updated_at`, `created_at`)
WHERE `delivered_at` IS NULL AND `status` = 'DELIVERED';
