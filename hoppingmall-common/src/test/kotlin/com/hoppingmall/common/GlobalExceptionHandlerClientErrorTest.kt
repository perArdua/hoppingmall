package com.hoppingmall.common

import org.hibernate.exception.ConstraintViolationException
import org.hibernate.exception.ConstraintViolationException.ConstraintKind
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import java.sql.SQLIntegrityConstraintViolationException

@RestController
class ClientErrorTestController(private val jdbcTemplate: JdbcTemplate) {

    @GetMapping("/client-error/argument")
    fun argument(): String = throw IllegalArgumentException("조회 기간은 90일을 넘을 수 없습니다.")

    @GetMapping("/client-error/require")
    fun requirement(@RequestParam("size") size: Int): String {
        require(size > 0) { "size는 1 이상이어야 합니다." }
        return "ok"
    }

    @GetMapping("/client-error/state")
    fun state(): String = check(false) { "이미 끝난 요청입니다." }.let { "ok" }

    @PostMapping("/client-error/duplicate")
    fun duplicate(): String {
        jdbcTemplate.update("INSERT INTO client_error_items (code) VALUES ('A1')")
        jdbcTemplate.update("INSERT INTO client_error_items (code) VALUES ('A1')")
        return "ok"
    }

    @PostMapping("/client-error/not-null")
    fun notNull(): String {
        jdbcTemplate.update("INSERT INTO client_error_items (code) VALUES (NULL)")
        return "ok"
    }

    @PostMapping("/client-error/hibernate-unique")
    fun hibernateUnique(): String = throw DataIntegrityViolationException(
        "could not execute statement",
        ConstraintViolationException(
            "could not execute statement",
            SQLIntegrityConstraintViolationException("Duplicate entry 'buyer@example.com' for key 'users.uk_users_email'", "23000", 1062),
            ConstraintKind.UNIQUE,
            "uk_users_email"
        )
    )

    @PostMapping("/client-error/mysql-duplicate")
    fun mysqlDuplicate(): String = throw DataIntegrityViolationException(
        "could not execute statement",
        SQLIntegrityConstraintViolationException("Duplicate entry '1-2' for key 'user_coupons.uk_user_coupon'", "23000", 1062)
    )

    @PostMapping("/client-error/upload")
    fun upload(@RequestParam("file") file: MultipartFile): String = file.originalFilename.orEmpty()

    @PostMapping("/client-error/upload-part")
    fun uploadPart(@RequestPart("image") image: MultipartFile): String = image.originalFilename.orEmpty()
}

@DisplayName("GlobalExceptionHandler 클라이언트 오류 응답 테스트")
@DisplayNameGeneration(ReplaceUnderscores::class)
class GlobalExceptionHandlerClientErrorTest {

    private val dataSource = DriverManagerDataSource("jdbc:h2:mem:client-error;DB_CLOSE_DELAY=-1", "sa", "")
    private val jdbcTemplate = JdbcTemplate(dataSource)

    private val mockMvc: MockMvc = MockMvcBuilders.standaloneSetup(ClientErrorTestController(jdbcTemplate))
        .setControllerAdvice(GlobalExceptionHandler())
        .build()

    @BeforeEach
    fun setUp() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS client_error_items")
        jdbcTemplate.execute("CREATE TABLE client_error_items (id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(10) NOT NULL UNIQUE)")
    }

    @Test
    fun IllegalArgumentException이면_400과_C001을_응답한다() {
        mockMvc.perform(get("/client-error/argument"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun require_실패도_400과_C001을_응답한다() {
        mockMvc.perform(get("/client-error/require").param("size", "0"))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
    }

    @Test
    fun IllegalStateException이면_409와_C002를_응답한다() {
        mockMvc.perform(get("/client-error/state"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("C002"))
    }

    @Test
    fun 실제_DB의_유일_제약_위반이면_409와_C003을_응답한다() {
        mockMvc.perform(post("/client-error/duplicate"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("C003"))
    }

    @Test
    fun 하이버네이트가_유일_제약으로_분류한_위반이면_409와_C003을_응답한다() {
        mockMvc.perform(post("/client-error/hibernate-unique"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("C003"))
    }

    @Test
    fun MySQL_중복_키_오류_번호면_409와_C003을_응답한다() {
        mockMvc.perform(post("/client-error/mysql-duplicate"))
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("C003"))
    }

    @Test
    fun 유일_제약이_아닌_무결성_위반은_서버_결함이라_500과_S001을_응답한다() {
        mockMvc.perform(post("/client-error/not-null"))
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.code").value("S001"))
    }

    @Test
    fun 파일_파라미터가_빠진_업로드는_400과_C001과_빠진_이름을_응답한다() {
        mockMvc.perform(multipart("/client-error/upload").file(MockMultipartFile("other", "a.csv", "text/csv", "a".toByteArray())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].field").value("file"))
            .andExpect(jsonPath("$.errors[0].reason").value("필수 값입니다."))
    }

    @Test
    fun 요청_파트가_빠진_업로드는_400과_C001과_빠진_이름을_응답한다() {
        mockMvc.perform(multipart("/client-error/upload-part").file(MockMultipartFile("other", "a.png", "image/png", "a".toByteArray())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("C001"))
            .andExpect(jsonPath("$.errors[0].field").value("image"))
    }
}
