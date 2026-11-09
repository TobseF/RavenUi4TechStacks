plugins {
    kotlin("multiplatform") version "2.2.21"
}

repositories { mavenCentral() }

// Where the Raven headers and libraven.a are (the build container puts them in /opt/raven).
val ravenDir = providers.environmentVariable("RAVEN_DIR").getOrElse("/opt/raven")

kotlin {
    mingwX64 {
        compilations.getByName("main").cinterops.create("raven") {
            defFile(project.file("src/nativeInterop/cinterop/raven.def"))
            includeDirs("$ravenDir/include")
            extraOpts("-libraryPath", "$ravenDir/static")
        }
        binaries.executable {
            entryPoint = "main"
            baseName = "simon-kotlin"
            // GUI subsystem (no console window), strip symbols, drop unused sections
            linkerOpts("-Wl,--subsystem,windows", "-s", "-Wl,--gc-sections")
        }
    }
}
