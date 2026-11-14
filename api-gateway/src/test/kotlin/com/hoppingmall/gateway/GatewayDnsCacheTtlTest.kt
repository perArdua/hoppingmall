package com.hoppingmall.gateway

import com.hoppingmall.gateway.config.GatewayHttpClientConfig
import io.netty.bootstrap.Bootstrap
import io.netty.buffer.Unpooled
import io.netty.channel.Channel
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.ChannelInitializer
import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.DatagramChannel
import io.netty.channel.socket.nio.NioDatagramChannel
import io.netty.handler.codec.dns.DatagramDnsQuery
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder
import io.netty.handler.codec.dns.DatagramDnsResponse
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder
import io.netty.handler.codec.dns.DefaultDnsRawRecord
import io.netty.handler.codec.dns.DnsQuestion
import io.netty.handler.codec.dns.DnsRecordType
import io.netty.handler.codec.dns.DnsSection
import io.netty.resolver.AddressResolver
import io.netty.resolver.dns.DnsAddressResolverGroup
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import reactor.netty.http.client.HttpClient
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger

@DisplayName("게이트웨이 HTTP 클라이언트의 이름 캐시")
@DisplayNameGeneration(ReplaceUnderscores::class)
@SpringBootTest(properties = [
    "jwt.secret=test-secret-key-for-testing-purposes-only-32chars!",
    "spring.cloud.gateway.routes[0].id=test",
    "spring.cloud.gateway.routes[0].uri=http://localhost:8080",
    "spring.cloud.gateway.routes[0].predicates[0]=Path=/test/**"
])
class GatewayDnsCacheTtlTest {

    @Autowired
    private lateinit var gatewayHttpClient: HttpClient

    private val loops = NioEventLoopGroup(1)
    private val addressQueries = AtomicInteger()
    private lateinit var dnsServer: Channel

    @BeforeEach
    fun startDnsServer() {
        dnsServer = Bootstrap()
            .group(loops)
            .channel(NioDatagramChannel::class.java)
            .handler(object : ChannelInitializer<DatagramChannel>() {
                override fun initChannel(channel: DatagramChannel) {
                    channel.pipeline().addLast(DatagramDnsQueryDecoder(), DatagramDnsResponseEncoder(), LongTtlAnswer())
                }
            })
            .bind(InetSocketAddress(InetAddress.getLoopbackAddress(), 0))
            .sync()
            .channel()
    }

    @AfterEach
    fun stopDnsServer() {
        dnsServer.close().sync()
        loops.shutdownGracefully().sync()
    }

    @Test
    fun 게이트웨이_HTTP_클라이언트는_기본_리졸버_대신_따로_정한_리졸버를_쓴다() {
        assertThat(gatewayHttpClient.configuration().resolver()).isInstanceOf(DnsAddressResolverGroup::class.java)
    }

    @Test
    fun DNS_응답_TTL이_10분이어도_5초_남짓_뒤에는_이름을_다시_조회한다() {
        val dnsAddress = dnsServer.localAddress() as InetSocketAddress
        val group = HttpClient.create()
            .resolver { spec ->
                GatewayHttpClientConfig.limitDnsCacheTtl(spec)
                    .dnsAddressResolverGroupProvider { builder ->
                        DnsAddressResolverGroup(builder.nameServerProvider(SingletonDnsServerAddressStreamProvider(dnsAddress)))
                    }
            }
            .configuration()
            .resolver()!!
        try {
            @Suppress("UNCHECKED_CAST")
            val resolver = group.getResolver(loops.next()) as AddressResolver<InetSocketAddress>
            val backend = InetSocketAddress.createUnresolved("order-service.test", 8084)

            assertThat(resolver.resolve(backend).sync().now.address).isEqualTo(InetAddress.getByAddress(BACKEND_ADDRESS))
            assertThat(addressQueries.get()).isEqualTo(1)

            await()
                .atMost(Duration.ofSeconds(15))
                .pollInterval(Duration.ofMillis(500))
                .until {
                    resolver.resolve(backend).sync()
                    addressQueries.get() >= 2
                }
        } finally {
            group.close()
        }
    }

    private inner class LongTtlAnswer : SimpleChannelInboundHandler<DatagramDnsQuery>() {
        override fun channelRead0(ctx: ChannelHandlerContext, query: DatagramDnsQuery) {
            val question = query.recordAt<DnsQuestion>(DnsSection.QUESTION)
            val response = DatagramDnsResponse(query.recipient(), query.sender(), query.id())
            response.addRecord(DnsSection.QUESTION, question)
            if (question.type() == DnsRecordType.A) {
                addressQueries.incrementAndGet()
                response.addRecord(
                    DnsSection.ANSWER,
                    DefaultDnsRawRecord(question.name(), DnsRecordType.A, DOCKER_DNS_TTL_SECONDS, Unpooled.wrappedBuffer(BACKEND_ADDRESS))
                )
            }
            ctx.writeAndFlush(response)
        }
    }

    companion object {
        private const val DOCKER_DNS_TTL_SECONDS = 600L
        private val BACKEND_ADDRESS = byteArrayOf(127, 0, 0, 1)
    }
}
