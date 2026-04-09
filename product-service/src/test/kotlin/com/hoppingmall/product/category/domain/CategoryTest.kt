package com.hoppingmall.product.category.domain

import com.hoppingmall.product.support.withId
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test

@DisplayName("Category 도메인")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class CategoryTest {

    @Test
    fun 카테고리를_생성한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0)

        assertThat(category.name).isEqualTo("전자기기")
        assertThat(category.parentCategoryId).isNull()
        assertThat(category.depth).isEqualTo(0)
    }

    @Test
    fun 하위_카테고리를_생성한다() {
        val category = Category.create(name = "노트북", parentCategoryId = 1L, depth = 1)

        assertThat(category.name).isEqualTo("노트북")
        assertThat(category.parentCategoryId).isEqualTo(1L)
        assertThat(category.depth).isEqualTo(1)
    }

    @Test
    fun 카테고리_이름을_수정한다() {
        val category = Category.create(name = "전자기기", parentCategoryId = null, depth = 0)

        category.update("가전제품")

        assertThat(category.name).isEqualTo("가전제품")
    }

    @Test
    fun 루트_카테고리인지_확인한다() {
        val root = Category.create(name = "전자기기", parentCategoryId = null, depth = 0)
        val child = Category.create(name = "노트북", parentCategoryId = 1L, depth = 1)

        assertThat(root.isRootCategory()).isTrue()
        assertThat(child.isRootCategory()).isFalse()
    }
}
