package com.hoppingmall.idempotency

import org.springframework.aop.support.AopUtils
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.config.BeanPostProcessor
import org.springframework.core.annotation.AnnotatedElementUtils
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.util.ClassUtils
import org.springframework.util.ReflectionUtils

@Component
class IdempotentReturnTypeVerifier : BeanPostProcessor {

    override fun postProcessBeforeInitialization(bean: Any, beanName: String): Any {
        val targetClass = ClassUtils.getUserClass(AopUtils.getTargetClass(bean))
        if (!targetClass.name.startsWith(APPLICATION_PACKAGE_PREFIX)) {
            return bean
        }
        val invalid = ReflectionUtils.getUniqueDeclaredMethods(targetClass)
            .filter { AnnotatedElementUtils.hasAnnotation(it, Idempotent::class.java) }
            .filterNot { ResponseEntity::class.java.isAssignableFrom(it.returnType) }
            .map { "${targetClass.simpleName}.${it.name}" }
        if (invalid.isNotEmpty()) {
            throw BeanInitializationException(
                "@Idempotent 메서드는 응답을 저장·재생하도록 ResponseEntity를 돌려줘야 합니다: ${invalid.joinToString()}"
            )
        }
        return bean
    }

    private companion object {
        const val APPLICATION_PACKAGE_PREFIX = "com.hoppingmall."
    }
}
