plugins {
    groovy
    id("com.gradleup.shadow") version "8.3.5"
    id("qupath-conventions")
}

qupathExtension {
    name = "qupath-extension-qurad"
    group = "io.github.icm-dac"
    version = "0.4.0"
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

tasks.register<JavaExec>("headless") {
    group = "application"
    description = "Run the QuRad calculator headlessly on an image + GeoJSON objects (see HeadlessRunner)"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("qupath.ext.qurad.HeadlessRunner")
    val runnerArgs = (project.findProperty("runnerArgs") as String?) ?: ""
    val runnerArgsJson = project.findProperty("runnerArgsJson") as String?
    args = if (runnerArgsJson != null) (groovy.json.JsonSlurper().parseText(runnerArgsJson) as List<*>).map { it.toString() }
           else runnerArgs.split(" ").filter { it.isNotBlank() }
    maxHeapSize = "8g"
}

tasks.register<JavaExec>("profile") {
    group = "application"
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("qupath.ext.qurad.Profile")
    val runnerArgs = (project.findProperty("runnerArgs") as String?) ?: ""
    args = runnerArgs.split(" ").filter { it.isNotBlank() }
    maxHeapSize = "8g"
}
