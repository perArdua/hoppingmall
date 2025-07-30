package com.hoppingmall.mall.product.domain

import com.hoppingmall.mall.global.enums.ProductStatus
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Assertions.*

@DisplayName("Product")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 상품_생성_성공() {
            val sellerId = 1L
            val name = "테스트 상품"
            val description = "테스트 상품 설명"
            val price = 10000L
            val status = ProductStatus.AVAILABLE

            val product = Product.create(sellerId, name, description, price, status)

            assertEquals(sellerId, product.sellerId)
            assertEquals(name, product.name)
            assertEquals(description, product.description)
            assertEquals(price, product.price)
            assertEquals(status, product.status)
        }

        @Test
        fun 기본_상태로_상품_생성_성공() {
            val sellerId = 1L
            val name = "테스트 상품"
            val description = "테스트 상품 설명"
            val price = 10000L

            val product = Product.create(sellerId, name, description, price, ProductStatus.AVAILABLE)

            assertEquals(ProductStatus.AVAILABLE, product.status)
        }

        @Test
        fun 품절_상태로_상품_생성_성공() {
            val sellerId = 1L
            val name = "테스트 상품"
            val description = "테스트 상품 설명"
            val price = 10000L

            val product = Product.create(sellerId, name, description, price, ProductStatus.SOLD_OUT)

            assertEquals(ProductStatus.SOLD_OUT, product.status)
        }
    }
} 