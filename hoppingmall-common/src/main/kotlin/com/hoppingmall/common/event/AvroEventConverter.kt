package com.hoppingmall.common.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.springframework.stereotype.Component

@Component
class AvroEventConverter(private val objectMapper: ObjectMapper) {

    private val schemaMap: Map<String, Schema> = mapOf(
        "PaymentCompletedEvent" to PaymentCompletedEvent.getClassSchema(),
        "PaymentFailedEvent" to PaymentFailedEvent.getClassSchema(),
        "PaymentCancelledEvent" to PaymentCancelledEvent.getClassSchema(),
        "PointEarnRequestEvent" to PointEarnRequestEvent.getClassSchema(),
        "MembershipUpdateRequestEvent" to MembershipUpdateRequestEvent.getClassSchema(),
        "NotificationEvent" to NotificationEvent.getClassSchema(),
        "RefundCompletedEvent" to RefundCompletedEvent.getClassSchema(),
        "PaymentCompleted" to PaymentCompletedEvent.getClassSchema(),
        "PaymentFailed" to PaymentFailedEvent.getClassSchema(),
        "PaymentCancelled" to PaymentCancelledEvent.getClassSchema(),
        "PointEarnRequested" to PointEarnRequestEvent.getClassSchema(),
        "MembershipUpdateRequested" to MembershipUpdateRequestEvent.getClassSchema(),
        "RefundCompleted" to RefundCompletedEvent.getClassSchema(),
        "PaymentReversalRequested" to PaymentCancelledEvent.getClassSchema(),
        "PaymentCancellationRequested" to PaymentCancellationRequestedEvent.getClassSchema(),
        "PaymentCancellationCompleted" to PaymentCancellationCompletedEvent.getClassSchema(),
        "PaymentCancellationFailed" to PaymentCancellationFailedEvent.getClassSchema()
    )

    fun convertJsonToAvro(eventType: String, jsonData: String): GenericRecord {
        val schema = schemaMap[eventType]
            ?: if (eventType.endsWith("NotificationRequested")) {
                NotificationEvent.getClassSchema()
            } else {
                throw IllegalArgumentException("Unknown event type: $eventType")
            }
        val jsonMap = objectMapper.readValue(jsonData, Map::class.java)
        return buildGenericRecord(schema, jsonMap)
    }

    private fun buildGenericRecord(schema: Schema, map: Map<*, *>): GenericRecord {
        val record = GenericData.Record(schema)
        for (field in schema.fields) {
            val value = map[field.name()]
            record.put(field.name(), convertValue(field.schema(), value))
        }
        return record
    }

    private fun convertValue(schema: Schema, value: Any?): Any? {
        return when (schema.type) {
            Schema.Type.UNION -> {
                if (value == null) return null
                val nonNullSchema = schema.types.first { it.type != Schema.Type.NULL }
                convertValue(nonNullSchema, value)
            }
            Schema.Type.ENUM -> GenericData.EnumSymbol(schema, value.toString())
            Schema.Type.LONG -> (value as? Number)?.toLong() ?: 0L
            Schema.Type.INT -> (value as? Number)?.toInt() ?: 0
            Schema.Type.BOOLEAN -> value as? Boolean ?: false
            Schema.Type.STRING -> value?.toString() ?: ""
            Schema.Type.ARRAY -> {
                val elementSchema = schema.elementType
                val list = value as? List<*> ?: emptyList<Any>()
                list.map { item ->
                    if (elementSchema.type == Schema.Type.RECORD && item is Map<*, *>) {
                        buildGenericRecord(elementSchema, item)
                    } else {
                        convertValue(elementSchema, item)
                    }
                }
            }
            Schema.Type.RECORD -> {
                if (value is Map<*, *>) buildGenericRecord(schema, value)
                else GenericData.Record(schema)
            }
            else -> value
        }
    }
}
