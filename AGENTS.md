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
│   ├── UI/                     # дизайн-система (локальный SPM CoreUI): SwiftUI-компоненты, тема, токены
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

### Agent skills

Дополнительные инструкции для агентов (формат Cursor skills): каталог `.agents/skills/<имя>/SKILL.md`.

---

## Абсолютные запреты

- Никакого KMP, Compose Multiplatform, Flutter, React Native
- Никакого XML layouts, `Fragment`, `LiveData` в Android
- Никакого UIKit в iOS (кроме точки входа `@main App`)
- Никаких completion handlers в iOS — только `async/await`
- Никаких хардкодных токенов и API-ключей
- Никакого Java, Objective-C
- **Нарушать правила архитектуры и модульности приложения** — запрещено: границы слоёв (Clean Architecture), направление зависимостей, правила `core` / `feature` / `services` и платформенные конвенции из этого файла нужно соблюдать; обходы "ради скорости" недопустимы без явного согласования и правки документации

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

Агентам: эти правила обязательны; см. также "Абсолютные запреты" (про архитектуру и модульность).

Clean Architecture, одинакова по смыслу на обеих платформах.

Ориентир для всего кода: **чистый и тестируемый** — понятные имена и границы ответственности, без лишней сложности; домен и use case не привязывать к UI и конкретным фреймворкам; зависимости задавать явно (интерфейсы, DI), чтобы критичную логику можно было проверять unit-тестами без тяжёлой инфраструктуры.

```
UI → ViewModel → UseCase → Repository (interface)
                                  ↓
                            Repository (impl) → Network / Cache / Player
```

- Domain не знает о data и ui; зависимости текут только внутрь
- UseCase = один сценарий, одна публичная операция
- DTO отдельно от доменных моделей; маппинг только в направлении `DTO → Domain`
- Где это уместно по смыслу — **семантические обёртки** вместо «голых» примитивов и строк: идентификаторы сущностей, длительность, единицы измерения и т.п., чтобы тип системы отражал назначение и не смешивал разные по смыслу значения. На Kotlin — `value class` или отдельные маленькие типы в domain; при необходимости **`typealias`** для читаемости сигнатур. На Swift — узкие `struct` с явным именем; при необходимости **`typealias`** (учитывая, что он не создаёт новый номинальный тип, в отличие от `struct`). Не использовать один общий `String`/`Int`/`UUID` для всего подряд без имени смысла.
- ViewModel хранит UI-состояние, принимает события от UI, вызывает UseCase — не репозиторий напрямую
- **Реактивная архитектура** — предпочтительна: данные и события моделируются как потоки во времени, UI подписывается на них, а не опрашивает состояние вручную и не размазывает одноразовые колбэки по слоям. Конкретные примитивы — в платформенных конвенциях ниже.

---

## Кросс-платформенный UI

- **Структурное соответствие**: информационная архитектура, иерархия экранов, навигация и состав ключевых сценариев на Android и iOS должны **согласованно** отражать один и тот же продукт (одни и те же пользовательские задачи и потоки).
- **Платформенные различия**: реализация следует идиомам каждой платформы (Material / HIG, привычные жесты, системные компоненты и паттерны — где это уместно). Паритет не означает пиксель-в-пиксель; он означает предсказуемую и по смыслу эквивалентную работу интерфейса.

---

## Android-конвенции

- Архитектура UI: **MVVM + UDF** — состояние вниз, события вверх
- Реактивность: **Kotlin Flow** — холодный `Flow` из репозиториев и data-слоя; для UI-состояния и разового потребления в ViewModel — `StateFlow` / `SharedFlow`; сборка в UI — `collectAsStateWithLifecycle` и аналоги. Избегать императивного «дергания» состояния без потока причин.
- State — иммутабельный `data class`, хранится в `StateFlow`
- События — `sealed interface`
- ViewModel: `viewModel()` через Koin, зависимости через конструктор
- Корутины только через `viewModelScope`, никакого `GlobalScope`
- Исключения: предпочитать явный **`try`/`catch`** вместо **`runCatching`** — яснее поток управления и обработка ошибок; `runCatching` не использовать как значение по умолчанию (допустим, если осознанно нужен `Result` и это согласовано с окружающим кодом).
- Базовая тема приложения поднимается через `MultiplayerDesignSystem` из `android/core/ui`
- Для app-specific UI использовать `MultiplayerTheme` и его токены (`colors`, `spacing`, `radius`, `elevation`, `icons`)
- Для базовых текстов и поверхностей предпочитать `MultiplayerText` и `MultiplayerSurface`
- Прямой `MaterialTheme` вне `core/ui` запрещён, кроме редких interop-случаев, где нужен Material API
- Не хардкодить цвета, отступы, скругления и elevation в feature-модулях — только через токены дизайн-системы
- Новые общие Compose-компоненты и preview helpers добавлять в `android/core/ui`, а не дублировать по feature-модулям
- Если Material 3 уже покрывает стандартную типографику или color semantics, использовать их через `MultiplayerTheme.typography` и `MultiplayerTheme.materialColorScheme`
- `@Preview(showBackground = true)` для каждого экранного компонента
- Для preview экранов использовать `MultiplayerPreview` или явно оборачивать контент в `MultiplayerDesignSystem`
- `*Route` — точка входа с ViewModel; `*Screen` — чистый компонент только с state и лямбдами
- **detekt** обязателен для Android-кода (см. подраздел ниже).
- **Gradle для агентов**: не передавать `./gradlew` флаг `--no-daemon` (оставляем поведение Gradle по умолчанию: переиспользование daemon ускоряет сборки и типичен для локальной разработки).

### detekt (Android)

- **Запуск**: из каталога `android/` выполнить `./gradlew detekt`. Успешное завершение без ошибок — часть проверки после правок Kotlin или Gradle в `android/`.
- **Подключение в модулях**: конвенция `multiplayer.detekt` подставляется вместе с Kotlin (`multiplayer.kotlin.library` для JVM-модулей; для Android — после `org.jetbrains.kotlin.android` или `org.jetbrains.kotlin.plugin.compose` в `build-logic`).
- **Конфигурация**: `android/config/detekt/detekt.yml`. Базовые правила Detekt 2 и [compose-rules](https://mrmans0n.github.io/compose-rules/detekt) (секция `Compose:`). Правила и пороги меняют здесь; общие отключения — только с кратким комментарием в YAML или в коде.
- **Jetpack Compose в Android-модулях**: в `build.gradle.kts` модуля с `multiplayer.android.library` или `multiplayer.android.application` добавлять `id("multiplayer.android.compose")` и `alias(libs.plugins.kotlin.compose)` (как в `app`, `core/ui`, фичах), чтобы единообразно включить Compose Compiler и зависимости BOM.
- **Подавления**: точечно — `@Suppress("ИмяПравила")` на объявлении (см. [документацию detekt](https://detekt.dev/docs/introduction/suppressing-rules)); для Compose-правил — id из отчёта (например `MagicNumber`, `ComposableNaming`). Глобально — `active: false` в `detekt.yml` или правка порога, если это осознанное соглашение команды.
- **Ссылки**: [detekt Gradle](https://detekt.dev/docs/gettingstarted/gradle), [compose-rules + detekt](https://mrmans0n.github.io/compose-rules/detekt).

---

## iOS-конвенции

- Соблюдать официальные **гайдлайны Apple**: [Human Interface Guidelines](https://developer.apple.com/design/human-interface-guidelines/) (поведение и внешний вид в духе платформы), [Swift API Design Guidelines](https://swift.org/documentation/api-design-guidelines/) (имена и форма публичного API), а также актуальную документацию по SwiftUI и Swift Concurrency. Умышленные отступления — только с явным обоснованием.
- **Дизайн-система**: локальный пакет **`ios/Core/UI`** (SwiftPM, product **`CoreUI`**, `import CoreUI`) — единственный слой общих SwiftUI-примитивов для приложения: тема (**`MultiplayerDesignSystem`**, **`MultiplayerTheme`**), токены (цвета, отступы, радиусы, elevation, иконки), переиспользуемые компоненты (**`MultiplayerText`**, **`MultiplayerSurface`**, фоны вроде **`MultiplayerBrandBackground`** и далее по мере развития). Экраны и корневой UI оборачивают контент в дизайн-систему и читают стиль из темы; **не хардкодить** произвольные цвета, отступы и скругления в `Feature/*` и `App` — только через токены и компоненты **`CoreUI`**, кроме осознанных исключений согласованно с командой.
- Новые общие SwiftUI-компоненты и preview helpers добавлять в **`ios/Core/UI`**, а не дублировать по feature-модулям.
- Архитектура UI: **MVVM** — состояние вниз, действия вверх
- Реактивность: только **`AsyncSequence` / `AsyncStream`** для асинхронных последовательностей значений во времени; подписка из SwiftUI — через `task` / `.task` и стандартные async-паттерны.
- ViewModel: `@Observable` (iOS 17+), `@MainActor`
- State — `struct`, мутируется только внутри ViewModel
- Действия — `enum`
- DI через конструктор (initializer injection)
- `async throws` для всех сетевых операций
- `#Preview` для каждого компонента
- `*View` — точка входа, создаёт ViewModel; `*ContentView` — чистый компонент
- Логически разделять экраны или крупные блоки по **отдельным файлам, по необходимости** — чтобы не раздувать один файл и сохранять читаемость и удобство навигации по коду

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
| Android: базовая структура | ✅ Готово     |
| Android: плеер (Kithara)   | 🔲 В очереди |
| Android: Yandex Music      | 🔲 В очереди |
| Android: UI                | 🔲 В очереди |
| iOS: базовая структура     | ✅ Готово     |
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
