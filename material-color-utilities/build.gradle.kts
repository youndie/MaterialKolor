@file:Suppress("OPT_IN_USAGE")

import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.multiplatform.android.library)
    alias(libs.plugins.poko)
    alias(libs.plugins.dokka)
    alias(libs.plugins.publish)
}

kotlin {
    explicitApi()

    applyDefaultHierarchyTemplate()

    android {
        compileSdk = libs.versions.sdk.compile.get().toInt()
        minSdk = libs.versions.sdk.min.library.get().toInt()
        namespace = "com.materialkolor.colorutilities"

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    js {
        browser()
    }

    wasmJs {
        browser()
    }

    macosArm64()

    linuxX64()

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "material-color-utilities"
        }
    }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }

    jvmToolchain(libs.versions.jvmTarget.get().toInt())
}

// Publishing this fork to the reposilite at kotlin.website, alongside the upstream Maven Central setup
// that stays untouched. The workflow `publish-reposilite.yml` overrides GROUP so that the fork never
// shares coordinates with the real artifact, and turns signing off: the repository does not need it
// and the fork has no key.
//
// Both repositories are registered unconditionally. Without credentials the `wip` PUT fails loudly
// instead of the publish task having no repository to publish to and passing having done nothing.
publishing {
    repositories {
        maven {
            name = "wip"
            url = uri("https://reposilite.kotlin.website/snapshots")
            credentials {
                username = (findProperty("REPOSILITE_USER") as String?).orEmpty()
                password = (findProperty("REPOSILITE_SECRET") as String?).orEmpty()
            }
        }
        // The same publication written where the workflow can walk it: the pre-flight and the
        // read-back check the coordinates the build actually produced, not a list somebody typed.
        maven {
            name = "localCopy"
            url = rootProject.layout.buildDirectory.dir("published").get().asFile.toURI()
        }
    }
}
