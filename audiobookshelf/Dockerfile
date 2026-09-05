ARG BUILD_FROM=ghcr.io/hassio-addons/base:21.0.4
ARG AUDIOBOOKSHELF_VERSION="2.36.0"
ARG NUSQLITE3_VERSION="1.2"

# The web client is a Nuxt application that is generated up front and then
# served as static files by the server, so it has to be built before the image
# can be put together.
#
# What comes out is JavaScript and CSS, which is the same whatever it was built
# on, so this stage is pinned to the machine doing the building. Left to follow
# the target, an aarch64 image would run the whole Node toolchain under
# emulation for no benefit. Node 20 is what upstream builds with.
# hadolint ignore=DL3029
FROM --platform=${BUILDPLATFORM} node:20 AS client
ARG AUDIOBOOKSHELF_VERSION

WORKDIR /usr/src/audiobookshelf

# The base path everything is addressed from is settled when the client is
# built, which is the one thing that stops it working under Home Assistant
# Ingress, where the path is only known per request. The patch moves the last
# of it that was fixed at build time onto the runtime configuration, which
# NGINX fills in on the way past.
COPY patches /patches

# The client is built for the site root. That is what the published port sees,
# and it leaves the Ingress rewrites a document that needs correcting rather
# than an unrelated base path that needs replacing.
# hadolint ignore=DL3003
RUN \
    curl -J -L -o /tmp/audiobookshelf.tar.gz \
        "https://github.com/advplyr/audiobookshelf/archive/v${AUDIOBOOKSHELF_VERSION}.tar.gz" \
    && tar zxf /tmp/audiobookshelf.tar.gz --strip 1 -C /usr/src/audiobookshelf \
    \
    && patch -p1 < /patches/ingress-runtime-base-path.patch \
    \
    && cd /usr/src/audiobookshelf/client \
    && npm ci \
    && ROUTER_BASE_PATH='' NODE_ENV=production npm run generate

# The server pulls in a native module, which has to be compiled against the
# same Node that ends up running it. So this stage follows the target and
# builds on the app image rather than on a Node image of its own.
# hadolint ignore=DL3006
FROM ${BUILD_FROM} AS server
ARG AUDIOBOOKSHELF_VERSION
ARG NUSQLITE3_VERSION
ARG BUILD_ARCH

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

ENV NODE_ENV=production

WORKDIR /usr/src/audiobookshelf

# Installed rather than clean installed, because "npm ci" refuses this lock
# file. It carries a reference to fsevents without an entry to go with it,
# which is what npm writes when the lock is made anywhere other than macOS, and
# newer npm than upstream builds with treats that as out of sync. It is reached
# only through nodemon, which is not installed here anyway. What comes out is
# the same tree either way: every one of the 272 packages in the lock is
# installed at the version the lock names, and nothing outside it is added.
#
# Full text search over anything that is not plain ASCII goes through the
# nunicode extension. Audiobookshelf downloads it at runtime when it is
# missing, which an app has no business doing, so it is fetched here and
# pointed at with NUSQLITE3_PATH instead.
RUN \
    apk add --no-cache \
        build-base=0.5-r4 \
        nodejs=24.18.1-r0 \
        npm=11.12.1-r0 \
        python3=3.14.7-r1 \
        unzip=6.0-r16 \
    \
    && curl -J -L -o /tmp/audiobookshelf.tar.gz \
        "https://github.com/advplyr/audiobookshelf/archive/v${AUDIOBOOKSHELF_VERSION}.tar.gz" \
    && tar zxf /tmp/audiobookshelf.tar.gz --strip 1 -C /usr/src/audiobookshelf \
    \
    && npm install --omit=dev --no-audit --no-fund \
    \
    && if [[ "${BUILD_ARCH}" = "aarch64" ]]; then ARCH="arm64"; fi \
    && if [[ "${BUILD_ARCH}" = "amd64" ]]; then ARCH="x64"; fi \
    \
    && curl -J -L -o /tmp/libnusqlite3.zip \
        "https://github.com/mikiher/nunicode-sqlite/releases/download/v${NUSQLITE3_VERSION}/libnusqlite3-linux-musl-${ARCH}.zip" \
    && unzip -q /tmp/libnusqlite3.zip -d /usr/local/lib/nusqlite3

# hadolint ignore=DL3006
FROM ${BUILD_FROM}

# Set shell
SHELL ["/bin/bash", "-o", "pipefail", "-c"]

# ffmpeg does the transcoding and, as ffprobe, reads the durations and chapters
# out of the files during a scan. NGINX faces Ingress and the network;
# Audiobookshelf itself only ever listens on loopback.
RUN \
    apk add --no-cache \
        ffmpeg=8.1.2-r0 \
        nginx=1.30.4-r1 \
        nodejs=24.18.1-r0 \
    \
    && rm -f -r \
        /etc/nginx \
        /tmp/*

COPY --from=server /usr/src/audiobookshelf/index.js /opt/audiobookshelf/index.js
COPY --from=server /usr/src/audiobookshelf/package.json /opt/audiobookshelf/package.json
COPY --from=server /usr/src/audiobookshelf/server /opt/audiobookshelf/server
COPY --from=server /usr/src/audiobookshelf/node_modules /opt/audiobookshelf/node_modules
COPY --from=server /usr/local/lib/nusqlite3 /usr/local/lib/nusqlite3
COPY --from=client /usr/src/audiobookshelf/client/dist /opt/audiobookshelf/client/dist

# Copy root filesystem
COPY rootfs /

# Health check
HEALTHCHECK \
    CMD curl --silent --fail http://127.0.0.1:49000/status || exit 1

# Build arguments
ARG BUILD_ARCH
ARG BUILD_DATE
ARG BUILD_DESCRIPTION
ARG BUILD_NAME
ARG BUILD_REF
ARG BUILD_REPOSITORY
ARG BUILD_VERSION

# Labels
LABEL \
    io.hass.name="${BUILD_NAME}" \
    io.hass.description="${BUILD_DESCRIPTION}" \
    io.hass.arch="${BUILD_ARCH}" \
    io.hass.type="addon" \
    io.hass.version=${BUILD_VERSION} \
    maintainer="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.title="${BUILD_NAME}" \
    org.opencontainers.image.description="${BUILD_DESCRIPTION}" \
    org.opencontainers.image.vendor="Home Assistant Community Apps" \
    org.opencontainers.image.authors="Franck Nijhof <opensource@frenck.dev>" \
    org.opencontainers.image.licenses="MIT" \
    org.opencontainers.image.url="https://frenck.dev/home-assistant-apps" \
    org.opencontainers.image.source="https://github.com/${BUILD_REPOSITORY}" \
    org.opencontainers.image.documentation="https://github.com/${BUILD_REPOSITORY}/blob/main/README.md" \
    org.opencontainers.image.created=${BUILD_DATE} \
    org.opencontainers.image.revision=${BUILD_REF} \
    org.opencontainers.image.version=${BUILD_VERSION}
