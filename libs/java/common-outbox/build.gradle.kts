plugins {
    id("telemed.spring-library-conventions")
}

dependencies {
    api(libs.spring.boot.starter.data.jpa)
    compileOnly(libs.spring.boot.starter.webmvc)

    api(project(":common-events"))
}




