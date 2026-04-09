plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	id("io.spring.dependency-management") version "1.1.7"
	`java-library`
	jacoco
}

group = "com.hoppingmall"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(21)
	}
}

repositories {
	mavenCentral()
	maven { url = uri("https://packages.confluent.io/maven/") }
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.boot:spring-boot-dependencies:3.5.0")
	}
}

dependencies {
	implementation("com.hoppingmall:hoppingmall-common:0.0.1-SNAPSHOT")
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.springframework.boot:spring-boot-starter-cache")
	implementation("com.github.ben-manes.caffeine:caffeine")
	implementation("io.micrometer:micrometer-core")
	compileOnly("org.springframework.boot:spring-boot-starter-data-redis")
	compileOnly("org.redisson:redisson-spring-boot-starter:4.3.0")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.mockito.kotlin:mockito-kotlin:5.1.0")
	testImplementation("org.springframework.boot:spring-boot-starter-data-redis")
	testImplementation("org.redisson:redisson-spring-boot-starter:4.3.0")
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
