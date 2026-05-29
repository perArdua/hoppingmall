package com.hoppingmall.cache

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer

/**
 * 캐시 L2(Redis) 값 직렬화 지원.
 *
 * 기존 기본값(JDK 직렬화)은 캐시 값 타입(ProductResponse 등)이 Serializable이 아니라 직렬화에서 실패했다(L2 no-op).
 * 기본 경로는 "캐시별 타입드 직렬화"([TypedCacheValueSerializer]) — @class/default typing 없이 선언된 타입으로만
 * 역직렬화하므로 폴리모픽 역직렬화(가젯) 표면이 없다.
 *
 * - [mapper]: KotlinModule + JavaTimeModule(ISO 날짜). default typing 없음. 타입드 직렬화기 + JavaType 생성에 공용.
 * - [typeOf]/[listOf]: 서비스 CacheConfig가 캐시별 값 타입(JavaType)을 선언할 때 사용.
 * - [fallback]: valueType 미선언(=타입을 모르는) 캐시 전용 보조. 타입을 모르니 @class가 필요해 GenericJackson2를
 *   쓰되 PTV를 com.hoppingmall.* + java.* + NullValue로 한정한다(allow-all 금지). 현재 모든 캐시가 valueType을
 *   선언하므로 이 경로는 사용되지 않으며 런타임 동적 생성 캐시의 안전망일 뿐이다.
 */
object CacheValueSerializer {

    val mapper: ObjectMapper = ObjectMapper().apply {
        registerModule(KotlinModule.Builder().build())
        registerModule(JavaTimeModule())
        disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    }

    fun typeOf(type: Class<*>): JavaType = mapper.typeFactory.constructType(type)

    fun listOf(elementType: Class<*>): JavaType =
        mapper.typeFactory.constructCollectionType(List::class.java, elementType)

    fun fallback(): GenericJackson2JsonRedisSerializer {
        val typeValidator = BasicPolymorphicTypeValidator.builder()
            .allowIfSubType("com.hoppingmall.")
            .allowIfSubType("java.")
            .allowIfSubType("org.springframework.cache.support.NullValue")
            .build()
        val fallbackMapper = ObjectMapper().apply {
            registerModule(KotlinModule.Builder().build())
            registerModule(JavaTimeModule())
            disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            activateDefaultTyping(
                typeValidator,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.PROPERTY
            )
        }
        return GenericJackson2JsonRedisSerializer(fallbackMapper)
    }
}
