package com.hoppingmall.gateway

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.ClassPathResource

@DisplayName("프로필별 게이트웨이 전달 헤더 처리 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ForwardHeadersConfigurationTest {

    @ParameterizedTest
    @ValueSource(strings = ["application.yml", "application-docker.yml", "application-k8s.yml"])
    fun 모든_프로필이_전달_헤더_처리를_끈다(file: String) {
        val source = YamlPropertySourceLoader().load(file, ClassPathResource(file)).single()

        assertThat(source.getProperty("server.forward-headers-strategy")?.toString()).isEqualTo("none")
    }
}
