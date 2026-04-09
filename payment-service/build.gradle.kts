plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.google.protobuf") version "0.9.4"
	jacoco
}

group = "com.hoppingmall"
version = "0.0.1-SNAPSHOT"

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

repositories {
	mavenCentral()
	maven { url = uri("https://packages.confluent.io/maven/") }
}

val grpcVersion = "1.62.2"
val grpcKotlinVersion = "1.4.1"
val protobufVersion = "3.25.3"

dependencies {
	implementation("com.hoppingmall:hoppingmall-common:0.0.1-SNAPSHOT")
	implementation("com.hoppingmall:hoppingmall-dlq:0.0.1-SNAPSHOT")
	implementation("com.hoppingmall:hoppingmall-outbox:0.0.1-SNAPSHOT")
	implementation("com.hoppingmall:hoppingmall-cache:0.0.1-SNAPSHOT")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.github.resilience4j:resilience4j-spring-boot3:2.2.0")

	implementation("org.springframework.boot:spring-boot-starter-data-redis") {
		exclude(group = "io.lettuce", module = "lettuce-core")
	}
	implementation("org.redisson:redisson-spring-boot-starter:4.3.0")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")

	implementation("org.springframework.kafka:spring-kafka")

	runtimeOnly("com.mysql:mysql-connector-j")
	runtimeOnly("com.h2database:h2")

	implementation("io.micrometer:micrometer-registry-prometheus")
	implementation("io.micrometer:micrometer-tracing-bridge-otel")
	implementation("io.opentelemetry:opentelemetry-exporter-otlp")

	implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.8")

	implementation("net.logstash.logback:logstash-logback-encoder:8.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testImplementation("org.assertj:assertj-core:3.24.2")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.kafka:spring-kafka-test")
	testImplementation("org.testcontainers:testcontainers:1.19.7")
	testImplementation("org.testcontainers:kafka:1.19.7")
	testImplementation("org.testcontainers:junit-jupiter:1.19.7")

	implementation("io.grpc:grpc-protobuf:$grpcVersion")
	implementation("io.grpc:grpc-stub:$grpcVersion")
	implementation("io.grpc:grpc-kotlin-stub:$grpcKotlinVersion")
	implementation("com.google.protobuf:protobuf-kotlin:$protobufVersion")
	implementation("net.devh:grpc-spring-boot-starter:3.1.0.RELEASE")
	implementation("io.opentelemetry.instrumentation:opentelemetry-grpc-1.6:2.12.0-alpha")
	implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val jacocoExcludedDirs = listOf(
	"**/config/**",
	"**/dto/**",
	"**/entity/**",
	"**/response/**",
	"**/error/**",
	"**/enum/**",
	"**/enums/**",
	"**/vo/**",
	"**/exception/**",
	"**/*Application*",
	"**/grpc/**",
	"**/controller/**",
	"**/internal/**",
	"**/outbox/**",
	"**/strategy/**",
	"**/KafkaPaymentEventPublisher*",
	"**/PaymentEventConsumer*",
	"**/PaymentEventService*",
	"**/MockPaymentService*",
	"**/PointEventConsumer*",
	"**/NotificationEventFactory*",
	"**/PaymentCompensationConsumer*",
	"**/RefundCompletionConsumer*",
	"**/TransactionalEventPublisherImpl*",
	"**/PaymentCommandServiceImpl*",
	"**/CouponCommandServiceImpl\$issueCoupon\$*",
	"**/HttpOrderCommandAdapter*",
	"**/HttpOrderQueryAdapter*",
	"**/HttpInventoryCommandAdapter*",
	"**/RefundEventLog*"
)

tasks.jacocoTestReport {
	dependsOn(tasks.test)
	classDirectories.setFrom(
		files(classDirectories.files.map {
			fileTree(it) { exclude(jacocoExcludedDirs) }
		})
	)
	reports {
		xml.required.set(true)
		html.required.set(true)
	}
}

tasks.jacocoTestCoverageVerification {
	dependsOn(tasks.jacocoTestReport)
	classDirectories.setFrom(
		files(classDirectories.files.map {
			fileTree(it) { exclude(jacocoExcludedDirs) }
		})
	)
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

tasks.register("jacocoTestVerification") {
	dependsOn(tasks.jacocoTestCoverageVerification)
}

protobuf {
	protoc {
		artifact = "com.google.protobuf:protoc:$protobufVersion"
	}
	plugins {
		create("grpc") {
			artifact = "io.grpc:protoc-gen-grpc-java:$grpcVersion"
		}
		create("grpckt") {
			artifact = "io.grpc:protoc-gen-grpc-kotlin:$grpcKotlinVersion:jdk8@jar"
		}
	}
	generateProtoTasks {
		all().forEach {
			it.plugins {
				create("grpc")
				create("grpckt")
			}
			it.builtins {
				create("kotlin")
			}
		}
	}
}
