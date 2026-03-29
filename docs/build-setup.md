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

4. Сгенерировать Xcode workspace и открыть его:

```bash
./ios/scripts/bootstrap-ios.sh
```

Скрипт проверяет `KITHARA_DIR`, при необходимости запускает `just xcframework` в Kithara и выполняет `tuist generate` из каталога `ios/`. Альтернатива вручную: `cd ios && tuist generate` (при необходимости предварительно `export KITHARA_DIR=...`).

5. Открыть **`ios/MultiPlayer.xcworkspace`** в Xcode.

6. Проверка линтера (после правок Swift):

```bash
cd ios && swiftlint lint
```
