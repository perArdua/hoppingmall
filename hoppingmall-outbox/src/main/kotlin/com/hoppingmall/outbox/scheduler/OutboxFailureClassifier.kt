package com.hoppingmall.outbox.scheduler

import org.apache.kafka.common.InvalidRecordException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.RecordBatchTooLargeException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.RetriableException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.TransactionalIdAuthorizationException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeoutException

object OutboxFailureClassifier {

    enum class Side { BROKER, MESSAGE }

    private const val MAX_CAUSE_DEPTH = 16

    private val messageRejections: List<Class<out Throwable>> = listOf(
        SerializationException::class.java,
        RecordTooLargeException::class.java,
        RecordBatchTooLargeException::class.java,
        InvalidTopicException::class.java,
        AuthorizationException::class.java,
        InvalidRecordException::class.java
    )

    private val producerWideAuthorizationErrors: List<Class<out Throwable>> = listOf(
        ClusterAuthorizationException::class.java,
        TransactionalIdAuthorizationException::class.java
    )

    private val topicMetadataNotYetPropagated: Class<out Throwable> = UnknownTopicOrPartitionException::class.java

    private val brokerUnavailability: List<Class<out Throwable>> = listOf(
        topicMetadataNotYetPropagated,
        RetriableException::class.java,
        TimeoutException::class.java,
        SocketException::class.java,
        SocketTimeoutException::class.java,
        UnknownHostException::class.java
    )

    fun classify(throwable: Throwable): Side {
        val sides = causeChain(throwable).mapNotNull(::sideOf)
        return if (Side.MESSAGE in sides && Side.BROKER !in sides) Side.MESSAGE else Side.BROKER
    }

    private fun sideOf(throwable: Throwable): Side? = when {
        producerWideAuthorizationErrors.any { it.isInstance(throwable) } -> Side.BROKER
        messageRejections.any { it.isInstance(throwable) } -> Side.MESSAGE
        brokerUnavailability.any { it.isInstance(throwable) } -> Side.BROKER
        else -> null
    }

    private fun causeChain(throwable: Throwable): List<Throwable> {
        val chain = mutableListOf<Throwable>()
        var current: Throwable? = throwable
        while (current != null && chain.size < MAX_CAUSE_DEPTH && chain.none { it === current }) {
            chain.add(current)
            current = current.cause
        }
        return chain
    }
}
