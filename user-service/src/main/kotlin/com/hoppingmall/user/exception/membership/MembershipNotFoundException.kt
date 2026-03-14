package com.hoppingmall.user.exception.membership

import com.hoppingmall.user.common.BusinessException

class MembershipNotFoundException : BusinessException(MembershipErrorCode.MEMBERSHIP_NOT_FOUND)
