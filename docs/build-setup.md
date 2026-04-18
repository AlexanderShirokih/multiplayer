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

4. Включить режим локального бинаря Kithara — переменная окружения `KITHARA_LOCAL_DEV=1`. Без неё `Package.swift` пакета Kithara пытается скачать `KitharaFFIInternal.xcframework.zip` из релиза на GitHub и завершается ошибкой `404`; с ней SwiftPM подхватывает локальный `KITHARA_DIR/apple/KitharaFFIInternal.xcframework`. Экспортировать в той же сессии, из которой запускается генерация и `xcodebuild`:

```bash
export KITHARA_LOCAL_DEV=1
```

5. Сгенерировать Xcode workspace и открыть его:

```bash
KITHARA_LOCAL_DEV=1 ./ios/scripts/bootstrap-ios.sh
```

Скрипт проверяет `KITHARA_DIR`, при необходимости запускает `just xcframework` в Kithara и выполняет `tuist generate` из каталога `ios/`. Альтернатива вручную: `cd ios && KITHARA_LOCAL_DEV=1 tuist generate` (при необходимости предварительно `export KITHARA_DIR=...`).

6. Открыть `ios/MultiPlayer.xcworkspace` в Xcode. Если запускать `xcodebuild` из терминала, переменная `KITHARA_LOCAL_DEV=1` тоже должна быть в окружении при `-resolvePackageDependencies` и сборке, например:

```bash
cd ios
KITHARA_LOCAL_DEV=1 xcodebuild \
  -workspace MultiPlayer.xcworkspace \
  -scheme MultiPlayer \
  -configuration Debug \
  -destination 'generic/platform=iOS Simulator' \
  -derivedDataPath .derived-data \
  build
```

7. Проверка линтера (после правок Swift):

```bash
cd ios && swiftlint lint
```
