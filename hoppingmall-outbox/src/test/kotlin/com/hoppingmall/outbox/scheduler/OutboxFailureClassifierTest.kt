package com.hoppingmall.outbox.scheduler

import com.hoppingmall.outbox.scheduler.OutboxFailureClassifier.Side
import org.apache.kafka.common.InvalidRecordException
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.AuthorizationException
import org.apache.kafka.common.errors.ClusterAuthorizationException
import org.apache.kafka.common.errors.DisconnectException
import org.apache.kafka.common.errors.InvalidTopicException
import org.apache.kafka.common.errors.NetworkException
import org.apache.kafka.common.errors.NotEnoughReplicasException
import org.apache.kafka.common.errors.ProducerFencedException
import org.apache.kafka.common.errors.RecordBatchTooLargeException
import org.apache.kafka.common.errors.RecordTooLargeException
import org.apache.kafka.common.errors.SerializationException
import org.apache.kafka.common.errors.TimeoutException
import org.apache.kafka.common.errors.TopicAuthorizationException
import org.apache.kafka.common.errors.TransactionalIdAuthorizationException
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.kafka.KafkaException as SpringKafkaException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ExecutionException

@DisplayName("OutboxFailureClassifier")
@DisplayNameGeneration(ReplaceUnderscores::class)
class OutboxFailureClassifierTest {

    @Test
    fun 프로듀서_생성_실패는_브로커_쪽이다() {
        val failure = KafkaException("Failed to construct kafka producer", ConnectException("Connection refused"))

        assertThat(OutboxFailureClassifier.classify(failure)).isEqualTo(Side.BROKER)
    }

    @Test
    fun 연결과_메타데이터_시간초과는_브로커_쪽이다() {
        listOf(
            TimeoutException("Topic payment not present in metadata after 60000 ms."),
            DisconnectException("disconnected"),
            NetworkException("network"),
            NotEnoughReplicasException("not enough replicas"),
            java.util.concurrent.TimeoutException("future timed out"),
            SocketTimeoutException("read timed out"),
            UnknownHostException("kafka")
        ).forEach { failure ->
            assertThat(OutboxFailureClassifier.classify(failure)).describedAs(failure.toString()).isEqualTo(Side.BROKER)
        }
    }

    @Test
    fun 트랜잭션_시작_커밋_중단_실패는_브로커_쪽이다() {
        listOf(
            KafkaException("TransactionalId tx-1: Invalid transition attempted from state READY to state COMMITTING_TRANSACTION"),
            TimeoutException("Timeout expired after 60000ms while awaiting EndTxn(true)"),
            TimeoutException("Timeout expired after 60000ms while awaiting InitProducerId"),
            ProducerFencedException("fenced")
        ).forEach { failure ->
            assertThat(OutboxFailureClassifier.classify(failure)).describedAs(failure.toString()).isEqualTo(Side.BROKER)
        }
    }

    @Test
    fun 감싼_예외는_원인을_따라가_브로커_쪽으로_판정한다() {
        val sendFailure = ExecutionException(TimeoutException("Expiring 1 record(s)"))
        val springWrapped = SpringKafkaException("Send failed", ExecutionException(DisconnectException("gone")))

        assertThat(OutboxFailureClassifier.classify(sendFailure)).isEqualTo(Side.BROKER)
        assertThat(OutboxFailureClassifier.classify(springWrapped)).isEqualTo(Side.BROKER)
    }

    @Test
    fun 직렬화와_레코드_자체가_거부된_실패는_메시지_쪽이다() {
        listOf(
            SerializationException("Error serializing Avro message"),
            RecordTooLargeException("The message is 2000000 bytes"),
            RecordBatchTooLargeException("batch too large"),
            InvalidTopicException("bad topic"),
            TopicAuthorizationException("not authorized"),
            AuthorizationException("not authorized"),
            InvalidRecordException("invalid record")
        ).forEach { failure ->
            assertThat(OutboxFailureClassifier.classify(failure)).describedAs(failure.toString()).isEqualTo(Side.MESSAGE)
        }
    }

    @Test
    fun 감싼_예외는_원인을_따라가_메시지_쪽으로_판정한다() {
        val sendFailure = ExecutionException(RecordTooLargeException("too large"))
        val springWrapped = SpringKafkaException("Send failed", ExecutionException(SerializationException("bad")))

        assertThat(OutboxFailureClassifier.classify(sendFailure)).isEqualTo(Side.MESSAGE)
        assertThat(OutboxFailureClassifier.classify(springWrapped)).isEqualTo(Side.MESSAGE)
    }

    @Test
    fun 원인에_브로커_쪽_신호가_섞이면_메시지_쪽_예외라도_브로커_쪽이다() {
        val registryDown = SerializationException("Error serializing Avro message", ConnectException("Connection refused"))
        val registryTimeout = SerializationException("Error registering Avro schema", TimeoutException("timeout"))

        assertThat(OutboxFailureClassifier.classify(registryDown)).isEqualTo(Side.BROKER)
        assertThat(OutboxFailureClassifier.classify(registryTimeout)).isEqualTo(Side.BROKER)
    }

    @Test
    fun 프로듀서_전체를_막는_권한_실패는_한_메시지_탓이_아니므로_브로커_쪽이다() {
        listOf(
            ClusterAuthorizationException("Cluster authorization failed."),
            TransactionalIdAuthorizationException("Transactional Id authorization failed."),
            KafkaException("Send failed", TransactionalIdAuthorizationException("Transactional Id authorization failed."))
        ).forEach { failure ->
            assertThat(OutboxFailureClassifier.classify(failure)).describedAs(failure.toString()).isEqualTo(Side.BROKER)
        }
    }

    @Test
    fun 토픽을_못_찾는_실패는_메타데이터_전파_지연으로_보고_브로커_쪽이다() {
        assertThat(OutboxFailureClassifier.classify(UnknownTopicOrPartitionException("unknown")))
            .isEqualTo(Side.BROKER)
    }

    @Test
    fun 알_수_없는_예외는_브로커_쪽으로_본다() {
        listOf(
            RuntimeException("boom"),
            IllegalStateException("unexpected"),
            RuntimeException()
        ).forEach { failure ->
            assertThat(OutboxFailureClassifier.classify(failure)).describedAs(failure.toString()).isEqualTo(Side.BROKER)
        }
    }

    @Test
    fun 원인이_순환해도_멈추고_판정한다() {
        val outer = RuntimeException("outer")
        val inner = RecordTooLargeException("inner")
        outer.initCause(inner)
        inner.initCause(outer)

        assertThat(OutboxFailureClassifier.classify(outer)).isEqualTo(Side.MESSAGE)
    }

    @Test
    fun 원인이_아주_깊으면_정해진_깊이까지만_본다() {
        var failure: Throwable = RecordTooLargeException("deep")
        repeat(20) { failure = RuntimeException("wrap $it", failure) }

        assertThat(OutboxFailureClassifier.classify(failure)).isEqualTo(Side.BROKER)
    }
}
