package com.hoppingmall.user.domain.repository

import com.hoppingmall.user.domain.Seller
import jakarta.persistence.LockModeType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Lock
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional

interface SellerRepository : JpaRepository<Seller, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    override fun findById(id: Long): Optional<Seller>

    fun findNullableByUserId(userId: Long): Seller?

    fun existsByBusinessNumber(businessNumber: String): Boolean

    fun findAllByApprovalStatus(approvalStatus: Seller.ApprovalStatus, pageable: Pageable): Page<Seller>

    @Query(
        value = "select s from Seller s join User u on u.id = s.userId where $APPLICANT_MATCHES",
        countQuery = "select count(s) from Seller s join User u on u.id = s.userId where $APPLICANT_MATCHES"
    )
    fun searchByApplicant(
        @Param("status") status: Seller.ApprovalStatus?,
        @Param("pattern") pattern: String,
        pageable: Pageable
    ): Page<Seller>

    companion object {
        const val LIKE_ESCAPE = "!"
        private const val APPLICANT_MATCHES = "(:status is null or s.approvalStatus = :status) " +
            "and (lower(u.name) like :pattern escape '$LIKE_ESCAPE' or lower(u.email.value) like :pattern escape '$LIKE_ESCAPE')"
    }
}
