package com.hoppingmall.common.sort

import com.hoppingmall.common.GlobalExceptionHandler
import jakarta.persistence.Entity
import jakarta.persistence.Id
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.web.PageableHandlerMethodArgumentResolver
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@SpringBootApplication
class SortErrorTestApplication

@Entity
class SortErrorTestItem(
    @Id
    var id: Long = 0,
    var ownerId: Long = 0,
    var name: String = ""
)

interface SortErrorTestItemRepository : JpaRepository<SortErrorTestItem, Long> {
    fun findBy(pageable: Pageable): Slice<SortErrorTestItem>

    fun findByOwnerId(ownerId: Long, pageable: Pageable): Slice<SortErrorTestItem>
}

@RestController
class SortErrorTestController(private val repository: SortErrorTestItemRepository) {
    @GetMapping("/sort-error/items")
    fun items(pageable: Pageable): List<Long> = repository.findBy(pageable).content.map { it.id }

    @GetMapping("/sort-error/owners/{ownerId}/items")
    fun ownerItems(@PathVariable("ownerId") ownerId: Long, pageable: Pageable): List<Long> =
        repository.findByOwnerId(ownerId, pageable).content.map { it.id }
}

@DataJpaTest
@DisplayName("GlobalExceptionHandler 정렬 속성 오류 응답 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerSortErrorTest {

    @Autowired
    private lateinit var repository: SortErrorTestItemRepository

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp() {
        repository.saveAll(
            listOf(
                SortErrorTestItem(id = 1, ownerId = 7, name = "a"),
                SortErrorTestItem(id = 2, ownerId = 7, name = "b")
            )
        )
        mockMvc = MockMvcBuilders.standaloneSetup(SortErrorTestController(repository))
            .setCustomArgumentResolvers(PageableHandlerMethodArgumentResolver())
            .setControllerAdvice(GlobalExceptionHandler())
            .build()
    }

    @Test
    fun 목록_조회에서_없는_정렬_속성이면_400과_C001을_응답한다() {
        mockMvc.perform(get("/sort-error/items").param("sort", "foo"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 조건_조회에서_없는_정렬_속성이면_400과_C001을_응답한다() {
        mockMvc.perform(get("/sort-error/owners/7/items").param("sort", "foo,desc"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun 있는_정렬_속성이면_그대로_정렬해_200을_응답한다() {
        mockMvc.perform(get("/sort-error/owners/7/items").param("sort", "name,desc"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0]").value(2))
            .andExpect(jsonPath("$[1]").value(1))
    }
}
