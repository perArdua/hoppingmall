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
		languageVersion = JavaLanguageVersion.of(17)
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

tasks.test {
	useJUnitPlatform()
	finalizedBy(tasks.jacocoTestReport)
}

val jacocoExcludedDirs = listOf(
	"**/config/**",
	"**/dto/**",
	"**/MallApplication*",
	"**/MallApplicationTests*"
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
