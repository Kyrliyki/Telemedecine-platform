rootProject.name = "telemed-libs-java"



dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs"){
            from(files("../../gradle/libs.versions.toml"))
        }
    }

}

includeBuild("../../build-logic")
include("common-security")
include("common-messaging")
include("common-observability")
include("common-outbox")
include("common-events")