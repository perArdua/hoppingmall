package com.hoppingmall.consistency.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.hoppingmall.consistency.support.MonitorSources
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.PathMatchingResourcePatternResolver
import org.springframework.core.io.support.PropertiesLoaderUtils
import java.io.File
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

@DisplayName("설정 키마다 읽는 코드가 있다")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConfigurationKeyUsageTest {

    private val mainSources: String = File("src/main/kotlin").walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .joinToString("\n") { it.readText() }

    private val placeholderKeys: Set<String> =
        Regex("""\$\{([a-zA-Z0-9.\-\[\]]+)""").findAll(mainSources).map { it.groupValues[1] }.toSet()

    private val sqlParams: Set<String> =
        Regex("""\{\{([A-Z_]+)}}""").findAll(MonitorSources.catalogSql()).map { it.groupValues[1] }.toSet()

    private val invariantIds: Set<String> = MonitorSources.definitions().map { it.id }.toSet()

    private val springMetadata: List<Pair<String, String>> by lazy {
        val mapper = ObjectMapper()
        PathMatchingResourcePatternResolver()
            .getResources("classpath*:META-INF/*spring-configuration-metadata.json")
            .flatMap { resource ->
                mapper.readTree(resource.inputStream)["properties"]
                    ?.map { it["name"].asText() to (it["type"]?.asText() ?: "") }
                    .orEmpty()
            }
    }

    private fun configuredKeys(): Set<String> =
        listOf("application.properties", "invariants.properties")
            .flatMap { PropertiesLoaderUtils.loadProperties(ClassPathResource(it)).stringPropertyNames() }
            .toSet()

    private fun unusedReason(key: String): String? = when {
        key in placeholderKeys -> null
        key.startsWith(CONSISTENCY_PREFIX) -> consistencyReason(key.removePrefix(CONSISTENCY_PREFIX))
        springMetadata.any { (name, type) -> key == name || (type.startsWith("java.util.Map") && key.startsWith("$name.")) } -> null
        else -> "코드의 자리표시자에도 스프링 설정 메타데이터에도 없음"
    }

    private fun consistencyReason(path: String): String? {
        val segments = path.split('.').flatMap { segment ->
            val bracket = Regex("""^([a-z0-9\-]+)\[(.+)]$""").find(segment)
            if (bracket == null) listOf(segment) else listOf(bracket.groupValues[1], bracket.groupValues[2])
        }
        var owner: KClass<*> = ConsistencyProperties::class
        var index = 0
        while (index < segments.size) {
            val name = camelCase(segments[index])
            val parameter = owner.primaryConstructor?.parameters?.firstOrNull { it.name == name }
                ?: return "${owner.simpleName}에 '$name' 필드가 없음"
            if (!isRead(name)) return "'$name'을 읽는 코드가 없음"
            val type = parameter.type
            if ((type.classifier as KClass<*>) == Map::class) {
                val mapKey = segments.getOrNull(index + 1) ?: return "맵 키가 없음"
                mapKeyReason(name, mapKey)?.let { return it }
                val valueType = valueClass(type)
                if (valueType == String::class) return if (index + 2 == segments.size) null else "문자열 값 아래 키가 있음"
                owner = valueType
                index += 2
            } else {
                owner = type.classifier as KClass<*>
                index += 1
            }
        }
        return null
    }

    private fun mapKeyReason(field: String, mapKey: String): String? = when (field) {
        "params" -> if (mapKey in sqlParams) null else "점검 SQL이 쓰지 않는 파라미터 '$mapKey'"
        "policies" -> if (mapKey in invariantIds) null else "점검 목록에 없는 항목 '$mapKey'"
        else -> null
    }

    private fun valueClass(type: KType): KClass<*> = type.arguments[1].type!!.classifier as KClass<*>

    private fun isRead(field: String): Boolean {
        val uses = Regex("""\b$field\b""").findAll(mainSources).count()
        val declarations = Regex("""\bval $field\s*:""").findAll(mainSources).count()
        return uses > declarations
    }

    private fun camelCase(kebab: String): String =
        kebab.split('-').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar(Char::uppercaseChar) }.joinToString("")

    @Test
    fun 설정_파일의_모든_키는_읽는_코드가_있다() {
        val unused = configuredKeys().mapNotNull { key -> unusedReason(key)?.let { "$key: $it" } }

        assertThat(configuredKeys()).hasSizeGreaterThan(50)
        assertThat(unused).isEmpty()
    }

    @Test
    fun 읽는_코드가_없는_키는_잡아낸다() {
        assertThat(unusedReason("consistency.policies[INV-1].repair-policy")).contains("repairPolicy")
        assertThat(unusedReason("consistency.policies[INV-99].severity")).contains("INV-99")
        assertThat(unusedReason("consistency.params.UNUSED_PARAM")).contains("UNUSED_PARAM")
        assertThat(unusedReason("consistency.sources.order.url.extra")).isNotNull()
        assertThat(unusedReason("spring.datasource.not-a-property")).isNotNull()
        assertThat(unusedReason("consistency.lease.seconds")).isNull()
        assertThat(unusedReason("consistency.sources.order.maximum-pool-size")).isNull()
        assertThat(unusedReason("management.metrics.tags.region")).isNull()
    }

    companion object {
        private const val CONSISTENCY_PREFIX = "consistency."
    }
}
