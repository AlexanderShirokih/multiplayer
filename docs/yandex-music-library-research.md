# Yandex Music API: плейлисты и списки треков

## Цель

Изучить часть Yandex Music API, которая нужна для:

- получения списка плейлистов пользователя;
- получения конкретного плейлиста вместе с треками;
- получения списка треков из "Мне нравится";
- догрузки полных данных по трекам.

Дополнительно: спроектировать устойчивые к изменениям API модели данных и read-only репозиторий, которые можно реализовать в архитектуре `core/domain` + `services/yandex`.

## Источники

1. OpenAPI-спецификация: `https://github.com/acherkashin/yandex-music-open-api/blob/main/src/yandex-music.yaml`
2. Документация неофициального клиента:
   - `https://yandex-music.readthedocs.io/en/main/yandex_music.client.html`
   - `https://yandex-music.readthedocs.io/en/main/yandex_music.playlist.html`
   - `https://yandex-music.readthedocs.io/en/main/yandex_music.tracks_list.html`
   - `https://yandex-music.readthedocs.io/en/main/yandex_music.track_short.html`
3. README клиента MarshalX: `https://github.com/MarshalX/yandex-music-api`

## Результат живой проверки через `curl`

Проверка выполнена вручную через OAuth authorization code + PKCE и затем через прямые HTTP-запросы к боевым endpoint'ам.

Что удалось подтвердить:

- `POST https://oauth.yandex.ru/token` работает и выдает `access_token` и `refresh_token`;
- `GET https://login.yandex.ru/info?format=json` работает и возвращает профиль пользователя;
- `GET https://api.music.yandex.net/account/status` работает.

Что показала живая проверка в двух окружениях:

1. В окружении с включенным VPN:
   - `GET /account/status` вернул `200`;
   - в `result.account` пришло `serviceAvailable: false`;
   - `GET /users/{userId}/playlists/list` вернул `451 Unavailable For Legal Reasons`;
   - `GET /users/{userId}/likes/tracks` тоже вернул `451 Unavailable For Legal Reasons`.

2. После отключения VPN:
   - `GET /account/status` вернул `200`;
   - `result.account.serviceAvailable = true`;
   - `result.account.region = 225`;
   - `GET /users/{userId}/playlists/list` вернул `200`;
   - `GET /users/{userId}/playlists/{kind}` вернул `200`;
   - `GET /users/{userId}/playlists?kinds=...&mixed=false&rich-tracks=false` вернул `200`;
   - `POST /tracks/` вернул `200`;
   - `GET /users/{userId}/likes/tracks` вернул `200`, но payload не совпал со спецификацией.

Фактический ответ `account/status` в рабочем окружении содержал:

- `result.account.region = 225`
- `result.account.serviceAvailable = true`
- `result.permissions.values = ["landing-play", "feed-play", "mix-play"]`

Вывод:

- доступность Yandex Music API зависит от окружения;
- проблема `451` была не в OAuth flow и не в endpoint'ах как таковых, а в `serviceAvailable: false`;
- после перехода в рабочее окружение удалось подтвердить реальные payload'ы playlist endpoint'ов и `POST /tracks/`;
- `likes/tracks` требует отдельной осторожности: в живой проверке endpoint вернул `200`, но `result` оказался строкой `"private-library"`, а не `TracksList`.

Практическое решение:

- перед интеграцией music-endpoint'ов сначала проверять `GET /account/status`;
- если `serviceAvailable == false`, дальнейшую проверку и реализацию нужно считать заблокированной внешним ограничением и эскалировать пользователю;
- `likes/tracks` нужно проектировать как endpoint с вариативным ответом, а не как гарантированный `TracksList`.

## Релевантные эндпоинты

### 0. Проверка доступности сервиса

`GET /account/status`

Назначение:

- понять, доступен ли вообще Yandex Music API для текущей сессии и окружения;
- отделить проблемы авторизации от внешних ограничений сервиса.

Что важно читать в ответе:

- `result.account.serviceAvailable`
- `result.account.region`
- `result.permissions.values`

Фактическое наблюдение по live-проверке:

- endpoint работает;
- при `serviceAvailable: false` следующие music-endpoint'ы возвращают `451`;
- при `serviceAvailable: true` playlist endpoint'ы начинают отвечать штатно.

Вывод для проекта:

- `account/status` нужно считать обязательным preflight-check перед диагностикой library/playlists API.

### 1. Список плейлистов пользователя

`GET /users/{userId}/playlists/list`

Назначение:

- получить список плейлистов пользователя;
- использовать для экрана библиотеки или списка коллекций.

Возвращает:

- `result: Playlist[]`

Важное замечание:

- документация `yandex-music` отдельно указывает, что этот вызов не возвращает полноценный список треков плейлиста; для загрузки треков нужно вызывать `users_playlists()` или `Playlist.fetch_tracks()`.

Вывод для проекта:

- это endpoint для `PlaylistSummary`, а не для полной модели плейлиста.
- живая форма ответа подтверждена;
- фактически в live-ответе пришли поля:
  - `owner`
  - `playlistUuid`
  - `available`
  - `uid`
  - `kind`
  - `title`
  - `revision`
  - `snapshot`
  - `trackCount`
  - `visibility`
  - `collective`
  - `created`
  - `modified`
  - `isBanner`
  - `isPremiere`
  - `durationMs`
  - `cover`
  - `ogImage`
  - `tags`
  - `customWave`
  - `derivedColors`
- поля вроде `description`, `likesCount` и `tracks` в этом ответе не пришли.

### 2. Один плейлист по `kind`

`GET /users/{userId}/playlists/{kind}`

Назначение:

- загрузить один конкретный плейлист;
- использовать на экране деталей плейлиста.

Возвращает:

- `result: Playlist`

Практический смысл:

- основной endpoint для детального представления плейлиста;
- именно здесь имеет смысл ожидать список `tracks` и метаданные синхронизации (`revision`, `snapshot`).

Статус live-проверки:

- подтверждено.

Фактически в live-ответе дополнительно пришли:

- `likesCount`
- `pager`
- `tracks`
- `lastOwnerPlaylists`
- `hasTrailer`
- `trailer`

Форма `tracks` в live-ответе:

- элементы верхнего массива имели поля:
  - `id`
  - `originalIndex`
  - `originalShuffleIndex`
  - `recent`
  - `timestamp`
  - `track`
- вложенный `track` был полным rich-объектом трека.

### 3. Несколько плейлистов по идентификаторам

`GET /users/{userId}/playlists?kinds=1000,1003&mixed=false&rich-tracks=false`

Назначение:

- пакетно получить несколько плейлистов по `kind`.

Возвращает:

- `result: Playlist[]`

Параметры:

- `kinds`: список `kind`;
- `mixed`: `boolean`;
- `rich-tracks`: `boolean`.

Практический смысл:

- удобно для батч-загрузки уже известных плейлистов;
- `rich-tracks=false` подтверждает, что API поддерживает "облегченный" вариант треков.

Статус live-проверки:

- подтверждено.

Фактическое поведение при `rich-tracks=false`:

- endpoint все равно вернул поле `tracks`;
- но внутри `tracks` пришли короткие элементы вида:
  - `timestamp`
  - `id`
  - `albumId`

Вывод для проекта:

- `rich-tracks=false` не означает отсутствие `tracks`;
- это означает short track representation вместо rich nested track.

### 4. Треки "Мне нравится"

`GET /users/{userId}/likes/tracks`

Назначение:

- получить библиотеку liked tracks пользователя.

Возвращает:

- `result.library: TracksList`

Важная деталь:

- в ответе есть дополнительная обертка `library`, то есть структура отличается от прямого `result: TracksList`.

Практический смысл:

- нужен отдельный DTO для верхнего ответа, а не только для `TracksList`.

Статус live-проверки:

- endpoint подтвержден только частично.

Фактическое live-наблюдение:

- endpoint вернул `200`;
- но `result` оказался строкой `"private-library"`, а не объектом.

Вывод для проекта:

- `likes/tracks` нельзя моделировать как всегда-успешный `TracksList`;
- нужен вариант ответа для sentinel-значения приватной или недоступной библиотеки.

### 5. Полные данные по трекам

`POST /tracks/`

Body:

- `track-ids: string[]`
- `with-positions: boolean`

Назначение:

- догрузить полные `Track` по списку коротких идентификаторов.

Практический смысл:

- нужен как второй шаг после `likes/tracks`;
- нужен как резервный путь, если плейлист пришел с неполными `tracks`.

Статус live-проверки:

- подтверждено.

Фактическое live-поведение:

- `POST /tracks/` успешно вернул `200`;
- это read-only endpoint, хотя используется метод `POST`;
- при передаче form body с несколькими `track-ids` endpoint вернул массив полных `Track`.

Вывод для проекта:

- этот `POST` действительно нужен для чтения и enrichment;
- read-only поведение endpoint'а надо отдельно зафиксировать в реализации и тестах, чтобы команда не восприняла его как mutation API.

## Что именно возвращает API

### Плейлист

По OpenAPI `Playlist` содержит много полей, но для приложения на текущем этапе реально полезны следующие:

- `kind`
- `playlistUuid`
- `uid`
- `title`
- `description`
- `cover`
- `ogImage`
- `owner`
- `trackCount`
- `durationMs`
- `visibility`
- `likesCount`
- `revision`
- `snapshot`
- `tracks`

Поля высокого риска по стабильности или полезности:

- `backgroundColor`
- `textColor`
- `tags`
- `prerolls`
- branding/open graph поля

Вывод:

- в домен не стоит тащить весь `Playlist` 1:1;
- нужен отдельный слой DTO, а в домен следует маппить только устойчивую и продуктово значимую часть.

## Список liked tracks

`TracksList` по спецификации содержит:

- `uid`
- `revisions` в OpenAPI
- `tracks: TrackShort[]`

Но документация `yandex-music` описывает объект как:

- `uid`
- `revision`
- `tracks`

Вывод:

- есть несовпадение `revision` vs `revisions`;
- DTO-декодер должен уметь принимать оба варианта.
- кроме этого, живой endpoint может вернуть не объект, а строковый sentinel `"private-library"`.

## Короткая версия трека

Здесь есть два близких, но не идентичных представления:

1. `TrackShort` из liked tracks:
   - `id`
   - `albumId`
   - `timestamp`

2. `TrackItem` внутри `Playlist.tracks`:
   - `id`
   - `playCount`
   - `recent`
   - `timestamp`
   - `track` (может быть `null`, если треки не enriched)

Документация Python-клиента фактически смешивает эти варианты в один класс `TrackShort`, где опционально появляются:

- `album_id`
- `play_count`
- `recent`
- `track`
- `original_index`

Вывод:

- в `services/yandex` лучше иметь собственный DTO, который покрывает оба случая;
- в домен лучше выносить уже нормализованный `PlaylistTrackEntry`.

## Полный трек

`Track` содержит существенно больше данных. Для текущих сценариев библиотеки и плейлиста достаточно считать базовыми:

- `id`
- `title`
- `durationMs`
- `available`
- `availableForPremiumUsers`
- `coverUri`
- `ogImage`
- `artists`
- `albums`
- `lyricsAvailable`

Поля вроде `fileSize`, `storageDir`, `normalization`, `major` важны не для UI библиотеки, а скорее для playback/download сценариев.

Вывод:

- доменная модель трека должна быть компактнее полного API-объекта;
- если позже плееру понадобятся дополнительные поля, их лучше расширять отдельно, а не зашивать весь DTO в домен заранее.

## Ключевые наблюдения и риски

1. API неофициальный.
   Это главный риск. Нельзя проектировать домен как прямое отражение YAML.

2. Один и тот же смысл представлен разными структурами.
   Лайкнутые треки приходят через `TracksList -> TrackShort`, а треки плейлиста через `Playlist.tracks -> TrackItem`.

3. Список плейлистов и детали плейлиста нужно разделять.
   `playlists/list` подходит для карточек коллекций, но не для экрана деталей.

4. Ответ liked tracks имеет лишнюю вложенность.
   Верхний `result.library` нельзя игнорировать при декодировании.

5. Есть несостыковки в названиях полей.
   Самый явный пример: `revision` vs `revisions`.

6. Идентификатор трека бывает составным.
   В документации клиента встречается формат `trackId:albumId`. Даже если API местами принимает просто `trackId`, доменная ссылка на трек должна уметь хранить и `albumId`.

7. Доступность Yandex Music API нужно проверять отдельно от OAuth.
   В live-проверке OAuth и `login.yandex.ru/info` сработали корректно, но `account/status` вернул `serviceAvailable: false`, после чего playlist и library endpoint'ы стали возвращать `451`.

8. `likes/tracks` имеет вариативный shape ответа.
   В документации ожидается `TracksList`, но в живом ответе для текущего аккаунта был получен sentinel `result = "private-library"`.

9. `playlists/list` и `playlists?...rich-tracks=false` действительно разные по полноте.
   Summary endpoint не вернул `tracks`, а batch endpoint с `rich-tracks=false` вернул короткие track-entries.

## Рекомендация по архитектуре

### Граница модулей

Рекомендуемое разделение:

- `core/domain`
  - доменные модели библиотеки и треков;
  - интерфейс репозитория только для чтения.
- `services/yandex`
  - DTO Yandex Music;
  - удаленный data source;
  - mapper `DTO -> Domain`;
  - реализация репозитория.

Это соответствует `AGENTS.md`:

- `feature/*` не знает о формате Yandex API;
- `services/*` зависят только от `core/domain` и `core/data`;
- DTO не выходят наружу.

### Почему доменные модели должны быть не-Yandex-specific

Несмотря на то что первым сервисом будет Yandex Music, экран библиотеки и экран плейлиста по смыслу продуктовые, а не API-специфичные. Поэтому:

- в `services/yandex` остаются DTO с полями и странностями API;
- в `core/domain` стоит хранить нормализованные модели `PlaylistSummary`, `Playlist`, `TrackPreview`, `Track`.

Исключение допустимо для семантических source-id:

- `YandexPlaylistKind`
- `YandexUserId`
- `YandexTrackId`

Их можно сохранить в домене как узкие типы, если на текущем этапе проект сознательно не строит мультипровайдерную абстракцию.

## Предлагаемая доменная модель

Ниже не буквальная реализация, а целевой shape для Android и iOS.

### Семантические идентификаторы

- `MusicProviderId`
- `ProviderUserId`
- `PlaylistKind`
- `PlaylistUuid`
- `TrackId`
- `AlbumId`
- `PlaylistRevision`
- `PlaylistSnapshot`
- `TrackListRevision`

### Опорные value objects

```kotlin
enum class MusicProviderId {
    YANDEX_MUSIC,
}

@JvmInline
value class ProviderUserId(val value: String)

@JvmInline
value class PlaylistKind(val value: Long)

@JvmInline
value class PlaylistUuid(val value: String)

@JvmInline
value class TrackId(val value: String)

@JvmInline
value class AlbumId(val value: String)

data class TrackRef(
    val trackId: TrackId,
    val albumId: AlbumId?,
)

data class PlaylistId(
    val ownerId: ProviderUserId,
    val kind: PlaylistKind,
)

data class PlaylistVersion(
    val revision: Long,
    val snapshot: Long?,
)
```

### Карточка плейлиста для списка

```kotlin
data class PlaylistSummary(
    val id: PlaylistId,
    val provider: MusicProviderId,
    val playlistUuid: PlaylistUuid?,
    val title: String,
    val ownerName: String?,
    val coverUriTemplate: String?,
    val trackCount: Int,
    val durationMs: Long?,
    val isAvailable: Boolean,
    val isCollective: Boolean,
    val visibility: PlaylistVisibility?,
)

enum class PlaylistVisibility {
    PUBLIC,
    PRIVATE,
}
```

Почему так:

- `playlistUuid` оставляем опциональным, потому что полагаться только на него рискованно;
- основной идентификатор для загрузки деталей все равно `(ownerId, kind)`;
- `description` и `likesCount` не включаем в summary, потому что они не пришли в подтвержденном live-ответе `playlists/list`;
- цвета, брендинг и похожие декоративные поля не поднимаем в домен сразу.

### Детали плейлиста

```kotlin
data class Playlist(
    val summary: PlaylistSummary,
    val version: PlaylistVersion?,
    val tracks: List<PlaylistTrackEntry>,
)

data class PlaylistTrackEntry(
    val position: Int,
    val addedAt: String?,
    val originalIndex: Int?,
    val originalShuffleIndex: Int?,
    val isRecent: Boolean?,
    val track: TrackPreview?,
    val trackRef: TrackRef,
)
```

Почему так:

- `position` нужен UI и очереди воспроизведения;
- `originalIndex` и `originalShuffleIndex` подтверждены живым ответом `playlists/{kind}`;
- `track` делаем nullable, потому что API может прислать только ссылку без rich-данных;
- `trackRef` обязателен: это минимальная стабильная единица для последующей догрузки.

### Liked tracks

```kotlin
data class SavedTracks(
    val ownerId: ProviderUserId,
    val revision: Long?,
    val tracks: List<SavedTrackEntry>,
)

data class SavedTrackEntry(
    val position: Int,
    val addedAt: String?,
    val trackRef: TrackRef,
    val track: TrackPreview?,
)
```

Почему это отдельная модель, а не просто `List<TrackPreview>`:

- у liked tracks есть свой revision-token;
- важен порядок;
- short-ответ может быть неполным и требовать batch enrichment через `/tracks/`.

Ограничение:

- живая форма `SavedTracks` пока не подтверждена, потому что реальный `GET /users/{userId}/likes/tracks` для текущего аккаунта вернул sentinel `"private-library"`;
- поэтому эта модель остается проектной, а не подтвержденной live-данными.

### Превью трека и полный трек

```kotlin
data class TrackPreview(
    val ref: TrackRef,
    val title: String,
    val artists: List<ArtistPreview>,
    val albumTitle: String?,
    val durationMs: Long?,
    val coverUriTemplate: String?,
    val isAvailable: Boolean,
)

data class Track(
    val preview: TrackPreview,
    val lyricsAvailable: Boolean,
    val isAvailableForPremium: Boolean,
    val isAvailableWithoutPermission: Boolean,
)

data class ArtistPreview(
    val id: String,
    val name: String,
)
```

Почему `Track` отделен от `TrackPreview`:

- список библиотеки чаще всего рисуется из превью;
- полный трек нужен реже;
- это уменьшает связность и объем обязательных полей.

## Предлагаемые DTO в `services/yandex`

Ниже минимальный набор DTO, который покрывает исследованные ответы.

```text
YandexUserPlaylistsListResponseDto
YandexPlaylistResponseDto
YandexLikedTracksResponseDto
YandexTracksListDto
YandexPlaylistDto
YandexPlaylistTrackItemDto
YandexTrackShortDto
YandexTrackDto
YandexAlbumDto
YandexArtistDto
YandexCoverDto
YandexOwnerDto
```

### Важные правила для DTO-слоя

1. `tracks` внутри `YandexPlaylistDto` должны быть опциональными.
   На `playlists/list` на них нельзя полагаться.

2. `YandexTracksListDto` должен поддерживать оба ключа:
   - `revision`
   - `revisions`

3. `YandexPlaylistTrackItemDto` и `YandexTrackShortDto` лучше не смешивать насильно.
   У них похожая цель, но разная форма ответа.

4. Для `TrackRef` нужно хранить:
   - raw `trackId`;
   - optional `albumId`.

5. URI обложек лучше хранить как raw/template value.
   Нормализацию размера и формата лучше вынести в отдельный helper.

## Репозиторий

### Целевой подход

Для продукта целевым нужно считать:

- `Flow`-first контракт на Android;
- offline-first архитектуру репозитория.

Что это означает:

- UI и use case читают данные из потоков, а не из разовых network-запросов;
- локальное хранилище становится основным источником истины для библиотеки;
- сеть используется как слой синхронизации, который обновляет локальные данные;
- даже при недоступной сети `observe`-слой должен продолжать отдавать последнее сохраненное состояние.

Важно:

- текущий Android-каркас в `services:yandex` пока remote-first;
- его нужно считать временной технической реализацией для валидации endpoint'ов, ошибок и маппинга;
- финальная форма должна быть offline-first.

### Что рекомендую сделать сейчас

На первом этапе достаточно репозитория только для чтения, без записи и без локальной синхронизации.

Причины:

- задача исследования ограничена чтением;
- API snapshot-oriented;
- кеширование, операции записи и optimistic update можно добавить позже отдельно;
- так проще удержать границы ответственности.

Но к этому нужно добавить еще одно правило:

- реализация должна уметь отдельно сигнализировать состояние "music service unavailable", а не сводить его к generic network error.
- этот шаг нужно считать промежуточным перед переходом к `Flow`-first и offline-first реализации.

### Финальный минимальный контракт `core/domain`

Это рекомендуемый минимальный набор сущностей для первой реализации. Здесь оставлены только:

- поля, подтвержденные живыми ответами;
- поля, без которых нельзя устойчиво открыть плейлист, отрисовать список и догрузить треки.

Ниже shape самих сущностей. Для Android целевой контракт репозитория при этом должен быть `Flow`-first.

```kotlin
enum class MusicProviderId {
    YANDEX_MUSIC,
}

@JvmInline
value class ProviderUserId(val value: String)

@JvmInline
value class PlaylistKind(val value: Long)

@JvmInline
value class PlaylistUuid(val value: String)

@JvmInline
value class TrackId(val value: String)

@JvmInline
value class AlbumId(val value: String)

data class PlaylistId(
    val ownerId: ProviderUserId,
    val kind: PlaylistKind,
)

data class TrackRef(
    val trackId: TrackId,
    val albumId: AlbumId?,
)

enum class PlaylistVisibility {
    PUBLIC,
    PRIVATE,
}

data class PlaylistSummary(
    val id: PlaylistId,
    val provider: MusicProviderId,
    val playlistUuid: PlaylistUuid?,
    val title: String,
    val ownerName: String?,
    val coverUriTemplate: String?,
    val trackCount: Int,
    val durationMs: Long?,
    val isAvailable: Boolean,
    val isCollective: Boolean,
    val visibility: PlaylistVisibility?,
)

data class Playlist(
    val summary: PlaylistSummary,
    val revision: Long?,
    val snapshot: Long?,
    val likesCount: Int?,
    val tracks: List<PlaylistTrackEntry>,
)

data class PlaylistTrackEntry(
    val position: Int,
    val addedAt: String?,
    val originalIndex: Int?,
    val originalShuffleIndex: Int?,
    val isRecent: Boolean?,
    val trackRef: TrackRef,
    val track: Track?,
)

data class TrackPreview(
    val ref: TrackRef,
    val title: String,
    val artists: List<ArtistPreview>,
    val durationMs: Long?,
    val coverUriTemplate: String?,
    val isAvailable: Boolean,
)

data class Track(
    val preview: TrackPreview,
    val lyricsAvailable: Boolean,
    val isAvailableForPremium: Boolean,
    val isAvailableWithoutPermission: Boolean,
)

data class ArtistPreview(
    val id: String,
    val name: String,
)

data class MusicServiceAvailability(
    val isAvailable: Boolean,
    val region: Int?,
    val permissions: Set<String>,
)

sealed interface SavedTracksResult {
    data class Available(
        val value: SavedTracks,
    ) : SavedTracksResult

    data object PrivateLibrary : SavedTracksResult
}

interface MusicLibraryRepository {
    fun observeAvailability(): Flow<MusicServiceAvailability>
    fun observeOwnPlaylists(): Flow<List<PlaylistSummary>>
    fun observePlaylist(id: PlaylistId): Flow<Playlist?>
    fun observeSavedTracks(): Flow<SavedTracksResult>
    fun observeTracks(refs: List<TrackRef>): Flow<List<Track>>

    suspend fun refreshAvailability()
    suspend fun refreshOwnPlaylists()
    suspend fun refreshPlaylist(id: PlaylistId)
    suspend fun refreshSavedTracks()
    suspend fun refreshTracks(refs: List<TrackRef>)
}
```

Пояснения к этому минимальному контракту:

- `PlaylistSummary` намеренно не содержит `description` и `likesCount`, потому что они не пришли в live-ответе `playlists/list`;
- `Playlist` уже содержит `likesCount`, потому что оно подтвердилось в `playlists/{kind}`;
- `PlaylistTrackEntry.track` в минимальном контракте уже `Track`, а не `TrackPreview`, потому что live-ответ `playlists/{kind}` действительно приносит rich nested track;
- `observe*`-методы нужны как стабильный контракт для UI и offline-first слоя;
- `refresh*`-методы нужны как явная команда синхронизации с сетью;
- `refreshTracks()` оставляем отдельно, потому что short-треки подтверждены в `playlists?...rich-tracks=false`, и для них нужен enrichment;
- `SavedTracksResult.Available` пока проектный сценарий, а `PrivateLibrary` подтвержден живым ответом.

Практическая интерпретация:

- `observe*` читают локальное состояние;
- `refresh*` обновляют локальный store из Yandex API;
- при недоступной сети UI продолжает жить от локальных данных.

### Интерфейс в `core/domain`

```kotlin
interface MusicLibraryRepository {
    fun observeAvailability(): Flow<MusicServiceAvailability>

    fun observeOwnPlaylists(): Flow<List<PlaylistSummary>>

    fun observePlaylist(id: PlaylistId): Flow<Playlist?>

    fun observeSavedTracks(): Flow<SavedTracksResult>

    fun observeTracks(refs: List<TrackRef>): Flow<List<Track>>

    suspend fun refreshAvailability()

    suspend fun refreshOwnPlaylists()

    suspend fun refreshPlaylist(id: PlaylistId)

    suspend fun refreshSavedTracks()

    suspend fun refreshTracks(refs: List<TrackRef>)
}
```

```kotlin
data class MusicServiceAvailability(
    val isAvailable: Boolean,
    val region: Int?,
    val permissions: Set<String>,
)

sealed interface SavedTracksResult {
    data class Available(
        val value: SavedTracks,
    ) : SavedTracksResult

    data object PrivateLibrary : SavedTracksResult
}
```

### Swift-вариант того же контракта

```swift
public protocol MusicLibraryRepository: Sendable {
    func observeAvailability() -> AsyncStream<MusicServiceAvailability>
    func observeOwnPlaylists() -> AsyncStream<[PlaylistSummary]>
    func observePlaylist(id: PlaylistId) -> AsyncStream<Playlist?>
    func observeSavedTracks() -> AsyncStream<SavedTracksResult>
    func observeTracks(refs: [TrackRef]) -> AsyncStream<[Track]>

    func refreshAvailability() async throws
    func refreshOwnPlaylists() async throws
    func refreshPlaylist(id: PlaylistId) async throws
    func refreshSavedTracks() async throws
    func refreshTracks(refs: [TrackRef]) async throws
}
```

### Почему интерфейс именно такой

1. `observeOwnPlaylists()`
   Дает UI поток локальных данных без привязки к прямому network call.

2. `observePlaylist(id:)` + `refreshPlaylist(id:)`
   Разделяют чтение локального состояния и синхронизацию с сетью для сценария "открыть плейлист".

3. `observeSavedTracks()` + `refreshSavedTracks()`
   У liked tracks собственный формат ответа, revision и дополнительный sentinel-сценарий `private-library`, поэтому это отдельный поток и отдельная команда синхронизации.

4. `observeTracks(refs:)` + `refreshTracks(refs:)`
   Закрывают сценарий enrichment после коротких ответов без утечки network-семантики в UI.

5. `observeAvailability()` + `refreshAvailability()`
   Нужны для раннего обнаружения состояния, в котором OAuth уже успешен, но сам music API недоступен и будет отвечать `451`.

### Что не стоит включать в первый контракт

- изменение названия плейлиста;
- вставку/удаление треков;
- изменение visibility;
- like/unlike.

Это лучше вынести позже в:

- отдельный write-репозиторий;
- или в расширение текущего репозитория после появления локального cache/source of truth.

## Рекомендуемый сценарий загрузки

### Экран библиотеки с плейлистами

1. Подписаться на `observeAvailability()`
2. Подписаться на `observeOwnPlaylists()`
3. При входе на экран вызвать `refreshAvailability()`
4. Если `isAvailable == false`, не идти в playlist sync и показать состояние недоступности сервиса
5. Если сервис доступен, вызвать `refreshOwnPlaylists()`
6. Не пытаться использовать `tracks` из summary-ответа как гарантированный источник данных

### Экран конкретного плейлиста

1. Подписаться на `observeAvailability()`
2. Подписаться на `observePlaylist(id)`
3. При открытии вызвать `refreshAvailability()`
4. Если `isAvailable == false`, завершить сценарий состоянием ошибки доступности
5. Если сервис доступен, вызвать `refreshPlaylist(id)`
6. Если часть `PlaylistTrackEntry.track == null`, собрать `TrackRef[]`
7. Вызвать `refreshTracks(refs)`

### Экран liked tracks

1. Подписаться на `observeAvailability()`
2. Подписаться на `observeSavedTracks()`
3. При входе вызвать `refreshAvailability()`
4. Если `isAvailable == false`, не идти в library sync
5. Если сервис доступен, вызвать `refreshSavedTracks()`
6. Если пришел `PrivateLibrary`, показать отдельное состояние недоступности библиотеки
7. Если пришел `Available`, проверить, хватает ли уже пришедших данных для списка
8. При необходимости догрузить детали через `refreshTracks(refs)`

## Почему это хорошо ложится на текущий проект

1. Не нарушает модульность.
   `feature/library` и `feature/player` получают чистые доменные модели, а не Yandex DTO.

2. Сохраняет тестируемость.
   Репозиторий легко мокать на уровне use case.

3. Хорошо ложится на offline-first.
   UI читает локальные потоки, а сеть становится отдельной фазой синхронизации.

4. Не связывает UI с нестабильным неофициальным API.

5. Учитывает реальные особенности ответа:
   - short vs rich track;
   - summary vs details playlist;
   - `revision`/`revisions`;
   - вложенный `library`;
   - sentinel `"private-library"` у liked tracks.

6. Учитывает еще одно реальное состояние интеграции:
   - OAuth успешен, но сам music API недоступен и отвечает `451`.

7. Оставляет пространство для будущего мультисервисного слоя, но не требует его прямо сейчас.

## Итоговая рекомендация

Для первой реализации стоит принять следующую схему:

- `services/yandex`:
  - Yandex-specific DTO;
  - tolerant decoders;
  - mapper в домен;
  - remote data source;
  - local data source;
  - sync layer, который обновляет локальное хранилище;
  - временно допустим remote-first каркас для валидации API, но целевая форма должна стать offline-first.

- `core/domain`:
  - `PlaylistSummary`
  - `Playlist`
  - `SavedTracks`
  - `TrackRef`
  - `TrackPreview`
  - `Track`
  - `MusicLibraryRepository`

Самое важное архитектурное решение:

- не моделировать `Playlist` и `Track` как прямые копии OpenAPI;
- разделить summary/detail и short/rich уровни данных;
- считать enrichment через `/tracks/` штатной частью дизайна, а не исключением;
- отдельно обрабатывать состояние `serviceAvailable == false` как внешний сервисный блокер, а не как обычную сетевую ошибку;
- финальный `MusicLibraryRepository` строить как `Flow`-first и offline-first, а не как thin wrapper над network API.
