# Vegam Stories: architecture

Single `app` module. The code is split into layers by package, and dependencies only point inward:

```
presentation  ──▶  domain  ◀──  data
 (Compose UI,      (models,       (Room, Retrofit,
  ViewModels)       use cases,     fake server,
                    repo interfaces) repo impls)
                         ▲
                        di  (Hilt wires data → domain)
```

```
com.vegam.myapplication
├── VegamStoriesApp.kt          @HiltAndroidApp + Coil image cache setup
├── MainActivity.kt             @AndroidEntryPoint, hosts VegamApp()
├── domain/                     Pure Kotlin. No Android/Room/Retrofit imports.
│   ├── model/                  Story, StoryDetail, StoryPage, Panel, Category, ReadingProgress, StoryVideo
│   ├── repository/             StoryRepository, LibraryRepository (interfaces)
│   └── usecase/                One class per action (GetStories, ToggleFavorite, SaveReadingProgress…)
├── data/
│   ├── remote/
│   │   ├── dto/                JSON shape expected from the server (@Serializable)
│   │   ├── api/StoryApi.kt     Retrofit interface: GET v1/feed
│   │   └── datasource/         StoryRemoteDataSource + Fake (dummy data) + Network (Retrofit)
│   ├── local/                  Room: VegamDatabase, DAOs, entities, JSON type converter
│   ├── mapper/                 DTO → Entity → Domain
│   ├── image/                  ImagePrefetcher (caches images for offline "Downloads")
│   └── repository/             Offline-first implementations
├── di/                         Hilt modules: Database, Network, Data/Repository, Dispatchers
├── presentation/
│   ├── navigation/             Type-safe @Serializable routes, NavHost, bottom-nav tabs
│   ├── components/             StoryCard, StoryGrid, StoryListRow, EmptyState…
│   ├── home/ reader/ favorites/ downloads/ history/ search/ menu/ info/
│   └── VegamApp.kt             Scaffold + bottom bar + snackbar host
└── ui/theme/                   Colors, Material 3 theme
```

## Data flow (offline-first)

1. A screen's ViewModel calls a use case, for example `GetStoriesUseCase(categoryId)`.
2. The use case returns a `Flow` from the repository, and the repository reads it from **Room**.
3. `RefreshContentUseCase` asks `StoryRemoteDataSource` for the feed and writes it into Room. Every open screen then updates automatically.
4. User state (favorites, downloads, reading progress) is kept in **separate tables**, so a server refresh never overwrites it.

## Moving from dummy data to the real server

1. In `app/build.gradle.kts`, set `BASE_URL` to your API and `USE_FAKE_BACKEND` to `false`.
2. Edit the `@SerialName`s in `data/remote/dto/FeedDto.kt` to match your JSON, and the endpoint in `StoryApi.kt`.
3. Nothing in `domain/` or `presentation/` needs to change.

If the server sends each comic page as one finished image (instead of separate panels), add `imageUrl` to `PageDto`/`StoryPage` and make `ComicPage` show that single image.

## Screens (taken from the reference video)

| Screen | What it does |
|---|---|
| Home | Logo, search and menu; category tabs (ALL + categories); Continue Reading card; 2-column grid with episode badge, "Read" badge, progress bar and heart; pull to refresh |
| Reader | Swipe between comic pages; THE END + Next story; a YouTube video page at the end; heart and download in the top bar; reading progress saved on every page |
| Favorites / Downloads | Bottom-nav tabs. Downloading caches all of a story's images so it can be read offline |
| Menu | Profile, Favorites, Downloads, Read stories, Settings, About, Privacy, Terms |
| Read stories | History with a progress bar for each story; can be cleared |
| Search | Live search over title, summary and category |
