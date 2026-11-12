package com.hoppingmall.product.inventory.service

import com.hoppingmall.product.inventory.exception.InventoryBudgetExceededException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatCode
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

@DisplayName("ReserveDeadline")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class ReserveDeadlineTest {

    @AfterEach
    fun tearDown() {
        ReserveDeadline.clear()
    }

    @Test
    fun 기한까지_여유가_200ms보다_많이_남았으면_통과한다() {
        ReserveDeadline.set(DEADLINE)

        assertThatCode { ReserveDeadline.check(DEADLINE - 250) }.doesNotThrowAnyException()
        assertThatCode { ReserveDeadline.check(DEADLINE - 200) }.doesNotThrowAnyException()
    }

    @Test
    fun 기한까지_여유가_200ms보다_적게_남았으면_INV006_예외를_던진다() {
        ReserveDeadline.set(DEADLINE)

        assertThatThrownBy { ReserveDeadline.check(DEADLINE - 150) }
            .isInstanceOfSatisfying(InventoryBudgetExceededException::class.java) {
                assertThat(it.errorCode.code).isEqualTo("INV006")
                assertThat(it.errorCode.status).isEqualTo(HttpStatus.TOO_MANY_REQUESTS)
            }
        assertThatThrownBy { ReserveDeadline.check(DEADLINE - 199) }
            .isInstanceOf(InventoryBudgetExceededException::class.java)
        assertThatThrownBy { ReserveDeadline.check(DEADLINE + 1) }
            .isInstanceOf(InventoryBudgetExceededException::class.java)
    }

    @Test
    fun 기한이_없으면_check는_아무것도_하지_않는다() {
        assertThat(ReserveDeadline.current()).isNull()
        assertThatCode { ReserveDeadline.check(Long.MAX_VALUE) }.doesNotThrowAnyException()
        assertThatCode { ReserveDeadline.check() }.doesNotThrowAnyException()
    }

    @Test
    fun clear_뒤에는_기한이_없다() {
        ReserveDeadline.set(DEADLINE)
        assertThat(ReserveDeadline.current()).isEqualTo(DEADLINE)

        ReserveDeadline.clear()

        assertThat(ReserveDeadline.current()).isNull()
        assertThatCode { ReserveDeadline.check(DEADLINE + 1) }.doesNotThrowAnyException()
    }

    @Test
    fun 기한은_설정한_스레드에서만_보인다() {
        ReserveDeadline.set(DEADLINE)
        val other = Executors.newSingleThreadExecutor()

        val seenByOther = AtomicReference<Long?>(DEADLINE)

        other.execute { seenByOther.set(ReserveDeadline.current()) }
        other.shutdown()

        assertThat(other.awaitTermination(10, TimeUnit.SECONDS)).isTrue()
        assertThat(seenByOther.get()).isNull()
        assertThat(ReserveDeadline.current()).isEqualTo(DEADLINE)
    }

    @Test
    fun 헤더_이름과_여유_시간은_정해진_값이다() {
        assertThat(ReserveDeadline.HEADER).isEqualTo("X-Reserve-Budget-Ms")
        assertThat(ReserveDeadline.MARGIN_MS).isEqualTo(200L)
        assertThat(ReserveDeadline.EXPIRY_GRACE_MS).isEqualTo(10_000L)
    }

    companion object {
        private const val DEADLINE = 1_800_000_000_000L
    }
}
