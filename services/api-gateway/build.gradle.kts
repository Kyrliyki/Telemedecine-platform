plugins {
    id("telemed.spring-application-conventions")
}


dependencies {
    implementation(libs.spring.cloud.starter.gateway.server.webflux)
    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.data.redis.reactive)
    implementation(libs.springdoc.openapi.starter.webflux.ui)
}

