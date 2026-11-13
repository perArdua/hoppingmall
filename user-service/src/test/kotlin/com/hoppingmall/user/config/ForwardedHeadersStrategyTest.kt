package com.hoppingmall.user.config

import jakarta.servlet.http.HttpServlet
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.boot.autoconfigure.AutoConfigurations
import org.springframework.boot.autoconfigure.web.embedded.EmbeddedWebServerFactoryCustomizerAutoConfiguration
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer
import org.springframework.boot.test.context.runner.WebApplicationContextRunner
import org.springframework.boot.web.servlet.ServletRegistrationBean
import org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@DisplayName("전달 헤더 처리 설정")
@DisplayNameGeneration(ReplaceUnderscores::class)
class ForwardedHeadersStrategyTest {

    @ParameterizedTest
    @ValueSource(strings = ["default", "docker", "k8s"])
    fun 쿠버네티스에서도_X_Forwarded_For가_요청의_원격_주소를_바꾸지_않는다(profile: String) {
        val remoteAddr = remoteAddrSeenByApplication(
            "spring.profiles.active=$profile",
            "spring.main.cloud-platform=kubernetes"
        )

        assertThat(remoteAddr).isEqualTo("127.0.0.1")
    }

    @Test
    fun 전달_헤더_처리를_켜면_X_Forwarded_For가_요청의_원격_주소를_바꾼다() {
        val remoteAddr = remoteAddrSeenByApplication(
            "spring.main.cloud-platform=kubernetes",
            "server.forward-headers-strategy=native"
        )

        assertThat(remoteAddr).isEqualTo(CLIENT_SUPPLIED_ADDRESS)
    }

    private fun remoteAddrSeenByApplication(vararg properties: String): String {
        lateinit var remoteAddr: String
        WebApplicationContextRunner { AnnotationConfigServletWebServerApplicationContext() }
            .withInitializer(ConfigDataApplicationContextInitializer())
            .withConfiguration(
                AutoConfigurations.of(
                    ServletWebServerFactoryAutoConfiguration::class.java,
                    EmbeddedWebServerFactoryCustomizerAutoConfiguration::class.java
                )
            )
            .withUserConfiguration(RemoteAddrServletConfig::class.java)
            .withPropertyValues("server.port=0", *properties)
            .run { context ->
                val port = (context.sourceApplicationContext as ServletWebServerApplicationContext).webServer.port
                val request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port/remote-addr"))
                    .header("X-Forwarded-For", CLIENT_SUPPLIED_ADDRESS)
                    .GET()
                    .build()
                remoteAddr = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString()).body()
            }
        return remoteAddr
    }

    @Configuration(proxyBeanMethods = false)
    class RemoteAddrServletConfig {

        @Bean
        fun remoteAddrServlet(): ServletRegistrationBean<HttpServlet> =
            ServletRegistrationBean(
                object : HttpServlet() {
                    override fun doGet(request: HttpServletRequest, response: HttpServletResponse) {
                        response.writer.write(request.remoteAddr)
                    }
                },
                "/remote-addr"
            )
    }

    companion object {
        private const val CLIENT_SUPPLIED_ADDRESS = "203.0.113.7"
    }
}
