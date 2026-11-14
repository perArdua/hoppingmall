package com.hoppingmall.user.config.ratelimit

import jakarta.servlet.http.HttpServletRequest

class ClientIpResolver(
    private val trustedProxyCount: Int = 0
) {

    init {
        require(trustedProxyCount >= 0) { "user.rate-limit.trusted-proxy-count는 0 이상이어야 합니다: $trustedProxyCount" }
    }

    fun resolve(request: HttpServletRequest): String {
        if (trustedProxyCount == 0) {
            return request.remoteAddr
        }
        val forwarded = request.getHeaders(FORWARDED_FOR_HEADER).toList()
            .flatMap { it.split(",") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (forwarded.size < trustedProxyCount) {
            return request.remoteAddr
        }
        return forwarded[forwarded.size - trustedProxyCount]
    }

    companion object {
        private const val FORWARDED_FOR_HEADER = "X-Forwarded-For"
    }
}
