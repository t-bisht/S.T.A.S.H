#!/usr/bin/env bash
# docker_build.sh — build (and optionally push) a Stash backend service image.
#
# Multi-project repo. Each service lives under java/<service>/ with its own
# Dockerfile. Pass the subproject name as the first positional argument.
#
# Usage:
#   ./docker_build.sh user_engine                            # local build (arm64, --load)
#   ./docker_build.sh user_engine --push                     # multi-arch build + push
#   ./docker_build.sh user_engine --skip-bootjar             # reuse existing build/libs/*.jar
#   ./docker_build.sh user_engine --push --skip-bootjar
#
# Image: ${REGISTRY}/stash-<service-tag>:${VERSION}  (also tagged :latest)
# where <service-tag> = subproject name with underscores → dashes
# (e.g. user_engine → stash-user-engine).

set -euo pipefail

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------
REGISTRY="obake"
PLATFORM_PUSH="linux/amd64,linux/arm64"   # multi-platform for registry (--push)
PLATFORM_LOCAL="linux/arm64"              # native platform for local --load

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
log() { echo "[docker_build] $*"; }
err() { echo "[docker_build] ERROR: $*" >&2; }
die() { err "$*"; exit 1; }

# Read version from root build.gradle:  version = '0.1.0-SNAPSHOT'
# Anchored at start-of-line so a `version` reference inside a closure can't shadow.
get_version() {
    grep -m1 -E "^[[:space:]]*version[[:space:]]*=" build.gradle \
        | sed -E "s/.*version[[:space:]]*=[[:space:]]*['\"]([^'\"]+)['\"].*/\1/"
}

# Read archiveBaseName from the subproject's build.gradle, e.g. 'user-engine'.
get_jar_base_name() {
    local subproject="$1"
    grep -m1 -E "archiveBaseName" "${subproject}/build.gradle" \
        | sed -E "s/.*archiveBaseName[[:space:]]*=[[:space:]]*['\"]([^'\"]+)['\"].*/\1/"
}

run_bootjar() {
    local subproject="$1"
    log "Running ./gradlew :${subproject}:bootJar"
    ./gradlew --no-daemon ":${subproject}:bootJar"
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
if [[ $# -eq 0 ]] || [[ "${1:-}" == "-h" ]] || [[ "${1:-}" == "--help" ]]; then
    sed -n '2,15p' "$0"
    exit 0
fi

SUBPROJECT="$1"
shift

PUSH="false"
SKIP_BOOTJAR="false"

for arg in "$@"; do
    case "$arg" in
        --push)         PUSH="true" ;;
        --skip-bootjar) SKIP_BOOTJAR="true" ;;
        *)              die "Unknown argument: $arg" ;;
    esac
done

# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

[[ -f build.gradle ]]              || die "No root build.gradle at $SCRIPT_DIR"
[[ -d "$SUBPROJECT" ]]             || die "No subproject directory: ${SUBPROJECT}"
[[ -f "${SUBPROJECT}/Dockerfile" ]] || die "No Dockerfile at ${SUBPROJECT}/Dockerfile"
[[ -f "${SUBPROJECT}/build.gradle" ]] || die "No build.gradle at ${SUBPROJECT}/build.gradle"

VERSION=$(get_version)
[[ -n "$VERSION" ]] || die "Could not read version from root build.gradle"

JAR_BASE=$(get_jar_base_name "$SUBPROJECT")
[[ -n "$JAR_BASE" ]] || die "Could not read archiveBaseName from ${SUBPROJECT}/build.gradle"

# user_engine → stash-user-engine
IMAGE_NAME="stash-${JAR_BASE}"

if [[ "$SKIP_BOOTJAR" != "true" ]]; then
    run_bootjar "$SUBPROJECT"
else
    log "Skipping bootJar (--skip-bootjar)"
fi

JAR="${SUBPROJECT}/build/libs/${JAR_BASE}-${VERSION}.jar"
[[ -f "$JAR" ]] || die "Expected fat JAR not found at $JAR"

FULL_TAG="${REGISTRY}/${IMAGE_NAME}:${VERSION}"
LATEST_TAG="${REGISTRY}/${IMAGE_NAME}:latest"

if [[ "$PUSH" == "true" ]]; then
    log "Building and pushing  →  $FULL_TAG  ($PLATFORM_PUSH)"
    docker buildx build \
        --platform "$PLATFORM_PUSH" \
        -f "${SUBPROJECT}/Dockerfile" \
        -t "$FULL_TAG" \
        -t "$LATEST_TAG" \
        --push \
        "${SUBPROJECT}"
    log "Pushed    $FULL_TAG  and  $LATEST_TAG"
else
    log "Building  →  $FULL_TAG  ($PLATFORM_LOCAL, local only)"
    docker buildx build \
        --platform "$PLATFORM_LOCAL" \
        -f "${SUBPROJECT}/Dockerfile" \
        -t "$FULL_TAG" \
        -t "$LATEST_TAG" \
        --load \
        "${SUBPROJECT}"
    log "Built     $FULL_TAG  (not pushed)"
fi

log "Done"
