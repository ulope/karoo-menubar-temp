import java.util.Properties

pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// karoo-ext is published to GitHub Packages, which always requires authentication
// (any GitHub account with a token that has the `read:packages` scope works).
// Provide credentials via local.properties (gpr.user / gpr.key), Gradle properties,
// or the GPR_USER / GPR_KEY environment variables (used in CI).
val localProperties = Properties().apply {
    val file = rootDir.resolve("local.properties")
    if (file.isFile) file.inputStream().use { load(it) }
}

fun credential(propertyName: String, envName: String): String? =
    providers.gradleProperty(propertyName).orNull
        ?: localProperties.getProperty(propertyName)
        ?: System.getenv(envName)

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven {
            name = "karooExt"
            url = uri("https://maven.pkg.github.com/hammerheadnav/karoo-ext")
            credentials {
                username = credential("gpr.user", "GPR_USER")
                password = credential("gpr.key", "GPR_KEY")
            }
            content { includeGroup("io.hammerhead") }
        }
    }
}

rootProject.name = "karoo-statusbar-temp"
include(":app")
