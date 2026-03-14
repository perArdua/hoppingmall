plugins {
	kotlin("jvm")
	kotlin("plugin.spring")
	kotlin("plugin.jpa")
	id("org.springframework.boot")
	id("io.spring.dependency-management")
	jacoco
}

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

kotlin {
	compilerOptions {
		freeCompilerArgs.addAll("-Xjsr305=strict")
	}
}

configurations {
	compileOnly {
		extendsFrom(configurations.annotationProcessor.get())
	}
}

dependencies {
	implementation(project(":shared"))
	implementation(project(":infra"))
	implementation(project(":product-api"))
	implementation(project(":order-api"))
	implementation(project(":payment-api"))
	implementation(project(":user-api"))
	implementation(project(":user-domain"))
	implementation(project(":product-domain"))
	implementation(project(":order-domain"))
	implementation(project(":payment-domain"))
	implementation(project(":notification-domain"))
	implementation(project(":settlement-domain"))

	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-actuator")

	implementation("org.springframework.kafka:spring-kafka")

	developmentOnly("org.springframework.boot:spring-boot-devtools")

	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("com.h2database:h2")

	annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

	testImplementation(testFixtures(project(":shared")))
	testImplementation(testFixtures(project(":user-domain")))
	testImplementation(testFixtures(project(":product-domain")))
	testImplementation(testFixtures(project(":order-domain")))
	testImplementation(testFixtures(project(":payment-domain")))
	testImplementation(testFixtures(project(":settlement-domain")))
	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testImplementation("org.assertj:assertj-core:3.24.2")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.springframework.security:spring-security-test")

	implementation("org.springframework.boot:spring-boot-starter-data-redis") {
		exclude(group = "io.lettuce", module = "lettuce-core")
	}
	implementation("org.redisson:redisson-spring-boot-starter:4.3.0")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")

	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-brave")
	implementation("io.zipkin.reporter2:zipkin-reporter-brave")

	implementation("org.apache.commons:commons-csv:1.12.0")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

	implementation("net.logstash.logback:logstash-logback-encoder:8.0")
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
	"**/com/hoppingmall/mall/global/jwt/**",
	"**/exception/**",
	"**/com/hoppingmall/mall/global/common/service/OutboxEventService*",
	"**/com/hoppingmall/mall/global/common/repository/OutboxEventRepository*",
	"**/com/hoppingmall/mall/refund/service/RefundCompletionConsumer*",
	"**/com/hoppingmall/mall/global/adapter/**",
	"**/com/hoppingmall/mall/*/controller/Internal*"
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
