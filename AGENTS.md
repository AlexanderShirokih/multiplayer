# MultiPlayer — Агентное окружение

Этот файл — единственный источник правды для всех AI-агентов (Cursor, Claude, Codex и др.).

---

## О проекте

**MultiPlayer** — универсальный потоковый аудио-плеер для нативных мобильных платформ.  
Архитектура: **полностью нативная** — два независимых проекта (Android, iOS) в одном репозитории, без общего кода.  
Движок плеера: **[Kithara](https://github.com/zvuk/kithara)**.  
Первый сервис: **Yandex Music**.

---

## Структура репозитория

Проект **многомодульный с вертикальным (feature-based) разделением**.

### Android (Gradle multi-module)

```
android/
├── app/                        # точка входа, Application, навигация
├── core/
│   ├── domain/                 # базовые модели и интерфейсы репозиториев
│   ├── data/                   # сетевая инфраструктура, Ktor, хранилище
│   ├── ui/                     # общие Compose-компоненты, тема, типографика
│   └── player/                 # facade над Kithara (движок плеера)
├── feature/
│   ├── auth/                   # авторизация (OAuth / Yandex ID)
│   ├── player/                 # экран воспроизведения (Now Playing)
│   ├── library/                # библиотека (избранное, плейлисты)
│   └── search/                 # поиск треков, исполнителей
└── services/
    └── yandex/                 # Yandex Music API адаптер
```

### iOS (Swift Package Manager local packages)

```
ios/
├── App/                        # точка входа, AppDelegate, корневая навигация
├── Core/
│   ├── Domain/                 # базовые модели и протоколы репозиториев
│   ├── Data/                   # сетевая инфраструктура, Ktor/URLSession, хранилище
│   ├── UI/                     # общие SwiftUI-компоненты, тема
│   └── Player/                 # facade над Kithara (движок плеера)
├── Feature/
│   ├── Auth/                   # авторизация (OAuth / Yandex ID)
│   ├── Player/                 # экран воспроизведения (Now Playing)
│   ├── Library/                # библиотека (избранное, плейлисты)
│   └── Search/                 # поиск треков, исполнителей
└── Services/
    └── YandexMusic/            # Yandex Music API адаптер
```

### Документация

```
docs/
└── player-api.md               # спецификация API плеера (заполняется по мере разработки)
```

---

## Абсолютные запреты

- Никакого KMP, Compose Multiplatform, Flutter, React Native
- Никакого XML layouts, `Fragment`, `LiveData` в Android
- Никакого UIKit в iOS (кроме точки входа `@main App`)
- Никаких completion handlers в iOS — только `async/await`
- Никаких хардкодных токенов и API-ключей
- Никакого Java, Objective-C

---

## Технологический стек


|                  | Android                    | iOS                      |
| ---------------- | -------------------------- | ------------------------ |
| Язык             | Kotlin                     | Swift                    |
| UI               | Jetpack Compose            | SwiftUI                  |
| Движок плеера    | Kithara (Kotlin биндинги)  | Kithara (Swift биндинги) |
| DI               | Koin                       | Koin                     |
| HTTP             | Ktor Client                | Ktor Client              |
| Авторизация      | OAuth 2.0 / Yandex ID      | OAuth 2.0 / Yandex ID    |
| Хранение токенов | EncryptedSharedPreferences | Keychain                 |


---

## Модульность

Разделение **вертикальное (feature-based)**. Три типа модулей:

| Тип | Примеры | Правило зависимостей |
|-----|---------|----------------------|
| `app` | `app/` | Знает обо всех модулях, собирает граф |
| `core/*` | `core/domain`, `core/ui`, `core/player` | Только между собой; `core/domain` — ни от кого |
| `feature/*` | `feature/player`, `feature/search` | Зависит только от `core/*` и `services/*`; **не зависит от других feature** |
| `services/*` | `services/yandex` | Зависит только от `core/domain` и `core/data` |

**Запрещено**: прямая зависимость `feature` → `feature`. Взаимодействие между фичами — только через `app` (навигация) или `core/domain` (общие модели).

---

## Архитектура

Clean Architecture, одинакова по смыслу на обеих платформах.

```
UI → ViewModel → UseCase → Repository (interface)
                                  ↓
                            Repository (impl) → Network / Cache / Player
```

- Domain не знает о data и ui; зависимости текут только внутрь
- UseCase = один сценарий, одна публичная операция
- DTO отдельно от доменных моделей; маппинг только в направлении `DTO → Domain`
- ViewModel хранит UI-состояние, принимает события от UI, вызывает UseCase — не репозиторий напрямую

---

## Android-конвенции

- Архитектура UI: **MVVM + UDF** — состояние вниз, события вверх
- State — иммутабельный `data class`, хранится в `StateFlow`
- События — `sealed interface`
- ViewModel: `viewModel()` через Koin, зависимости через конструктор
- Корутины только через `viewModelScope`, никакого `GlobalScope`
- Material 3: `MaterialTheme.colorScheme`, `MaterialTheme.typography`
- `@Preview(showBackground = true)` для каждого экранного компонента
- `*Route` — точка входа с ViewModel; `*Screen` — чистый компонент только с state и лямбдами

---

## iOS-конвенции

- Архитектура UI: **MVVM** — состояние вниз, действия вверх
- ViewModel: `@Observable` (iOS 17+), `@MainActor`
- State — `struct`, мутируется только внутри ViewModel
- Действия — `enum`
- DI через конструктор (initializer injection)
- `async throws` для всех сетевых операций
- `#Preview` для каждого компонента
- `*View` — точка входа, создаёт ViewModel; `*ContentView` — чистый компонент

---

## Плеер (Kithara)

[Kithara](https://github.com/zvuk/kithara) — модульный Rust audio engine.  
Биндинги: Kotlin (Android) и Swift (iOS). 

Ключевые модули:

- `kithara-play` — Engine, Player, Mixer (основной API воспроизведения)

**Принцип**: Kithara-типы не выходят за пределы `player/` слоя. ViewModel работает с нашим собственным интерфейсом, а не с Kithara напрямую.  
Спецификация API плеера — в `docs/player-api.md`.

---

## Yandex Music

- Авторизация: OAuth 2.0 через [Yandex ID](https://oauth.yandex.ru/)
- `client_id` / `client_secret` — только через конфиг: `local.properties` → `BuildConfig` (Android), `.xcconfig` → `Info.plist` (iOS)
- DTO отдельно от доменных моделей, маппинг при входе в domain-слой
- Логировать запросы в debug-сборке (без токенов в логах)
- Стрим-URL действителен ограниченное время — не кешировать дольше 30 минут
- Запросы к `/download-info` — только последовательно
- При 401 → один автоматический refresh; при повторном 401 → разлогин

---

## Соглашения

- Ветки: `feature/<platform>/<desc>`, `fix/<platform>/<desc>`
- Коммиты: `[android/ios] feat/fix/refactor: описание`
- Android: `*Screen`, `*ViewModel`, `*State`, `*Event`, `*UseCase`, `*Repository`
- iOS: `*View`, `*ViewModel`, `*State`, `*Action`, `*UseCase`, `*Repository`

---

## Текущий статус


| Этап                       | Статус       |
| -------------------------- | ------------ |
| Агентное окружение         | ✅ Готово     |
| Android: базовая структура | 🔲 В очереди |
| Android: плеер (Kithara)   | 🔲 В очереди |
| Android: Yandex Music      | 🔲 В очереди |
| Android: UI                | 🔲 В очереди |
| iOS: базовая структура     | 🔲 В очереди |
| iOS: плеер (Kithara)       | 🔲 В очереди |
| iOS: Yandex Music          | 🔲 В очереди |
| iOS: UI                    | 🔲 В очереди |


---

## Ссылки

- [Kithara](https://github.com/zvuk/kithara) / [docs.rs/kithara](https://docs.rs/kithara)
- [Yandex Music API (unofficial)](https://github.com/MarshalX/yandex-music-api)
- [Yandex Music OpenAPI spec](https://github.com/acherkashin/yandex-music-open-api/blob/main/src/yandex-music.yaml)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [SwiftUI](https://developer.apple.com/xcode/swiftui/)
- [Koin](https://insert-koin.io/)

