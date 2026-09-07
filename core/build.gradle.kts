plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

// No jvmToolchain(): that would make Gradle try to auto-provision/download a JDK, which needs
// network access this environment does not allow. Instead the Kotlin compiler is told to target
// JVM 17 bytecode explicitly (it can cross-target below the JDK actually running Gradle), which
// keeps it consistent with compileJava's target set above without downloading anything.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}
