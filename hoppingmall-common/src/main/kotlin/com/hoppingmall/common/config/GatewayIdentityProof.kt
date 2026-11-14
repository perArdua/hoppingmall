package com.hoppingmall.common.config

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class GatewayIdentityProof(
    secret: String,
    // gateway.identity.previous-secret(환경 변수 GATEWAY_IDENTITY_PREVIOUS_SECRET, 선택): 회전하는 동안만 받는 이전 값.
    // 회전 순서: 서비스에 새 값을 지금 값(GATEWAY_IDENTITY_SECRET), 옛 값을 이전 값으로 넣고 재시작
    // → 게이트웨이를 새 값으로 재시작(게이트웨이는 지금 값만 붙인다) → 서비스에서 이전 값을 빼고 재시작.
    previousSecret: String = ""
) {

    private val expected: ByteArray? = secret.toProof()

    private val accepted: List<ByteArray> =
        if (expected == null) emptyList() else listOfNotNull(expected, previousSecret.toProof())

    val enabled: Boolean
        get() = expected != null

    fun isFromGateway(request: HttpServletRequest): Boolean {
        if (expected == null) return true
        val proof = request.getHeader(HEADER)?.toByteArray(StandardCharsets.UTF_8) ?: return false
        return accepted.fold(false) { matched, candidate -> MessageDigest.isEqual(proof, candidate) || matched }
    }

    private fun String.toProof(): ByteArray? = takeIf { it.isNotBlank() }?.toByteArray(StandardCharsets.UTF_8)

    companion object {
        const val HEADER = "x-gateway-identity"
    }
}
