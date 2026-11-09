# Kotlin/Native variant: cross-compiled on Linux for mingwX64, Raven linked statically.
#   docker build -t raven-mingw ../raven-lib     (once)
#   docker build --output type=local,dest=dist .
FROM raven-mingw AS raven

FROM gradle:8.14.3-jdk21 AS build
COPY --from=raven /opt/raven /opt/raven
WORKDIR /build
# Kotlin/Native downloads its own LLVM/MinGW toolchain into ~/.konan: this layer
# only depends on the build files, so it is cached between source changes.
COPY settings.gradle.kts build.gradle.kts ./
COPY src/nativeInterop ./src/nativeInterop
RUN mkdir -p src/mingwX64Main/kotlin && echo 'fun main() {}' > src/mingwX64Main/kotlin/Main.kt \
    && gradle --no-daemon -q cinteropRavenMingwX64 && rm -rf src/mingwX64Main build
COPY src ./src
RUN gradle --no-daemon linkReleaseExecutableMingwX64 \
    && cp build/bin/mingwX64/releaseExecutable/simon-kotlin.exe /simon-kotlin.exe \
    && ls -la /simon-kotlin.exe

FROM scratch
COPY --from=build /simon-kotlin.exe /
