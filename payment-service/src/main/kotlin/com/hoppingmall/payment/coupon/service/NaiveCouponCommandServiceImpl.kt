package com.hoppingmall.payment.coupon.service

import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.dto.request.CouponCreateRequest
import com.hoppingmall.payment.coupon.dto.response.CouponResponse
import com.hoppingmall.payment.coupon.dto.response.UserCouponResponse
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.exception.CouponExhaustedException
import com.hoppingmall.payment.coupon.exception.CouponNotFoundException
import com.hoppingmall.payment.coupon.exception.CouponNotAvailableException
import com.hoppingmall.payment.coupon.exception.CouponExpiredException
import com.hoppingmall.payment.coupon.exception.CouponMinAmountNotMetException
import com.hoppingmall.payment.coupon.enum.UserCouponStatus
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.context.annotation.Profile
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal

@Service
@Profile("coupon-naive")
@Transactional
class NaiveCouponCommandServiceImpl(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository
) : CouponCommandService {

    @PersistenceContext
    private lateinit var entityManager: EntityManager

    override fun createCoupon(request: CouponCreateRequest): CouponResponse {
        val coupon = com.hoppingmall.payment.coupon.domain.Coupon.create(
            name = request.name,
            code = request.code,
            discountType = request.discountType,
            discountValue = request.discountValue,
            minOrderAmount = request.minOrderAmount,
            maxDiscountAmount = request.maxDiscountAmount,
            totalQuantity = request.totalQuantity,
            validFrom = request.validFrom,
            validTo = request.validTo
        )
        return CouponResponse.from(couponRepository.save(coupon))
    }

    override fun changeCouponStatus(couponId: Long, status: CouponStatus): CouponResponse {
        val coupon = couponRepository.findByIdOrNull(couponId) ?: throw CouponNotFoundException()
        coupon.changeStatus(status)
        return CouponResponse.from(couponRepository.save(coupon))
    }

    override fun issueCoupon(userId: Long, couponId: Long): UserCouponResponse {
        val stale = entityManager.createNativeQuery(
            "SELECT issued_quantity, total_quantity FROM coupons WHERE id = :id"
        ).setParameter("id", couponId).singleResult as Array<*>
        val staleIssued = (stale[0] as Number).toInt()
        val staleTotal = (stale[1] as Number).toInt()

        if (staleIssued >= staleTotal) throw CouponExhaustedException()

        @Suppress("MagicNumber")
        Thread.sleep((5..20).random().toLong())

        entityManager.createNativeQuery(
            "UPDATE coupons SET issued_quantity = :newValue WHERE id = :id"
        ).setParameter("newValue", staleIssued + 1)
         .setParameter("id", couponId)
         .executeUpdate()

        val userCoupon = UserCoupon.create(userId = userId, couponId = couponId)
        val savedUserCoupon = userCouponRepository.save(userCoupon)

        val coupon = couponRepository.findByIdOrNull(couponId) ?: throw CouponNotFoundException()
        return UserCouponResponse.from(savedUserCoupon, coupon)
    }

    override fun useCoupon(userId: Long, couponId: Long, orderAmount: BigDecimal, orderId: Long): BigDecimal {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId)
            ?: throw CouponNotFoundException()

        if (userCoupon.status != UserCouponStatus.ISSUED) throw CouponNotAvailableException()

        val coupon = couponRepository.findByIdOrNull(couponId) ?: throw CouponNotFoundException()

        if (!coupon.isValid()) throw CouponExpiredException()

        if (orderAmount < coupon.minOrderAmount) throw CouponMinAmountNotMetException()

        val discountAmount = coupon.calculateDiscount(orderAmount)
        userCoupon.use(orderId)
        userCouponRepository.save(userCoupon)

        return discountAmount
    }

    override fun restoreCouponByPayment(couponId: Long, userId: Long) {
        val userCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId) ?: return
        if (userCoupon.status == UserCouponStatus.USED) {
            userCoupon.restore()
            userCouponRepository.save(userCoupon)
        }
    }

    override fun restoreCouponByOrder(orderId: Long) {
        val userCoupon = userCouponRepository.findByOrderId(orderId) ?: return
        if (userCoupon.status == UserCouponStatus.USED) {
            userCoupon.restore()
            userCouponRepository.save(userCoupon)
        }
    }
}
