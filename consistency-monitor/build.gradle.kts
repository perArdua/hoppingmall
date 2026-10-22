plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("org.springframework.boot") version "3.5.0"
	id("io.spring.dependency-management") version "1.1.7"
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
}

val invariantsSql = file(
	listOf("../scripts/invariants/invariants.sql", "/scripts/invariants/invariants.sql")
		.first { file(it).exists() }
)

dependencies {
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-jdbc")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("io.micrometer:micrometer-registry-prometheus")

	runtimeOnly("com.mysql:mysql-connector-j")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testRuntimeOnly("com.h2database:h2")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.processResources {
	from(invariantsSql)
}

tasks.withType<Test> {
	useJUnitPlatform()
}

val jacocoExcludedDirs = listOf(
	"**/config/**",
	"**/dto/**",
	"**/enums/**",
	"**/*Application*"
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
