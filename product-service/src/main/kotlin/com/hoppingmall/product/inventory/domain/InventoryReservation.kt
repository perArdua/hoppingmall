package com.hoppingmall.product.inventory.domain

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
        Index(name = "idx_reservation_expires_status", columnList = "expiresAt, status")
    ]
)
class InventoryReservation(
    @Column(nullable = false, unique = true, length = 36)
    val reservationId: String,

    @Column(nullable = false)
    val productId: Long,

    @Column(nullable = false)
    val quantity: Int,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: ReservationStatus = ReservationStatus.RESERVED,

    @Column(nullable = false)
    val expiresAt: LocalDateTime,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    companion object {
        fun create(productId: Long, quantity: Int, ttlMinutes: Long): InventoryReservation {
            val now = LocalDateTime.now()
            return InventoryReservation(
                reservationId = UUID.randomUUID().toString(),
                productId = productId,
                quantity = quantity,
                expiresAt = now.plusMinutes(ttlMinutes),
                createdAt = now,
                updatedAt = now
            )
        }
    }
}
