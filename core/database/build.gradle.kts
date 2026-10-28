plugins {
    id("plakagozcusu.android.library")
    id("plakagozcusu.hilt")
}

android {
    namespace = "com.plakagozcusu.core.database"
}

ksp {
    // Exported schemas are committed so every future migration can be tested against them.
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    api(project(":core:model"))
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.room.testing)
}
