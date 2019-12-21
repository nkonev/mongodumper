import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.gradle.language.jvm.tasks.ProcessResources
import java.io.ByteArrayOutputStream

plugins {
	id("org.springframework.boot") version "2.2.2.RELEASE"
	id("io.spring.dependency-management") version "1.0.8.RELEASE"
	kotlin("jvm") version "1.3.61"
	kotlin("plugin.spring") version "1.3.61"
}

group = "com.github.nkonev"
version = "0.0.1"
java.sourceCompatibility = JavaVersion.VERSION_11

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.data:spring-data-mongodb")
	implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
	implementation("org.jetbrains.kotlin:kotlin-reflect")
	implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
	testImplementation("org.springframework.boot:spring-boot-starter-test") {
		exclude(group = "org.junit.vintage", module = "junit-vintage-engine")
	}

	testImplementation("org.seleniumhq.selenium:selenium-java:3.141.59")
	testImplementation("org.testcontainers:testcontainers:1.12.4")
	testImplementation ("org.testcontainers:junit-jupiter:1.12.4")
	testImplementation ("org.testcontainers:selenium:1.12.4")
}

tasks.withType<Test> {
	useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
	kotlinOptions {
		freeCompilerArgs = listOf("-Xjsr305=strict")
		jvmTarget = "11"
	}
}

fun getGitHash(): String {
	val stdout = ByteArrayOutputStream()
	exec {
		commandLine ("git", "rev-parse", "--short", "HEAD")
		standardOutput = stdout
	}
	return stdout.toString().trim()
}

tasks {
	"processResources"(ProcessResources::class) {
		filesMatching("**/git.properties") {
			// https://docs.gradle.org/6.0.1/userguide/working_with_files.html#sec:filtering_files
			filter(org.apache.tools.ant.filters.ReplaceTokens::class, "tokens" to mapOf("hash" to getGitHash()))
		}
	}
}