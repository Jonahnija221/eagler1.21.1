val ecj = configurations.create("ecj")
dependencies {
    ecj("org.eclipse.jdt:ecj:3.36.0")
}
tasks.withType<JavaCompile> {
    doFirst {
        options.isFork = true
        options.forkOptions.executable = org.gradle.internal.jvm.Jvm.current().javaExecutable.absolutePath
        options.forkOptions.jvmArgs = listOf(
            "-cp", ecj.asPath, "org.eclipse.jdt.internal.compiler.batch.Main"
        )
        // Instruct ECJ to proceed on error
        options.compilerArgs.add("-proceedOnError")
    }
}
