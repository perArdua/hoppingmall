package com.hoppingmall.consistency.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver
import org.springframework.boot.context.properties.source.ConfigurationPropertySources
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.support.ResourcePropertySource
import java.time.Duration

@DisplayName("점검 정책 설정 바인딩")
@DisplayNameGeneration(ReplaceUnderscores::class)
class InvariantPolicyBindingTest {

    private fun bind(overrides: Map<String, Any> = emptyMap()): Pair<ConsistencyProperties, Duration> {
        val sources = MutablePropertySources().apply {
            addLast(MapPropertySource("overrides", overrides))
            addLast(ResourcePropertySource(ClassPathResource("application.properties")))
            addLast(ResourcePropertySource(ClassPathResource("invariants.properties")))
        }
        val binder = Binder(ConfigurationPropertySources.from(sources), PropertySourcesPlaceholdersResolver(sources))
        val properties = binder.bind("consistency", ConsistencyProperties::class.java).get()
        val interval = Duration.ofMillis(binder.bind("consistency.interval-ms", Long::class.javaObjectType).get())
        return properties to interval
    }

    @Test
    fun 주문_결제_짝_점검의_유예는_점검_주기와_같다() {
        val (properties, interval) = bind()

        assertThat(interval).isEqualTo(Duration.ofSeconds(60))
        assertThat(properties.policyOf("INV-1").graceSeconds).isEqualTo(interval)
        assertThat(properties.policyOf("INV-1R").graceSeconds).isEqualTo(interval)
    }

    @Test
    fun 점검_주기를_바꾸면_주문_결제_짝_점검의_유예도_따라간다() {
        val (properties, interval) = bind(mapOf("CONSISTENCY_INTERVAL_MS" to "30000"))

        assertThat(interval).isEqualTo(Duration.ofSeconds(30))
        assertThat(properties.policyOf("INV-1").graceSeconds).isEqualTo(interval)
        assertThat(properties.policyOf("INV-1R").graceSeconds).isEqualTo(interval)
    }

    @Test
    fun 단위_없이_적은_유예는_초로_읽는다() {
        val (properties, _) = bind()

        assertThat(properties.policyOf("INV-2b").graceSeconds).isEqualTo(Duration.ofSeconds(60))
        assertThat(properties.policyOf("INV-4").graceSeconds).isEqualTo(Duration.ZERO)
    }
}
