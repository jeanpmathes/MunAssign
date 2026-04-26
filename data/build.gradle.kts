plugins {
    alias(libs.plugins.kotlinJvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(projects.domain)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.poi.ooxml)
    implementation(libs.sqlite.jdbc)

    testImplementation(libs.kotlin.test)
}
