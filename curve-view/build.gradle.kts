plugins {
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.jetbrainsKotlinAndroid)
    `maven-publish`
}

group = "com.github.ZionHeZY.WeatherCurveView"
version = providers.gradleProperty("version").orElse("0.2.0-SNAPSHOT").get()

android {
    namespace = "dev.hezy.curveview"
    compileSdk = 35

    defaultConfig {
        minSdk = 24
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    publishing {
        singleVariant("release") { withSourcesJar() }
    }
    sourceSets["main"].assets.srcDir(layout.buildDirectory.dir("generated/licenseAssets"))
}

val licenseAssets = tasks.register<Copy>("licenseAssets") {
    from(rootProject.layout.projectDirectory.file("LICENSE"))
    into(layout.buildDirectory.dir("generated/licenseAssets/weather-curve-view"))
}
tasks.named("preBuild") { dependsOn(licenseAssets) }
tasks.withType<Jar>().matching { it.name == "sourceReleaseJar" }.configureEach {
    from(rootProject.layout.projectDirectory.file("LICENSE")) { into("META-INF") }
}

publishing {
    repositories {
        maven {
            name = "verification"
            url = uri(rootProject.layout.buildDirectory.dir("repository"))
        }
    }
}

afterEvaluate {
    publishing.publications.register<MavenPublication>("release") {
        from(components["release"])
        artifactId = "curve-view"
        pom {
            name.set("WeatherCurveView")
            description.set("A configurable Android weather curve View with selection and accessibility support.")
            url.set("https://github.com/ZionHeZY/WeatherCurveView")
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set("ZionHeZY")
                    name.set("Zhongyang He")
                    url.set("https://github.com/ZionHeZY")
                }
            }
            scm {
                url.set("https://github.com/ZionHeZY/WeatherCurveView")
                connection.set("scm:git:https://github.com/ZionHeZY/WeatherCurveView.git")
                developerConnection.set("scm:git:ssh://git@github.com/ZionHeZY/WeatherCurveView.git")
            }
        }
    }
}