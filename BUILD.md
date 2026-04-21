# Build Setup

## Предварительные требования

- Локально склонирован репозиторий `kithara`
- Установлены Rust toolchain, `cargo-ndk`, `cargo-swift` и `just`
- Для Android настроен `ANDROID_NDK_HOME`
- Для iOS: [Tuist](https://docs.tuist.dev) (`brew install tuist`), при необходимости [SwiftLint](https://github.com/realm/SwiftLint) (`brew install swiftlint`)

## Android

1. Собрать и скопировать Android-артефакты из `kithara`:

```bash
KITHARA_DIR=/path/to/kithara ./android/scripts/update-kithara-android.sh
```

2. Открыть `android/` в Android Studio или собрать через Gradle.

Артефакты копируются в `android/libs/` и не коммитятся в git.

## iOS

Источник правды для Xcode — манифесты Tuist (`ios/Workspace.swift`, `ios/Tuist.swift`, `ios/**/Project.swift`).

1. Собрать локальный XCFramework в репозитории `kithara`:

```bash
cd /path/to/kithara && just xcframework
```

2. Создать `ios/App/Configs/Local.xcconfig` на основе `Local.example.xcconfig`.
3. Задать путь к клону Kithara:

```xcconfig
KITHARA_DIR = /path/to/kithara
```

4. Если нужен локальный бинарь Kithara, включить режим локальной сборки. Без него `Package.swift` пакета Kithara пытается скачать `KitharaFFIInternal.xcframework.zip` из релиза на GitHub и может завершиться ошибкой `404`; с локальным режимом SwiftPM подхватывает `KITHARA_DIR/apple/KitharaFFIInternal.xcframework`.

Для `bootstrap-ios.sh` есть явный флаг:

```bash
./ios/scripts/bootstrap-ios.sh --local-build
```

Он экспортирует `KITHARA_LOCAL_DEV=1` на время `tuist generate`.

5. Если запускать `tuist`, `tuist test` или `xcodebuild` вручную, переменная `KITHARA_LOCAL_DEV=1` по-прежнему должна быть в окружении:

```bash
export KITHARA_LOCAL_DEV=1
```

6. Сгенерировать Xcode workspace и открыть его:

```bash
./ios/scripts/bootstrap-ios.sh --local-build
```

Скрипт проверяет `KITHARA_DIR`, при необходимости запускает `just xcframework` в Kithara и выполняет `tuist generate` из каталога `ios/`. Альтернатива вручную: `cd ios && KITHARA_LOCAL_DEV=1 tuist generate` (при необходимости предварительно `export KITHARA_DIR=...`).

7. Открыть `ios/MultiPlayer.xcworkspace` в Xcode. Если запускать `xcodebuild` из терминала, переменная `KITHARA_LOCAL_DEV=1` тоже должна быть в окружении при `-resolvePackageDependencies` и сборке, например:

```bash
cd ios
KITHARA_LOCAL_DEV=1 xcodebuild \
  -workspace MultiPlayer.xcworkspace \
  -scheme MultiPlayer \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  build
```

8. Проверка линтера (после правок Swift):

```bash
cd ios && swiftlint lint
```
