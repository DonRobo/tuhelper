import com.github.gradle.node.npm.task.NpxTask
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
    group = "yarn"

    inputs.file("package.json")
    inputs.file("yarn.lock")
    outputs.dir("node_modules")
        .withPropertyName("nodeModules")

    outputs.cacheIf { true }

    args.set(listOf("install"))
}

tasks.register<NodeTask>("runDev") {
    group = "yarn"
    dependsOn("yarnInstall", "generateOpenApiBindings")

    environment.put("BROWSER", "none")
    script.set(file("$projectDir/node_modules/react-scripts/bin/react-scripts.js"))
    args.set(listOf("start"))
}

tasks.register<YarnTask>("build") {
    group = "build"
    dependsOn("yarnInstall", "generateOpenApiBindings")

    args.set(listOf("build"))

    outputs.dir("build")
        .withPropertyName("buildDir")
    inputs.dir("src")
        .withPropertyName("srcDir")
    inputs.dir("public")
        .withPropertyName("publicDir")
    inputs.file("package.json")
        .withPropertyName("packageJson")
    inputs.file("yarn.lock")
        .withPropertyName("yarnLock")
    inputs.file("tsconfig.json")
        .withPropertyName("tsconfigJson")

    outputs.cacheIf { true }
}

tasks.register<YarnTask>("yarnAddDependency") {
    group = "yarn"

    val dependency = project.property("dependency") as String?
    requireNotNull(dependency) { "`dependency` is required" }
    args.set(listOf("add", dependency, "--save"))
}

tasks.register("clean") {
    group = "build"

    doFirst {
        file("node_modules").deleteRecursively()
        file("build").deleteRecursively()
        file("$projectDir/src/generated").deleteRecursively()
    }
}

tasks.register<NpxTask>("generateOpenApiBindings") {
    group = "openApi"
    dependsOn(":backend:generateOpenApiDocs")

    doFirst {
        file("$projectDir/src/generated").deleteRecursively()
    }

    this.command.set("openapi-typescript-codegen")
    this.args.set(listOf("--input", "../backend/build/docs/swagger.json", "--output", "./src/generated"))

    inputs.file("$rootDir/backend/build/docs/swagger.json")
        .withPropertyName("swaggerJson")
    outputs.dir("$projectDir/src/generated")
        .withPropertyName("generatedDir")
    outputs.cacheIf { true }
}
