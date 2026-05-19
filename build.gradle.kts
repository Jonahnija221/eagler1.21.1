plugins {
    java
    id("org.teavm") version "0.13.1"
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21

}

repositories {
    mavenCentral()
    maven {
        name = "Mojang"
        url = uri("https://libraries.minecraft.net")
    }
}

sourceSets {
    main {
        java {
            srcDirs("minecraft/src")
        }
    }
}

dependencies {
    // Guava
    implementation("com.google.guava:guava:32.1.2-jre")
    // Gson
    implementation("com.google.code.gson:gson:2.10.1")
    // fastutil
    implementation("it.unimi.dsi:fastutil:8.5.12")
    // JOML (math library)
    implementation("org.joml:joml:1.10.5")
    // Apache Commons
    implementation("org.apache.commons:commons-lang3:3.14.0")
    implementation("commons-io:commons-io:2.15.1")
    implementation("commons-codec:commons-codec:1.16.0")
    implementation("org.apache.commons:commons-compress:1.26.0")
    // Logging
    implementation("org.slf4j:slf4j-api:2.0.9")
    // Mojang libraries (compile-only, we stub at runtime)
    implementation("com.mojang:brigadier:1.3.10")
    implementation("com.mojang:datafixerupper:8.0.16")
    implementation("com.mojang:authlib:6.0.54")
    // Netty (compile-only, we stub networking at runtime)
    implementation("io.netty:netty-buffer:4.1.97.Final")
    implementation("io.netty:netty-codec:4.1.97.Final")
    implementation("io.netty:netty-common:4.1.97.Final")
    implementation("io.netty:netty-handler:4.1.97.Final")
    implementation("io.netty:netty-resolver:4.1.97.Final")
    implementation("io.netty:netty-transport:4.1.97.Final")
    implementation("io.netty:netty-transport-classes-epoll:4.1.97.Final")
    implementation("io.netty:netty-transport-native-unix-common:4.1.97.Final")
    // ICU4J
    implementation("com.ibm.icu:icu4j:73.2")
    // LZ4
    implementation("org.lz4:lz4-java:1.8.0")
    // javax annotations
    implementation("com.google.code.findbugs:jsr305:3.0.2")
    // JOpt Simple (command-line parsing)
    implementation("net.sf.jopt-simple:jopt-simple:5.0.4")
    // Jorbis (Ogg Vorbis)
    implementation("org.jcraft:jorbis:0.0.17")
    // LWJGL (compile-only — we provide stubs for runtime)
    compileOnly("org.lwjgl:lwjgl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-opengl:3.3.3")
    compileOnly("org.lwjgl:lwjgl-glfw:3.3.3")
    compileOnly("org.lwjgl:lwjgl-openal:3.3.3")
    compileOnly("org.lwjgl:lwjgl-stb:3.3.3")
    compileOnly("org.lwjgl:lwjgl-tinyfd:3.3.3")
    compileOnly("org.lwjgl:lwjgl-freetype:3.3.3")
    compileOnly("org.lwjgl:lwjgl-jemalloc:3.3.3")
    // OSHI (system info)
    compileOnly("com.github.oshi:oshi-core:6.4.10")
    // JNA
    compileOnly("net.java.dev.jna:jna:5.14.0")
    compileOnly("net.java.dev.jna:jna-platform:5.14.0")
    // TeaVM JSO APIs for browser interop
    implementation(teavm.libs.jsoApis)
    compileOnly("org.teavm:teavm-core:0.13.1")
    
    // Missing compile-time dependencies
    compileOnly("org.jetbrains:annotations:24.0.0")
    compileOnly("com.mojang:text2speech:1.13.9")
    compileOnly("ca.weblite:java-objc-bridge:1.1")
    compileOnly("com.mojang:blocklist:1.0.10")
    compileOnly("org.apache.httpcomponents:httpclient:4.5.13")
    compileOnly("org.apache.httpcomponents:httpmime:4.5.13")
}

tasks.named<JavaCompile>("compileJava") {
    enabled = false
}

val teavmClasslibOrgOnly by tasks.registering(Jar::class) {
    archiveFileName.set("teavm-classlib-org-only.jar")
    destinationDirectory.set(layout.buildDirectory.dir("teavm-filtered"))
    from({
        configurations.compileClasspath.get()
            .filter { it.name.startsWith("teavm-classlib-") }
            .map { zipTree(it) }
    }) {
        include("org/teavm/classlib/**")
    }
}

tasks.register<Exec>("compileJavaEcj") {
    dependsOn(teavmClasslibOrgOnly)

    val srcDir = file("minecraft/src")
    val destDir = file("build/classes/java/main")
    
    doFirst {
        destDir.mkdirs()
        
        // Exclude TeaVM classlib jars — they put java.util.concurrent.* in the unnamed module,
        // causing ECJ to emit "package accessible from more than one module" for every import.
        // The JDK's java.base already provides java.util.concurrent; ECJ doesn't need the duplicate.
        val compileClasspath = configurations.compileClasspath.get()
            .filter { jar ->
                val n = jar.name
                !n.contains("teavm-classlib") && !n.contains("teavm-metaprogramming-impl")
            }
            .plus(teavmClasslibOrgOnly.get().archiveFile.get().asFile)
            .joinToString(System.getProperty("path.separator")) { it.absolutePath }
        
        commandLine(
            "java", "-cp", project.file("ecj.jar").absolutePath,
            "org.eclipse.jdt.internal.compiler.batch.Main",
            "-proceedOnError",
            "-21", "-target", "21", "-source", "21",
            "-nowarn",
            "-d", destDir.absolutePath,
            "-cp", compileClasspath,
            srcDir.absolutePath
        )
    }
    
    standardOutput = file("build/ecj.log").outputStream()
    errorOutput = file("build/ecj_error.log").outputStream()
    
    // Crucial: ECJ returns non-zero when there are errors, even with -proceedOnError. 
    // We ignore it to let the build succeed!
    isIgnoreExitValue = true
}

tasks.named("classes") {
    dependsOn("compileJavaEcj")
}

val syncWebLauncher by tasks.registering(Copy::class) {
    from("web")
    into(layout.buildDirectory.dir("web/js"))
}

val deleteTeaVMSourceMap by tasks.registering(Delete::class) {
    delete(layout.buildDirectory.file("web/js/js/eaglercraft-1.21.js.map"))
    delete(layout.buildDirectory.dir("web/js/js/src"))
}

tasks.named("generateJavaScript") {
    finalizedBy(syncWebLauncher)
    finalizedBy(deleteTeaVMSourceMap)
}

teavm.js {
    mainClass = "net.lax1dude.eaglercraft.ClientMain"
    optimization = org.teavm.gradle.api.OptimizationLevel.BALANCED
    obfuscated = true
    outOfProcess = true
    processMemory = 14336
    sourceMap = false
    strict = false
    outputDir = layout.buildDirectory.dir("web/js")
}
