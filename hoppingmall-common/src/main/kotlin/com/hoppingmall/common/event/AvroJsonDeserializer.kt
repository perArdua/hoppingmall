package com.hoppingmall.common.event

import io.confluent.kafka.serializers.KafkaAvroDeserializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.serialization.Deserializer

class AvroJsonDeserializer : Deserializer<String> {

    private val avroDeserializer = KafkaAvroDeserializer()

    override fun configure(configs: Map<String, *>, isKey: Boolean) {
        avroDeserializer.configure(configs, isKey)
    }

    override fun deserialize(topic: String, data: ByteArray?): String? {
        if (data == null) return null
        val record = avroDeserializer.deserialize(topic, data) ?: return null
        if (record is GenericRecord) {
            return genericRecordToJson(record)
        }
        return record.toString()
    }

    override fun close() {
        avroDeserializer.close()
    }

    private fun genericRecordToJson(record: GenericRecord): String {
        val map = mutableMapOf<String, Any?>()
        for (field in record.schema.fields) {
            val value = record.get(field.name())
            map[field.name()] = convertAvroValue(value)
        }
        return com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(map)
    }

    private fun convertAvroValue(value: Any?): Any? {
        return when (value) {
            null -> null
            is org.apache.avro.util.Utf8 -> value.toString()
            is GenericRecord -> {
                val map = mutableMapOf<String, Any?>()
                for (field in value.schema.fields) {
                    map[field.name()] = convertAvroValue(value.get(field.name()))
                }
                map
            }
            is org.apache.avro.generic.GenericData.EnumSymbol -> value.toString()
            is List<*> -> value.map { convertAvroValue(it) }
            else -> value
        }
    }
}
