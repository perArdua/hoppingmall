package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.category.domain.repository.CategoryRepository
import com.hoppingmall.mall.category.exception.CategoryNotFoundException
import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
import java.math.BigDecimal
import com.hoppingmall.mall.product.domain.ProductImage
import com.hoppingmall.mall.product.domain.repository.ProductImageRepository
import com.hoppingmall.mall.product.domain.repository.ProductRepository
import com.hoppingmall.mall.product.dto.request.ProductCreateRequest
import com.hoppingmall.mall.product.dto.request.ProductUpdateRequest
import com.hoppingmall.mall.product.exception.ProductNotFoundException
import com.hoppingmall.mall.support.fixture.fixture
import com.hoppingmall.mall.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.*

@DisplayName("ProductCommandServiceImpl")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ProductCommandServiceImplTest {

    private val productRepository: ProductRepository = mock()
    private val productImageRepository: ProductImageRepository = mock()
    private val categoryRepository: CategoryRepository = mock()
    private val productCommandService = ProductCommandServiceImpl(productRepository, productImageRepository, categoryRepository)

    @Nested
    @DisplayName("createProduct")
    inner class CreateProduct {
        @Test
        fun 상품_생성_성공() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                categoryId = 1L,
                name = "테스트 상품",
                description = "테스트 상품 설명",
                price = BigDecimal("10000"),
                imageUrls = listOf("https://example.com/image.jpg"),
                status = ProductStatus.AVAILABLE
            )

            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(1L)
            }
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.createProduct(request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.categoryId).isEqualTo(request.categoryId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.description).isEqualTo(request.description)
            assertThat(result.price).isEqualTo(request.price)
            assertThat(result.status).isEqualTo(request.status)
            assertThat(result.imageUrls).containsExactly("https://example.com/image.jpg")

            val savedProduct = productCaptor.firstValue
            assertThat(savedProduct.name).isEqualTo(request.name)
            assertThat(savedProduct.sellerId).isEqualTo(request.sellerId)
            assertThat(savedProduct.categoryId).isEqualTo(request.categoryId)

            val savedImages = imagesCaptor.firstValue
            assertThat(savedImages[0].imageUrl).isEqualTo("https://example.com/image.jpg")

            verify(categoryRepository).existsById(request.categoryId)
            verify(productRepository).save(any())
            verify(productImageRepository).saveAll(any<List<ProductImage>>())
        }

        @Test
        fun 다중_이미지_상품_생성_성공() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                categoryId = 1L,
                name = "다중 이미지 상품",
                description = "이미지 3장",
                price = BigDecimal("30000"),
                imageUrls = listOf("https://example.com/1.jpg", "https://example.com/2.jpg", "https://example.com/3.jpg"),
                status = ProductStatus.AVAILABLE
            )

            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(1L)
            }
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.createProduct(request)

            // Assertions
            assertThat(result.imageUrls).containsExactly(
                "https://example.com/1.jpg",
                "https://example.com/2.jpg",
                "https://example.com/3.jpg"
            )
            val savedImages = imagesCaptor.firstValue
            assertThat(savedImages).hasSize(3)
            assertThat(savedImages[0].sortOrder).isEqualTo(0)
            assertThat(savedImages[1].sortOrder).isEqualTo(1)
            assertThat(savedImages[2].sortOrder).isEqualTo(2)
        }

        @Test
        fun 이미지_URL이_없는_상품_생성_성공() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                categoryId = 1L,
                name = "테스트 상품",
                description = "테스트 상품 설명",
                price = BigDecimal("10000"),
                imageUrls = null,
                status = ProductStatus.AVAILABLE
            )

            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(1L)
            }
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.createProduct(request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrls).containsExactly("D:/hoppingmall/product/images/default-product.jpg")

            val savedProduct = productCaptor.firstValue
            assertThat(savedProduct.name).isEqualTo(request.name)
            assertThat(savedProduct.sellerId).isEqualTo(request.sellerId)

            val savedImages = imagesCaptor.firstValue
            assertThat(savedImages[0].imageUrl).isEqualTo("D:/hoppingmall/product/images/default-product.jpg")

            verify(categoryRepository).existsById(request.categoryId)
            verify(productRepository).save(any())
            verify(productImageRepository).saveAll(any<List<ProductImage>>())
        }

        @Test
        fun 존재하지_않는_카테고리로_상품_생성_시_예외_발생() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                categoryId = 999L,
                name = "테스트 상품",
                description = "테스트 상품 설명",
                price = BigDecimal("10000"),
                status = ProductStatus.AVAILABLE
            )

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(false)

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.createProduct(request) }
                .isInstanceOf(CategoryNotFoundException::class.java)

            verify(categoryRepository).existsById(request.categoryId)
            verify(productRepository, never()).save(any())
        }
    }

    @Nested
    @DisplayName("updateProduct")
    inner class UpdateProduct {
        @Test
        fun 상품_수정_성공() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                categoryId = 2L,
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = BigDecimal("20000"),
                imageUrls = listOf("https://example.com/updated-image.jpg"),
                status = ProductStatus.SOLD_OUT
            )

            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            val existingImages = listOf(ProductImage.fixture(productId = productId))
            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductIdOrderBySortOrder(productId)).thenReturn(existingImages)
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.categoryId).isEqualTo(request.categoryId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.description).isEqualTo(request.description)
            assertThat(result.price).isEqualTo(request.price)
            assertThat(result.status).isEqualTo(request.status)
            assertThat(result.imageUrls).containsExactly("https://example.com/updated-image.jpg")

            verify(categoryRepository).existsById(request.categoryId)
            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductIdOrderBySortOrder(productId)
            verify(productImageRepository).saveAll(any<List<ProductImage>>())
        }

        @Test
        fun 존재하지_않는_상품_수정_시_예외_발생() {
            // Data
            val productId = 999L
            val request = ProductUpdateRequest(
                categoryId = 1L,
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = BigDecimal("20000"),
                imageUrls = listOf("https://example.com/updated-image.jpg"),
                status = ProductStatus.AVAILABLE
            )

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.updateProduct(productId, request) }
                .isInstanceOf(ProductNotFoundException::class.java)

            verify(productRepository).findById(productId)
            verify(productImageRepository, never()).findByProductIdOrderBySortOrder(any())
            verify(productImageRepository, never()).saveAll(any<List<ProductImage>>())
        }

        @Test
        fun 존재하지_않는_카테고리로_상품_수정_시_예외_발생() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                categoryId = 999L,
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = BigDecimal("20000"),
                status = ProductStatus.AVAILABLE
            )

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(false)

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.updateProduct(productId, request) }
                .isInstanceOf(CategoryNotFoundException::class.java)

            verify(categoryRepository).existsById(request.categoryId)
            verify(productRepository, never()).findById(any())
        }

        @Test
        fun 이미지가_없는_상품_수정_성공() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                categoryId = 1L,
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = BigDecimal("20000"),
                imageUrls = listOf("https://example.com/new-image.jpg"),
                status = ProductStatus.AVAILABLE
            )

            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductIdOrderBySortOrder(productId)).thenReturn(emptyList())
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrls).containsExactly("https://example.com/new-image.jpg")

            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductIdOrderBySortOrder(productId)
            verify(productImageRepository).saveAll(any<List<ProductImage>>())
        }

        @Test
        fun 이미지_URL이_없는_상품_수정_성공() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                categoryId = 1L,
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = BigDecimal("20000"),
                imageUrls = null,
                status = ProductStatus.AVAILABLE
            )

            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imagesCaptor = argumentCaptor<List<ProductImage>>()

            // Context
            whenever(categoryRepository.existsById(request.categoryId)).thenReturn(true)
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductIdOrderBySortOrder(productId)).thenReturn(emptyList())
            whenever(productImageRepository.saveAll(imagesCaptor.capture())).thenAnswer {
                imagesCaptor.firstValue.mapIndexed { index, image -> image.withId(index.toLong() + 1) }
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrls).containsExactly("D:/hoppingmall/product/images/default-product.jpg")

            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductIdOrderBySortOrder(productId)
            verify(productImageRepository).saveAll(any<List<ProductImage>>())
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    inner class DeleteProduct {
        @Test
        fun 상품_삭제_성공() {
            // Data
            val productId = 1L
            val product = Product.fixture().withId(productId)
            val productImage = ProductImage.fixture(productId = productId).withId(1L)

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(product))
            whenever(productImageRepository.findByProductIdOrderBySortOrder(productId)).thenReturn(listOf(productImage))

            // Interaction
            productCommandService.deleteProduct(productId)

            // Assertions
            assertThat(product.deletedAt).isNotNull()
            assertThat(productImage.deletedAt).isNotNull()
        }

        @Test
        fun 존재하지_않는_상품_삭제_시_예외_발생() {
            // Data
            val productId = 999L

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.deleteProduct(productId) }
                .isInstanceOf(ProductNotFoundException::class.java)

            verify(productImageRepository, never()).findByProductIdOrderBySortOrder(any())
        }

        @Test
        fun 이미지가_없는_상품_삭제_성공() {
            // Data
            val productId = 1L
            val product = Product.fixture().withId(productId)

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(product))
            whenever(productImageRepository.findByProductIdOrderBySortOrder(productId)).thenReturn(emptyList())

            // Interaction
            productCommandService.deleteProduct(productId)

            // Assertions
            assertThat(product.deletedAt).isNotNull()
        }
    }
}
