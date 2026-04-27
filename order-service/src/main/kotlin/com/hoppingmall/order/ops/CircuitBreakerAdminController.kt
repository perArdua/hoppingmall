package com.hoppingmall.order.ops

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Profile
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ops/circuitbreakers")
@Profile("loadtest")
class CircuitBreakerAdminController(
    private val registry: CircuitBreakerRegistry
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/{name}/reset")
    fun reset(@PathVariable name: String): Map<String, Any> {
        val cb = registry.circuitBreaker(name)
        val before = cb.state.name
        cb.reset()
        log.info("CircuitBreaker reset: name={}, before={}, after={}", name, before, cb.state.name)
        return mapOf("name" to name, "before" to before, "after" to cb.state.name)
    }

    @PostMapping("/reset-all")
    fun resetAll(): List<Map<String, Any>> {
        return registry.allCircuitBreakers.map {
            val before = it.state.name
            it.reset()
            log.info("CircuitBreaker reset: name={}, before={}, after={}", it.name, before, it.state.name)
            mapOf<String, Any>("name" to it.name, "before" to before, "after" to it.state.name)
        }
    }

    @PostMapping("/disable-all")
    fun disableAll(): List<Map<String, Any>> {
        return registry.allCircuitBreakers.map {
            val before = it.state.name
            it.transitionToDisabledState()
            log.info("CircuitBreaker disabled: name={}, before={}, after={}", it.name, before, it.state.name)
            mapOf<String, Any>("name" to it.name, "before" to before, "after" to it.state.name)
        }
    }

    @PostMapping("/enable-all")
    fun enableAll(): List<Map<String, Any>> {
        return registry.allCircuitBreakers.map {
            val before = it.state.name
            it.transitionToClosedState()
            log.info("CircuitBreaker enabled: name={}, before={}, after={}", it.name, before, it.state.name)
            mapOf<String, Any>("name" to it.name, "before" to before, "after" to it.state.name)
        }
    }
}
