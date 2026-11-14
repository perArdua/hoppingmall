package com.hoppingmall.common.config

import jakarta.servlet.http.HttpServletRequest
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

class GatewayIdentityProof(secret: String) {

    private val expected: ByteArray? =
        secret.takeIf { it.isNotBlank() }?.toByteArray(StandardCharsets.UTF_8)

    val enabled: Boolean
        get() = expected != null

    fun isFromGateway(request: HttpServletRequest): Boolean {
        val expectedProof = expected ?: return true
        val proof = request.getHeader(HEADER) ?: return false
        return MessageDigest.isEqual(proof.toByteArray(StandardCharsets.UTF_8), expectedProof)
    }

    companion object {
        const val HEADER = "x-gateway-identity"
    }
}
