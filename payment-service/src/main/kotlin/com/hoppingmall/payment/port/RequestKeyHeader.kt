package com.hoppingmall.payment.port

import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders

const val REQUEST_KEY_HEADER = "X-Request-Key"

internal fun requestKeyEntity(requestKey: String?): HttpEntity<Void>? {
    if (requestKey == null) return null
    val headers = HttpHeaders()
    headers.set(REQUEST_KEY_HEADER, requestKey)
    return HttpEntity(headers)
}
