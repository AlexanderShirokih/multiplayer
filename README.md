# MultiPlayer

Универсальный потоковый аудио-плеер для Android и iOS.

## Поддерживаемые платформы

| Платформа | Язык | UI |
|-----------|------|----|
| Android | Kotlin | Jetpack Compose |
| iOS | Swift | SwiftUI |

## Поддерживаемые сервисы

- **Yandex Music** *(в разработке)*

## Стек

- **Плеер**: [Kithara](https://github.com/zvuk/kithara) — модульный Rust audio engine (Kotlin и Swift биндинги)
- **HTTP**: Ktor Client
- **DI**: Koin
- **Архитектура**: Clean Architecture + MVVM

## Структура репозитория

Многомодульный проект с вертикальным (feature-based) разделением.

```
android/
├── app/            — точка входа, навигация
├── core/           — domain, data, ui, player (Kithara facade)
├── feature/        — auth, player, library, search
└── services/       — yandex (Yandex Music адаптер)

ios/
├── App/            — точка входа, навигация
├── Core/           — Domain, Data, UI, Player (Kithara facade)
├── Feature/        — Auth, Player, Library, Search
└── Services/       — YandexMusic

docs/               — документация и спецификации
AGENTS.md           — инструкции для AI-агентов
```

## Разработка

Инструкции для AI-агентов и архитектурные решения — в [AGENTS.md](AGENTS.md).
