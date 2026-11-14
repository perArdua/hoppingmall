package com.hoppingmall.product.inventory.domain

import com.hoppingmall.common.EnumNameConverter
import com.hoppingmall.product.inventory.enums.ReservationStatus
import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(
    name = "inventory_reservations",
    indexes = [
        Index(name = "idx_reservation_id", columnList = "reservationId", unique = true),
        Index(name = "idx_reservation_product_status", columnList = "productId, status"),
        Index(name = "idx_reservation_expires_status", columnList = "expiresAt, status"),
        Index(name = "idx_reservation_product_status_expires", columnList = "productId, status, expiresAt"),
        Index(name = "idx_reservation_status_expires", columnList = "status, expiresAt"),
        Index(name = "idx_reservation_status_created", columnList = "status, createdAt"),
        Index(name = "uk_reservation_request_product", columnList = "requestKey, productId", unique = true)
    ]
)
class InventoryReservation(
    @Column(nullable = false, unique = true, length = 36)
    val reservationId: String,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    val quantity: Int,

    @Convert(converter = ReservationStatusConverter::class)
    @Column(nullable = false, length = 32)
    var status: ReservationStatus = ReservationStatus.RESERVED,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(length = REQUEST_KEY_MAX_LENGTH)
    val requestKey: String? = null
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    companion object {
        const val REQUEST_KEY_MAX_LENGTH = 64

        fun create(productId: Long, quantity: Int, ttlSeconds: Long, requestKey: String? = null): InventoryReservation {
            val now = LocalDateTime.now()
            return create(productId, quantity, now.plusSeconds(ttlSeconds), now, requestKey)
        }

        fun create(
            productId: Long,
            quantity: Int,
            expiresAt: LocalDateTime,
            now: LocalDateTime = LocalDateTime.now(),
            requestKey: String? = null
        ): InventoryReservation =
            InventoryReservation(
                reservationId = UUID.randomUUID().toString(),
                productId = productId,
                quantity = quantity,
                expiresAt = expiresAt,
                createdAt = now,
                updatedAt = now,
                requestKey = requestKey
            )
    }
}

class ReservationStatusConverter : EnumNameConverter<ReservationStatus>(ReservationStatus::class.java)
