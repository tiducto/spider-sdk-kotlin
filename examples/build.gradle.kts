plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvmToolchain(25)

    jvm()

    compilerOptions {
        optIn.add("kotlin.time.ExperimentalTime")
    }

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(project(":client"))
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
