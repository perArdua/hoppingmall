package com.hoppingmall.mall.global.common.controller

import com.hoppingmall.mall.global.common.domain.DLQMessage
import com.hoppingmall.mall.global.common.domain.DLQStatus
import com.hoppingmall.mall.global.common.response.ApiResponse
import com.hoppingmall.mall.global.common.service.DLQService
import org.springframework.data.domain.Page
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/admin/dlq")
class DLQController(
    private val dlqService: DLQService
) {
    
    @GetMapping("/stats")
    fun getDLQStats(): ApiResponse<Map<String, Any>> {
        val stats = dlqService.getDLQStats()
        return ApiResponse.success(stats)
    }
    
    @PostMapping("/retry/{topic}")
    fun retryDLQMessages(
        @PathVariable topic: String,
        @RequestParam(defaultValue = "50") maxCount: Int
    ): ApiResponse<Map<String, Any>> {
        val result = dlqService.retryDLQMessagesByTopic(topic, maxCount)
        return ApiResponse.success(result)
    }
    
    @PostMapping("/retry/message/{id}")
    fun retryDLQMessage(@PathVariable id: Long): ApiResponse<String> {
        val success = dlqService.retryDLQMessage(id)
        return if (success) {
            ApiResponse.success("DLQ 메시지 재처리 성공: ID $id")
        } else {
            ApiResponse.success("DLQ 메시지 재처리 실패: ID $id")
        }
    }
    
    @DeleteMapping("/clear/{topic}")
    fun clearProcessedDLQMessages(@PathVariable topic: String): ApiResponse<String> {
        val deletedCount = dlqService.clearProcessedDLQMessages(topic)
        return ApiResponse.success("처리 완료된 DLQ 메시지 삭제: $topic (삭제된 메시지: ${deletedCount}개)")
    }
    
    @GetMapping("/messages/{topic}")
    fun getDLQMessages(
        @PathVariable topic: String,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<DLQMessage>> {
        val messages = dlqService.getDLQMessages(topic, page, size)
        return ApiResponse.success(messages)
    }
    
    @GetMapping("/messages")
    fun getDLQMessagesByStatus(
        @RequestParam status: DLQStatus,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<DLQMessage>> {
        val messages = dlqService.getDLQMessagesByStatus(status, page, size)
        return ApiResponse.success(messages)
    }
}
