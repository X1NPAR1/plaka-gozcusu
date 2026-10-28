plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.hilt")
}

android {
    namespace = "com.plakagozcusu.core.datastore"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.datastore.preferences)
}
