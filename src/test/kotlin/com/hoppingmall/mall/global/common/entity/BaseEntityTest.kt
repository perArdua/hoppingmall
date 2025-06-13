package com.hoppingmall.mall.global.common.entity

import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository
import org.springframework.beans.factory.annotation.Autowired
import java.time.LocalDateTime

@DataJpaTest
class BaseEntityTest @Autowired constructor(
    val testRepository: TestEntityRepository
) {

    @Test
    fun `BaseEntity 상속한 엔티티는 생성 시 createdAt이 자동으로 설정된다`() {
        val entity = testRepository.save(TestEntity())

        assertThat(entity.createdAt).isNotNull
        assertThat(entity.updatedAt).isNotNull
    }

    @Test
    fun `softDelete 호출 시 deletedAt이 설정된다`() {
        val entity = TestEntity()
        entity.softDelete()

        assertThat(entity.deletedAt).isNotNull
        assertThat(entity.deletedAt).isBeforeOrEqualTo(LocalDateTime.now())
    }
}

@Entity
@Table(name = "test_entity")
class TestEntity : BaseEntity()

@Repository
interface TestEntityRepository : JpaRepository<TestEntity, Long>
