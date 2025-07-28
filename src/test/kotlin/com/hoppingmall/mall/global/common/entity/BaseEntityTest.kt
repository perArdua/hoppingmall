package com.hoppingmall.mall.global.common.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@DataJpaTest
@DisplayName("BaseEntity")
@DisplayNameGeneration(ReplaceUnderscores::class)
class BaseEntityTest @Autowired constructor(
    val testRepository: TestEntityRepository
) {

    @Nested
    @DisplayName("생성")
    inner class Creation {
        @Test
        fun BaseEntity_상속한_엔티티는_생성_시_createdAt이_자동으로_설정된다() {
            val entity = testRepository.save(TestEntity())

            assertThat(entity.createdAt).isNotNull
            assertThat(entity.updatedAt).isNotNull
        }
    }

    @Nested
    @DisplayName("softDelete")
    inner class SoftDelete {
        @Test
        fun softDelete_호출_시_deletedAt이_설정된다() {
            val entity = TestEntity()
            entity.softDelete()

            assertThat(entity.deletedAt).isNotNull
            assertThat(entity.deletedAt).isBeforeOrEqualTo(LocalDateTime.now())
        }
    }
}

@Entity
@Table(name = "test_entity")
class TestEntity : BaseEntity()

@Repository
interface TestEntityRepository : JpaRepository<TestEntity, Long>
