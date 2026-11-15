package com.hoppingmall.user.auth.domain

import com.hoppingmall.common.EnumNameConverter

enum class SessionRevokeReason {
    LOGOUT,
    PASSWORD_CHANGED,
    REUSE_DETECTED,
    EVICTED
}

class SessionRevokeReasonConverter : EnumNameConverter<SessionRevokeReason>(SessionRevokeReason::class.java)
