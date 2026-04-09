package com.hoppingmall.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("NotFoundMarker")
@DisplayNameGeneration(ReplaceUnderscores::class)
class NotFoundMarkerTest {

    @Test
    fun INSTANCE는_기본_reason을_가진다() {
        assertThat(NotFoundMarker.INSTANCE.reason).isEqualTo("NOT_FOUND")
    }

    @Test
    fun isNotFound는_NotFoundMarker_인스턴스에_대해_true를_반환한다() {
        assertThat(NotFoundMarker.isNotFound(NotFoundMarker.INSTANCE)).isTrue()
    }

    @Test
    fun isNotFound는_커스텀_NotFoundMarker에_대해_true를_반환한다() {
        assertThat(NotFoundMarker.isNotFound(NotFoundMarker("CUSTOM"))).isTrue()
    }

    @Test
    fun isNotFound는_일반_객체에_대해_false를_반환한다() {
        assertThat(NotFoundMarker.isNotFound("some-string")).isFalse()
    }

    @Test
    fun isNotFound는_null에_대해_false를_반환한다() {
        assertThat(NotFoundMarker.isNotFound(null)).isFalse()
    }

    @Test
    fun 같은_reason을_가진_두_인스턴스는_동등하다() {
        val a = NotFoundMarker("X")
        val b = NotFoundMarker("X")
        assertThat(a).isEqualTo(b)
    }

    @Test
    fun 다른_reason을_가진_두_인스턴스는_동등하지_않다() {
        val a = NotFoundMarker("A")
        val b = NotFoundMarker("B")
        assertThat(a).isNotEqualTo(b)
    }
}
