dependencies {
    // jgit
    implementation("org.eclipse.jgit", "org.eclipse.jgit", "5.9.0.202009080501-r")
    // fileutils
    implementation("commons-io", "commons-io", "2.8.0")
    // gzip
    implementation("org.apache.commons", "commons-compress", "1.20")
}

application {
    mainClassName = "MainKt"
}
