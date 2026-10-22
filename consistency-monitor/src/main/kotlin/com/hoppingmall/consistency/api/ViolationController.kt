package com.hoppingmall.consistency.api

import com.hoppingmall.consistency.dto.ResolveViolationRequest
import com.hoppingmall.consistency.violation.Violation
import com.hoppingmall.consistency.violation.ViolationRepository
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/violations")
class ViolationController(private val violationRepository: ViolationRepository) {

    @GetMapping
    fun list(@RequestParam(defaultValue = "true") openOnly: Boolean): List<Violation> =
        violationRepository.findAll(openOnly)

    @PostMapping("/{id}/resolve")
    fun resolve(
        @PathVariable id: Long,
        @Valid @RequestBody request: ResolveViolationRequest
    ): ResponseEntity<Violation> {
        val violation = violationRepository.findById(id) ?: return ResponseEntity.notFound().build()
        if (violation.resolvedAt != null) return ResponseEntity.status(HttpStatus.CONFLICT).body(violation)
        violationRepository.resolve(id, violationRepository.now(), request.ackedBy, request.resolutionNote)
        return ResponseEntity.ok(violationRepository.findById(id))
    }
}
