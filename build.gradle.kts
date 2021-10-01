import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.1.1")
    }
}


plugins {
    kotlin("jvm") version ("1.5.31")
    id("com.github.johnrengelman.shadow") version ("7.0.0")
}

group = "io.github.duplexsystem.${rootProject.name}"
version = "1.0-SNAPSHOT"
val mainClassName = "Main"

tasks.withType<Jar> {
    manifest {
        attributes["Main-Class"] = mainClassName
    }
}

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))

    implementation("io.arrow-kt:arrow-fx-coroutines:${project.extra["arrow_version"]}")
    implementation("io.arrow-kt:arrow-fx-stm:${project.extra["arrow_version"]}")

    implementation("io.helidon.webserver:helidon-webserver:${project.extra["helidon_version"]}")

    testImplementation("io.helidon.webclient:helidon-webclient:${project.extra["helidon_version"]}")

    implementation("org.apache.logging.log4j:log4j-core:${project.extra["log4j_version"]}")
    implementation("org.apache.logging.log4j:log4j-api:${project.extra["log4j_version"]}")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "16"
}

kotlin {
    sourceSets.all {
        languageSettings.apply {
            languageVersion = "1.6"
            progressiveMode = true
        }
    }
}

gradleEnterprise {
    buildScan {
        termsOfServiceUrl = "https://gradle.com/terms-of-service"
        termsOfServiceAgree = "yes"
        publishOnFailure()
    }
}

tasks.withType<Test>().configureEach {
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).takeIf { it > 0 } ?: 1
}

tasks {
    build {
        dependsOn("proguard")
    }
    shadowJar {
        mergeServiceFiles()
        exclude("**/*.kotlin_metadata", "**/*.kotlin_metadata", "**/*.kotlin_module", "**/*.kotlin_builtins", "**/module-info.class", "META-INF/maven/**")
    }
}

tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    verbose()

    injars(tasks.named("shadowJar"))

    outjars("build/libs/${rootProject.name}-${version}-all-proguard-obfuscated.jar")

    val javaHome = System.getProperty("java.home")
    libraryjars(
        mapOf("jarfilter" to "!**.jar",
            "filter"    to "!module-info.class"),
        fileTree("$javaHome/jmods").filter { it.isFile() }.files
    )

    allowaccessmodification()

    mergeinterfacesaggressively()

    repackageclasses("")

    overloadaggressively()

    dontwarn()

    printmapping("build/libs/proguard-mapping.txt")

    keep("""public class $mainClassName {
            public static void main(java.lang.String[]);
    }
    """)
}