plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	kotlin("plugin.jpa") version "1.9.25"
	jacoco
}

group = "com.hoppingmall"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

repositories {
	mavenCentral()
}

dependencies {
	// 기본 Spring Boot 스타터
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	
	// Kotlin 관련
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	// Kafka
	implementation("org.springframework.kafka:spring-kafka")

	// dev tools
	developmentOnly("org.springframework.boot:spring-boot-devtools")

	// MySQL 드라이버
	runtimeOnly("com.mysql:mysql-connector-j")

	// h2
	runtimeOnly("com.h2database:h2")

	// 컴파일 타임 설정 프로세서
	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	// 테스트 관련
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.springframework.security:spring-security-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testImplementation("org.assertj:assertj-core:3.24.2")

	// token provider용
	implementation("io.jsonwebtoken:jjwt-api:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
	runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")

	// Redis
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

allOpen {
	annotation("jakarta.persistence.Entity")
	annotation("jakarta.persistence.MappedSuperclass")
	annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

jacoco {
	toolVersion = "0.8.11"
}

val jacocoExcludedDirs = listOf(
	"**/config/**",
	"**/dto/**",
	"**/MallApplication*",
	"**/com/hoppingmall/mall/MallApplicationTests*",
	"**/com/hoppingmall/mall/global/common/entity/**",
	"**/com/hoppingmall/mall/global/common/response/**",
	"**/com/hoppingmall/mall/global/common/error/**",
	"**/com/hoppingmall/mall/global/enums/**",
	"**/com/hoppingmall/mall/global/vo/**",
	"**/com/hoppingmall/mall/global/jwt/TokenProviderImpl*",
	"**/exception/**",
	"**/com/hoppingmall/mall/payment/service/MockPaymentService*",
	"**/com/hoppingmall/mall/payment/service/KafkaPaymentEventPublisher*",
	"**/com/hoppingmall/mall/global/common/service/OutboxEventService*",
	"**/com/hoppingmall/mall/global/common/repository/OutboxEventRepository*",
	"**/com/hoppingmall/mall/point/service/PointEventConsumer*",
	"**/com/hoppingmall/mall/payment/service/PaymentCompensationConsumer*",
	"**/com/hoppingmall/mall/payment/service/PaymentEventConsumer*",
	"**/com/hoppingmall/mall/membership/service/MembershipEventConsumer*",
	"**/com/hoppingmall/mall/membership/domain/MembershipEventLog*",
	"**/com/hoppingmall/mall/product/service/ProductStatisticsScheduler*",
	"**/com/hoppingmall/mall/product/service/StatisticsEventConsumer*",
	"**/com/hoppingmall/mall/product/domain/StatisticsEventLog*"
)

tasks.jacocoTestReport {
	dependsOn(tasks.test)

	val filteredClassDirs = files(
		classDirectories.files.map {
			fileTree(it) {
				exclude(jacocoExcludedDirs)
			}
		}
	)

	classDirectories.setFrom(filteredClassDirs)

	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.test)

	val filteredClassDirs = files(
		classDirectories.files.map {
			fileTree(it) {
				exclude(jacocoExcludedDirs)
			}
		}
	)

	classDirectories.setFrom(filteredClassDirs)

	violationRules {
		rule {
			element = "CLASS"
			limit {
				counter = "LINE"
				value = "COVEREDRATIO"
				minimum = "0.80".toBigDecimal()
			}
		}
	}
}
