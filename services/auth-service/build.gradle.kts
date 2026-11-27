plugins {
    id("telemed.spring-application-conventions")
}

dependencies {

    implementation(libs.telemed.common.events)
    implementation(libs.telemed.common.outbox)
    implementation(libs.telemed.common.messaging)
    implementation(libs.telemed.common.security)
    implementation(libs.telemed.common.observability)

    implementation(libs.spring.boot.starter.security)
    implementation(libs.spring.boot.starter.webmvc)
    implementation(libs.spring.boot.starter.validation)
    implementation(libs.spring.boot.starter.amqp)
    implementation(libs.spring.boot.starter.data.jpa)
    implementation(libs.spring.boot.starter.data.redis)

    implementation(libs.nimbus.jose.jwt)
    implementation(libs.spring.boot.starter.security.oauth2.resource.server)
    implementation(libs.spring.boot.starter.oauth2.authorization.server)



    runtimeOnly(libs.postgresql)
    implementation(libs.flyway.core)
    implementation(libs.flyway.database.postgresql)
    implementation(libs.spring.boot.starter.flyway)

    implementation(libs.springdoc.openapi.starter.webmvc.ui)

    testImplementation(libs.spring.boot.starter.test)

}