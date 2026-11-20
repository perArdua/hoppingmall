package com.hoppingmall.consistency.support

import org.springframework.jdbc.core.JdbcTemplate

class InvariantFixture(private val jdbc: (String) -> JdbcTemplate) {

    fun truncateAll() {
        TABLES.forEach { (source, tables) -> tables.forEach { jdbc(source).execute("DELETE FROM $it") } }
    }

    fun order(id: Long, status: String, updatedMinutesAgo: Long = 0, createdMinutesAgo: Long = updatedMinutesAgo, deleted: Boolean = false) {
        jdbc("order").update(
            "INSERT INTO orders (id, buyer_id, status, total_amount, cancel_retry_count, created_at, updated_at, deleted_at) " +
                "VALUES (?, 1, ?, 10000, 0, NOW(6) - INTERVAL ? MINUTE, NOW(6) - INTERVAL ? MINUTE, IF(?, NOW(6), NULL))",
            id, status, createdMinutesAgo, updatedMinutesAgo, deleted
        )
    }

    fun orderItem(id: Long, orderId: Long, reservationId: String?, deleted: Boolean = false) {
        jdbc("order").update(
            "INSERT INTO order_items (id, order_id, product_id, seller_id, product_name, product_price, quantity, total_price, " +
                "reservation_id, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, 1, 1, 'item', 1000, 1, 1000, ?, NOW(6), NOW(6), IF(?, NOW(6), NULL))",
            id, orderId, reservationId, deleted
        )
    }

    fun payment(
        id: Long,
        orderId: Long,
        status: String,
        updatedMinutesAgo: Long = 0,
        createdMinutesAgo: Long = updatedMinutesAgo,
        deleted: Boolean = false
    ) {
        jdbc("payment").update(
            "INSERT INTO payments (id, order_id, user_id, amount, point_amount, method, status, created_at, updated_at, deleted_at) " +
                "VALUES (?, ?, 1, 10000, 0, 'CREDIT_CARD', ?, NOW(6) - INTERVAL ? MINUTE, NOW(6) - INTERVAL ? MINUTE, IF(?, NOW(6), NULL))",
            id, orderId, status, createdMinutesAgo, updatedMinutesAgo, deleted
        )
    }

    fun reservation(id: Long, reservationId: String, status: String, updatedMinutesAgo: Long = 0, createdMinutesAgo: Long = updatedMinutesAgo) {
        jdbc("product").update(
            "INSERT INTO inventory_reservations (id, reservation_id, product_id, quantity, status, created_at, updated_at, expires_at) " +
                "VALUES (?, ?, 1, 2, ?, NOW(6) - INTERVAL ? MINUTE, NOW(6) - INTERVAL ? MINUTE, NOW(6) + INTERVAL 10 MINUTE)",
            id, reservationId, status, createdMinutesAgo, updatedMinutesAgo
        )
    }

    fun sagaLog(orderId: Long, eventId: String, secondsAgo: Long) {
        jdbc("order").update(
            "INSERT INTO saga_event_logs (order_id, event_id, event_type, timed_out, created_at, timeout_at) " +
                "VALUES (?, ?, 'PAYMENT', 0, NOW(6) - INTERVAL ? SECOND, NOW(6))",
            orderId, eventId, secondsAgo
        )
    }

    fun outboxEvent(
        source: String,
        id: Long,
        eventType: String,
        aggregateId: String,
        topic: String,
        status: String,
        eventData: String,
        processedSecondsAgo: Long? = null,
        createdMinutesAgo: Long = 0
    ) {
        jdbc(source).update(
            "INSERT INTO outbox_events (id, aggregate_type, aggregate_id, event_type, event_data, topic, status, processed, retry_count, " +
                "created_at, updated_at, processed_at) " +
                "VALUES (?, 'Payment', ?, ?, ?, ?, ?, ?, 0, NOW(6) - INTERVAL ? MINUTE, NOW(6), IF(? IS NULL, NULL, NOW(6) - INTERVAL ? SECOND))",
            id, aggregateId, eventType, eventData, topic, status, processedSecondsAgo != null, createdMinutesAgo,
            processedSecondsAgo, processedSecondsAgo
        )
    }

    fun dlq(source: String, id: Long, status: String, createdMinutesAgo: Long, archived: Boolean = false) {
        jdbc(source).update(
            "INSERT INTO dlq_messages (id, original_topic, original_partition, original_offset, retry_count, error_timestamp, status, " +
                "created_at, archived_at) VALUES (?, 'payment', 0, 0, 0, 0, ?, NOW(6) - INTERVAL ? MINUTE, ?)",
            id, status, createdMinutesAgo, if (archived) 1L else null
        )
    }

    fun orderIdempotencyRecord(orderId: Long) {
        jdbc("order").update(
            "INSERT INTO idempotency_records (idempotency_key, endpoint, http_method, response_status, response_body, created_at, expires_at) " +
                "VALUES (?, '/api/v1/orders', 'POST', 201, ?, NOW(6), NOW(6) + INTERVAL 1 DAY)",
            "client-$orderId", """{"code":"SUCCESS","data":{"id":$orderId}}"""
        )
    }

    fun pointHistory(paymentId: Long?, type: String, amount: String, eventId: String?, userId: Long = 1, orderId: Long? = null) {
        jdbc("payment").update(
            "INSERT INTO point_histories (user_id, payment_id, order_id, type, amount, event_id, created_at) VALUES (?, ?, ?, ?, ?, ?, NOW(6))",
            userId, paymentId, orderId, type, amount.toBigDecimal(), eventId
        )
    }

    fun userCoupon(id: Long, orderId: Long?, status: String) {
        jdbc("payment").update(
            "INSERT INTO user_coupons (id, user_id, coupon_id, order_id, status, created_at, used_at) VALUES (?, 1, ?, ?, ?, NOW(6), NOW(6))",
            id, id, orderId, status
        )
    }

    fun paymentEventLog(orderId: Long, paymentId: Long, transactionId: String) {
        jdbc("payment").update(
            "INSERT INTO payment_event_logs (order_id, payment_id, transaction_id, created_at) VALUES (?, ?, ?, NOW(6))",
            orderId, paymentId, transactionId
        )
    }

    fun statisticsEventLog(orderId: Long, eventId: String) {
        jdbc("product").update(
            "INSERT INTO statistics_event_logs (order_id, event_id, event_type, created_at) VALUES (?, ?, 'ORDER_PAID', NOW(6))",
            orderId, eventId
        )
    }

    fun membershipEventLog(orderId: Long, paymentId: Long, eventId: String) {
        jdbc("user").update(
            "INSERT INTO membership_event_logs (order_id, payment_id, event_id, created_at) VALUES (?, ?, ?, NOW(6))",
            orderId, paymentId, eventId
        )
    }

    fun insertRecentDataset() {
        order(101, "CANCELLED")
        payment(201, 101, "SUCCESS")
        order(102, "CANCELLED")
        payment(202, 102, "FAILED")
        order(103, "PAID", createdMinutesAgo = 5)
        payment(203, 103, "SUCCESS")
        order(104, "CANCELLED", deleted = true)
        payment(204, 104, "SUCCESS")
        orderIdempotencyRecord(103)

        order(111, "PAID")
        payment(211, 111, "CANCELLED")
        order(112, "CANCELLED")
        payment(212, 112, "REFUNDED")
        order(113, "CANCEL_FAILED")
        payment(213, 113, "REFUNDED")
        order(114, "PENDING")
        payment(214, 114, "CANCELLED")

        payment(221, 9221, "PENDING", createdMinutesAgo = 30)
        payment(222, 9222, "PENDING", createdMinutesAgo = 1)
        order(121, "PAYING", createdMinutesAgo = 30)
        order(122, "PAYING", createdMinutesAgo = 1)

        order(131, "CANCELLED")
        orderItem(1311, 131, "r-131")
        reservation(131, "r-131", "CONFIRMED", createdMinutesAgo = 5)
        order(132, "CANCELLED")
        orderItem(1321, 132, "r-132")
        reservation(132, "r-132", "CANCELLED", createdMinutesAgo = 5)
        order(133, "PAID")
        orderItem(1331, 133, "r-133")
        reservation(133, "r-133", "CONFIRMED", createdMinutesAgo = 5)
        order(134, "CANCELLED")
        orderItem(1341, 134, "r-134", deleted = true)
        reservation(134, "r-134", "CONFIRMED", createdMinutesAgo = 5)

        reservation(141, "r-141", "RESERVED", createdMinutesAgo = 5)
        reservation(142, "r-142", "RESERVED", createdMinutesAgo = 0)
        reservation(144, "r-144", "EXPIRED", createdMinutesAgo = 5)

        userCoupon(151, 9151, "USED")
        userCoupon(152, 103, "USED")
        userCoupon(153, null, "AVAILABLE")

        payment(261, 161, "SUCCESS")
        payment(262, 161, "SUCCESS")

        pointHistory(211, "EARN", "300", "earn-211")
        pointHistory(213, "EARN", "300", "earn-213")
        pointHistory(213, "RECLAIM", "-300", "refund-213")
        pointHistory(212, "USE", "-3000", "use-212")
        pointHistory(212, "REFUND", "1000", "refund-point-212-1")
        pointHistory(214, "USE", "-1000", "use-214")
        pointHistory(214, "REFUND", "1500", "use-cancel-214")
        pointHistory(214, "REFUND", "1500", "refund-point-214-1")
        pointHistory(201, "EARN", "100", "earn-201-zero-user", userId = 0)
        paymentEventLog(101, 201, "")
        paymentEventLog(103, 203, "tx-203")
        statisticsEventLog(0, "stat-zero")
        statisticsEventLog(103, "stat-103")
        membershipEventLog(103, 203, "")
        membershipEventLog(103, 203, "member-103")

        order(171, "CANCELLED")
        payment(271, 171, "CANCELLED")
        outboxEvent("payment", 1, "PaymentCompleted", "271", "payment", "PUBLISHED",
            """{"paymentId":271,"orderId":171,"transactionId":"tx-171"}""", processedSecondsAgo = 200)
        outboxEvent("payment", 2, "PaymentCancelled", "271", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-171","paymentId":271,"orderId":171}""", processedSecondsAgo = 190)
        sagaLog(171, "payment-completed-tx-171", 60)
        sagaLog(171, "pc-171", 120)
        order(172, "CANCELLED")
        payment(272, 172, "CANCELLED")
        outboxEvent("payment", 3, "PaymentCompleted", "272", "payment", "PUBLISHED",
            """{"paymentId":272,"orderId":172,"transactionId":"tx-172"}""", processedSecondsAgo = 200)
        outboxEvent("payment", 4, "PaymentCancelled", "272", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-172","paymentId":272,"orderId":172}""", processedSecondsAgo = 190)
        sagaLog(172, "payment-completed-tx-172", 120)
        sagaLog(172, "pc-172", 60)
        order(173, "CANCEL_FAILED")
        payment(273, 173, "SUCCESS")
        outboxEvent("payment", 5, "PaymentCompleted", "273", "payment", "PUBLISHED",
            """{"paymentId":273,"orderId":173,"transactionId":"tx-173"}""", processedSecondsAgo = 200)
        outboxEvent("payment", 6, "PaymentCancellationFailed", "173", "payment-compensation", "PUBLISHED",
            """{"eventType":"PaymentCancellationFailed","eventId":"cf-173","orderId":173}""", processedSecondsAgo = 190)
        outboxEvent("payment", 7, "PointEarnRequested", "273", "point-earn-request", "PUBLISHED",
            """{"eventId":"earn-173","orderId":173}""", processedSecondsAgo = 300)
        sagaLog(173, "payment-completed-tx-173", 30)
        sagaLog(173, "cf-173", 180)
        sagaLog(173, "earn-173", 10)

        outboxEvent("order", 11, "OrderCreated", "171", "order", "PENDING", "{}", createdMinutesAgo = 5)
        outboxEvent("order", 12, "OrderCreated", "172", "order", "PENDING", "{}", createdMinutesAgo = 0)
        outboxEvent("payment", 13, "PaymentCompleted", "999", "payment", "RETRYING", "{}", createdMinutesAgo = 5)

        SOURCES_WITH_DLQ.forEachIndexed { index, source ->
            val base = 100L * (index + 1)
            dlq(source, base + 1, "PENDING", createdMinutesAgo = 5)
            dlq(source, base + 2, "PROCESSED", createdMinutesAgo = 5)
            dlq(source, base + 3, "FAILED", createdMinutesAgo = 5, archived = true)
            dlq(source, base + 4, "RETRYING", createdMinutesAgo = 0)
        }
    }

    fun insertStaleCrossSourceDataset() {
        order(501, "CANCELLED", updatedMinutesAgo = STALE)
        payment(601, 501, "SUCCESS", updatedMinutesAgo = STALE)
        order(502, "PAID", updatedMinutesAgo = STALE)
        payment(602, 502, "REFUNDED", updatedMinutesAgo = STALE)
        order(503, "CANCELLED", updatedMinutesAgo = STALE)
        orderItem(5031, 503, "r-503")
        reservation(503, "r-503", "CONFIRMED", updatedMinutesAgo = STALE)
        reservation(504, "r-504", "RESERVED", updatedMinutesAgo = STALE)
        order(505, "CANCELLED", updatedMinutesAgo = STALE)
        payment(605, 505, "CANCELLED", updatedMinutesAgo = STALE)
        outboxEvent("payment", 21, "PaymentCompleted", "605", "payment", "PUBLISHED",
            """{"paymentId":605,"orderId":505,"transactionId":"tx-505"}""", processedSecondsAgo = STALE * 60 + 20)
        outboxEvent("payment", 22, "PaymentCancelled", "605", "payment-compensation", "PUBLISHED",
            """{"eventId":"pc-505","paymentId":605,"orderId":505}""", processedSecondsAgo = STALE * 60 + 10)
        sagaLog(505, "payment-completed-tx-505", STALE * 60)
        sagaLog(505, "pc-505", STALE * 60 + 5)
    }

    companion object {
        const val STALE = 1440L

        val SOURCES_WITH_DLQ = listOf("order", "payment", "product", "user", "notification")

        val TABLES = mapOf(
            "order" to listOf("orders", "order_items", "saga_event_logs", "outbox_events", "dlq_messages", "idempotency_records"),
            "payment" to listOf("payments", "point_histories", "user_coupons", "payment_event_logs", "outbox_events", "dlq_messages"),
            "product" to listOf("inventory_reservations", "statistics_event_logs", "dlq_messages"),
            "user" to listOf("membership_event_logs", "dlq_messages"),
            "notification" to listOf("dlq_messages")
        )
    }
}
