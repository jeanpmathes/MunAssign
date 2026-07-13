import org.gradle.api.tasks.bundling.Zip

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
}

val portablePackageVersion = "0.1.0"

kotlin {
    jvmToolchain(21)
    jvm()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.compose.runtime)
            implementation(libs.compose.foundation)
            implementation(libs.compose.material3)
            implementation(libs.compose.materialIconsExtended)
            implementation(libs.compose.ui)
            implementation(libs.compose.components.resources)
            implementation(libs.compose.uiToolingPreview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
        jvmMain.dependencies {
            implementation(projects.domain)
            implementation(projects.data)
            implementation(compose.desktop.currentOs)
            implementation(libs.koin.core)
            implementation(libs.kotlinx.coroutinesSwing)
            runtimeOnly(libs.slf4j.simple)
        }
        jvmTest.dependencies {
            implementation(libs.exposed.jdbc)
            runtimeOnly(libs.sqlite.jdbc)
            runtimeOnly(libs.slf4j.simple)
        }
    }
}

compose.desktop {
    application {
        mainClass = "org.munika.assignment.MainKt"

        nativeDistributions {
            modules("java.sql")
            packageName = "MunAssign"
            packageVersion = portablePackageVersion

            windows {
                iconFile.set(project.file("src/jvmMain/resources/icons/munassign.ico"))
            }
        }
    }
}

tasks.register<Zip>("packagePortableZip") {
    group = "distribution"
    description = "Creates a Windows portable MunAssign zip from the Compose app image."
    dependsOn("createDistributable")

    val appImageDirectory = layout.buildDirectory.dir("compose/binaries/main/app/MunAssign")
    from(appImageDirectory)
    destinationDirectory = layout.buildDirectory.dir("compose/binaries/main/zip")
    archiveFileName = "MunAssign-$portablePackageVersion-windows-portable.zip"
}

tasks.named("assemble") {
    dependsOn("packagePortableZip")
}
