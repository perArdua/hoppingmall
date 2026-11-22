package com.hoppingmall.order.port

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.client.HttpClientErrorException
import java.util.function.Predicate

class ReservationKeyConflictPredicate : Predicate<Throwable> {

    override fun test(throwable: Throwable): Boolean =
        throwable is HttpClientErrorException.Conflict && errorCode(throwable) == RESERVATION_REQUEST_KEY_CONFLICT

    private fun errorCode(conflict: HttpClientErrorException.Conflict): String? =
        runCatching { errorBodyReader.readTree(conflict.responseBodyAsString).path("code").textValue() }.getOrNull()

    companion object {
        private const val RESERVATION_REQUEST_KEY_CONFLICT = "INV009"
        private val errorBodyReader = ObjectMapper()
    }
}
