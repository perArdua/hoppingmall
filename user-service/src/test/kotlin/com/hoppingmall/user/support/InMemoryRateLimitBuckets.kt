package com.hoppingmall.user.support

import io.github.bucket4j.Bucket
import io.github.bucket4j.BucketConfiguration
import io.github.bucket4j.distributed.BucketProxy
import io.github.bucket4j.distributed.proxy.ProxyManager
import io.github.bucket4j.distributed.proxy.RemoteBucketBuilder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Supplier

class InMemoryRateLimitBuckets {

    private val buckets = ConcurrentHashMap<String, Bucket>()

    val keys: Set<String>
        get() = buckets.keys

    fun proxyManager(): ProxyManager<String> {
        val builder = mock<RemoteBucketBuilder<String>> {
            on { build(any<String>(), any<Supplier<BucketConfiguration>>()) } doAnswer { invocation ->
                val key = invocation.getArgument<String>(0)
                val configuration = invocation.getArgument<Supplier<BucketConfiguration>>(1)
                val bucket = buckets.computeIfAbsent(key) { localBucket(configuration.get()) }
                mock<BucketProxy> {
                    on { tryConsumeAndReturnRemaining(any()) } doAnswer { bucket.tryConsumeAndReturnRemaining(it.getArgument(0)) }
                }
            }
        }
        return mock { on { builder() } doReturn builder }
    }

    private fun localBucket(configuration: BucketConfiguration): Bucket {
        val builder = Bucket.builder()
        configuration.bandwidths.forEach { builder.addLimit(it) }
        return builder.build()
    }
}
