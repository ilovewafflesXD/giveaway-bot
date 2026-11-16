// Builds WaffleHost and every module. Run `./gradlew dist` to collect the jars in dist/.
subprojects {
    apply(plugin = "java")

    group = "com.wafflemc"
    version = "2.1.0"

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/")
    }

    extensions.configure<JavaPluginExtension> {
        toolchain.languageVersion.set(JavaLanguageVersion.of(25))
    }

    tasks.withType<JavaCompile> {
        options.encoding = "UTF-8"
        options.release.set(25)
    }

    dependencies {
        "compileOnly"("io.papermc.paper:paper-api:26.3.build.253-stable")
    }
}

// Every module compiles against the host API but never bundles it.
configure(subprojects.filter { it.path.startsWith(":modules:") }) {
    dependencies {
        "compileOnly"(project(":host"))
    }
    tasks.withType<Jar> {
        archiveBaseName.set("WaffleModule-${project.name}")
    }
}

tasks.register<Copy>("dist") {
    group = "distribution"
    description = "Copies the host jar and all module jars into dist/."
    dependsOn(subprojects.map { "${it.path}:jar" })
    into(layout.projectDirectory.dir("dist"))
    into("modules") {
        from(subprojects.filter { it.path.startsWith(":modules:") }.map { it.tasks.named("jar") })
    }
    from(project(":host").tasks.named("jar"))
}
