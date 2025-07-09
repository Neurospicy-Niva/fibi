import org.springframework.boot.gradle.tasks.bundling.BootBuildImage

plugins {
    kotlin("jvm") version "2.2.0"
    kotlin("plugin.spring") version "2.2.0"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.github.ben-manes.versions") version "0.52.0"
}

group = "icu.neurospicy"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.spring.io/milestone") }
}

extra["springAiVersion"] = "1.0.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-quartz")
    implementation("org.springframework.ai:spring-ai-starter-model-ollama")
    implementation("org.apache.camel.springboot:camel-spring-boot-starter")
    implementation("org.apache.camel.springboot:camel-exec-starter")
    implementation("org.apache.camel.springboot:camel-file-watch-starter")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("com.maximeroussy.invitrode:invitrode:2.0.2") // used for ids - generates random english pronounceable words
    implementation("org.mnode.ical4j:ical4j:4.1.1")
    implementation("icu.neurospicy:simple-iso8601-arithmetic:1.0.0") // For parsing complex time expressions in routine templates

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")
    developmentOnly("org.springframework.ai:spring-ai-spring-boot-docker-compose")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.ai:spring-ai-spring-boot-testcontainers")
    testImplementation("io.mockk:mockk:1.14.4")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:mongodb")
    testImplementation("org.testcontainers:ollama")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(kotlin("stdlib"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.ai:spring-ai-bom:${property("springAiVersion")}")
    }
    imports {
        mavenBom("org.apache.camel.springboot:camel-spring-boot-bom:4.12.0")
    }
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val aiTest by sourceSets.creating {
    kotlin.srcDir("src/aiTest/kotlin")
    resources.srcDir("src/aiTest/resources")

    compileClasspath += sourceSets["main"].output
    compileClasspath += configurations["testCompileClasspath"]

    runtimeClasspath += output + compileClasspath + configurations["testRuntimeClasspath"]
}
val aiTestTask = tasks.register<Test>("aiTest") {
    description = "Runs the AI-related tests."
    group = "verification"
    testClassesDirs = aiTest.output.classesDirs
    classpath = aiTest.runtimeClasspath
    useJUnitPlatform()
}
tasks.named<ProcessResources>("processAiTestResources") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

// Configure the image name for bootBuildImage:
tasks.withType<BootBuildImage> {
    imageName.set("icu.neurospicy/fibi:latest")
}