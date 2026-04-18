# MultiPlayer

Универсальный потоковый аудио-плеер для Android и iOS.

## Дизайн

Макет приветственного экрана MultiPlayer для iOS с блоком входа через Яндекс Музыку

## Поддерживаемые платформы


| Платформа | Язык   | UI              |
| --------- | ------ | --------------- |
| Android   | Kotlin | Jetpack Compose |
| iOS       | Swift  | SwiftUI         |


## Поддерживаемые сервисы

- **Yandex Music** *(в разработке)*

## Структура репозитория

Многомодульный проект с вертикальным (feature-based) разделением.

```
android/
├── scripts/        — update-kithara-android.sh (AAR → android/libs/)
├── app/            — точка входа, навигация
├── core/           — domain, data, ui, player (Kithara facade)
├── feature/        — auth, player, library, search
└── services/       — yandex (Yandex Music адаптер)

ios/
├── Workspace.swift — манифест Tuist (генерация MultiPlayer.xcworkspace)
├── Tuist.swift     — конфигурация Tuist
├── Tuist/          — ProjectDescriptionHelpers
├── scripts/        — bootstrap-ios.sh (Kithara + tuist generate)
├── App/            — точка входа, навигация (Project.swift + Sources)
├── Core/           — Domain, Data, UI, Player (Kithara facade)
├── Feature/        — Auth, Player, Library, Search
└── Services/       — YandexMusic, KitharaPlayer

docs/               — документация и спецификации
AGENTS.md           — инструкции для AI-агентов
```

### iOS: сборка

Xcode-проект генерируется Tuist; подробности — [docs/build-setup.md](docs/build-setup.md). Кратко: `./ios/scripts/bootstrap-ios.sh`, затем открыть `ios/MultiPlayer.xcworkspace`.