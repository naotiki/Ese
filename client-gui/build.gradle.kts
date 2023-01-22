import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
    //id("org.jetbrains.dokka") version "1.7.20"
}

group = "me.naotiki"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
    maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
}
@OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.desktop.components.splitPane)
    implementation(compose.preview)
    //implementation(compose.uiTooling)
    implementation(compose.materialIconsExtended)
    implementation(project(":core"))
}


compose.desktop {
    application {
        mainClass = "MainKt"
        jvmArgs+=listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "EseLinux"
            packageVersion = "1.0.0"
            linux{
                shortcut=true
            }
            windows{

                console=false
                menu=true
                shortcut=true
            }
        }
    }
}
/*
subprojects {
    apply(plugin = "org.jetbrains.dokka")
}
*/
