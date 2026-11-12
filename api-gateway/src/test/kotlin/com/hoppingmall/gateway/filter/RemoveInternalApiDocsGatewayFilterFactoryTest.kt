package com.hoppingmall.gateway.filter

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory

@ExtendWith(MockitoExtension::class)
@DisplayName("서비스 API 문서에서 내부 API 경로 제거")
@DisplayNameGeneration(ReplaceUnderscores::class)
class RemoveInternalApiDocsGatewayFilterFactoryTest {

    @Mock
    private lateinit var modifyResponseBody: ModifyResponseBodyGatewayFilterFactory

    private val objectMapper = ObjectMapper()

    private fun factory() = RemoveInternalApiDocsGatewayFilterFactory(modifyResponseBody, objectMapper)

    private fun json(text: String): String = text.replace('\'', '"')

    @Test
    fun internal로_시작하는_경로만_빼고_나머지_경로는_그대로_둔다() {
        val docs = json(
            """
            {'paths': {
              '/internal': {'get': {}},
              '/internal/api/v1/points/earn': {'post': {}},
              '/internals/report': {'get': {}},
              '/api/v1/internal/notes': {'get': {}},
              '/ops/orders': {'get': {}}
            }}
            """
        )

        val result = objectMapper.readTree(factory().removeInternalPaths(docs))

        assertThat(result.path("paths").fieldNames().asSequence().toList())
            .containsExactly("/internals/report", "/api/v1/internal/notes", "/ops/orders")
    }

    @Test
    fun 남은_경로와_다른_구성요소가_참조하는_스키마는_간접_참조까지_남긴다() {
        val docs = json(
            """
            {'paths': {
              '/api/v1/points': {'get': {'responses': {'200': {'${'$'}ref': '#/components/responses/PointPage'}}}},
              '/internal/api/v1/points/earn': {'post': {'requestBody': {'content': {'application/json': {'schema': {'${'$'}ref': '#/components/schemas/EarnRequest'}}}}}}
            },
            'components': {
              'responses': {'PointPage': {'content': {'application/json': {'schema': {'${'$'}ref': '#/components/schemas/PointPage'}}}}},
              'schemas': {
                'PointPage': {'properties': {'items': {'items': {'${'$'}ref': '#/components/schemas/PointItem'}}}},
                'PointItem': {'properties': {'amount': {'type': 'integer'}}},
                'EarnRequest': {'properties': {'item': {'${'$'}ref': '#/components/schemas/PointItem'}, 'source': {'${'$'}ref': '#/components/schemas/EarnSource'}}},
                'EarnSource': {'type': 'string'}
              }
            }}
            """
        )

        val result = objectMapper.readTree(factory().removeInternalPaths(docs))

        assertThat(result.path("components").path("schemas").fieldNames().asSequence().toList())
            .containsExactlyInAnyOrder("PointPage", "PointItem")
        assertThat(result.path("components").path("responses").has("PointPage")).isTrue()
    }

    @Test
    fun 스키마가_없는_문서는_내부_경로만_뺀다() {
        val docs = json("{'paths': {'/api/v1/orders': {}, '/internal/api/v1/orders': {}}, 'info': {'title': 'order'}}")

        val result = objectMapper.readTree(factory().removeInternalPaths(docs))

        assertThat(result.path("paths").fieldNames().asSequence().toList()).containsExactly("/api/v1/orders")
        assertThat(result.path("info").path("title").asText()).isEqualTo("order")
    }

    @Test
    fun JSON_객체가_아닌_본문은_그대로_돌려준다() {
        assertThat(factory().removeInternalPaths("[]")).isEqualTo("[]")
    }
}
