plugins {
    id("telemed.spring-library-conventions")
}

dependencies {
    api(libs.spring.boot.starter.security)
    api(libs.spring.boot.starter.security.oauth2.resource.server)
    compileOnly(libs.spring.boot.starter.data.redis)
    compileOnly(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.data.redis)
    testImplementation(libs.testcontainers.junit.jupiter)
}