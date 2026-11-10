# Headless match server (ARCH-033) — one authoritative match per process, reached over TCP (see
# server/launcher/ServerLauncher). Build from the repo root so the build context includes the
# gitignored assets/ directory:
#
#   docker build -f deploy/server/Dockerfile -t elderpath-server .
#
# assets/ (data/pieces.yaml, abilities.yaml, spells.yaml, relics.yaml, images, fonts, ...) is
# gitignored and NOT checked into this repo — it must already exist on disk wherever this image
# is built (a developer machine or CI with the real assets, never this cloud sandbox). See
# deploy/README.md for the full explanation and limitations of this image.

############################
# Build stage: assemble the server module's runnable distribution
############################
FROM eclipse-temurin:17-jdk AS build
WORKDIR /workspace

# Everything the server module's build needs: the multi-module Gradle project (settings.gradle
# declares core/server/cli/lwjgl3, so all four project directories must exist even though only
# core+server are actually compiled for :server:installDist) plus the real game assets.
COPY . .

RUN chmod +x gradlew && ./gradlew :server:installDist --no-daemon -x test

############################
# Runtime stage: just a JRE, the assembled distribution, and the assets it reads at startup
############################
FROM eclipse-temurin:17-jre AS runtime
RUN useradd --system --create-home --home-dir /app --shell /usr/sbin/nologin elderpath
WORKDIR /app

COPY --from=build /workspace/server/build/install/server/ ./
COPY --from=build /workspace/assets/ ./assets/
RUN chown -R elderpath:elderpath /app

USER elderpath

# 4567: the match's own TCP protocol (see elderpath-multiplayer-events.md). 8080: HTTP
# /healthz/live and /healthz/ready for an external prober (--health-port, see ServerLauncher).
EXPOSE 4567 8080

ENTRYPOINT ["bin/server"]
# Every flag here can be overridden by a Pod's `args:` (see deploy/k8s) — these are just
# reasonable defaults for "start a server, bind to all interfaces, report health on 8080".
CMD ["--bind", "0.0.0.0", "--port", "4567", "--assets", "assets", "--health-port", "8080", "--idle-timeout-s", "120"]
