package com.hoppingmall.product.product.domain

import com.hoppingmall.product.common.enums.ProductStatus
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import java.math.BigDecimal

@DisplayName("Product 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ProductTest {

    @Test
    fun 상품을_생성한다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        )

        assertThat(product.sellerId).isEqualTo(1L)
        assertThat(product.categoryId).isEqualTo(1L)
        assertThat(product.name).isEqualTo("테스트상품")
        assertThat(product.description).isEqualTo("설명")
        assertThat(product.price).isEqualByComparingTo(BigDecimal("10000"))
        assertThat(product.status).isEqualTo(ProductStatus.AVAILABLE)
    }

    @Test
    fun 상품을_수정한다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        )

        product.update(
            name = "수정상품", description = "수정설명",
            price = BigDecimal("20000"), categoryId = 2L, status = ProductStatus.SOLD_OUT
        )

        assertThat(product.name).isEqualTo("수정상품")
        assertThat(product.description).isEqualTo("수정설명")
        assertThat(product.price).isEqualByComparingTo(BigDecimal("20000"))
        assertThat(product.categoryId).isEqualTo(2L)
        assertThat(product.status).isEqualTo(ProductStatus.SOLD_OUT)
    }

    @Test
    fun 가격이_0_이하이면_예외를_발생시킨다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        )

        assertThatThrownBy {
            product.update(
                name = "수정상품", description = "수정설명",
                price = BigDecimal.ZERO, categoryId = 1L, status = ProductStatus.AVAILABLE
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }
}
