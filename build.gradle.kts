plugins {
    java
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.openapi.generator") version "7.26.0"
}

group = "com.outlasttrialsstats"
version = file("version.txt").readText().trim()
description = "totstats-discord-bot"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
}

val testcontainersVersion = "1.21.4"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-liquibase")
    implementation("net.dv8tion:JDA:6.7.0")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:3.2.0")
    implementation("org.openapitools:jackson-databind-nullable:0.2.12")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    developmentOnly("org.springframework.boot:spring-boot-devtools")
    developmentOnly("org.springframework.boot:spring-boot-docker-compose")

    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

openApiGenerate {
    inputSpec.set(layout.projectDirectory.file("src/main/resources/api/api.yml").asFile.path)
    generatorName = "spring"
    outputDir = layout.buildDirectory.dir("generated/openapi")
    apiPackage = "com.outlasttrialsstats.backend.api"
    modelPackage = "com.outlasttrialsstats.backend.api.model"
    configOptions = mapOf(
        "interfaceOnly" to "true",
        "reactive" to "true",
        "useSpringBoot3" to "true",
        "useTags" to "true",
        "useBeanValidation" to "true",
    )
}

sourceSets.main {
    java.srcDir(tasks.openApiGenerate.map { "${it.outputDir.get()}/src/main/java" })
}

tasks.jar {
    enabled = false
}

tasks.test {
    useJUnitPlatform()
}
