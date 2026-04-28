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
    implementation(libs.slf4j.api)
    implementation(libs.sqlite.jdbc)
    runtimeOnly(libs.log4j.to.slf4j)

    testImplementation(libs.kotlin.test)
    testRuntimeOnly(libs.slf4j.simple)
}
