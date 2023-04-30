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

tasks.register<YarnTask>("runDev") {
    dependsOn("yarnInstall")
    args.set(listOf("start"))
}

tasks.register("clean") {
    doFirst {
        file("node_modules").deleteRecursively()
        file("build").deleteRecursively()
    }
}
