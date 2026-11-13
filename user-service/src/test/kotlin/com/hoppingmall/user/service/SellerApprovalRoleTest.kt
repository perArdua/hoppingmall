package com.hoppingmall.user.service

import com.hoppingmall.user.UserServiceApplicationTests
import com.hoppingmall.user.auth.JwtProperties
import com.hoppingmall.user.auth.TokenProvider
import com.hoppingmall.user.auth.domain.repository.AccessTokenBlacklistRepository
import com.hoppingmall.user.auth.service.AuthServiceImpl
import com.hoppingmall.user.auth.service.RefreshTokenService
import com.hoppingmall.user.common.enums.Role
import com.hoppingmall.user.common.vo.Email
import com.hoppingmall.user.common.vo.PasswordCreator
import com.hoppingmall.user.config.ratelimit.NoOpLoginAttemptLimiter
import com.hoppingmall.user.domain.Seller
import com.hoppingmall.user.domain.User
import com.hoppingmall.user.domain.repository.SellerRepository
import com.hoppingmall.user.domain.repository.UserRepository
import com.hoppingmall.user.dto.request.SellerApprovalRequest
import com.hoppingmall.user.dto.request.SignInRequest
import com.hoppingmall.user.exception.user.UserNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.DisplayNameGeneration
import org.junit.jupiter.api.DisplayNameGenerator.ReplaceUnderscores
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.test.context.TestPropertySource

@SpringBootTest
@Import(UserServiceApplicationTests.TestInfraConfig::class)
@TestPropertySource(properties = [
    "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.redisson.spring.starter.RedissonAutoConfigurationV2,org.redisson.spring.starter.RedissonAutoConfigurationV4,org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
    "spring.main.allow-bean-definition-overriding=true",
    "spring.profiles.active=test"
])
@DisplayName("판매자 승인과 사용자 역할")
@DisplayNameGeneration(ReplaceUnderscores::class)
class SellerApprovalRoleTest {

    @Autowired
    private lateinit var adminCommandService: AdminCommandService

    @Autowired
    private lateinit var userQueryService: UserQueryService

    @Autowired
    private lateinit var userRepository: UserRepository

    @Autowired
    private lateinit var sellerRepository: SellerRepository

    @Autowired
    private lateinit var passwordCreator: PasswordCreator

    @Autowired
    private lateinit var tokenProvider: TokenProvider

    @Autowired
    private lateinit var jwtProperties: JwtProperties

    private lateinit var authService: AuthServiceImpl

    @BeforeEach
    fun setUp() {
        authService = AuthServiceImpl(
            userQueryService = userQueryService,
            tokenProvider = tokenProvider,
            refreshTokenService = mock<RefreshTokenService>(),
            jwtProperties = jwtProperties,
            userRepository = userRepository,
            accessTokenBlacklistRepository = mock<AccessTokenBlacklistRepository>(),
            loginAttemptLimiter = NoOpLoginAttemptLimiter()
        )
    }

    @AfterEach
    fun tearDown() {
        sellerRepository.deleteAll()
        userRepository.deleteAll()
    }

    @Test
    fun 판매자_신청을_승인하면_사용자_역할이_SELLER가_된다() {
        val user = saveUser("approve@example.com", Role.BUYER)
        val seller = sellerRepository.save(Seller.create(user, "111-11-11111"))

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))

        assertThat(sellerRepository.findById(seller.id!!).get().getApprovalStatus())
            .isEqualTo(Seller.ApprovalStatus.APPROVED)
        assertThat(userRepository.findById(user.id!!).get().getRole()).isEqualTo(Role.SELLER)
    }

    @Test
    fun 판매자_신청을_거절하면_사용자_역할은_BUYER로_남는다() {
        val user = saveUser("reject@example.com", Role.BUYER)
        val seller = sellerRepository.save(Seller.create(user, "222-22-22222"))

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("REJECTED"))

        assertThat(sellerRepository.findById(seller.id!!).get().getApprovalStatus())
            .isEqualTo(Seller.ApprovalStatus.REJECTED)
        assertThat(userRepository.findById(user.id!!).get().getRole()).isEqualTo(Role.BUYER)
    }

    @Test
    fun ADMIN의_판매자_신청을_승인해도_ADMIN으로_남는다() {
        val user = saveUser("admin-seller@example.com", Role.ADMIN)
        val seller = sellerRepository.save(Seller.create(user, "333-33-33333"))

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))

        assertThat(userRepository.findById(user.id!!).get().getRole()).isEqualTo(Role.ADMIN)
    }

    @Test
    fun 신청한_사용자가_없으면_승인되지_않는다() {
        val user = saveUser("missing@example.com", Role.BUYER)
        val seller = sellerRepository.save(Seller.create(user, "666-66-66666"))
        userRepository.deleteById(user.id!!)

        assertThatThrownBy {
            adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))
        }.isInstanceOf(UserNotFoundException::class.java)

        assertThat(sellerRepository.findById(seller.id!!).get().getApprovalStatus())
            .isEqualTo(Seller.ApprovalStatus.PENDING)
    }

    @Test
    fun 승인_전에_받은_access_token은_BUYER로_남고_재발급하면_SELLER가_실린다() {
        val user = saveUser("refresh@example.com", Role.BUYER)
        val seller = sellerRepository.save(Seller.create(user, "444-44-44444"))
        val beforeApproval = authService.login(SignInRequest("refresh@example.com", RAW_PASSWORD))

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))
        val refreshed = authService.refreshAccessToken(beforeApproval.refreshToken)

        assertThat(tokenProvider.getUserRoleFromToken(beforeApproval.accessToken)).isEqualTo(Role.BUYER)
        assertThat(tokenProvider.getUserRoleFromToken(refreshed.accessToken)).isEqualTo(Role.SELLER)
    }

    @Test
    fun 승인_뒤_다시_로그인하면_SELLER가_실린다() {
        val user = saveUser("relogin@example.com", Role.BUYER)
        val seller = sellerRepository.save(Seller.create(user, "555-55-55555"))

        adminCommandService.updateSellerApprovalStatus(seller.id!!, SellerApprovalRequest("APPROVED"))
        val afterApproval = authService.login(SignInRequest("relogin@example.com", RAW_PASSWORD))

        assertThat(tokenProvider.getUserRoleFromToken(afterApproval.accessToken)).isEqualTo(Role.SELLER)
    }

    private fun saveUser(email: String, role: Role): User =
        userRepository.save(
            User.create(
                email = Email(email),
                password = passwordCreator.encode(RAW_PASSWORD),
                name = "판매자신청자",
                role = role
            )
        )

    companion object {
        private const val RAW_PASSWORD = "Password1234"
    }
}
