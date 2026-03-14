package com.hoppingmall.user.exception.membership

import com.hoppingmall.user.common.BusinessException

class MembershipAlreadyExistsException : BusinessException(MembershipErrorCode.MEMBERSHIP_ALREADY_EXISTS)
