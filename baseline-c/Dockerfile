# C baseline: Raven + the C runtime only.
#   docker build --output type=local,dest=dist .
FROM raven-mingw AS build
COPY main.c /build/main.c
RUN x86_64-w64-mingw32-gcc -Os -s -mwindows -Wl,--gc-sections -I/opt/raven/include -o /simon-c.exe /build/main.c \
        -L/opt/raven/static -lraven -lravenuuid -luser32 -lgdi32 -lshell32 -lcomdlg32 -lole32 -loleaut32 -ladvapi32 -lwinhttp \
    && ls -la /simon-c.exe

FROM scratch
COPY --from=build /simon-c.exe /
