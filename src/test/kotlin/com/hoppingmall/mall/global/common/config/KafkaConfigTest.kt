package com.hoppingmall.mall.global.common.config

import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.kafka.support.serializer.JsonDeserializer
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@DisplayName("KafkaConfig 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class KafkaConfigTest {

    @Test
    fun 트랜잭션_아이디는_인스턴스별로_유니크하다() {
        val config1 = createConfig()
        val config2 = createConfig()

        val producerFactory1 = config1.producerFactory() as DefaultKafkaProducerFactory<String, Any>
        val producerFactory2 = config2.producerFactory() as DefaultKafkaProducerFactory<String, Any>

        val prefix1 = readField(producerFactory1, "transactionIdPrefix")
        val prefix2 = readField(producerFactory2, "transactionIdPrefix")

        assertNotNull(prefix1)
        assertNotNull(prefix2)

        assertTrue(prefix1.startsWith("hoppingmall-tx-"))
        assertTrue(prefix2.startsWith("hoppingmall-tx-"))
        assertNotEquals(prefix1, prefix2)
    }

    @Test
    fun JsonDeserializer는_신뢰_패키지를_제한한다() {
        val config = createConfig()
        val consumerFactory = config.consumerFactory() as DefaultKafkaConsumerFactory<String, Any>

        val configs = readConfigs(consumerFactory)
        val trusted = configs[JsonDeserializer.TRUSTED_PACKAGES]?.toString()

        assertNotNull(trusted)

        assertEquals(
            "com.hoppingmall.mall.payment.dto.event,com.hoppingmall.mall.notification.dto.event",
            trusted
        )
        assertNotEquals("*", trusted)
    }

    @Test
    fun 컨슈머_타임아웃_설정이_적용된다() {
        val config = createConfig()
        val consumerFactory = config.consumerFactory() as DefaultKafkaConsumerFactory<String, Any>

        val configs = readConfigs(consumerFactory)

        assertEquals(300_000, configs[ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG])
        assertEquals(100, configs[ConsumerConfig.MAX_POLL_RECORDS_CONFIG])
        assertEquals(30_000, configs[ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG])
        assertEquals(10_000, configs[ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG])
    }

    private fun createConfig(): KafkaConfig {
        val config = KafkaConfig()
        setField(config, "bootstrapServers", "localhost:9092")
        setField(config, "groupId", "test-group")
        setField(config, "autoOffsetReset", "earliest")
        setField(config, "concurrency", 1)
        return config
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        field.set(target, value)
    }

    private fun readField(target: Any, fieldName: String): String? {
        val field = target.javaClass.getDeclaredField(fieldName)
        field.isAccessible = true
        return field.get(target)?.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun readConfigs(factory: Any): Map<String, Any> {
        val field = runCatching { factory.javaClass.getDeclaredField("configs") }.getOrNull()
        if (field != null) {
            field.isAccessible = true
            return field.get(factory) as Map<String, Any>
        }
        val method = factory.javaClass.methods.firstOrNull {
            it.name == "getConfigurationProperties" && it.parameterCount == 0
        }
        if (method != null) {
            return method.invoke(factory) as Map<String, Any>
        }
        return emptyMap()
    }
}
