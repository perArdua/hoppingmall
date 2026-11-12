package com.hoppingmall.product.inventory.domain.repository

import com.hoppingmall.product.inventory.domain.InventoryReservation
import com.hoppingmall.product.inventory.enums.ReservationStatus
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface InventoryReservationRepository : JpaRepository<InventoryReservation, Long> {

    fun findByReservationId(reservationId: String): InventoryReservation?

    fun findByReservationIdIn(reservationIds: List<String>): List<InventoryReservation>

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryReservation r
        SET r.status = :targetStatus, r.updatedAt = :updatedAt
        WHERE r.reservationId = :reservationId
        AND r.status = :expectedStatus
    """)
    fun updateStatusByCas(
        @Param("reservationId") reservationId: String,
        @Param("expectedStatus") expectedStatus: ReservationStatus,
        @Param("targetStatus") targetStatus: ReservationStatus,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryReservation r
        SET r.status = :targetStatus, r.updatedAt = :updatedAt
        WHERE r.reservationId IN :reservationIds
        AND r.status = :expectedStatus
    """)
    fun batchUpdateStatusByCas(
        @Param("reservationIds") reservationIds: List<String>,
        @Param("expectedStatus") expectedStatus: ReservationStatus,
        @Param("targetStatus") targetStatus: ReservationStatus,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Query(
        value = """
            SELECT id, product_id, quantity FROM inventory_reservations
            WHERE reservation_id IN (:reservationIds) AND status = 'RESERVED'
            ORDER BY id
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun lockCancellable(@Param("reservationIds") reservationIds: List<String>): List<Array<Any>>

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE inventory_reservations SET status = 'CANCELLED', updated_at = :updatedAt
            WHERE id IN (:ids)
        """,
        nativeQuery = true
    )
    fun cancelByIds(
        @Param("ids") ids: List<Long>,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Query("""
        SELECT r FROM InventoryReservation r
        WHERE r.status = :status
        AND r.expiresAt < :now
        ORDER BY r.expiresAt ASC
    """)
    fun findExpiredReservations(
        @Param("status") status: ReservationStatus,
        @Param("now") now: LocalDateTime,
        limit: Pageable
    ): List<InventoryReservation>
}
