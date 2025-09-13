plugins {
    kotlin("jvm") version "2.0.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.yandex.cloud:java-sdk-serverless:2.8.0")
    implementation("org.telegram:telegrambots-client:9.1.0")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.register<Zip>("ycFunctionZip") {
    dependsOn("test")

    archiveFileName = "ycFunction.zip"
    destinationDirectory = layout.buildDirectory.dir("yc")
    from(".") {
        include("src/main/**")
        include("build.gradle.kts")
    }
}

tasks.register<Exec>("ycDeployFunction") {
    dependsOn("ycFunctionZip")

    workingDir = File("build/yc")
    executable = providers.exec { commandLine( "which", "yc") }.standardOutput.asText.get().trimIndent()
    args(
        "serverless", "function", "version", "create",
        "--function-name=yabarsik",
        "--runtime=kotlin20",
        "--entrypoint=com.github.drewlakee.yabarsik.YcHandler",
        "--memory=128m",
        "--execution-timeout=5m",
        "--source-path=ycFunction.zip"
    )
}

