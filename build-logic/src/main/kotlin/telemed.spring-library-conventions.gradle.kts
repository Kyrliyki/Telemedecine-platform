import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.getByType
import org.springframework.boot.gradle.tasks.bundling.BootJar
plugins {
    id("telemed.java-library-conventions")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

tasks.named<BootJar>("bootJar") {
    enabled = false
}

tasks.named<Jar>("jar") {
    enabled = true
    archiveClassifier.set("")
}

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

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
    "testAnnotationProcessor"(libs.findLibrary("mapstruct-processor").get())

    "testImplementation"(libs.findLibrary("spring-boot-starter-test").get())
    "testImplementation"(platform(libs.findLibrary("testcontainers-bom").get()))
    "testImplementation"(libs.findLibrary("testcontainers-junit-jupiter").get())

}

