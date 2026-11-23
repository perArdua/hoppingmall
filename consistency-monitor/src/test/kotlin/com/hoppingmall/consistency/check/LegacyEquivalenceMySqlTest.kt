package com.hoppingmall.consistency.check

import com.hoppingmall.consistency.invariant.InvariantCatalog
import com.hoppingmall.consistency.source.SourceDatabases
import com.hoppingmall.consistency.support.FlywaySchemas
import com.hoppingmall.consistency.support.InvariantFixture
import com.hoppingmall.consistency.support.LegacyInvariants
import com.hoppingmall.consistency.support.MonitorSources
import com.hoppingmall.consistency.support.MySqlContainerSupport
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource

@DisplayName("옛 SQL(한 서버에서 DB 사이 JOIN)과 새 점검의 결과 대조 (실제 MySQL, Flyway 스키마)")
@DisplayNameGeneration(ReplaceUnderscores::class)
class LegacyEquivalenceMySqlTest {

    private val urls = MonitorSources.SOURCES.associateWith { MySqlContainerSupport.jdbcUrl("${it}_db") }
    private val jdbc = urls.mapValues { (_, url) ->
        JdbcTemplate(DriverManagerDataSource(url, MySqlContainerSupport.username, MySqlContainerSupport.password))
    }
    private val fixture = InvariantFixture { jdbc.getValue(it) }
    private val properties = MonitorSources.properties(
        urls, MySqlContainerSupport.username, MySqlContainerSupport.password, chunkSize = 3, lookupBatchSize = 2
    )
    private val sources = SourceDatabases(properties)
    private val catalog = InvariantCatalog(DefaultResourceLoader(), properties)
    private val checks = InvariantChecks(catalog, sources, properties)
    private val definitions = catalog.definitions.associateBy { it.id }

    @AfterEach
    fun tearDown() {
        sources.close()
    }

    private fun legacyRows(id: String): List<Map<String, Any?>> = jdbc.getValue("order").queryForList(LEGACY.getValue(id))

    private fun sweepRows(id: String): List<Map<String, Any?>> {
        val rows = mutableListOf<Map<String, Any?>>()
        checks.sweep(definitions.getValue(id)) { rows += it }
        return rows
    }

    @Test
    fun 새_점검과_옛_SQL은_같은_16개_항목을_같은_순서로_가진다() {
        assertThat(catalog.definitions.map { it.id }).containsExactlyElementsOf(LEGACY.keys)
    }

    @ParameterizedTest
    @MethodSource("allIds")
    fun 최근에_바뀐_데이터에서_새_점검은_옛_SQL과_같은_행을_돌려준다(id: String) {
        fixture.truncateAll()
        fixture.insertRecentDataset()

        val legacy = legacyRows(id)
        val recent = checks.recent(definitions.getValue(id)) { emptyList() }

        assertThat(legacy).`as`("데이터셋에 $id 어긋남이 있어야 대조가 의미 있다").isNotEmpty()
        assertThat(normalized(recent.rows)).isEqualTo(normalized(legacy))
        if (checks.isSweepable(definitions.getValue(id))) {
            assertThat(normalized(sweepRows(id)).distinct()).isEqualTo(normalized(legacy).distinct())
        }
    }

    @ParameterizedTest
    @MethodSource("crossSourceIds")
    fun 오래된_어긋남은_최근_검사에_없고_열린_위반이면_다시_확인하며_전체_훑기가_잡는다(id: String) {
        fixture.truncateAll()
        fixture.insertStaleCrossSourceDataset()
        val definition = definitions.getValue(id)

        val legacy = legacyRows(id)
        val openKeys = legacy.map { it.values.first().toString() }
        val withoutOpen = checks.recent(definition) { emptyList() }
        val withOpen = checks.recent(definition) { openKeys }

        assertThat(legacy).isNotEmpty()
        assertThat(withoutOpen.rows).isEmpty()
        assertThat(openKeys).noneMatch(withoutOpen::covers)
        assertThat(normalized(withOpen.rows)).isEqualTo(normalized(legacy))
        assertThat(openKeys).allMatch(withOpen::covers)
        assertThat(normalized(sweepRows(id)).distinct()).isEqualTo(normalized(legacy).distinct())
    }

    companion object {
        private val LEGACY = LegacyInvariants.parse(MonitorSources.legacySql(), MonitorSources.PARAMS)

        @JvmStatic
        fun allIds(): List<String> = LEGACY.keys.toList()

        @JvmStatic
        fun crossSourceIds(): List<String> = listOf("INV-1", "INV-1R", "INV-3a", "INV-3b", "INV-10")

        @JvmStatic
        @BeforeAll
        fun migrate() {
            MonitorSources.SOURCES.forEach { source ->
                FlywaySchemas.migrate(
                    MySqlContainerSupport.jdbcUrl("${source}_db"),
                    MySqlContainerSupport.username,
                    MySqlContainerSupport.password,
                    FlywaySchemas.serviceOfSource.getValue(source)
                )
            }
        }

        fun normalized(rows: List<Map<String, Any?>>): List<String> =
            rows.map { row -> row.entries.joinToString(", ") { (key, value) -> "${key.lowercase()}=$value" } }.sorted()
    }
}
