#!/usr/bin/env bash
# Automatically load .env if present
if [ -f .env ]; then
  echo "Loading local environment variables from .env..."
  set -a
  source .env
  set +a
fi

# Explicitly ensure local dev profile and disable origin enforcement for local dev
export SPRING_PROFILES_ACTIVE="dev"
export ORIGIN_VERIFY_ENFORCE="false"
export ORIGIN_VERIFY_ENABLED="false"

echo "Starting Spring Boot in local dev mode (profile: ${SPRING_PROFILES_ACTIVE})..."
exec ./mvnw spring-boot:run "$@"
