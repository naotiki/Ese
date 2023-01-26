plugins {
    kotlin("jvm")
}

group = "me.naotiki"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.6.4")
    implementation("io.insert-koin:koin-core:3.3.2")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.insert-koin:koin-test-junit5:3.3.2")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}
