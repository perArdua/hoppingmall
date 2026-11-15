package com.hoppingmall.product.product.service

import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.category.exception.CategoryNotFoundException
import com.hoppingmall.product.common.file.exception.UnregisteredImageException
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.inventory.domain.Inventory
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.product.exception.ProductNotFoundException
import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.util.Optional

@DisplayName("ProductCommandServiceImpl")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductCommandServiceImplTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var productImageLinker: ProductImageLinker

    @Mock
    private lateinit var inventoryCommandService: InventoryCommandService

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    @InjectMocks
    private lateinit var service: ProductCommandServiceImpl

    @Test
    fun 상품을_생성한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val image = ProductImage.create(productId = 1L, imageUrl = "/default.jpg", sortOrder = 0).withId(1L)
        val request = ProductCreateRequest(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(productImageLinker.link(1L, null)).thenReturn(listOf("/default.jpg"))
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenReturn(listOf(image))

        val result = service.createProduct(request)

        assertThat(result.name).isEqualTo("테스트상품")
    }

    @Test
    fun 이미지_URL과_함께_상품을_생성한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val image = ProductImage.create(productId = 1L, imageUrl = "http://img.jpg", sortOrder = 0).withId(1L)
        val request = ProductCreateRequest(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), imageUrls = listOf("http://img.jpg")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productImageLinker.link(1L, listOf("http://img.jpg"))).thenReturn(listOf("http://img.jpg"))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenReturn(listOf(image))

        val result = service.createProduct(request)

        assertThat(result.imageUrls).containsExactly("http://img.jpg")
    }

    @Test
    fun 존재하지_않는_카테고리로_상품_생성_시_예외를_발생시킨다() {
        val request = ProductCreateRequest(
            sellerId = 1L, categoryId = 999L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000")
        )

        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.createProduct(request) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 상품을_수정한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val image = ProductImage.create(productId = 1L, imageUrl = "/default.jpg", sortOrder = 0).withId(1L)
        val request = ProductUpdateRequest(
            categoryId = 1L, name = "수정상품",
            description = "수정설명", price = BigDecimal("20000")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productRepository.findById(1L)).thenReturn(Optional.of(product))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(image))

        val result = service.updateProduct(1L, request)

        assertThat(result.name).isEqualTo("수정상품")
        assertThat(result.imageUrls).containsExactly("/default.jpg")
        assertThat(image.deletedAt).isNull()
    }

    @Test
    fun 업로드하지_않은_이미지_주소로는_상품을_만들지_않는다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val request = ProductCreateRequest(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), imageUrls = listOf("https://tracker.example.com/a.gif")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productImageLinker.link(1L, request.imageUrls)).thenThrow(UnregisteredImageException())

        assertThatThrownBy { service.createProduct(request) }
            .isInstanceOf(UnregisteredImageException::class.java)
        verify(productRepository, never()).save(any<Product>())
    }

    @Test
    fun 이미지를_바꾸면_지금_이미지를_넘겨_새_주소만_확인하고_옛_이미지를_지운다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val product = Product.create(
            sellerId = 7L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val oldImage = ProductImage.create(productId = 1L, imageUrl = "http://cdn/old.jpg", sortOrder = 0).withId(1L)
        val newImage = ProductImage.create(productId = 1L, imageUrl = "http://cdn/new.jpg", sortOrder = 0).withId(2L)
        val request = ProductUpdateRequest(
            categoryId = 1L, name = "수정상품",
            description = "수정설명", price = BigDecimal("20000"), imageUrls = listOf("http://cdn/new.jpg")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productRepository.findById(1L)).thenReturn(Optional.of(product))
        whenever(productRepository.save(any<Product>())).thenReturn(product)
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(oldImage))
        whenever(productImageLinker.link(7L, listOf("http://cdn/new.jpg"), listOf("http://cdn/old.jpg")))
            .thenReturn(listOf("http://cdn/new.jpg"))
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenReturn(listOf(newImage))

        val result = service.updateProduct(1L, request)

        assertThat(result.imageUrls).containsExactly("http://cdn/new.jpg")
        assertThat(oldImage.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_상품_수정_시_예외를_발생시킨다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0).withId(1L)
        val request = ProductUpdateRequest(
            categoryId = 1L, name = "수정상품",
            description = "수정설명", price = BigDecimal("20000")
        )

        whenever(categoryRepository.findNullableById(1L)).thenReturn(category)
        whenever(productRepository.findById(1L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.updateProduct(1L, request) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }

    @Test
    fun 존재하지_않는_카테고리로_상품_수정_시_예외를_발생시킨다() {
        val request = ProductUpdateRequest(
            categoryId = 999L, name = "수정상품",
            description = "수정설명", price = BigDecimal("20000")
        )

        whenever(categoryRepository.findNullableById(999L)).thenReturn(null)

        assertThatThrownBy { service.updateProduct(1L, request) }
            .isInstanceOf(CategoryNotFoundException::class.java)
    }

    @Test
    fun 상품을_삭제한다() {
        val product = Product.create(
            sellerId = 1L, categoryId = 1L, name = "테스트상품",
            description = "설명", price = BigDecimal("10000"), status = ProductStatus.AVAILABLE
        ).withId(1L)
        val image = ProductImage.create(productId = 1L, imageUrl = "/img.jpg", sortOrder = 0).withId(1L)

        whenever(productRepository.findById(1L)).thenReturn(Optional.of(product))
        val inventory = Inventory.create(productId = 1L, stockQuantity = 3).withId(1L)
        whenever(productImageRepository.findByProductIdOrderBySortOrder(1L)).thenReturn(listOf(image))
        whenever(inventoryRepository.findByProductIdForUpdate(1L)).thenReturn(inventory)

        service.deleteProduct(1L)

        assertThat(product.deletedAt).isNotNull()
        assertThat(image.deletedAt).isNotNull()
        assertThat(inventory.deletedAt).isNotNull()
    }

    @Test
    fun 존재하지_않는_상품_삭제_시_예외를_발생시킨다() {
        whenever(productRepository.findById(999L)).thenReturn(Optional.empty())

        assertThatThrownBy { service.deleteProduct(999L) }
            .isInstanceOf(ProductNotFoundException::class.java)
    }
}
