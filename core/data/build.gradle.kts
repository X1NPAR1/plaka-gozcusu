plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.hilt")
}

android {
    namespace = "com.plakagozcusu.core.data"
}

dependencies {
    api(project(":core:model"))
    api(project(":core:common"))
    api(project(":core:storage"))
    api(project(":core:datastore"))
    api(project(":core:database"))
    implementation(libs.room.runtime)
    implementation(libs.work.runtime.ktx)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)
    testImplementation(libs.room.runtime)
    testImplementation(libs.work.testing)
}
