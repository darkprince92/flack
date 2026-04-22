# ── Stage 1: build ───────────────────────────────────────────
FROM --platform=linux/amd64 eclipse-temurin:11-jdk-focal AS builder

WORKDIR /flack

# Gradle wrapper — changes rarely; best cache layer
COPY gradle/ gradle/
COPY gradlew gradlew.bat* ./
RUN chmod +x gradlew

# Build scripts and local deps (needed at Gradle config time).
# AlloyFL/build.gradle and AlloyFL/lib/ are required because
# settings.gradle declares AlloyFL as a subproject.
COPY build.gradle settings.gradle ./
COPY libs/ libs/
COPY AlloyFL/build.gradle AlloyFL/build.gradle
COPY AlloyFL/lib/ AlloyFL/lib/

# Download all Maven/fileTree dependencies.
# This layer is only invalidated when build files or libs/ change,
# NOT when src/ changes — source-only rebuilds hit this cache.
RUN ./gradlew dependencies --no-daemon

# Source code (copied after deps to preserve the cache hit above)
COPY src/ src/

# Build fat JAR → build/libs/flack-1.0-all.jar
RUN ./gradlew shadowJar --no-daemon

# ── Stage 2: runtime ─────────────────────────────────────────
# JRE only; glibc (focal) required for native .so SAT solvers.
FROM --platform=linux/amd64 eclipse-temurin:11-jre-focal

WORKDIR /flack

# Fat JAR only (~3.3MB); build tools and source stay in the builder stage.
COPY --from=builder /flack/build/libs/flack-1.0-all.jar ./build/libs/

# Linux .so solver libraries only (macOS .dylib excluded by .dockerignore).
COPY solvers/*.so solvers/
COPY benchmark/ benchmark/

# -Djava.library.path=solvers is required for the JVM to load native solvers.
ENTRYPOINT ["java", "-Djava.library.path=solvers", "-cp", "build/libs/flack-1.0-all.jar", "flack.loc"]
