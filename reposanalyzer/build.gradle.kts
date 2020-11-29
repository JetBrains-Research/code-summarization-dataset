repositories {
    maven(url = "https://dl.bintray.com/egor-bogomolov/astminer/")
}

dependencies {
    // astminer
    implementation("io.github.vovak.astminer", "astminer", "0.6")
    // dependency astminer
    implementation("com.github.ajalt.clikt", "clikt", "3.0.1")
    // jgit
    implementation("org.eclipse.jgit", "org.eclipse.jgit", "5.9.0.202009080501-r")
}
