plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.spring)
    alias(libs.plugins.spring.boot)
    application
}

group = "dev.additive"
version = "0.1.0"

repositories {
    mavenCentral()
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.additive.ApplicationKt")
}

dependencies {
    implementation(platform(libs.spring.boot.dependencies))
    implementation(libs.spring.boot.starter.web)
    implementation(kotlin("reflect"))

    testImplementation(kotlin("test-junit5"))
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test {
    useJUnitPlatform()
}

val npm = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")
val npmManifests = files("package.json", "package-lock.json")
val installFrontendDependencies by tasks.registering(Exec::class) {
    commandLine(npm + "ci")
    inputs.files(npmManifests)
    outputs.dir("node_modules")
}

val tailwindOutput = layout.buildDirectory.file("generated/tailwind/practice.css")
val compileTailwind by tasks.registering(Exec::class) {
    dependsOn(installFrontendDependencies)
    commandLine(npm + listOf("run", "build:css", "--", "--output", tailwindOutput.get().asFile.absolutePath))
    inputs.files(npmManifests, "src/main/css/practice.css")
    inputs.dir("src/main/resources/static")
    outputs.file(tailwindOutput)
}

tasks.processResources {
    from(compileTailwind) {
        into("static")
    }
}
