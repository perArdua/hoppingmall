package com.hoppingmall.product.product.service

import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.exception.ProductException
import com.hoppingmall.product.product.exception.code.ProductErrorCode
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.verifyNoInteractions
import org.mockito.kotlin.whenever
import java.math.BigDecimal

@DisplayName("ProductOwnershipPolicy")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductOwnershipPolicyTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @InjectMocks
    private lateinit var policy: ProductOwnershipPolicy

    private val seller = UserPrincipal.of(10L, "SELLER")
    private val admin = UserPrincipal.of(99L, "ADMIN")

    private fun product(sellerId: Long) = Product.create(
        sellerId = sellerId,
        categoryId = 1L,
        name = "상품",
        description = "설명",
        price = BigDecimal("1000"),
        status = ProductStatus.AVAILABLE
    )

    @Test
    fun 판매자가_만드는_상품의_판매자는_본문과_상관없이_로그인_사용자다() {
        assertThat(policy.sellerIdForCreate(seller, 20L)).isEqualTo(10L)
        assertThat(policy.sellerIdForCreate(seller, null)).isEqualTo(10L)
    }

    @Test
    fun 관리자가_만드는_상품의_판매자는_본문_값이고_없으면_관리자_자신이다() {
        assertThat(policy.sellerIdForCreate(admin, 20L)).isEqualTo(20L)
        assertThat(policy.sellerIdForCreate(admin, null)).isEqualTo(99L)
    }

    @Test
    fun 자기_상품이면_통과한다() {
        whenever(productRepository.findNullableById(1L)).thenReturn(product(10L))

        assertThatCode { policy.checkOwner(1L, seller) }.doesNotThrowAnyException()
    }

    @Test
    fun 다른_판매자의_상품이면_접근_거부_예외를_던진다() {
        whenever(productRepository.findNullableById(1L)).thenReturn(product(20L))

        assertThatThrownBy { policy.checkOwner(1L, seller) }
            .isInstanceOf(ProductException::class.java)
            .extracting("errorCode")
            .isEqualTo(ProductErrorCode.PRODUCT_ACCESS_DENIED)
    }

    @Test
    fun 상품이_없으면_판단을_뒤의_처리에_맡기고_통과한다() {
        whenever(productRepository.findNullableById(1L)).thenReturn(null)

        assertThatCode { policy.checkOwner(1L, seller) }.doesNotThrowAnyException()
    }

    @Test
    fun 관리자는_상품을_조회하지_않고_통과한다() {
        policy.checkOwner(1L, admin)

        verifyNoInteractions(productRepository)
    }
}
