package com.hoppingmall.gateway.config

import org.springframework.cloud.gateway.config.HttpClientCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import reactor.netty.transport.NameResolverProvider.NameResolverSpec
import java.time.Duration

@Configuration
class GatewayHttpClientConfig {

    @Bean
    fun gatewayDnsCacheTtlCustomizer(): HttpClientCustomizer =
        HttpClientCustomizer { httpClient -> httpClient.resolver { spec -> limitDnsCacheTtl(spec) } }

    companion object {
        val DNS_CACHE_MAX_TTL: Duration = Duration.ofSeconds(5)

        fun limitDnsCacheTtl(spec: NameResolverSpec): NameResolverSpec =
            spec.cacheMaxTimeToLive(DNS_CACHE_MAX_TTL)
    }
}
