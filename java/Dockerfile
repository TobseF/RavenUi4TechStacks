# Java variant: HotSpot JVM in a jlink'ed runtime with only java.base,
# a tiny native launcher (JNI invocation API) and raven.dll.
#   docker build -t raven-mingw ../raven-lib     (once)
#   docker build --output type=local,dest=dist .
FROM raven-mingw AS raven

FROM debian:trixie-slim AS build
# Linux JDK (javac, jlink) and the Windows JDK of the same version (for jni.h)
# plus its jmods (since JDK 24 a separate download): jlink assembles the
# Windows runtime from them.
ARG JDK_VERSION=jdk-25.0.1%2B8
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl unzip gcc-mingw-w64-x86-64-win32 \
    && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /jdk/linux /jdk/windows \
    && curl -fsSL "https://api.adoptium.net/v3/binary/version/${JDK_VERSION}/linux/x64/jdk/hotspot/normal/eclipse" | tar xz -C /jdk/linux --strip-components=1 \
    && curl -fsSL -o /tmp/win.zip "https://api.adoptium.net/v3/binary/version/${JDK_VERSION}/windows/x64/jdk/hotspot/normal/eclipse" \
    && unzip -q /tmp/win.zip -d /tmp/win && mv /tmp/win/*/* /jdk/windows/ && rm -rf /tmp/win /tmp/win.zip \
    && curl -fsSL -o /tmp/jmods.zip "https://api.adoptium.net/v3/binary/version/${JDK_VERSION}/windows/x64/jmods/hotspot/normal/eclipse" \
    && unzip -q /tmp/jmods.zip -d /tmp/jmods && mkdir -p /jdk/windows/jmods \
    && mv "$(dirname "$(find /tmp/jmods -name java.base.jmod)")"/* /jdk/windows/jmods/ \
    && rm -rf /tmp/jmods /tmp/jmods.zip && test -f /jdk/windows/jmods/java.base.jmod
ENV PATH=/jdk/linux/bin:$PATH

COPY --from=raven /opt/raven/shared/raven.dll /opt/raven/raven.dll
# the generated binding of Raven: raven/R.java
COPY --from=raven /src/raven/bindings/java/src/main/java /build/src
WORKDIR /build
COPY src ./src
COPY launcher ./launcher

RUN mkdir -p out/classes /out/simon-java \
    && javac --release 25 -d out/classes $(find src -name '*.java') \
    && jar --create --file /out/simon-java/simon.jar -C out/classes . \
    && jlink --module-path /jdk/windows/jmods --add-modules java.base \
         --strip-java-debug-attributes --no-header-files --no-man-pages --strip-native-commands \
         --compress=zip-9 --output /out/simon-java/runtime \
    && x86_64-w64-mingw32-gcc -Os -s -municode -mwindows -I/jdk/windows/include -I/jdk/windows/include/win32 \
         -o /out/simon-java/simon-java.exe launcher/launcher.c \
    && cd /out/simon-java/runtime \
    # UCRT forwarders: part of Windows 10+.  msvcp140/jli: only java.exe needs them (checked with objdump).
    # jvm.lib: import library for linking.  cacerts/public_suffix_list: TLS data, no networking here.
    && rm bin/api-ms-win-*.dll bin/ucrtbase.dll bin/msvcp140.dll bin/jli.dll lib/jvm.lib \
          lib/security/cacerts lib/security/public_suffix_list.dat \
    && cd /build \
    && cp /opt/raven/raven.dll /out/simon-java/ \
    && du -sh /out/simon-java /out/simon-java/*

FROM scratch
COPY --from=build /out /
