import com.github.gradle.node.task.NodeTask
import com.github.gradle.node.yarn.task.YarnTask

plugins {
    id("com.github.node-gradle.node") version "4.0.0"
}

node {
    version.set("18.16.0")
    download.set(true)
}

tasks.register<YarnTask>("yarnInstall") {
    inputs.file("package.json")
    inputs.file("yarn.lock")
    outputs.dir("node_modules")
        .withPropertyName("nodeModules")

    outputs.cacheIf { true }

    args.set(listOf("install"))
}

tasks.register<NodeTask>("runDev") {
    dependsOn("yarnInstall")

    environment.put("BROWSER", "none")
    script.set(file("$projectDir/node_modules/react-scripts/bin/react-scripts.js"))
    args.set(listOf("start"))
}

tasks.register<YarnTask>("build") {
    dependsOn("yarnInstall")
    args.set(listOf("build"))

    outputs.dir("build")
        .withPropertyName("buildDir")
    inputs.dir("src")
        .withPropertyName("srcDir")
    inputs.dir("public")
        .withPropertyName("publicDir")
}

tasks.register<YarnTask>("yarnAddDependency") {
    val dependency = project.property("dependency") as String?
    requireNotNull(dependency) { "`dependency` is required" }
    args.set(listOf("add", dependency, "--save"))
}

tasks.register("clean") {
    doFirst {
        file("node_modules").deleteRecursively()
        file("build").deleteRecursively()
    }
}
