package com.hoppingmall.consistency.violation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
import org.springframework.context.annotation.Import
import java.time.LocalDateTime

@JdbcTest(properties = ["spring.datasource.url=jdbc:h2:mem:violations;MODE=MySQL;DB_CLOSE_DELAY=-1"])
@Import(ViolationRepository::class)
@DisplayName("ViolationRepository")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ViolationRepositoryTest {

    @Autowired
    private lateinit var repository: ViolationRepository

    private val t0 = LocalDateTime.of(2026, 10, 22, 19, 0, 0)

    @Test
    fun 새_위반을_저장하고_열린_위반으로_조회한다() {
        repository.insert("INV-1", "10", "CRITICAL", t0, t0, """{"order_id":10}""")

        val open = repository.findOpen("INV-1").single()

        assertThat(open.entityKey).isEqualTo("10")
        assertThat(open.firstSeen).isEqualTo(t0)
        assertThat(open.confirmedAt).isEqualTo(t0)
        assertThat(repository.findAll(openOnly = true)).hasSize(1)
        assertThat(repository.findById(open.id)).isEqualTo(open)
    }

    @Test
    fun 재관측하면_last_seen을_갱신하고_처음_확정된_시각은_유지한다() {
        repository.insert("INV-2b", "7", "WARN", t0, null, "{}")
        val id = repository.findOpen("INV-2b").single().id

        repository.touch(id, t0.plusSeconds(60), t0.plusSeconds(60), """{"v":1}""")
        repository.touch(id, t0.plusSeconds(120), t0.plusSeconds(120), """{"v":2}""")

        val violation = repository.findById(id)!!
        assertThat(violation.lastSeen).isEqualTo(t0.plusSeconds(120))
        assertThat(violation.confirmedAt).isEqualTo(t0.plusSeconds(60))
        assertThat(violation.snapshotJson).isEqualTo("""{"v":2}""")
    }

    @Test
    fun 해소하면_열린_목록에서_빠지고_두_번_해소되지_않는다() {
        repository.insert("INV-6", "3", "CRITICAL", t0, t0, "{}")
        val id = repository.findOpen("INV-6").single().id

        assertThat(repository.resolve(id, t0.plusSeconds(60), "system", "auto")).isTrue()
        assertThat(repository.resolve(id, t0.plusSeconds(120), "kim", "again")).isFalse()

        assertThat(repository.findOpen("INV-6")).isEmpty()
        assertThat(repository.findAll(openOnly = false).single { it.id == id })
            .extracting("resolvedAt", "ackedBy", "resolutionNote")
            .containsExactly(t0.plusSeconds(60), "system", "auto")
    }

    @Test
    fun DB_현재_시각을_읽는다() {
        assertThat(repository.now()).isNotNull()
    }
}
