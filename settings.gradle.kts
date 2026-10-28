pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "PlakaGozcusu"

include(":app")
include(":core:common")
include(":core:model")
include(":core:designsystem")
include(":feature:onboarding")
include(":vision:api")
include(":vision:models")
include(":vision:pipeline")
include(":core:camera")
include(":core:storage")
include(":core:database")
include(":core:datastore")
include(":core:data")
