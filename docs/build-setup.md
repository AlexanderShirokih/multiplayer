# Build Setup

## Предварительные требования

- Локально склонирован репозиторий `kithara`
- Установлены Rust toolchain, `cargo-ndk`, `cargo-swift` и `just`
- Для Android настроен `ANDROID_NDK_HOME`

## Android

1. Собрать и скопировать Android-артефакты из `kithara`:

```bash
KITHARA_DIR=/path/to/kithara ./scripts/update-kithara-android.sh
```

2. Открыть `android/` в Android Studio или собрать через Gradle.

Артефакты копируются в `android/libs/` и не коммитятся в git.

## iOS

1. Собрать локальный XCFramework в репозитории `kithara`:

```bash
cd /path/to/kithara && just xcframework
```

2. Создать `ios/App/Configs/Local.xcconfig` на основе `Local.example.xcconfig`.
3. Задать в локальной конфигурации:

```xcconfig
KITHARA_DIR = /path/to/kithara
KITHARA_LOCAL_DEV = 1
```

4. Открыть `ios/App/MultiPlayer.xcodeproj` и собрать приложение.
