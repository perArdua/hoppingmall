package com.hoppingmall.product.common.file

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("FileStorageProperties")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class FileStoragePropertiesTest {

    private val properties = FileStorageProperties().apply { publicBaseUrl = "https://img.example.com/b/" }

    @Test
    fun 객체_키로_공개_주소를_만든다() {
        assertThat(properties.publicUrl("product/images/a.jpg")).isEqualTo("https://img.example.com/b/product/images/a.jpg")
        assertThat(properties.defaultImageUrl).isEqualTo("https://img.example.com/b/product/images/default-product.jpg")
    }

    @Test
    fun 공개_주소에서_객체_키를_꺼낸다() {
        assertThat(properties.keyOf("https://img.example.com/b/product/images/a.jpg")).isEqualTo("product/images/a.jpg")
    }

    @Test
    fun 저장소_주소가_아니면_키가_없다() {
        assertThat(properties.keyOf("https://evil.example.com/b/product/images/a.jpg")).isNull()
        assertThat(properties.keyOf("https://img.example.com/b/")).isNull()
        assertThat(properties.keyOf("/tmp/hoppingmall/product/images/a.jpg")).isNull()
    }
}
