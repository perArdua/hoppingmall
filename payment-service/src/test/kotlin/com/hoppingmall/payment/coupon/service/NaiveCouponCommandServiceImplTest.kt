package com.hoppingmall.payment.coupon.service

import com.hoppingmall.common.BaseEntity
import com.hoppingmall.payment.coupon.domain.Coupon
import com.hoppingmall.payment.coupon.domain.UserCoupon
import com.hoppingmall.payment.coupon.domain.repository.CouponRepository
import com.hoppingmall.payment.coupon.domain.repository.UserCouponRepository
import com.hoppingmall.payment.coupon.enum.CouponStatus
import com.hoppingmall.payment.coupon.enum.DiscountType
import com.hoppingmall.payment.coupon.exception.CouponExhaustedException
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDateTime

@DisplayName("NaiveCouponCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class NaiveCouponCommandServiceImplTest {

    @Mock
    private lateinit var couponRepository: CouponRepository

    @Mock
    private lateinit var userCouponRepository: UserCouponRepository

    @Mock
    private lateinit var entityManager: EntityManager

    @InjectMocks
    private lateinit var service: NaiveCouponCommandServiceImpl

    @BeforeEach
    fun inject() {
        val emField = NaiveCouponCommandServiceImpl::class.java.getDeclaredField("entityManager")
        emField.isAccessible = true
        emField.set(service, entityManager)
    }

    private fun createCoupon(id: Long = 1L, total: Int = 100, issued: Int = 0): Coupon {
        val coupon = Coupon.create(
            name = "테스트 쿠폰",
            code = "TEST-$id",
            discountType = DiscountType.FIXED_AMOUNT,
            discountValue = BigDecimal("1000"),
            minOrderAmount = BigDecimal("10000"),
            maxDiscountAmount = null,
            totalQuantity = total,
            validFrom = LocalDateTime.now().minusDays(1),
            validTo = LocalDateTime.now().plusDays(30)
        )
        val idField = BaseEntity::class.java.getDeclaredField("id")
        idField.isAccessible = true
        idField.set(coupon, id)
        repeat(issued) { coupon.issue() }
        return coupon
    }

    private fun mockStaleSelect(issued: Int, total: Int): Query {
        val query: Query = mock()
        whenever(entityManager.createNativeQuery(eq("SELECT issued_quantity, total_quantity FROM coupons WHERE id = :id")))
            .thenReturn(query)
        whenever(query.setParameter(eq("id"), any())).thenReturn(query)
        whenever(query.singleResult).thenReturn(arrayOf<Any>(issued, total))
        return query
    }

    private fun mockUpdate(): Query {
        val query: Query = mock()
        whenever(entityManager.createNativeQuery(eq("UPDATE coupons SET issued_quantity = :newValue WHERE id = :id")))
            .thenReturn(query)
        whenever(query.setParameter(anyOrNull<String>(), any())).thenReturn(query)
        whenever(query.executeUpdate()).thenReturn(1)
        return query
    }

    @Test
    fun stale_read_기반으로_발급_가능하면_UPDATE를_실행한다() {
        mockStaleSelect(issued = 10, total = 100)
        val updateQuery = mockUpdate()
        val savedUserCoupon = UserCoupon.create(userId = 5L, couponId = 1L).also {
            val idField = BaseEntity::class.java.getDeclaredField("id")
            idField.isAccessible = true
            idField.set(it, 100L)
        }
        whenever(userCouponRepository.save(any<UserCoupon>())).thenReturn(savedUserCoupon)
        whenever(couponRepository.findById(1L)).thenReturn(java.util.Optional.of(createCoupon(issued = 11)))

        val response = service.issueCoupon(userId = 5L, couponId = 1L)

        assertThat(response.couponId).isEqualTo(1L)
        org.mockito.kotlin.verify(updateQuery).executeUpdate()
    }

    @Test
    fun 이미_소진된_쿠폰은_발급되지_않는다() {
        mockStaleSelect(issued = 100, total = 100)

        assertThatThrownBy { service.issueCoupon(userId = 5L, couponId = 1L) }
            .isInstanceOf(CouponExhaustedException::class.java)
    }
}
