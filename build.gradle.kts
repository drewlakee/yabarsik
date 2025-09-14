plugins {
    kotlin("jvm") version "2.2.0"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.yandex.cloud:java-sdk-serverless:2.8.0")
    implementation("org.telegram:telegrambots-client:9.1.0")
    implementation("software.amazon.awssdk:s3:2.33.9")
    implementation("software.amazon.awssdk:apache-client:2.33.9")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml:2.13.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.9.8")
    implementation("org.http4k:http4k-connect-core:6.17.0.0")
    implementation("org.http4k:http4k-client-okhttp:6.17.0.0")

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
        "--source-path=ycFunction.zip",
        "--service-account-id=ajeduks5d1ag7q1rkbo6",
        "--environment=CONFIGURATION_S3_OBJECT_ID=configuration.yml,CONFIGURATION_S3_BUCKET=yabarsik",
        "--secret=environment-variable=TELEGRAM_TOKEN,id=e6qunf2om3830utk4li6,key=token",
        "--secret=environment-variable=AWS_ACCESS_KEY_ID,id=e6q7hvehrvtsf655otla,key=key-identifier",
        "--secret=environment-variable=AWS_SECRET_ACCESS_KEY,id=e6q7hvehrvtsf655otla,key=secret-key"
    )
}

