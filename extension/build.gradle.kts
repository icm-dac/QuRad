plugins {
    groovy
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-qurad"
    group = "io.github.icm-dac"
    version = "0.3.0"
    description = "Radiomics feature extraction for QuPath (QuRad)"
    automaticModule = "io.github.icmdac.qupath.extension.qurad"
}

dependencies {
    shadow(libs.bundles.qupath)
    shadow(libs.bundles.logging)
    shadow(libs.qupath.fxtras)
    shadow(libs.bundles.groovy)

    testImplementation(libs.bundles.qupath)
    testImplementation(libs.bundles.groovy)
    testImplementation(libs.junit)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.0")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
