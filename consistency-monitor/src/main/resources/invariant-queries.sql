-- @INV INV-1 | 주문은 CANCELLED인데 같은 주문의 결제가 SUCCESS
-- @SQL order recent-orders
SELECT o.id AS order_id
FROM orders o
WHERE o.status = 'CANCELLED'
  AND o.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL payment recent-payments
SELECT p.order_id
FROM payments p
WHERE p.status = 'SUCCESS'
  AND p.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL order orders-by-id
SELECT o.id AS order_id, o.status AS order_status, o.updated_at AS order_updated_at
FROM orders o
WHERE o.id IN (:ids)
  AND o.deleted_at IS NULL
  AND o.status = 'CANCELLED';
-- @SQL payment payments-by-order
SELECT p.order_id, p.id AS payment_id, p.status AS payment_status, p.updated_at AS payment_updated_at
FROM payments p
WHERE p.order_id IN (:ids)
  AND p.deleted_at IS NULL
  AND p.status = 'SUCCESS';
-- @SQL order sweep-bounds
SELECT MIN(o.id) AS min_id, MAX(o.id) AS max_id
FROM orders o;
-- @SQL order sweep
SELECT o.id AS order_id
FROM orders o
WHERE o.id BETWEEN :fromId AND :toId
  AND o.status = 'CANCELLED';

-- @INV INV-1R | 결제는 CANCELLED/REFUNDED인데 주문은 살아 있음
-- @SQL order recent-orders
SELECT o.id AS order_id
FROM orders o
WHERE o.status IN ('PAYING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCEL_FAILED')
  AND o.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL payment recent-payments
SELECT p.order_id
FROM payments p
WHERE p.status IN ('CANCELLED', 'REFUNDED')
  AND p.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL order orders-by-id
SELECT o.id AS order_id, o.status AS order_status, o.updated_at AS order_updated_at
FROM orders o
WHERE o.id IN (:ids)
  AND o.deleted_at IS NULL
  AND o.status IN ('PAYING', 'PAID', 'SHIPPED', 'DELIVERED', 'CANCEL_FAILED');
-- @SQL payment payments-by-order
SELECT p.order_id, p.id AS payment_id, p.status AS payment_status, p.updated_at AS payment_updated_at
FROM payments p
WHERE p.order_id IN (:ids)
  AND p.deleted_at IS NULL
  AND p.status IN ('CANCELLED', 'REFUNDED');
-- @SQL payment sweep-bounds
SELECT MIN(p.id) AS min_id, MAX(p.id) AS max_id
FROM payments p;
-- @SQL payment sweep
SELECT p.order_id
FROM payments p
WHERE p.id BETWEEN :fromId AND :toId
  AND p.status IN ('CANCELLED', 'REFUNDED');

-- @INV INV-2a | 결제가 {{PENDING_MAX_MINUTES}}분 넘게 PENDING
-- @SQL payment check
SELECT p.id AS payment_id, p.order_id, p.status, p.created_at, TIMESTAMPDIFF(MINUTE, p.created_at, NOW()) AS age_minutes
FROM payments p
WHERE p.deleted_at IS NULL
  AND p.status = 'PENDING'
  AND p.created_at < NOW() - INTERVAL {{PENDING_MAX_MINUTES}} MINUTE;

-- @INV INV-2b | 주문이 {{PAYING_MAX_MINUTES}}분 넘게 PAYING
-- @SQL order check
SELECT o.id AS order_id, o.status, o.created_at, TIMESTAMPDIFF(MINUTE, o.created_at, NOW()) AS age_minutes
FROM orders o
WHERE o.deleted_at IS NULL
  AND o.status = 'PAYING'
  AND o.created_at < NOW() - INTERVAL {{PAYING_MAX_MINUTES}} MINUTE;

-- @INV INV-3a | 주문은 CANCELLED인데 그 주문의 재고 예약이 CONFIRMED
-- @SQL order recent-orders
SELECT oi.reservation_id
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.status = 'CANCELLED'
  AND o.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE
  AND oi.reservation_id IS NOT NULL;
-- @SQL product recent-reservations
SELECT r.reservation_id
FROM inventory_reservations r
WHERE r.status = 'CONFIRMED'
  AND r.updated_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL order order-items-by-reservation
SELECT oi.reservation_id, o.id AS order_id, o.status AS order_status, oi.id AS order_item_id
FROM order_items oi
JOIN orders o ON o.id = oi.order_id
WHERE oi.reservation_id IN (:ids)
  AND oi.deleted_at IS NULL
  AND o.deleted_at IS NULL
  AND o.status = 'CANCELLED';
-- @SQL product reservations-by-id
SELECT r.reservation_id, r.status AS reservation_status, r.quantity
FROM inventory_reservations r
WHERE r.reservation_id IN (:ids)
  AND r.status = 'CONFIRMED';
-- @SQL order sweep-bounds
SELECT MIN(o.id) AS min_id, MAX(o.id) AS max_id
FROM orders o;
-- @SQL order sweep
SELECT oi.reservation_id
FROM orders o
JOIN order_items oi ON oi.order_id = o.id
WHERE o.id BETWEEN :fromId AND :toId
  AND o.status = 'CANCELLED'
  AND oi.reservation_id IS NOT NULL;

-- @INV INV-3b | 어느 주문 항목도 가리키지 않는 RESERVED/CONFIRMED 예약 ({{ORPHAN_GRACE_MINUTES}}분 경과)
-- @SQL product recent-reservations
SELECT r.reservation_id
FROM inventory_reservations r
WHERE r.status IN ('RESERVED', 'CONFIRMED')
  AND r.updated_at >= NOW() - INTERVAL (:windowMinutes + {{ORPHAN_GRACE_MINUTES}}) MINUTE;
-- @SQL product reservations-by-id
SELECT r.reservation_id, r.product_id, r.quantity, r.status, r.created_at, r.expires_at
FROM inventory_reservations r
WHERE r.reservation_id IN (:ids)
  AND r.status IN ('RESERVED', 'CONFIRMED')
  AND r.created_at < NOW() - INTERVAL {{ORPHAN_GRACE_MINUTES}} MINUTE;
-- @SQL order order-items-by-reservation
SELECT oi.reservation_id
FROM order_items oi
WHERE oi.reservation_id IN (:ids)
  AND oi.deleted_at IS NULL;
-- @SQL product sweep-bounds
SELECT MIN(r.id) AS min_id, MAX(r.id) AS max_id
FROM inventory_reservations r;
-- @SQL product sweep
SELECT r.reservation_id
FROM inventory_reservations r
WHERE r.id BETWEEN :fromId AND :toId
  AND r.status IN ('RESERVED', 'CONFIRMED');

-- @INV INV-4 | 쿠폰은 USED인데 그 주문에 SUCCESS 결제가 없음
-- @SQL payment check
SELECT uc.id AS user_coupon_id, uc.user_id, uc.coupon_id, uc.order_id, uc.used_at
FROM user_coupons uc
LEFT JOIN payments p ON p.order_id = uc.order_id AND p.status = 'SUCCESS' AND p.deleted_at IS NULL
WHERE uc.deleted_at IS NULL
  AND uc.status = 'USED'
  AND p.id IS NULL;

-- @INV INV-5 | 결제가 SUCCESS가 아닌데 적립(EARN) 포인트가 회수(RECLAIM, 이전 형식 REFUND refund-{결제}·refund-{결제}-{환불})되지 않음
-- @SQL payment check
SELECT h.id AS point_history_id, h.user_id, h.payment_id, h.amount, p.status AS payment_status
FROM point_histories h
JOIN payments p ON p.id = h.payment_id AND p.deleted_at IS NULL
WHERE h.deleted_at IS NULL
  AND h.type = 'EARN'
  AND p.status <> 'SUCCESS'
  AND NOT EXISTS (
    SELECT 1 FROM point_histories r
    WHERE r.payment_id = h.payment_id AND r.deleted_at IS NULL
      AND (r.type = 'RECLAIM'
        OR (r.type = 'REFUND' AND (r.event_id = CONCAT('refund-', h.payment_id) OR r.event_id LIKE CONCAT('refund-', h.payment_id, '-%'))))
  );

-- @INV INV-13 | 결제가 FAILED·CANCELLED·REFUNDED인데 결제에 쓴 포인트(USE)가 다 되돌려지지 않음(되돌림 = REFUND use-…·refund-point-…)
-- @SQL payment check
SELECT p.id AS payment_id, p.user_id, p.status AS payment_status, u.used, COALESCE(r.restored, 0) AS restored
FROM payments p
JOIN (
  SELECT payment_id, SUM(-amount) AS used FROM point_histories
  WHERE type = 'USE' AND deleted_at IS NULL AND payment_id IS NOT NULL
  GROUP BY payment_id
) u ON u.payment_id = p.id
LEFT JOIN (
  SELECT payment_id, SUM(amount) AS restored FROM point_histories
  WHERE type = 'REFUND' AND deleted_at IS NULL AND (event_id LIKE 'use-%' OR event_id LIKE 'refund-point-%')
  GROUP BY payment_id
) r ON r.payment_id = p.id
WHERE p.deleted_at IS NULL
  AND p.status IN ('FAILED', 'CANCELLED', 'REFUNDED')
  AND u.used > COALESCE(r.restored, 0);

-- @INV INV-14 | 결제에 쓴 포인트(USE)보다 많은 포인트가 되돌려짐(되돌림 = REFUND use-…·refund-point-…, {{POINT_PAYMENT_SINCE}} 이후 결제만)
-- @SQL payment check
SELECT r.payment_id, r.restored, COALESCE(u.used, 0) AS used
FROM (
  SELECT h.payment_id, SUM(h.amount) AS restored FROM point_histories h
  WHERE h.type = 'REFUND' AND h.deleted_at IS NULL AND h.payment_id IS NOT NULL AND (h.event_id LIKE 'use-%' OR h.event_id LIKE 'refund-point-%')
    AND h.created_at >= '{{POINT_PAYMENT_SINCE}}'
    AND NOT EXISTS (SELECT 1 FROM payments p WHERE p.id = h.payment_id AND p.created_at < '{{POINT_PAYMENT_SINCE}}')
  GROUP BY h.payment_id
) r
LEFT JOIN (
  SELECT payment_id, SUM(-amount) AS used FROM point_histories
  WHERE type = 'USE' AND deleted_at IS NULL
  GROUP BY payment_id
) u ON u.payment_id = r.payment_id
WHERE r.restored > COALESCE(u.used, 0);

-- @INV INV-6 | 한 주문에 SUCCESS 결제가 2건 이상
-- @SQL payment check
SELECT p.order_id, COUNT(*) AS success_payments, GROUP_CONCAT(p.id ORDER BY p.id) AS payment_ids
FROM payments p
WHERE p.deleted_at IS NULL
  AND p.status = 'SUCCESS'
GROUP BY p.order_id
HAVING COUNT(*) > 1;

-- @INV INV-7 | 이벤트 처리 이력의 식별자가 0이거나 멱등 키가 빈 문자열
-- @SQL payment check-payment
SELECT CONCAT('payment_db.point_histories:', h.id) AS entity, h.user_id, h.order_id, h.payment_id, h.event_id AS idempotency_key, h.created_at
FROM point_histories h
WHERE h.deleted_at IS NULL
  AND h.type = 'EARN'
  AND (h.user_id = 0 OR h.order_id = 0 OR h.payment_id = 0 OR h.event_id = '')
UNION ALL
SELECT CONCAT('payment_db.payment_event_logs:', l.id), NULL, l.order_id, l.payment_id, l.transaction_id, l.created_at
FROM payment_event_logs l
WHERE l.deleted_at IS NULL
  AND (l.order_id = 0 OR l.payment_id = 0 OR l.transaction_id = '');
-- @SQL product check-product
SELECT CONCAT('product_db.statistics_event_logs:', s.id) AS entity, NULL AS user_id, s.order_id, NULL AS payment_id, s.event_id AS idempotency_key, s.created_at
FROM statistics_event_logs s
WHERE s.deleted_at IS NULL
  AND (s.order_id = 0 OR s.event_id = '');
-- @SQL user check-user
SELECT CONCAT('user_db.membership_event_logs:', m.id) AS entity, NULL AS user_id, m.order_id, m.payment_id, m.event_id AS idempotency_key, m.created_at
FROM membership_event_logs m
WHERE m.deleted_at IS NULL
  AND (m.order_id = 0 OR m.payment_id = 0 OR m.event_id = '');

-- @INV INV-9 | 주문 생성 API로 만든 주문인데 멱등 레코드(키=clientId-clientSeq)가 가리키지 않음 ({{ORPHAN_GRACE_MINUTES}}분 경과)
-- @SQL order check
SELECT o.id AS order_id, o.buyer_id, o.status, o.created_at
FROM orders o
WHERE o.deleted_at IS NULL
  AND o.created_at < NOW() - INTERVAL {{ORPHAN_GRACE_MINUTES}} MINUTE
  AND NOT EXISTS (
    SELECT 1 FROM idempotency_records r
    WHERE r.endpoint = '/api/v1/orders'
      AND CAST(JSON_EXTRACT(r.response_body, '$.data.id') AS UNSIGNED) = o.id
  );

-- @INV INV-10 | 같은 주문의 결제→주문 이벤트가 outbox 순서와 다른 순서로 처리됨(saga_event_logs 기록 시각 기준, 같은 토픽을 서로 다른 소비자 그룹이 처리하는 짝은 제외)
-- @SQL payment recent-events
SELECT CAST(JSON_EXTRACT(e.event_data, '$.orderId') AS UNSIGNED) AS order_id
FROM outbox_events e FORCE INDEX (idx_outbox_processed_processed_at)
WHERE e.processed = 1
  AND e.processed_at >= NOW() - INTERVAL :windowMinutes MINUTE
  AND e.status = 'PUBLISHED'
  AND e.topic IN ('payment', 'payment-compensation');
-- @SQL order recent-saga-logs
SELECT s.order_id
FROM saga_event_logs s
WHERE s.created_at >= NOW() - INTERVAL :windowMinutes MINUTE;
-- @SQL payment payments-by-order
SELECT p.id AS payment_id
FROM payments p
WHERE p.order_id IN (:ids);
-- @SQL payment events-by-aggregate
SELECT e.id AS outbox_id, e.event_type, e.topic, CAST(JSON_EXTRACT(e.event_data, '$.orderId') AS UNSIGNED) AS order_id,
  CASE e.event_type
    WHEN 'PaymentCompleted' THEN CONCAT('payment-completed-', JSON_UNQUOTE(JSON_EXTRACT(e.event_data, '$.transactionId')))
    ELSE JSON_UNQUOTE(JSON_EXTRACT(e.event_data, '$.eventId'))
  END AS saga_event_id
FROM outbox_events e
WHERE e.aggregate_id IN (:ids)
  AND e.status = 'PUBLISHED'
  AND e.topic IN ('payment', 'payment-compensation');
-- @SQL order saga-logs-by-event
SELECT s.event_id, s.created_at
FROM saga_event_logs s
WHERE s.event_id IN (:ids);
-- @SQL payment sweep-bounds
SELECT MIN(e.id) AS min_id, MAX(e.id) AS max_id
FROM outbox_events e;
-- @SQL payment sweep
SELECT CAST(JSON_EXTRACT(e.event_data, '$.orderId') AS UNSIGNED) AS order_id
FROM outbox_events e
WHERE e.id BETWEEN :fromId AND :toId
  AND e.status = 'PUBLISHED'
  AND e.topic IN ('payment', 'payment-compensation');

-- @INV INV-11 | Outbox 이벤트가 {{OUTBOX_MAX_SECONDS}}초 넘게 미발행(PENDING/RETRYING)
-- @SQL order check-order
SELECT CONCAT('order_db.outbox_events:', e.id) AS entity, e.event_type, e.topic, e.status, e.retry_count, e.created_at, TIMESTAMPDIFF(SECOND, e.created_at, NOW()) AS age_seconds
FROM outbox_events e
WHERE e.status IN ('PENDING', 'RETRYING')
  AND e.created_at < NOW() - INTERVAL {{OUTBOX_MAX_SECONDS}} SECOND;
-- @SQL payment check-payment
SELECT CONCAT('payment_db.outbox_events:', e.id) AS entity, e.event_type, e.topic, e.status, e.retry_count, e.created_at, TIMESTAMPDIFF(SECOND, e.created_at, NOW()) AS age_seconds
FROM outbox_events e
WHERE e.status IN ('PENDING', 'RETRYING')
  AND e.created_at < NOW() - INTERVAL {{OUTBOX_MAX_SECONDS}} SECOND;

-- @INV INV-12 | DLQ 메시지가 {{DLQ_MAX_SECONDS}}초 넘게 미처리(PENDING/RETRYING/FAILED)
-- @SQL order check-order
SELECT CONCAT('order_db.dlq_messages:', d.id) AS entity, d.original_topic, d.status, d.retry_count, d.created_at, TIMESTAMPDIFF(SECOND, d.created_at, NOW()) AS age_seconds
FROM dlq_messages d
WHERE d.deleted_at IS NULL AND d.archived_at IS NULL
  AND d.status IN ('PENDING', 'RETRYING', 'FAILED')
  AND d.created_at < NOW() - INTERVAL {{DLQ_MAX_SECONDS}} SECOND;
-- @SQL payment check-payment
SELECT CONCAT('payment_db.dlq_messages:', d.id) AS entity, d.original_topic, d.status, d.retry_count, d.created_at, TIMESTAMPDIFF(SECOND, d.created_at, NOW()) AS age_seconds
FROM dlq_messages d
WHERE d.deleted_at IS NULL AND d.archived_at IS NULL
  AND d.status IN ('PENDING', 'RETRYING', 'FAILED')
  AND d.created_at < NOW() - INTERVAL {{DLQ_MAX_SECONDS}} SECOND;
-- @SQL product check-product
SELECT CONCAT('product_db.dlq_messages:', d.id) AS entity, d.original_topic, d.status, d.retry_count, d.created_at, TIMESTAMPDIFF(SECOND, d.created_at, NOW()) AS age_seconds
FROM dlq_messages d
WHERE d.deleted_at IS NULL AND d.archived_at IS NULL
  AND d.status IN ('PENDING', 'RETRYING', 'FAILED')
  AND d.created_at < NOW() - INTERVAL {{DLQ_MAX_SECONDS}} SECOND;
-- @SQL user check-user
SELECT CONCAT('user_db.dlq_messages:', d.id) AS entity, d.original_topic, d.status, d.retry_count, d.created_at, TIMESTAMPDIFF(SECOND, d.created_at, NOW()) AS age_seconds
FROM dlq_messages d
WHERE d.deleted_at IS NULL AND d.archived_at IS NULL
  AND d.status IN ('PENDING', 'RETRYING', 'FAILED')
  AND d.created_at < NOW() - INTERVAL {{DLQ_MAX_SECONDS}} SECOND;
-- @SQL notification check-notification
SELECT CONCAT('notification_db.dlq_messages:', d.id) AS entity, d.original_topic, d.status, d.retry_count, d.created_at, TIMESTAMPDIFF(SECOND, d.created_at, NOW()) AS age_seconds
FROM dlq_messages d
WHERE d.deleted_at IS NULL AND d.archived_at IS NULL
  AND d.status IN ('PENDING', 'RETRYING', 'FAILED')
  AND d.created_at < NOW() - INTERVAL {{DLQ_MAX_SECONDS}} SECOND;
