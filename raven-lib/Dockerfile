# Raven C library, cross-compiled for Windows x64 with MinGW-w64.
# Produces (in /opt/raven):
#   include/          public headers
#   static/libraven.a static library   (Rust, Kotlin/Native link it into the exe)
#   static/libravenuuid.a  MinGW-w64's libuuid (COM/UI Automation GUIDs): the
#                     MinGW sysroot of Kotlin/Native lacks the UIA ones
#   shared/raven.dll  shared library   (Java / GraalVM load it via FFM)
#
#   docker build -t raven-mingw raven-lib
FROM debian:trixie-slim

ARG RAVEN_REPO=https://github.com/oscar-soirey/Raven.git
ARG RAVEN_COMMIT=32722ad0678b126b2629934882a53452e60dccec
ARG VULKAN_HEADERS_TAG=v1.3.290

RUN apt-get update && apt-get install -y --no-install-recommends \
        ca-certificates git cmake ninja-build gcc-mingw-w64-x86-64-win32 binutils-mingw-w64-x86-64 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /src
RUN git init raven && cd raven && git remote add origin ${RAVEN_REPO} \
    && git fetch --depth 1 origin ${RAVEN_COMMIT} && git checkout FETCH_HEAD \
    && git clone --depth 1 --branch ${VULKAN_HEADERS_TAG} https://github.com/KhronosGroup/Vulkan-Headers.git /src/vulkan-headers

COPY mingw-toolchain.cmake /src/mingw-toolchain.cmake

# Everything not needed by a small game is switched off: no web pages, no
# image decoders, no examples/tools/tests. -ffunction-sections/-fdata-sections
# let the final linker drop unused code (--gc-sections).
ENV RAVEN_OPTS="-DCMAKE_TOOLCHAIN_FILE=/src/mingw-toolchain.cmake -DCMAKE_BUILD_TYPE=MinSizeRel \
    -DRAVEN_BUILD_EXAMPLES=OFF -DRAVEN_BUILD_TESTS=OFF -DRAVEN_BUILD_TOOLS=OFF \
    -DRAVEN_BUILD_WEB=OFF -DRAVEN_WITH_STB_IMAGE=OFF -DRAVEN_WITH_SVG=OFF \
    -DRAVEN_FETCH_VULKAN_HEADERS=OFF -DRAVEN_VULKAN_INCLUDE_DIR=/src/vulkan-headers/include"

RUN cmake -S raven -B build-static -G Ninja ${RAVEN_OPTS} -DRAVEN_BUILD_SHARED=OFF \
    && cmake --build build-static \
    && cmake -S raven -B build-shared -G Ninja ${RAVEN_OPTS} -DRAVEN_BUILD_SHARED=ON \
       -DCMAKE_SHARED_LINKER_FLAGS="-Wl,--gc-sections -s" \
    && cmake --build build-shared \
    && mkdir -p /opt/raven/static /opt/raven/shared \
    && cp -r raven/include /opt/raven/include \
    && find build-static -name 'libraven.a' -exec cp {} /opt/raven/static/ \; \
    && cp /usr/x86_64-w64-mingw32/lib/libuuid.a /opt/raven/static/libravenuuid.a \
    && find build-shared -name 'raven.dll' -exec cp {} /opt/raven/shared/ \; \
    && find build-shared -name 'libraven.dll.a' -exec cp {} /opt/raven/shared/ \; \
    && ls -la /opt/raven/static /opt/raven/shared
