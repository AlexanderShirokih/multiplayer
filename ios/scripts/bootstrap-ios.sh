#!/usr/bin/env bash
set -euo pipefail

# Готовит локальную среду для iOS: Kithara XCFramework и tuist generate.
# Запуск: из корня репозитория — ./ios/scripts/bootstrap-ios.sh
# или из ios/ — ./scripts/bootstrap-ios.sh

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
IOS_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${IOS_ROOT}/.." && pwd)"
LOCAL_XCCONFIG="${IOS_ROOT}/App/Configs/Local.xcconfig"

if [[ ! -f "${LOCAL_XCCONFIG}" ]]; then
  echo "Создайте ios/App/Configs/Local.xcconfig по образцу ios/App/Configs/Local.example.xcconfig" >&2
  exit 1
fi

# shellcheck disable=SC2002
KITHARA_DIR="$(
  sed -n 's/^[[:space:]]*KITHARA_DIR[[:space:]]*=[[:space:]]*//p' "${LOCAL_XCCONFIG}" | head -1 | tr -d '\r' | sed 's/^[[:space:]]*//;s/[[:space:]]*$//'
)"

if [[ -z "${KITHARA_DIR}" ]]; then
  echo "В ${LOCAL_XCCONFIG} не задан KITHARA_DIR" >&2
  exit 1
fi

if [[ ! -d "${KITHARA_DIR}" ]]; then
  echo "KITHARA_DIR указывает на несуществующий каталог: ${KITHARA_DIR}" >&2
  exit 1
fi

if [[ ! -f "${KITHARA_DIR}/justfile" ]]; then
  echo "В ${KITHARA_DIR} не найден justfile (ожидается клон репозитория Kithara)" >&2
  exit 1
fi

XC_FRAMEWORK="${KITHARA_DIR}/apple/KitharaFFIInternal.xcframework"
if [[ ! -d "${XC_FRAMEWORK}" ]]; then
  echo "Сборка XCFramework в Kithara (just xcframework)..."
  (cd "${KITHARA_DIR}" && just xcframework)
fi

if ! command -v tuist >/dev/null 2>&1; then
  echo "Установите Tuist: brew install tuist" >&2
  exit 1
fi

export KITHARA_DIR

cd "${IOS_ROOT}"
tuist generate

echo "Готово. Откройте ios/MultiPlayer.xcworkspace в Xcode."
