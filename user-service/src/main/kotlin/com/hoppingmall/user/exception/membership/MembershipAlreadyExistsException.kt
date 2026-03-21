package com.hoppingmall.user.exception.membership

import com.hoppingmall.common.BusinessException

class MembershipAlreadyExistsException : BusinessException(MembershipErrorCode.MEMBERSHIP_ALREADY_EXISTS)
