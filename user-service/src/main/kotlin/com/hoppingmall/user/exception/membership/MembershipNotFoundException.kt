package com.hoppingmall.user.exception.membership

import com.hoppingmall.common.BusinessException

class MembershipNotFoundException : BusinessException(MembershipErrorCode.MEMBERSHIP_NOT_FOUND)
