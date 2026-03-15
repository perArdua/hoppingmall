package com.hoppingmall.payment.port

import com.hoppingmall.payment.port.exception.InventoryRestoreFailedException
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.web.client.MockRestServiceServer
import org.springframework.test.web.client.match.MockRestRequestMatchers.method
import org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo
import org.springframework.test.web.client.response.MockRestResponseCreators.withStatus
import org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess
import org.springframework.web.client.RestTemplate

@DisplayName("HttpInventoryCommandAdapter")
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores::class)
class HttpInventoryCommandAdapterTest {

    private val restTemplate = RestTemplate()
    private val server = MockRestServiceServer.createServer(restTemplate)
    private val adapter = HttpInventoryCommandAdapter("http://localhost:8083", restTemplate)

    @Test
    fun 재고_복구_성공_시_정상_완료된다() {
        server.expect(requestTo("http://localhost:8083/internal/api/v1/inventory/10/increase?quantity=5"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withSuccess("", MediaType.APPLICATION_JSON))

        assertDoesNotThrow { adapter.increaseStock(10L, 5) }
        server.verify()
    }

    @Test
    fun 재고_복구_실패_시_InventoryRestoreFailedException을_던진다() {
        server.expect(requestTo("http://localhost:8083/internal/api/v1/inventory/10/increase?quantity=5"))
            .andExpect(method(HttpMethod.POST))
            .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR))

        assertThatThrownBy { adapter.increaseStock(10L, 5) }
            .isInstanceOf(InventoryRestoreFailedException::class.java)
            .hasMessageContaining("productId=10")
            .hasMessageContaining("quantity=5")

        server.verify()
    }
}
