plugins {
	kotlin("jvm") version "1.9.25"
	kotlin("plugin.spring") version "1.9.25"
	kotlin("plugin.jpa") version "1.9.25"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.github.davidmc24.gradle.plugin.avro") version "1.9.1"
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
	implementation("org.jetbrains.kotlin:kotlin-stdlib")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("net.logstash.logback:logstash-logback-encoder:8.0")
	api("io.micrometer:micrometer-tracing-bridge-otel")
	api("io.opentelemetry:opentelemetry-exporter-otlp")
	api("org.apache.avro:avro:1.11.3")
	api("io.confluent:kafka-avro-serializer:7.6.0") {
		exclude(group = "io.swagger.core.v3", module = "swagger-annotations")
	}
	compileOnly("org.redisson:redisson-spring-boot-starter:4.3.0")
	compileOnly("io.micrometer:micrometer-core")
}
