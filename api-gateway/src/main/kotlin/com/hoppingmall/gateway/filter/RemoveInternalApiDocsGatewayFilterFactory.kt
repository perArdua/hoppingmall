package com.hoppingmall.gateway.filter

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.cloud.gateway.filter.GatewayFilter
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory
import org.springframework.cloud.gateway.filter.factory.rewrite.ModifyResponseBodyGatewayFilterFactory
import org.springframework.cloud.gateway.filter.factory.rewrite.RewriteFunction
import org.springframework.stereotype.Component
import reactor.core.publisher.Mono

@Component
class RemoveInternalApiDocsGatewayFilterFactory(
    private val modifyResponseBody: ModifyResponseBodyGatewayFilterFactory,
    private val objectMapper: ObjectMapper
) : AbstractGatewayFilterFactory<RemoveInternalApiDocsGatewayFilterFactory.Config>(Config::class.java) {

    class Config

    override fun apply(config: Config): GatewayFilter =
        modifyResponseBody.apply(
            ModifyResponseBodyGatewayFilterFactory.Config().setRewriteFunction(
                String::class.java,
                String::class.java,
                RewriteFunction<String, String> { _, body -> Mono.just(removeInternalPaths(body)) }
            )
        )

    fun removeInternalPaths(body: String): String {
        val root = runCatching { objectMapper.readTree(body) }.getOrNull() as? ObjectNode ?: return body
        (root.get("paths") as? ObjectNode)?.let { paths ->
            paths.properties().map { it.key }.filter(::isInternalPath).forEach { paths.remove(it) }
        }
        removeUnreferencedSchemas(root)
        return objectMapper.writeValueAsString(root)
    }

    private fun isInternalPath(path: String): Boolean = path == INTERNAL_PREFIX || path.startsWith("$INTERNAL_PREFIX/")

    private fun removeUnreferencedSchemas(root: ObjectNode) {
        val schemas = root.path("components").get("schemas") as? ObjectNode ?: return
        val pending = ArrayDeque(
            root.properties().asSequence()
                .flatMap { (name, node) ->
                    if (name == "components") node.properties().asSequence().filter { it.key != "schemas" }.map { it.value }
                    else sequenceOf(node)
                }
                .flatMap { schemaRefs(it) }
                .toList()
        )
        val reachable = mutableSetOf<String>()
        while (pending.isNotEmpty()) {
            val name = pending.removeFirst()
            if (reachable.add(name)) {
                schemas.get(name)?.let { pending.addAll(schemaRefs(it)) }
            }
        }
        schemas.properties().map { it.key }.filterNot { it in reachable }.forEach { schemas.remove(it) }
    }

    private fun schemaRefs(node: JsonNode): List<String> =
        node.findValues(REF_FIELD)
            .map { it.asText() }
            .filter { it.startsWith(SCHEMA_REF_PREFIX) }
            .map { it.removePrefix(SCHEMA_REF_PREFIX) }

    companion object {
        private const val INTERNAL_PREFIX = "/internal"
        private const val REF_FIELD = "\$ref"
        private const val SCHEMA_REF_PREFIX = "#/components/schemas/"
    }
}
