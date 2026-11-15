package com.hoppingmall.common.page

import com.hoppingmall.common.FieldErrorDetail
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.data.domain.Pageable
import org.springframework.web.method.HandlerMethod
import org.springframework.web.servlet.HandlerInterceptor

class PageableLimitInterceptor : HandlerInterceptor {

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (handler !is HandlerMethod || handler.methodParameters.none { Pageable::class.java.isAssignableFrom(it.parameterType) }) {
            return true
        }
        val errors = listOfNotNull(
            check(request, PageRequestLimits.PAGE, 0, Int.MAX_VALUE),
            check(request, PageRequestLimits.SIZE, 1, PageRequestLimits.MAX_PAGE_SIZE)
        )
        if (errors.isNotEmpty()) {
            throw RequestRangeException(errors)
        }
        return true
    }

    private fun check(request: HttpServletRequest, name: String, min: Int, max: Int): FieldErrorDetail? {
        val raw = request.getParameter(name)?.trim()
        if (raw.isNullOrEmpty()) {
            return null
        }
        val value = raw.toIntOrNull() ?: return FieldErrorDetail(name, INVALID_FORMAT)
        return RangeCheck(name, value, min, max).error()
    }

    private companion object {
        const val INVALID_FORMAT = "형식이 올바르지 않습니다."
    }
}
