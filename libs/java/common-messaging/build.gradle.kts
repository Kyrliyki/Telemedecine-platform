plugins {
    id("telemed.spring-library-conventions")
}

dependencies {
    api(libs.spring.boot.starter.amqp)
    api(libs.spring.boot.starter.json)
    api(libs.spring.boot.starter.webmvc)
    api(project(":common-events"))
    testImplementation(libs.testcontainers.rabbitmq)
}