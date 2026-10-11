pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven {
            url = uri(providers.gradleProperty("curveRepository").orElse("../../build/repository").get())
            content { includeGroup("com.github.ZionHeZY.WeatherCurveView") }
        }
        google()
        mavenCentral()
    }
}
rootProject.name = "curve-consumer"
