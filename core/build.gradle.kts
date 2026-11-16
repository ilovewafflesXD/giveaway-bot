dependencies {
    compileOnly(project(":host"))
}

tasks.withType<Jar> {
    // The host loads this jar as plugins/WaffleCore/core.jar.
    archiveFileName.set("core.jar")
}
