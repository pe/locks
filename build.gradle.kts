plugins {
    java
    application
    id("org.graalvm.buildtools.native") version "0.9.7"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("one.util:streamex:0.7.3")
    testImplementation("org.junit.jupiter:junit-jupiter:5.8.1")
}

tasks.getByName<Test>("test") {
    useJUnitPlatform()
}

application {
    mainClass.set("locks.Locks")
}

graalvmNative {
    binaries {
        named("main") {
            // Workaround. Graal sets US/en by default
            buildArgs.add("-Duser.country=CH")
            buildArgs.add("-Duser.language=de")
        }
    }
}
