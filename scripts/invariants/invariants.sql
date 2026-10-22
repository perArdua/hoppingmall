-- @INV INV-1 | 주문은 CANCELLED인데 같은 주문의 결제가 SUCCESS
SELECT o.id AS order_id, o.status AS order_status, p.id AS payment_id, p.status AS payment_status, o.updated_at AS order_updated_at, p.updated_at AS payment_updated_at
FROM order_db.orders o
JOIN payment_db.payments p ON p.order_id = o.id AND p.deleted_at IS NULL
WHERE o.deleted_at IS NULL
  AND o.status = 'CANCELLED'
  AND p.status = 'SUCCESS';

-- @INV INV-1R | 결제는 CANCELLED/REFUNDED인데 주문은 살아 있음
SELECT o.id AS order_id, o.status AS order_status, p.id AS payment_id, p.status AS payment_status, o.updated_at AS order_updated_at, p.updated_at AS payment_updated_at
FROM order_db.orders o
JOIN payment_db.payments p ON p.order_id = o.id AND p.deleted_at IS NULL
WHERE o.deleted_at IS NULL
  AND p.status IN ('CANCELLED', 'REFUNDED')
  AND o.status IN ('PAYING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCEL_FAILED');

-- @INV INV-2a | 결제가 {{PENDING_MAX_MINUTES}}분 넘게 PENDING
SELECT p.id AS payment_id, p.order_id, p.status, p.created_at, TIMESTAMPDIFF(MINUTE, p.created_at, NOW()) AS age_minutes
FROM payment_db.payments p
WHERE p.deleted_at IS NULL
  AND p.status = 'PENDING'
  AND p.created_at < NOW() - INTERVAL {{PENDING_MAX_MINUTES}} MINUTE;

-- @INV INV-2b | 주문이 {{PAYING_MAX_MINUTES}}분 넘게 PAYING
SELECT o.id AS order_id, o.status, o.created_at, TIMESTAMPDIFF(MINUTE, o.created_at, NOW()) AS age_minutes
FROM order_db.orders o
WHERE o.deleted_at IS NULL
  AND o.status = 'PAYING'
  AND o.created_at < NOW() - INTERVAL {{PAYING_MAX_MINUTES}} MINUTE;

-- @INV INV-3a | 주문은 CANCELLED인데 그 주문의 재고 예약이 CONFIRMED
SELECT o.id AS order_id, o.status AS order_status, oi.id AS order_item_id, r.reservation_id, r.status AS reservation_status, r.quantity
FROM order_db.orders o
JOIN order_db.order_items oi ON oi.order_id = o.id AND oi.deleted_at IS NULL
JOIN product_db.inventory_reservations r ON r.reservation_id = oi.reservation_id
WHERE o.deleted_at IS NULL
  AND o.status = 'CANCELLED'
  AND r.status = 'CONFIRMED';

-- @INV INV-3b | 어느 주문 항목도 가리키지 않는 RESERVED/CONFIRMED 예약 ({{ORPHAN_GRACE_MINUTES}}분 경과)
SELECT r.reservation_id, r.product_id, r.quantity, r.status, r.created_at, r.expires_at
FROM product_db.inventory_reservations r
LEFT JOIN order_db.order_items oi ON oi.reservation_id = r.reservation_id AND oi.deleted_at IS NULL
WHERE oi.id IS NULL
  AND r.status IN ('RESERVED', 'CONFIRMED')
  AND r.created_at < NOW() - INTERVAL {{ORPHAN_GRACE_MINUTES}} MINUTE;

-- @INV INV-4 | 쿠폰은 USED인데 그 주문에 SUCCESS 결제가 없음
SELECT uc.id AS user_coupon_id, uc.user_id, uc.coupon_id, uc.order_id, uc.used_at
FROM payment_db.user_coupons uc
LEFT JOIN payment_db.payments p ON p.order_id = uc.order_id AND p.status = 'SUCCESS' AND p.deleted_at IS NULL
WHERE uc.deleted_at IS NULL
  AND uc.status = 'USED'
  AND p.id IS NULL;

-- @INV INV-5 | 결제가 SUCCESS가 아닌데 적립(EARN) 포인트가 회수(REFUND)되지 않음
SELECT h.id AS point_history_id, h.user_id, h.payment_id, h.amount, p.status AS payment_status
FROM payment_db.point_histories h
JOIN payment_db.payments p ON p.id = h.payment_id AND p.deleted_at IS NULL
WHERE h.deleted_at IS NULL
  AND h.type = 'EARN'
  AND p.status <> 'SUCCESS'
  AND NOT EXISTS (
    SELECT 1 FROM payment_db.point_histories r
    WHERE r.payment_id = h.payment_id AND r.type = 'REFUND' AND r.deleted_at IS NULL
  );

-- @INV INV-6 | 한 주문에 SUCCESS 결제가 2건 이상
SELECT p.order_id, COUNT(*) AS success_payments, GROUP_CONCAT(p.id ORDER BY p.id) AS payment_ids
FROM payment_db.payments p
WHERE p.deleted_at IS NULL
  AND p.status = 'SUCCESS'
GROUP BY p.order_id
HAVING COUNT(*) > 1;

-- @INV INV-7 | 이벤트 처리 이력의 식별자가 0이거나 멱등 키가 빈 문자열
SELECT 'payment_db.point_histories' AS source, h.id, h.user_id, h.order_id, h.payment_id, h.event_id AS idempotency_key, h.created_at
FROM payment_db.point_histories h
WHERE h.deleted_at IS NULL
  AND h.type = 'EARN'
  AND (h.user_id = 0 OR h.order_id = 0 OR h.payment_id = 0 OR h.event_id = '')
UNION ALL
SELECT 'payment_db.payment_event_logs', l.id, NULL, l.order_id, l.payment_id, l.transaction_id, l.created_at
FROM payment_db.payment_event_logs l
WHERE l.deleted_at IS NULL
  AND (l.order_id = 0 OR l.payment_id = 0 OR l.transaction_id = '')
UNION ALL
SELECT 'product_db.statistics_event_logs', s.id, NULL, s.order_id, NULL, s.event_id, s.created_at
FROM product_db.statistics_event_logs s
WHERE s.deleted_at IS NULL
  AND (s.order_id = 0 OR s.event_id = '')
UNION ALL
SELECT 'user_db.membership_event_logs', m.id, NULL, m.order_id, m.payment_id, m.event_id, m.created_at
FROM user_db.membership_event_logs m
WHERE m.deleted_at IS NULL
  AND (m.order_id = 0 OR m.payment_id = 0 OR m.event_id = '');
