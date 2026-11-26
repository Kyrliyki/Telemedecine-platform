plugins {
    `kotlin-dsl`
}

group = "com.telemed.buildlogic"
version = "0.0.1"

dependencies {
    implementation(libs.spring.boot.gradle.plugin)
    implementation(libs.spring.dependency.management.gradle.plugin)
    implementation(libs.spotless.gradle.plugin)
    implementation(libs.spotbugs.gradle.plugin)
}
