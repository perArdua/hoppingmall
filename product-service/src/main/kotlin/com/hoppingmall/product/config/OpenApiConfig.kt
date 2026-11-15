package com.hoppingmall.product.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.common.ApiResponse as ApiEnvelope
import com.hoppingmall.common.UserPrincipal
import com.hoppingmall.idempotency.IdempotencyAspect
import com.hoppingmall.idempotency.Idempotent
import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.jackson.ModelResolver
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.media.StringSchema
import io.swagger.v3.oas.models.parameters.HeaderParameter
import io.swagger.v3.oas.models.responses.ApiResponse
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.customizers.OperationCustomizer
import org.springdoc.core.properties.SpringDocConfigProperties
import org.springdoc.core.utils.SpringDocUtils
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    init {
        SpringDocUtils.getConfig().addRequestWrapperToIgnore(UserPrincipal::class.java)
    }

    @Bean
    fun openAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("Product Service API")
                    .version("v1")
                    .description("상품, 카테고리, 재고, 리뷰, 위시리스트 관리 API")
            )
    }

    @Bean
    fun openApiModelResolver(objectMapper: ObjectMapper, springDocConfigProperties: SpringDocConfigProperties): ModelResolver =
        ModelResolver(objectMapper.copy()).openapi31(springDocConfigProperties.isOpenapi31)

    @Bean
    fun apiResponseEnvelopeCustomizer(): OpenApiCustomizer = OpenApiCustomizer { openApi ->
        openApi.components.addSchemas(ERROR_SCHEMA, errorSchema(openApi))
        openApi.paths.orEmpty().values
            .flatMap { it.readOperations() }
            .forEach { operation ->
                operation.responses.values.forEach { response ->
                    response.content?.remove(ANY_MEDIA_TYPE)?.let { response.content.addMediaType(JSON_MEDIA_TYPE, it) }
                }
                operation.responses.addApiResponse("default", errorResponse())
            }
    }

    @Bean
    fun idempotencyKeyHeaderCustomizer(): OperationCustomizer = OperationCustomizer { operation, handlerMethod ->
        if (handlerMethod.hasMethodAnnotation(Idempotent::class.java)) {
            operation.addParametersItem(
                HeaderParameter()
                    .name(IdempotencyAspect.IDEMPOTENCY_KEY_HEADER)
                    .required(true)
                    .description("같은 키로 다시 보내면 처음 응답을 그대로 돌려준다. 없으면 400(IDEM001).")
                    .schema(StringSchema())
            )
        }
        operation
    }

    private fun errorSchema(openApi: OpenAPI): Schema<*> {
        val resolved = ModelConverters.getInstance(openApi.specVersion == SpecVersion.V31)
            .resolveAsResolvedSchema(AnnotatedType(ApiEnvelope::class.java).resolveAsRef(false))
        resolved.referencedSchemas
            .filterKeys { it != resolved.schema.name }
            .forEach { (name, schema) -> openApi.components.schemas.putIfAbsent(name, schema) }
        return resolved.schema
            .addProperty("data", Schema<Any>().types(setOf("null")).description("오류 응답에서는 null"))
            .required(listOf("code", "message"))
            .description("오류 응답 봉투. code에 오류 코드가 온다.")
    }

    private fun errorResponse(): ApiResponse = ApiResponse()
        .description("오류(4xx·5xx). 본문 code에 오류 코드가 온다. 인증이 없거나 권한이 없는 요청은 본문 없이 401·403으로 올 수 있다.")
        .content(
            Content().addMediaType(
                JSON_MEDIA_TYPE,
                MediaType().schema(Schema<Any>().`$ref`("#/components/schemas/$ERROR_SCHEMA"))
            )
        )

    companion object {
        private const val ERROR_SCHEMA = "ErrorResponse"
        private const val ANY_MEDIA_TYPE = "*/*"
        private const val JSON_MEDIA_TYPE = "application/json"
    }
}
