package com.hoppingmall.order.ops

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("CircuitBreakerAdminController")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CircuitBreakerAdminControllerTest {

    private fun newController(): Pair<CircuitBreakerAdminController, CircuitBreakerRegistry> {
        val registry = CircuitBreakerRegistry.ofDefaults()
        registry.circuitBreaker("product-query")
        registry.circuitBreaker("payment-query")
        return CircuitBreakerAdminController(registry) to registry
    }

    @Test
    fun disable_all_은_모든_CB를_DISABLED_상태로_전환한다() {
        val (controller, registry) = newController()

        val result = controller.disableAll()

        assertThat(result).hasSize(2)
        assertThat(registry.allCircuitBreakers.map { it.state })
            .containsOnly(CircuitBreaker.State.DISABLED)
    }

    @Test
    fun enable_all_은_DISABLED_상태의_CB를_CLOSED로_복원한다() {
        val (controller, registry) = newController()
        registry.allCircuitBreakers.forEach { it.transitionToDisabledState() }

        val result = controller.enableAll()

        assertThat(result).hasSize(2)
        assertThat(registry.allCircuitBreakers.map { it.state })
            .containsOnly(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun reset_all_은_모든_CB를_CLOSED로_리셋한다() {
        val (controller, registry) = newController()
        registry.allCircuitBreakers.forEach { it.transitionToOpenState() }

        val result = controller.resetAll()

        assertThat(result).hasSize(2)
        assertThat(registry.allCircuitBreakers.map { it.state })
            .containsOnly(CircuitBreaker.State.CLOSED)
    }

    @Test
    fun reset_단일_CB는_이름으로_지정해서_리셋한다() {
        val (controller, registry) = newController()
        registry.circuitBreaker("product-query").transitionToOpenState()

        val result = controller.reset("product-query")

        assertThat(result["name"]).isEqualTo("product-query")
        assertThat(registry.circuitBreaker("product-query").state).isEqualTo(CircuitBreaker.State.CLOSED)
    }
}
