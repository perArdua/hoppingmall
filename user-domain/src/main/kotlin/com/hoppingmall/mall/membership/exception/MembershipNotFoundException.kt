package com.hoppingmall.mall.membership.exception

import com.hoppingmall.mall.global.common.error.exception.BusinessException
import com.hoppingmall.mall.membership.exception.code.MembershipErrorCode

class MembershipNotFoundException : BusinessException(MembershipErrorCode.MEMBERSHIP_NOT_FOUND)
