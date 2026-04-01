plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
	id("io.spring.dependency-management") version "1.1.7"
	`java-library`
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
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	compileOnly("org.redisson:redisson-spring-boot-starter:4.3.0")
}
