plugins {
    id("plakagozcusu.jvm.library")
}

dependencies {
    api(project(":vision:api"))
    implementation(libs.kotlinx.coroutines.core)
}
