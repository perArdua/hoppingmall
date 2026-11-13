package com.hoppingmall.dlq.reprocess

interface DLQMessageReprocessor {
    val topic: String
    val consumerGroup: String
    fun reprocess(key: String?, value: String?)
}
