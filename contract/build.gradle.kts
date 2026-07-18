plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.openapi.generator)
    `maven-publish`
}

group = "eu.tiducto"
version = "1.0.0-SNAPSHOT"

kotlin {
    jvmToolchain(25)

    applyDefaultHierarchyTemplate()

    jvm()

    android {
        namespace = "cz.davidkurzica.client.generated"
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
                api(libs.kotlinx.serialization.json)
                api(libs.kotlinx.datetime)
            }
        }
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

// --- OpenAPI Generator: models-only from the persisted-query contract spec ---
val generatedOpenApiDir = layout.buildDirectory.dir("generated/openapi")

openApiGenerate {
    generatorName.set("kotlin")
    inputSpec.set("$projectDir/openapi.json")
    outputDir.set(generatedOpenApiDir.get().asFile.path)
    modelPackage.set("cz.davidkurzica.client.generated")
    globalProperties.set(
        mapOf(
            "models" to "",
            "modelDocs" to "false",
            "modelTests" to "false",
        ),
    )
    configOptions.set(
        mapOf(
            // The `multiplatform` library already emits kotlinx-serialization `@Serializable`
            // annotations; also passing `serializationLibrary=kotlinx_serialization` duplicates
            // the annotation and fails to compile ("annotation is not repeatable").
            "library" to "multiplatform",
            "dateLibrary" to "kotlinx-datetime",
            "enumPropertyNaming" to "UPPERCASE",
            // Flatten out of the default "src/main/kotlin": AGP's art-profile task scans a
            // "src/main/baselineProfiles" sibling inside the generated dir and fails the build over
            // the undeclared dependency. Emitting to "<outputDir>/kotlin" sidesteps that entirely.
            "sourceFolder" to "kotlin",
        ),
    )
}

// OpenAPI Generator doesn't purge its output dir between runs, so a model dropped from the spec
// would linger as a ghost class. Wipe the dir before regenerating.
tasks.named("openApiGenerate") {
    doFirst { generatedOpenApiDir.get().asFile.deleteRecursively() }
}

// --- Persisted-query id constants generated from the same spec ---
val persistedQueriesDir: Provider<Directory> =
    layout.buildDirectory.dir("generated/persisted-queries/commonMain/kotlin")

val generatePersistedQueries by tasks.registering {
    val specFile = file("$projectDir/openapi.json")
    val outDir = persistedQueriesDir
    inputs.file(specFile)
    outputs.dir(outDir)
    doLast {
        val httpMethods = setOf("get", "post", "put", "delete", "patch", "head", "options", "trace")

        @Suppress("UNCHECKED_CAST")
        val spec = groovy.json.JsonSlurper().parse(specFile) as Map<String, Any?>

        @Suppress("UNCHECKED_CAST")
        val paths = spec["paths"] as Map<String, Map<String, Any?>>

        val constants = paths.values.map { pathItem ->
            @Suppress("UNCHECKED_CAST")
            val op = pathItem.entries.first { it.key.lowercase() in httpMethods }.value as Map<String, Any?>
            val summary = op["summary"] as String
            val pqId = op["x-persisted-query-id"] as String
            val constName = summary.replace(Regex("(?<=[a-z0-9])(?=[A-Z])"), "_").uppercase()
            "    const val $constName = \"$pqId\""
        }

        val pkgDir = outDir.get().dir("cz/davidkurzica/client/generated").asFile
        pkgDir.mkdirs()
        pkgDir.resolve("PersistedQueries.kt").writeText(
            buildString {
                appendLine("package cz.davidkurzica.client.generated")
                appendLine()
                appendLine("internal object PersistedQueries {")
                constants.forEach { appendLine(it) }
                appendLine("}")
            },
        )
    }
}

// Wire generated sources into commonMain. `builtBy` ties each dir to its producing task, so EVERY
// consumer (Kotlin compilation + AGP variant tasks) depends on generation — a plain srcDir path
// leaves AGP tasks scanning the generated tree with no declared dependency, which Gradle rejects.
kotlin.sourceSets.named("commonMain") {
    kotlin.srcDir(files(generatedOpenApiDir.map { it.dir("kotlin") }).builtBy(tasks.named("openApiGenerate")))
    kotlin.srcDir(files(persistedQueriesDir).builtBy(generatePersistedQueries))
}

// AGP's per-variant art-profile task scans a baselineProfiles dir under the generated output root, which
// `builtBy` (Kotlin-compile only) doesn't cover — so wire the AGP task(s) to generation explicitly.
tasks.matching { it.name.contains("ArtProfile") }.configureEach {
    dependsOn("openApiGenerate", generatePersistedQueries)
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
                // Graceful fallback: a local build without creds still configures;
                // only an actual `publish` needs these set (GITHUB_ACTOR / GITHUB_TOKEN).
                username = System.getenv("GITHUB_ACTOR").orEmpty()
                password = System.getenv("GITHUB_TOKEN").orEmpty()
            }
        }
    }
}
