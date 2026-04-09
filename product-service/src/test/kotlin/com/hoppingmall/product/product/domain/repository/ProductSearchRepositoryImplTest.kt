package com.hoppingmall.product.product.domain.repository

import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.product.domain.Product
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import jakarta.persistence.TypedQuery
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever
import org.springframework.data.domain.PageRequest
import java.math.BigDecimal

@DisplayName("ProductSearchRepositoryImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductSearchRepositoryImplTest {

    @Mock
    private lateinit var entityManager: EntityManager

    @Mock
    private lateinit var typedQuery: TypedQuery<Product>

    @Mock
    private lateinit var nativeQuery: Query

    @Nested
    @DisplayName("LIKE 검색")
    inner class LikeSearch {

        @Test
        fun 키워드로_LIKE_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:h2:mem:test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = "테스트", categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
            assertThat(result.hasNext()).isFalse()
        }

        @Test
        fun 키워드_없이_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:h2:mem:test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = null, categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun 빈_키워드로_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:h2:mem:test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = "", categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun 모든_조건으로_LIKE_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:h2:mem:test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = "테스트", categoryId = 1L, status = ProductStatus.AVAILABLE,
                minPrice = BigDecimal("1000"), maxPrice = BigDecimal("50000"), pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun 결과가_pageSize보다_많으면_hasNext가_true이다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:h2:mem:test")
            val pageable = PageRequest.of(0, 2)
            val products = listOf(
                Product.create(1L, 1L, "A", "d", BigDecimal("1000"), ProductStatus.AVAILABLE),
                Product.create(1L, 1L, "B", "d", BigDecimal("2000"), ProductStatus.AVAILABLE),
                Product.create(1L, 1L, "C", "d", BigDecimal("3000"), ProductStatus.AVAILABLE)
            )

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(products)

            val result = repo.searchProducts(
                keyword = null, categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.hasNext()).isTrue()
            assertThat(result.content).hasSize(2)
        }
    }

    @Nested
    @DisplayName("FullText 검색")
    inner class FullTextSearch {

        @Test
        fun MySQL에서_키워드로_FullText_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:mysql://localhost:3306/test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createNativeQuery(any<String>(), eq(Product::class.java))).thenReturn(nativeQuery)
            whenever(nativeQuery.resultList).thenReturn(emptyList<Product>())

            val result = repo.searchProducts(
                keyword = "테스트", categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun MySQL에서_모든_조건으로_FullText_검색한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:mysql://localhost:3306/test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createNativeQuery(any<String>(), eq(Product::class.java))).thenReturn(nativeQuery)
            whenever(nativeQuery.resultList).thenReturn(emptyList<Product>())

            val result = repo.searchProducts(
                keyword = "테스트", categoryId = 1L, status = ProductStatus.AVAILABLE,
                minPrice = BigDecimal("1000"), maxPrice = BigDecimal("50000"), pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun MySQL에서_키워드_없으면_LIKE_검색으로_폴백한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:mysql://localhost:3306/test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = null, categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }

        @Test
        fun MySQL에서_빈_키워드면_LIKE_검색으로_폴백한다() {
            val repo = ProductSearchRepositoryImpl(entityManager, "jdbc:mysql://localhost:3306/test")
            val pageable = PageRequest.of(0, 10)

            whenever(entityManager.createQuery(any<String>(), eq(Product::class.java))).thenReturn(typedQuery)
            whenever(typedQuery.resultList).thenReturn(emptyList())

            val result = repo.searchProducts(
                keyword = "  ", categoryId = null, status = null,
                minPrice = null, maxPrice = null, pageable = pageable
            )

            assertThat(result.content).isEmpty()
        }
    }
}
