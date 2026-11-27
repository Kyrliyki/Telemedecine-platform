plugins {
    id("telemed.spring-library-conventions")
}

dependencies {
    compileOnly(libs.spring.boot.starter.webmvc)
    testImplementation(libs.spring.boot.starter.webmvc)
}
