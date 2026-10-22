package com.hoppingmall.consistency.dto

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

data class ResolveViolationRequest(
    @field:NotBlank
    @field:Size(max = 64)
    val ackedBy: String = "",
    @field:NotBlank
    @field:Size(max = 500)
    val resolutionNote: String = ""
)
