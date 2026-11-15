package com.hoppingmall.product.product.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.hoppingmall.product.category.domain.Category
import com.hoppingmall.product.category.domain.repository.CategoryRepository
import com.hoppingmall.product.common.enums.ProductStatus
import com.hoppingmall.product.common.file.FileUploadConfig
import com.hoppingmall.product.grpc.GrpcProductQueryService
import com.hoppingmall.product.grpc.ProductIdRequest
import com.hoppingmall.product.grpc.ProductIdsRequest
import com.hoppingmall.product.internal.InternalProductController
import com.hoppingmall.product.inventory.domain.repository.InventoryRepository
import com.hoppingmall.product.inventory.service.InventoryCommandService
import com.hoppingmall.product.product.domain.Product
import com.hoppingmall.product.product.domain.ProductImage
import com.hoppingmall.product.product.domain.repository.ProductImageRepository
import com.hoppingmall.product.product.domain.repository.ProductRepository
import com.hoppingmall.product.product.dto.request.ProductCreateRequest
import com.hoppingmall.product.product.dto.request.ProductUpdateRequest
import com.hoppingmall.product.support.withId
import com.google.protobuf.Message
import jakarta.validation.Validation
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.whenever
import org.springframework.test.util.ReflectionTestUtils
import java.math.BigDecimal

@DisplayName("상품 청약철회 제한 표시·사유")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
@ExtendWith(MockitoExtension::class)
class ProductWithdrawalRestrictionTest {

    @Mock
    private lateinit var productRepository: ProductRepository

    @Mock
    private lateinit var productImageRepository: ProductImageRepository

    @Mock
    private lateinit var categoryRepository: CategoryRepository

    @Mock
    private lateinit var fileUploadConfig: FileUploadConfig

    @Mock
    private lateinit var inventoryCommandService: InventoryCommandService

    @Mock
    private lateinit var inventoryRepository: InventoryRepository

    private val objectMapper = ObjectMapper()
        .registerKotlinModule()
        .registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)

    private val validator = Validation.buildDefaultValidatorFactory().validator

    private fun service() = ProductCommandServiceImpl(
        productRepository, productImageRepository, categoryRepository,
        fileUploadConfig, inventoryCommandService, inventoryRepository
    )

    @Test
    fun 등록에서_제한과_사유를_정하면_저장하고_응답에_내려_준다() {
        stubCreate()

        val response = json(
            service().createProduct(
                createRequest(""","withdrawal_restricted":true,"withdrawal_restriction_reason":"CUSTOM_MADE"""")
            )
        )

        assertThat(response.path("withdrawal_restricted").asBoolean()).isTrue()
        assertThat(response.path("withdrawal_restriction_reason").asText()).isEqualTo("CUSTOM_MADE")
    }

    @Test
    fun 등록에서_정하지_않으면_제한하지_않는다() {
        stubCreate()

        val response = json(service().createProduct(createRequest("")))

        assertThat(response.path("withdrawal_restricted").isBoolean).isTrue()
        assertThat(response.path("withdrawal_restricted").asBoolean()).isFalse()
        assertThat(response.has("withdrawal_restriction_reason")).isTrue()
        assertThat(response.path("withdrawal_restriction_reason").isNull).isTrue()
    }

    @Test
    fun 수정에서_값을_주지_않으면_그대로_두고_풀면_사유도_지운다() {
        val product = restrictedProduct()
        stubUpdate(product)

        val kept = json(service().updateProduct(PRODUCT_ID, updateRequest("")))
        assertThat(kept.path("withdrawal_restricted").asBoolean()).isTrue()
        assertThat(kept.path("withdrawal_restriction_reason").asText()).isEqualTo("PERISHABLE")

        val cleared = json(service().updateProduct(PRODUCT_ID, updateRequest(""","withdrawal_restricted":false""")))
        assertThat(cleared.path("withdrawal_restricted").asBoolean()).isFalse()
        assertThat(cleared.path("withdrawal_restriction_reason").isNull).isTrue()
    }

    @Test
    fun 수정에서_제한과_사유를_바꾼다() {
        val product = restrictedProduct()
        stubUpdate(product)

        val changed = json(
            service().updateProduct(
                PRODUCT_ID,
                updateRequest(""","withdrawal_restricted":true,"withdrawal_restriction_reason":"VALUE_LOSS_ON_USE"""")
            )
        )

        assertThat(changed.path("withdrawal_restriction_reason").asText()).isEqualTo("VALUE_LOSS_ON_USE")
    }

    @Test
    fun 제한과_사유가_맞지_않는_요청은_검증에서_막는다() {
        assertThat(validator.validate(createRequest(""","withdrawal_restricted":true"""))).isNotEmpty()
        assertThat(validator.validate(createRequest(""","withdrawal_restriction_reason":"CUSTOM_MADE""""))).isNotEmpty()
        assertThat(validator.validate(createRequest(""","withdrawal_restricted":false,"withdrawal_restriction_reason":"CUSTOM_MADE""""))).isNotEmpty()
        assertThat(validator.validate(updateRequest(""","withdrawal_restricted":true"""))).isNotEmpty()
        assertThat(validator.validate(updateRequest(""","withdrawal_restriction_reason":"CUSTOM_MADE""""))).isNotEmpty()

        assertThat(validator.validate(createRequest(""","withdrawal_restricted":true,"withdrawal_restriction_reason":"COPYABLE_GOODS""""))).isEmpty()
        assertThat(validator.validate(createRequest(""))).isEmpty()
        assertThat(validator.validate(updateRequest(""))).isEmpty()
        assertThat(validator.validate(updateRequest(""","withdrawal_restricted":false"""))).isEmpty()
    }

    @Test
    fun 내부_상품_조회_HTTP는_제한과_사유를_준다() {
        val product = restrictedProduct()
        whenever(productRepository.findNullableById(PRODUCT_ID)).thenReturn(product)
        whenever(productRepository.findAllById(listOf(PRODUCT_ID))).thenReturn(listOf(product))
        whenever(productImageRepository.findByProductIdOrderBySortOrder(PRODUCT_ID)).thenReturn(emptyList())
        val controller = InternalProductController(productRepository, productImageRepository, inventoryRepository)

        val single = json(controller.getProduct(PRODUCT_ID).body!!)
        val many = json(controller.getProductsByIds(listOf(PRODUCT_ID)).body!!.single())

        listOf(single, many).forEach {
            assertThat(it.path("withdrawal_restricted").asBoolean()).isTrue()
            assertThat(it.path("withdrawal_restriction_reason").asText()).isEqualTo("PERISHABLE")
        }
    }

    @Test
    fun 내부_상품_조회_gRPC는_제한과_사유를_준다() {
        val product = restrictedProduct()
        whenever(productRepository.findNullableById(PRODUCT_ID)).thenReturn(product)
        whenever(productRepository.findAllById(listOf(PRODUCT_ID))).thenReturn(listOf(product))
        whenever(productImageRepository.findByProductIdOrderBySortOrder(PRODUCT_ID)).thenReturn(emptyList())
        val grpc = GrpcProductQueryService(productRepository, productImageRepository, inventoryRepository)

        val single = runBlocking { grpc.findProductById(ProductIdRequest.newBuilder().setProductId(PRODUCT_ID).build()) }
        val many = runBlocking {
            grpc.findProductsByIds(ProductIdsRequest.newBuilder().addProductIds(PRODUCT_ID).build()).productsList.single()
        }

        listOf(single, many).forEach {
            assertThat(field(it, "withdrawal_restricted")).isEqualTo(true)
            assertThat(field(it, "withdrawal_restriction_reason")).isEqualTo("PERISHABLE")
        }
    }

    private fun field(message: Message, name: String): Any? {
        val descriptor = message.descriptorForType.findFieldByName(name)
        assertThat(descriptor).describedAs("proto field %s", name).isNotNull()
        return message.getField(descriptor)
    }

    private fun restrictedProduct(): Product {
        val product = Product.create(
            sellerId = 1L, categoryId = CATEGORY_ID, name = "생화 꽃다발",
            description = "설명", price = BigDecimal("30000"), status = ProductStatus.AVAILABLE
        ).withId(PRODUCT_ID)
        val reasonType = Class.forName("com.hoppingmall.product.common.enums.WithdrawalRestrictionReason")
        ReflectionTestUtils.setField(product, "withdrawalRestricted", true)
        ReflectionTestUtils.setField(product, "withdrawalRestrictionReason", reasonType.enumConstants.first { (it as Enum<*>).name == "PERISHABLE" })
        return product
    }

    private fun stubCreate() {
        whenever(categoryRepository.findNullableById(CATEGORY_ID)).thenReturn(category())
        whenever(productRepository.save(any<Product>())).thenAnswer { it.getArgument<Product>(0).withId(PRODUCT_ID) }
        whenever(fileUploadConfig.defaultImagePath).thenReturn("/default.jpg")
        whenever(productImageRepository.saveAll(any<List<ProductImage>>())).thenAnswer { it.getArgument<List<ProductImage>>(0) }
    }

    private fun stubUpdate(product: Product) {
        whenever(categoryRepository.findNullableById(CATEGORY_ID)).thenReturn(category())
        whenever(productRepository.findById(PRODUCT_ID)).thenReturn(java.util.Optional.of(product))
        whenever(productRepository.save(any<Product>())).thenAnswer { it.getArgument<Product>(0) }
        whenever(productImageRepository.findByProductIdOrderBySortOrder(PRODUCT_ID)).thenReturn(emptyList())
    }

    private fun category() = Category.create(name = "꽃", parentCategoryId = null, depth = 0).withId(CATEGORY_ID)

    private fun createRequest(extra: String): ProductCreateRequest = objectMapper.readValue(
        """{"seller_id":1,"category_id":$CATEGORY_ID,"name":"상품","description":"설명","price":30000$extra}""",
        ProductCreateRequest::class.java
    )

    private fun updateRequest(extra: String): ProductUpdateRequest = objectMapper.readValue(
        """{"category_id":$CATEGORY_ID,"name":"상품","description":"설명","price":30000$extra}""",
        ProductUpdateRequest::class.java
    )

    private fun json(value: Any): JsonNode = objectMapper.valueToTree(value)

    companion object {
        private const val PRODUCT_ID = 1L
        private const val CATEGORY_ID = 3L
    }
}
