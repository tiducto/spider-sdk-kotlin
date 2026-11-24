val mirrorRepo: File = gradle.startParameter.projectProperties["mirrorRepo"]
    ?.let { gradle.startParameter.currentDir.resolve(it) }
    ?: throw GradleException("Pass -PmirrorRepo=<directory> to publish to the mirror repository.")

gradle.allprojects {
    plugins.withId("maven-publish") {
        extensions.configure<PublishingExtension> {
            repositories {
                maven {
                    name = "mirror"
                    url = mirrorRepo.toURI()
                }
            }
        }
    }
}
