package com.hoppingmall.mall.category.domain

import com.hoppingmall.mall.support.fixture.fixture
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

@DisplayName("Category")
@DisplayNameGeneration(ReplaceUnderscores::class)
class CategoryTest {

    @Nested
    @DisplayName("create")
    inner class Create {
        @Test
        fun 루트_카테고리를_생성한다() {
            // when
            val category = Category.create(name = "전자제품", parentCategoryId = null, depth = 0)

            // then
            assertEquals("전자제품", category.name)
            assertNull(category.parentCategoryId)
            assertEquals(0, category.depth)
        }

        @Test
        fun 하위_카테고리를_생성한다() {
            // when
            val category = Category.create(name = "노트북", parentCategoryId = 1L, depth = 1)

            // then
            assertEquals("노트북", category.name)
            assertEquals(1L, category.parentCategoryId)
            assertEquals(1, category.depth)
        }
    }

    @Nested
    @DisplayName("update")
    inner class Update {
        @Test
        fun 카테고리_이름을_변경한다() {
            // given
            val category = Category.fixture(name = "전자제품")

            // when
            category.update("가전제품")

            // then
            assertEquals("가전제품", category.name)
        }
    }

    @Nested
    @DisplayName("isRootCategory")
    inner class IsRootCategory {
        @Test
        fun 루트_카테고리이면_true를_반환한다() {
            // given
            val category = Category.fixture(parentCategoryId = null)

            // when & then
            assertTrue(category.isRootCategory())
        }

        @Test
        fun 하위_카테고리이면_false를_반환한다() {
            // given
            val category = Category.fixture(parentCategoryId = 1L, depth = 1)

            // when & then
            assertFalse(category.isRootCategory())
        }
    }
}
