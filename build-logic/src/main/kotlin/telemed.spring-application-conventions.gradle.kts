import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType


plugins {
    id("telemed.java-library-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

tasks.named<Jar>("jar") {
    enabled = false
}

dependencies {
    "implementation"(libs.findLibrary("spring-boot-starter-actuator").get())
    "implementation"(libs.findLibrary("micrometer-registry-prometheus").get())
    "implementation"(libs.findLibrary("loki-logback-appender").get())

    "compileOnly"(libs.findLibrary("lombok").get())
    "annotationProcessor"(libs.findLibrary("lombok").get())
    "testCompileOnly"(libs.findLibrary("lombok").get())
    "testAnnotationProcessor"(libs.findLibrary("lombok").get())

    "implementation"(libs.findLibrary("mapstruct").get())
    "annotationProcessor"(libs.findLibrary("mapstruct-processor").get())

    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testImplementation"(libs.findLibrary("spring-security-test").get())
    "testImplementation"(libs.findLibrary("spring-boot-webmvc-test").get())
    "testImplementation"(platform(libs.findLibrary("testcontainers-bom").get()))
    "testImplementation"(libs.findLibrary("testcontainers-junit-jupiter").get())
    "testImplementation"(libs.findLibrary("testcontainers-postgresql").get())
    "testImplementation"(libs.findLibrary("testcontainers-rabbitmq").get())

}


