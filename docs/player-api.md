# MultiPlayer — Player API

Этот документ будет заполняться по мере проектирования и реализации плеера.

## Движок

[Kithara](https://github.com/AlexanderShirokih/kithara) — Rust audio engine с Kotlin и Swift биндингами.  
Документация по API Kithara: https://docs.rs/kithara

## Статус

🟡 Спроектирован базовый UI-контракт мини-плеера для Android.

## Android Mini Player UI Contract

Мини-плеер вынесен в `android/core/player` и предназначен для переиспользования на разных экранах.

### Основные типы

- `NowPlayingStripExternalState` — подтверждённое состояние от контроллера плеера
- `NowPlayingStripState` — состояние, которое рисует Compose-компонент
- `NowPlayingStripAction` — действия UI
- `NowPlayingStripController` — интерфейс, через который UI отправляет команды и получает подтверждённое состояние
- `NowPlayingStripViewModel` — UDF-адаптер между controller и Compose

### Controller

```kotlin
interface NowPlayingStripController {
    val state: Flow<NowPlayingStripExternalState>

    suspend fun play()
    suspend fun pause()
    suspend fun skipNext()
    suspend fun skipPrevious()
    suspend fun seekTo(positionMs: Long)
}
```

### UDF правило

UI не делает optimistic update для `play`, `pause`, `previous`, `next`.

- Нажатие на control только отправляет команду в `NowPlayingStripController`
- Визуальное состояние меняется только после нового значения из `controller.state`
- Исключение только одно: во время drag по прогрессу `ViewModel` хранит локальный seek preview
- После завершения drag preview очищается, и UI снова опирается только на подтверждённое состояние контроллера
