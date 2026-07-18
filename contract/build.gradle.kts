plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    `maven-publish`
}

group = "eu.tiducto"
version = "1.0.0-SNAPSHOT"

// The models under src/commonMain/.../contract/models are GENERATED from spider-contract's openapi.json
// (scripts/generate-contract.sh / the generate-contract workflow) and committed. Don't hand-edit them —
// change the contract and regenerate.
kotlin {
    jvmToolchain(25)

    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "cz.davidkurzica.contract"
        compileSdk = libs.versions.androidCompileSdk.get().toInt()
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    iosArm64()
    iosSimulatorArm64()
    iosX64()
    macosArm64()
    macosX64()

    js(IR) {
        browser()
        nodejs()
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
        nodejs()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // api, not implementation: the models are @Serializable public types, so consumers
                // need the serialization runtime transitively.
                api(libs.kotlinx.serialization.json)
            }
        }
    }
}

publishing {
    // Prefix the artifactId so the published coordinates read as
    // eu.tiducto:spider-sdk-kotlin-contract(-<target>) rather than the bare module name.
    publications.withType<MavenPublication>().configureEach {
        artifactId = "spider-sdk-kotlin-$artifactId"
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/tiducto/spider-sdk-kotlin")
            credentials {
                username = System.getenv("GITHUB_ACTOR").orEmpty()
                password = System.getenv("GITHUB_TOKEN").orEmpty()
            }
        }
    }
}
