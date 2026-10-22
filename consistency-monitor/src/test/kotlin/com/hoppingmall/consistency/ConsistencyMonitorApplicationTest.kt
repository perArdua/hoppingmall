package com.hoppingmall.consistency

import com.hoppingmall.consistency.config.ConsistencyProperties
import com.hoppingmall.consistency.enums.RepairPolicy
import com.hoppingmall.consistency.enums.Severity
import com.hoppingmall.consistency.invariant.InvariantCatalog
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:monitor;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.hikari.connection-init-sql=SELECT 1",
        "consistency.initial-delay-ms=3600000"
    ]
)
@DisplayName("consistency-monitor 컨텍스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ConsistencyMonitorApplicationTest {

    @Autowired
    private lateinit var properties: ConsistencyProperties

    @Autowired
    private lateinit var catalog: InvariantCatalog

    @Test
    fun 모든_점검_항목에_정책이_선언되어_있다() {
        assertThat(catalog.definitions).hasSize(14)
        assertThat(properties.policies.keys).containsExactlyInAnyOrderElementsOf(catalog.definitions.map { it.id })
        assertThat(properties.policyOf("INV-1R").severity).isEqualTo(Severity.CRITICAL)
        assertThat(properties.policyOf("INV-2b").graceSeconds).isEqualTo(60)
        assertThat(properties.policyOf("INV-11").repairPolicy).isEqualTo(RepairPolicy.AUTO_RETRY)
    }
}
