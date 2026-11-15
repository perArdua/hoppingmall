package com.hoppingmall.common

import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.exc.InvalidFormatException
import com.fasterxml.jackson.databind.exc.InvalidNullException
import com.fasterxml.jackson.databind.exc.MismatchedInputException
import org.hibernate.exception.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.core.MethodParameter
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.dao.DuplicateKeyException
import org.springframework.data.mapping.PropertyReferenceException
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.validation.method.ParameterErrors
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingRequestHeaderException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.multipart.MaxUploadSizeExceededException
import org.springframework.web.multipart.support.MissingServletRequestPartException
import org.springframework.web.servlet.NoHandlerFoundException
import org.springframework.web.servlet.resource.NoResourceFoundException
import java.sql.SQLException

@RestControllerAdvice
class GlobalExceptionHandler(
    private val objectMapper: ObjectMapper? = null
) {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(ex: BusinessException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("BusinessException: code={}, message={}", ex.errorCode.code, ex.errorCode.message)
        val status = ex.errorCode.status
        return ResponseEntity.status(status)
            .body(ApiResponse.failure(ex.errorCode))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException): ResponseEntity<ApiResponse<Unit>> {
        val jsonBody = ex.parameter.hasParameterAnnotation(RequestBody::class.java)
        val errors = ex.bindingResult.fieldErrors.map { toDetail(it, jsonBody, "") }
        log.warn("ValidationException: {}", errors)
        return invalidInput(errors)
    }

    @ExceptionHandler(HandlerMethodValidationException::class)
    fun handleMethodValidationException(ex: HandlerMethodValidationException): ResponseEntity<ApiResponse<Unit>> {
        if (ex.isForReturnValue) {
            return handleException(ex)
        }
        val errors = ex.parameterValidationResults.flatMap { result ->
            val parameter = result.methodParameter
            if (result is ParameterErrors) {
                val prefix = (result.containerIndex ?: result.containerKey)?.let { "[$it]" } ?: ""
                val jsonBody = parameter.hasParameterAnnotation(RequestBody::class.java)
                result.fieldErrors.map { toDetail(it, jsonBody, prefix) }
            } else {
                val name = requestParameterName(parameter)
                result.resolvableErrors.map { FieldErrorDetail(name, it.defaultMessage ?: INVALID_VALUE) }
            }
        }
        log.warn("MethodValidationException: {}", errors)
        return invalidInput(errors)
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleUnreadableBody(ex: HttpMessageNotReadableException): ResponseEntity<ApiResponse<Unit>> {
        val mapping = generateSequence(ex.cause) { it.cause }.filterIsInstance<JsonMappingException>().firstOrNull()
        val field = mapping?.let { jsonPath(it.path) }.orEmpty()
        val errors = if (field.isEmpty()) emptyList() else listOf(FieldErrorDetail(field, unreadableReason(mapping)))
        log.warn("UnreadableBody: type={}, fields={}", (mapping ?: ex.cause ?: ex).javaClass.simpleName, errors.map { it.field })
        return invalidInput(errors)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameter(ex: MissingServletRequestParameterException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("MissingParameter: {}", ex.parameterName)
        return invalidInput(listOf(FieldErrorDetail(ex.parameterName, REQUIRED)))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("TypeMismatch: {}", ex.name)
        return invalidInput(listOf(FieldErrorDetail(ex.name, INVALID_FORMAT)))
    }

    @ExceptionHandler(MissingServletRequestPartException::class)
    fun handleMissingPart(ex: MissingServletRequestPartException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("MissingRequestPart: {}", ex.requestPartName)
        return invalidInput(listOf(FieldErrorDetail(ex.requestPartName, REQUIRED)))
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("IllegalArgument: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }

    @ExceptionHandler(IllegalStateException::class)
    fun handleIllegalState(ex: IllegalStateException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("IllegalState: {}", ex.message, ex)
        return ResponseEntity.status(CommonErrorCode.INVALID_STATE.status)
            .body(ApiResponse.failure(CommonErrorCode.INVALID_STATE))
    }

    @ExceptionHandler(DataIntegrityViolationException::class)
    fun handleDataIntegrityViolation(ex: DataIntegrityViolationException): ResponseEntity<ApiResponse<Unit>> {
        if (!isUniqueViolation(ex)) {
            return handleException(ex)
        }
        log.warn("DuplicateResource: constraint={}", constraintName(ex))
        return ResponseEntity.status(CommonErrorCode.DUPLICATE_RESOURCE.status)
            .body(ApiResponse.failure(CommonErrorCode.DUPLICATE_RESOURCE))
    }

    @ExceptionHandler(MissingRequestHeaderException::class)
    fun handleBadRequest(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.warn("BadRequest: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }

    @ExceptionHandler(PropertyReferenceException::class)
    fun handleInvalidSortProperty(ex: PropertyReferenceException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("InvalidSortProperty: {}", ex.message)
        return ResponseEntity.badRequest().body(ApiResponse.failure(CommonErrorCode.INVALID_INPUT))
    }

    @ExceptionHandler(NoResourceFoundException::class, NoHandlerFoundException::class)
    fun handleNotFound(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.debug("NotFound: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.RESOURCE_NOT_FOUND.status)
            .body(ApiResponse.failure(CommonErrorCode.RESOURCE_NOT_FOUND))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotAllowed(ex: HttpRequestMethodNotSupportedException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("MethodNotAllowed: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.METHOD_NOT_ALLOWED.status)
            .headers(ex.headers)
            .body(ApiResponse.failure(CommonErrorCode.METHOD_NOT_ALLOWED))
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException::class)
    fun handleUnsupportedMediaType(ex: HttpMediaTypeNotSupportedException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("UnsupportedMediaType: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.status)
            .headers(ex.headers)
            .body(ApiResponse.failure(CommonErrorCode.UNSUPPORTED_MEDIA_TYPE))
    }

    @ExceptionHandler(MaxUploadSizeExceededException::class)
    fun handlePayloadTooLarge(ex: MaxUploadSizeExceededException): ResponseEntity<ApiResponse<Unit>> {
        log.warn("PayloadTooLarge: {}", ex.message)
        return ResponseEntity.status(CommonErrorCode.PAYLOAD_TOO_LARGE.status)
            .body(ApiResponse.failure(CommonErrorCode.PAYLOAD_TOO_LARGE))
    }

    @ExceptionHandler(Exception::class)
    fun handleException(ex: Exception): ResponseEntity<ApiResponse<Unit>> {
        log.error("UnhandledException: {}", ex.message, ex)
        return ResponseEntity.status(CommonErrorCode.INTERNAL_ERROR.status)
            .body(ApiResponse.failure(CommonErrorCode.INTERNAL_ERROR))
    }

    private fun invalidInput(errors: List<FieldErrorDetail>): ResponseEntity<ApiResponse<Unit>> {
        val sorted = errors.sortedWith(compareBy({ it.field }, { it.reason }))
        return ResponseEntity.badRequest().body(
            ApiResponse(
                code = CommonErrorCode.INVALID_INPUT.code,
                message = CommonErrorCode.INVALID_INPUT.message,
                errors = sorted.ifEmpty { null }
            )
        )
    }

    private fun isUniqueViolation(ex: DataIntegrityViolationException): Boolean =
        ex is DuplicateKeyException || causes(ex).any { cause ->
            when (cause) {
                is ConstraintViolationException -> cause.kind == ConstraintViolationException.ConstraintKind.UNIQUE
                is SQLException -> cause.errorCode == MYSQL_DUPLICATE_ENTRY || cause.sqlState == SQL_STATE_UNIQUE_VIOLATION
                else -> false
            }
        }

    private fun constraintName(ex: DataIntegrityViolationException): String =
        causes(ex).filterIsInstance<ConstraintViolationException>().firstNotNullOfOrNull { it.constraintName } ?: UNKNOWN

    private fun causes(ex: Throwable): Sequence<Throwable> =
        generateSequence(ex) { current -> current.cause?.takeIf { it !== current } }.take(MAX_CAUSE_DEPTH)

    private fun toDetail(error: FieldError, jsonBody: Boolean, prefix: String): FieldErrorDetail {
        val field = if (jsonBody) jsonName(error.field) else error.field
        val path = when {
            prefix.isEmpty() -> field
            field.isEmpty() -> prefix
            else -> "$prefix.$field"
        }
        val reason = if (error.isBindingFailure) INVALID_FORMAT else error.defaultMessage ?: INVALID_VALUE
        return FieldErrorDetail(path, reason)
    }

    private fun jsonName(propertyPath: String): String {
        val strategy = objectMapper?.propertyNamingStrategy as? PropertyNamingStrategies.NamingBase
            ?: return propertyPath
        return propertyPath.split('.').joinToString(".") { segment ->
            val bracket = segment.indexOf('[')
            if (bracket < 0) strategy.translate(segment)
            else strategy.translate(segment.substring(0, bracket)) + segment.substring(bracket)
        }
    }

    private fun jsonPath(path: List<JsonMappingException.Reference>): String =
        path.fold("") { acc, reference ->
            val fieldName = reference.fieldName
            when {
                fieldName != null -> if (acc.isEmpty()) fieldName else "$acc.$fieldName"
                reference.index >= 0 -> "$acc[${reference.index}]"
                else -> acc
            }
        }

    private fun unreadableReason(mapping: JsonMappingException?): String = when (mapping) {
        is InvalidNullException -> REQUIRED
        is InvalidFormatException -> INVALID_FORMAT
        is MismatchedInputException -> MISSING_OR_INVALID
        else -> INVALID_VALUE
    }

    private fun requestParameterName(parameter: MethodParameter): String {
        val declared = parameter.getParameterAnnotation(RequestParam::class.java)?.let { it.name.ifEmpty { it.value } }
            ?: parameter.getParameterAnnotation(PathVariable::class.java)?.let { it.name.ifEmpty { it.value } }
            ?: parameter.getParameterAnnotation(RequestHeader::class.java)?.let { it.name.ifEmpty { it.value } }
        return declared?.ifEmpty { null } ?: parameter.parameterName.orEmpty()
    }

    private companion object {
        const val REQUIRED = "필수 값입니다."
        const val INVALID_FORMAT = "형식이 올바르지 않습니다."
        const val MISSING_OR_INVALID = "값이 없거나 형식이 올바르지 않습니다."
        const val INVALID_VALUE = "올바르지 않은 값입니다."
        const val UNKNOWN = "unknown"
        const val MYSQL_DUPLICATE_ENTRY = 1062
        const val SQL_STATE_UNIQUE_VIOLATION = "23505"
        const val MAX_CAUSE_DEPTH = 16
    }
}
