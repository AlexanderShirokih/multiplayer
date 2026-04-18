#!/usr/bin/env bash
set -euo pipefail

# Готовит локальную среду для iOS: Kithara XCFramework и tuist generate.
# Запуск: из корня репозитория — ./ios/scripts/bootstrap-ios.sh [--local-build]
# или из ios/ — ./scripts/bootstrap-ios.sh [--local-build]

print_usage() {
  cat <<'EOF'
Использование:
  ./ios/scripts/bootstrap-ios.sh [--local-build]

Опции:
  --local-build  Экспортирует KITHARA_LOCAL_DEV=1, чтобы SwiftPM использовал
                 локальный KitharaFFIInternal.xcframework вместо скачивания zip.
  -h, --help     Показать эту справку.
EOF
}

is_truthy() {
  case "${1:-}" in
    1|true|TRUE|yes|YES)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

USE_LOCAL_BUILD=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --local-build)
      USE_LOCAL_BUILD=1
      ;;
    -h|--help)
      print_usage
      exit 0
      ;;
    *)
      echo "Неизвестный аргумент: $1" >&2
      print_usage >&2
      exit 1
      ;;
  esac
  shift
done

if is_truthy "${KITHARA_LOCAL_DEV:-}"; then
  USE_LOCAL_BUILD=1
fi

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

if [[ "${USE_LOCAL_BUILD}" == "1" ]]; then
  export KITHARA_LOCAL_DEV=1
  echo "Включен локальный режим Kithara (KITHARA_LOCAL_DEV=1)."
fi

cd "${IOS_ROOT}"
tuist generate

echo "Готово. Откройте ios/MultiPlayer.xcworkspace в Xcode."
