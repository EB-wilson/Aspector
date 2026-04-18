import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
  `java`
  kotlin("jvm") version "2.2.20"
}

group = "com.github.EB-wilson"
version = "1.0-SNAPSHOT"

repositories {
  mavenCentral()
}

dependencies {
  arrayOf("asm", "asm-tree", "asm-commons").forEach {
    implementation("org.ow2.asm:$it:9.2")
  }

  implementation(kotlin("stdlib-jdk8"))
  implementation(kotlin("reflect"))
  implementation(kotlin("metadata-jvm"))

  testImplementation(kotlin("test"))
}

tasks.test {
  useJUnitPlatform()
}

java {
  withSourcesJar()
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
  jvmToolchain(21)

  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_1_8)
  }
}