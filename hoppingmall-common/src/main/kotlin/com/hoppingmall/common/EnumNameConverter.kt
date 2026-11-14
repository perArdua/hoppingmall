package com.hoppingmall.common

import jakarta.persistence.AttributeConverter

abstract class EnumNameConverter<E : Enum<E>>(
    private val enumType: Class<E>
) : AttributeConverter<E, String> {

    override fun convertToDatabaseColumn(attribute: E?): String? = attribute?.name

    override fun convertToEntityAttribute(dbData: String?): E? =
        dbData?.let { java.lang.Enum.valueOf(enumType, it) }
}
