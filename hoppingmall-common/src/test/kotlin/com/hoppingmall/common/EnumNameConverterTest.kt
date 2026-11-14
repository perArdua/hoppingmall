package com.hoppingmall.common

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test

@DisplayName("EnumNameConverter 단위 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class EnumNameConverterTest {

    private enum class Sample { FIRST, SECOND }

    private class SampleConverter : EnumNameConverter<Sample>(Sample::class.java)

    private val converter = SampleConverter()

    @Test
    fun enum_값은_이름_문자열로_저장한다() {
        assertThat(converter.convertToDatabaseColumn(Sample.SECOND)).isEqualTo("SECOND")
    }

    @Test
    fun 저장된_이름_문자열은_같은_이름의_enum_값으로_읽는다() {
        assertThat(converter.convertToEntityAttribute("FIRST")).isEqualTo(Sample.FIRST)
    }

    @Test
    fun null은_양쪽_모두_null로_둔다() {
        assertThat(converter.convertToDatabaseColumn(null)).isNull()
        assertThat(converter.convertToEntityAttribute(null)).isNull()
    }

    @Test
    fun enum에_없는_이름을_읽으면_예외가_난다() {
        assertThatThrownBy { converter.convertToEntityAttribute("THIRD") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }
}
