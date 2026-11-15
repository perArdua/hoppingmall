package com.hoppingmall.order.config

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.ClassPathResource

@DisplayName("운영 프로필의 API 명세 노출 설정")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ApiDocsProfileSettingTest {

    @ParameterizedTest
    @ValueSource(strings = ["application-docker.yml", "application-k8s.yml"])
    fun 운영_프로필은_따로_켜지_않으면_명세와_화면을_끈다(fileName: String) {
        val environment = StandardEnvironment()
        environment.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        environment.propertySources.remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME)
        YamlPropertySourceLoader().load(fileName, ClassPathResource(fileName))
            .forEach { environment.propertySources.addLast(it) }

        assertThat(environment.getProperty("springdoc.api-docs.enabled", Boolean::class.java)).isFalse()
        assertThat(environment.getProperty("springdoc.swagger-ui.enabled", Boolean::class.java)).isFalse()
    }
}
