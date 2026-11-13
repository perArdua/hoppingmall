package com.hoppingmall.notification.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.boot.DefaultBootstrapContext
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor
import org.springframework.core.env.MapPropertySource
import org.springframework.core.env.StandardEnvironment
import org.springframework.core.io.DefaultResourceLoader

@DisplayName("docker 프로필 datasource 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class DockerProfileDataSourceTest {

    private fun dockerEnvironment(variables: Map<String, Any>): StandardEnvironment {
        val environment = StandardEnvironment()
        environment.propertySources.remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)
        environment.propertySources.addFirst(MapPropertySource("variables", variables))
        ConfigDataEnvironmentPostProcessor.applyTo(
            environment,
            DefaultResourceLoader(),
            DefaultBootstrapContext(),
            listOf("docker")
        )
        return environment
    }

    @Test
    fun docker_프로필은_compose_MySQL의_notification_db를_쓴다() {
        val environment = dockerEnvironment(mapOf("MYSQL_ROOT_PASSWORD" to "compose-password"))

        assertThat(environment.getProperty("spring.datasource.url"))
            .startsWith("jdbc:mysql://mysql:3306/notification_db?")
            .contains("connectTimeout=3000", "socketTimeout=30000")
        assertThat(environment.getProperty("spring.datasource.driver-class-name"))
            .isEqualTo("com.mysql.cj.jdbc.Driver")
        assertThat(environment.getProperty("spring.datasource.username")).isEqualTo("root")
        assertThat(environment.getProperty("spring.datasource.password")).isEqualTo("compose-password")
        assertThat(environment.getProperty("spring.jpa.database-platform"))
            .isEqualTo("org.hibernate.dialect.MySQLDialect")
    }

    @Test
    fun docker_프로필_datasource_비밀번호는_환경_변수가_없으면_기본값으로_채우지_않는다() {
        val environment = dockerEnvironment(emptyMap())

        assertThatThrownBy { environment.getProperty("spring.datasource.password") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("MYSQL_ROOT_PASSWORD")
    }
}
