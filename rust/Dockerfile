# Rust variant: cross-compiled to x86_64-pc-windows-gnu, Raven linked statically.
#   docker build -t raven-mingw ../raven-lib     (once)
#   docker build --output type=local,dest=dist .
FROM raven-mingw AS raven

FROM rust:1.91-slim-trixie AS build
RUN apt-get update && apt-get install -y --no-install-recommends gcc-mingw-w64-x86-64-win32 \
    && rm -rf /var/lib/apt/lists/* \
    && rustup target add x86_64-pc-windows-gnu
COPY --from=raven /opt/raven /opt/raven
COPY --from=raven /src/raven/bindings/rust /src/raven/bindings/rust
WORKDIR /build
COPY Cargo.toml build.rs ./
COPY src ./src
ENV RAVEN_STATIC=1 RAVEN_LIB_DIR=/opt/raven/static \
    CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER=x86_64-w64-mingw32-gcc
RUN cargo build --release --target x86_64-pc-windows-gnu \
    && cp target/x86_64-pc-windows-gnu/release/simon.exe /simon-rust.exe \
    && x86_64-w64-mingw32-objdump -p /simon-rust.exe | grep 'DLL Name' && ls -la /simon-rust.exe

FROM scratch
COPY --from=build /simon-rust.exe /
