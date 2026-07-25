plugins {
    alias(libs.plugins.kotlin.multiplatform) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.android.kotlin.multiplatform.library) apply false
}

val versionProps = java.util.Properties().apply {
    file("version.properties").inputStream().use { load(it) }
}

allprojects {
    version = "${versionProps["contract"]}.${versionProps["patch"]}"
}
