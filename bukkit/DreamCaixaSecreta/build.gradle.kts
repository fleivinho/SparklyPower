import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(project(":bukkit:DreamCore", configuration = "shadowWithRuntimeDependencies"))
    compileOnly(project(":bukkit:DreamCash"))
    compileOnly(files("../../libs/NoteBlockAPI.jar"))
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}