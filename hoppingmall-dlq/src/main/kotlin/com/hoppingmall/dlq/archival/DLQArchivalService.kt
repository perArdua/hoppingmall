package com.hoppingmall.dlq.archival

import com.hoppingmall.dlq.domain.DLQMessage

interface DLQArchivalService {
    fun archive(dlqMessage: DLQMessage): Boolean
    fun archiveBatch(dlqMessages: List<DLQMessage>): Int
}
