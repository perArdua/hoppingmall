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

    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE InventoryReservation r
        SET r.status = com.hoppingmall.product.inventory.enums.ReservationStatus.CONFIRMED, r.updatedAt = :updatedAt
        WHERE r.reservationId IN :reservationIds
        AND r.status IN :expectedStatuses
    """)
    fun batchConfirmByCas(
        @Param("reservationIds") reservationIds: List<String>,
        @Param("expectedStatuses") expectedStatuses: List<ReservationStatus>,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Query(
        value = """
            SELECT id, product_id, quantity FROM inventory_reservations
            WHERE reservation_id IN (:reservationIds) AND status IN ('RESERVED', 'CLAIMED')
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

    @Query(
        value = """
            SELECT reservation_id FROM inventory_reservations
            WHERE product_id = :productId AND status = 'RESERVED' AND expires_at < :now
            ORDER BY expires_at, id LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findExpiredReservationIds(
        @Param("productId") productId: Long,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<String>

    @Query(
        value = """
            SELECT reservation_id, product_id FROM inventory_reservations
            WHERE status = 'RESERVED' AND created_at < :cutoff
            ORDER BY created_at, id LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findReservedCreatedBefore(
        @Param("cutoff") cutoff: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Array<Any>>

    @Query(
        value = """
            SELECT MIN(expires_at) FROM inventory_reservations
            WHERE status = 'RESERVED' AND expires_at < :now
        """,
        nativeQuery = true
    )
    fun oldestExpiredExpiresAt(@Param("now") now: LocalDateTime): LocalDateTime?

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE inventory_reservations SET status = 'CLAIMED', expires_at = :until, updated_at = :updatedAt
            WHERE reservation_id IN (:reservationIds) AND status = 'RESERVED'
        """,
        nativeQuery = true
    )
    fun claimByReservationIds(
        @Param("reservationIds") reservationIds: List<String>,
        @Param("until") until: LocalDateTime,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int

    @Query(
        value = """
            SELECT id, quantity FROM inventory_reservations
            WHERE reservation_id IN (:reservationIds) AND status = 'RESERVED'
            ORDER BY id
            FOR UPDATE
        """,
        nativeQuery = true
    )
    fun lockReservedByReservationIds(@Param("reservationIds") reservationIds: List<String>): List<Array<Any>>

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

    @Query(
        value = """
            SELECT product_id, COUNT(*) FROM inventory_reservations
            WHERE status = 'RESERVED' AND expires_at < :now
            GROUP BY product_id
        """,
        nativeQuery = true
    )
    fun countExpiredGroupByProduct(@Param("now") now: LocalDateTime): List<Array<Any>>

    @Query(
        value = """
            SELECT id, quantity FROM inventory_reservations
            WHERE product_id = :productId AND status = 'RESERVED' AND expires_at < :now
            ORDER BY expires_at, id LIMIT :limit
        """,
        nativeQuery = true
    )
    fun findExpiredChunk(
        @Param("productId") productId: Long,
        @Param("now") now: LocalDateTime,
        @Param("limit") limit: Int
    ): List<Array<Any>>

    @Modifying(clearAutomatically = true)
    @Query(
        value = """
            UPDATE inventory_reservations SET status = 'EXPIRED', updated_at = :updatedAt
            WHERE id IN (:ids) AND status = 'RESERVED'
        """,
        nativeQuery = true
    )
    fun expireByIds(
        @Param("ids") ids: List<Long>,
        @Param("updatedAt") updatedAt: LocalDateTime
    ): Int
}
