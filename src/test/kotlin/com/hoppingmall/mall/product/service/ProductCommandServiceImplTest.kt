package com.hoppingmall.mall.product.service

import com.hoppingmall.mall.global.enums.ProductStatus
import com.hoppingmall.mall.product.domain.Product
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
    private val productCommandService = ProductCommandServiceImpl(productRepository, productImageRepository)

    @Nested
    @DisplayName("createProduct")
    inner class CreateProduct {
        @Test
        fun 상품_생성_성공() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                name = "테스트 상품",
                description = "테스트 상품 설명",
                price = 10000L,
                imageUrl = "https://example.com/image.jpg",
                status = ProductStatus.AVAILABLE
            )
            
            val productCaptor = argumentCaptor<Product>()
            val imageCaptor = argumentCaptor<ProductImage>()

            // Context
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(1L)
            }
            whenever(productImageRepository.save(imageCaptor.capture())).thenAnswer {
                imageCaptor.firstValue.withId(1L)
            }

            // Interaction
            val result = productCommandService.createProduct(request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.description).isEqualTo(request.description)
            assertThat(result.price).isEqualTo(request.price)
            assertThat(result.status).isEqualTo(request.status)
            assertThat(result.imageUrl).isEqualTo(request.imageUrl)
            
            // 저장된 객체 검증
            val savedProduct = productCaptor.firstValue
            assertThat(savedProduct.name).isEqualTo(request.name)
            assertThat(savedProduct.sellerId).isEqualTo(request.sellerId)
            
            val savedImage = imageCaptor.firstValue
            assertThat(savedImage.imageUrl).isEqualTo(request.imageUrl)
            
            verify(productRepository).save(any())
            verify(productImageRepository).save(any())
        }

        @Test
        fun 이미지_URL이_없는_상품_생성_성공() {
            // Data
            val request = ProductCreateRequest(
                sellerId = 1L,
                name = "테스트 상품",
                description = "테스트 상품 설명",
                price = 10000L,
                imageUrl = null,
                status = ProductStatus.AVAILABLE
            )
            
            val productCaptor = argumentCaptor<Product>()
            val imageCaptor = argumentCaptor<ProductImage>()

            // Context
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(1L)
            }
            whenever(productImageRepository.save(imageCaptor.capture())).thenAnswer {
                imageCaptor.firstValue.withId(1L)
            }

            // Interaction
            val result = productCommandService.createProduct(request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(1L)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrl).isEqualTo("D:/hoppingmall/product/images/default-product.jpg")
            
            // 저장된 객체 검증
            val savedProduct = productCaptor.firstValue
            assertThat(savedProduct.name).isEqualTo(request.name)
            assertThat(savedProduct.sellerId).isEqualTo(request.sellerId)
            
            val savedImage = imageCaptor.firstValue
            assertThat(savedImage.imageUrl).isEqualTo("D:/hoppingmall/product/images/default-product.jpg")
            
            verify(productRepository).save(any())
            verify(productImageRepository).save(any())
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
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = 20000L,
                imageUrl = "https://example.com/updated-image.jpg",
                status = ProductStatus.SOLD_OUT
            )
            
            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imageCaptor = argumentCaptor<ProductImage>()
            
            val existingImage = ProductImage.fixture(productId = productId)
            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductId(productId)).thenReturn(existingImage)
            whenever(productImageRepository.save(imageCaptor.capture())).thenAnswer {
                imageCaptor.firstValue.withId(1L)
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.description).isEqualTo(request.description)
            assertThat(result.price).isEqualTo(request.price)
            assertThat(result.status).isEqualTo(request.status)
            assertThat(result.imageUrl).isEqualTo(request.imageUrl)
            
            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductId(productId)
            verify(productImageRepository).save(any())
        }

        @Test
        fun 존재하지_않는_상품_수정_시_예외_발생() {
            // Data
            val productId = 999L
            val request = ProductUpdateRequest(
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = 20000L,
                imageUrl = "https://example.com/updated-image.jpg",
                status = ProductStatus.AVAILABLE
            )

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.empty())

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.updateProduct(productId, request) }
                .isInstanceOf(ProductNotFoundException::class.java)
            
            verify(productRepository).findById(productId)
            verify(productImageRepository, never()).findByProductId(any())
            verify(productImageRepository, never()).save(any())
        }

        @Test
        fun 이미지가_없는_상품_수정_성공() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = 20000L,
                imageUrl = "https://example.com/new-image.jpg",
                status = ProductStatus.AVAILABLE
            )
            
            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imageCaptor = argumentCaptor<ProductImage>()
            

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductId(productId)).thenReturn(null)
            whenever(productImageRepository.save(imageCaptor.capture())).thenAnswer {
                imageCaptor.firstValue.withId(1L)
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrl).isEqualTo(request.imageUrl)
            
            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductId(productId)
            verify(productImageRepository).save(any())
        }

        @Test
        fun 이미지_URL이_없는_상품_수정_성공() {
            // Data
            val productId = 1L
            val request = ProductUpdateRequest(
                name = "수정된 상품명",
                description = "수정된 상품 설명",
                price = 20000L,
                imageUrl = null,
                status = ProductStatus.AVAILABLE
            )
            
            val existingProduct = Product.fixture().withId(productId)
            val productCaptor = argumentCaptor<Product>()
            val imageCaptor = argumentCaptor<ProductImage>()
            

            // Context
            whenever(productRepository.findById(productId)).thenReturn(java.util.Optional.of(existingProduct))
            whenever(productRepository.save(productCaptor.capture())).thenAnswer {
                productCaptor.firstValue.withId(productId)
            }
            whenever(productImageRepository.findByProductId(productId)).thenReturn(null)
            whenever(productImageRepository.save(imageCaptor.capture())).thenAnswer {
                imageCaptor.firstValue.withId(1L)
            }

            // Interaction
            val result = productCommandService.updateProduct(productId, request)

            // Assertions
            assertThat(result).isNotNull()
            assertThat(result.id).isEqualTo(productId)
            assertThat(result.name).isEqualTo(request.name)
            assertThat(result.imageUrl).isEqualTo("D:/hoppingmall/product/images/default-product.jpg")
            
            verify(productRepository).findById(productId)
            verify(productRepository).save(any())
            verify(productImageRepository).findByProductId(productId)
            verify(productImageRepository).save(any())
        }
    }

    @Nested
    @DisplayName("deleteProduct")
    inner class DeleteProduct {
        @Test
        fun 상품_삭제_성공() {
            // Data
            val productId = 1L
            val productImage = ProductImage.fixture(productId = productId)

            // Context
            whenever(productRepository.existsById(productId)).thenReturn(true)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(productImage)

            // Interaction
            productCommandService.deleteProduct(productId)

            // Assertions
            verify(productRepository).existsById(productId)
            verify(productImageRepository).findByProductId(productId)
            verify(productImageRepository).delete(productImage)
            verify(productRepository).deleteById(productId)
        }

        @Test
        fun 존재하지_않는_상품_삭제_시_예외_발생() {
            // Data
            val productId = 999L

            // Context
            whenever(productRepository.existsById(productId)).thenReturn(false)

            // Interaction & Assertions
            assertThatThrownBy { productCommandService.deleteProduct(productId) }
                .isInstanceOf(ProductNotFoundException::class.java)
            
            verify(productRepository).existsById(productId)
            verify(productImageRepository, never()).findByProductId(any())
            verify(productImageRepository, never()).delete(any())
            verify(productRepository, never()).deleteById(any())
        }

        @Test
        fun 이미지가_없는_상품_삭제_성공() {
            // Data
            val productId = 1L

            // Context
            whenever(productRepository.existsById(productId)).thenReturn(true)
            whenever(productImageRepository.findByProductId(productId)).thenReturn(null)

            // Interaction
            productCommandService.deleteProduct(productId)

            // Assertions
            verify(productRepository).existsById(productId)
            verify(productImageRepository).findByProductId(productId)
            verify(productImageRepository, never()).delete(any())
            verify(productRepository).deleteById(productId)
        }
    }
}
