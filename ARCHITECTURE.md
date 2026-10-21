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
│   ├── model/                  Story, StoryDetail, StoryPage (one image per page), Category, ReadingProgress, StoryVideo
│   ├── repository/             StoryRepository, LibraryRepository (interfaces)
│   └── usecase/                One class per action (GetStories, ToggleFavorite, SaveReadingProgress…)
├── data/
│   ├── remote/
│   │   ├── dto/                JSON shape expected from the server (@Serializable)
│   │   ├── api/StoryApi.kt     Retrofit interface: GET v1/feed
│   │   └── datasource/         StoryRemoteDataSource + Fake (dummy data) + Network (Retrofit)
│   ├── local/                  Room: VegamDatabase, DAOs, entities
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
5. **In the background** (`background/`): `StoryRefreshWorker` (WorkManager, every 6 h when online) runs the
   same refresh, which also sends queued likes, redraws the home screen widget, then `CheckForNewStoryUseCase`
   decides whether to post a "New story" notification (`NewStoryNotifier`; tapping it opens the reader).
   Alerts are opt-in (Settings → New story alerts). The newest story already announced or seen is kept in
   DataStore, so installing the app or opening Home never triggers an alert for an old story.

## Backend: Supabase + Cloudflare R2

- **Supabase** (`categories`, `stories`, `story_pages` tables) holds story text and image URLs.
  `story_pages` has one row per page: `story_id`, `page_index`, `image_url`.
  The app reads it through the REST API (`data/remote/api/StoryApi.kt`). Row Level Security allows
  read-only access to published stories.
- **Likes** (`supabase/migrations/003_story_likes.sql`): the heart is a like. `stories.like_count` is
  the total; `story_likes` has one row per (story, device). The app has no login, so it sends a random
  per-install id (`DeviceIdProvider`) to the `set_story_like()` function (`POST /rpc/set_story_like`),
  which is the only way to write likes. The heart and count change locally at once; `LikeSyncer` sends
  queued likes (`pending_likes` table) in the background and before every refresh.
- **Cloudflare R2** holds the images, served from `https://media.vegamstories.com/<story-id>/...`.
- Keys go in `local.properties` (not committed):
  ```
  SUPABASE_URL=https://xxxx.supabase.co
  SUPABASE_ANON_KEY=eyJ...   (or sb_publishable_...)
  ```
  Without them, `USE_FAKE_BACKEND` turns on automatically and the app shows dummy data.
- Every refresh makes the local Room cache match Supabase: stories that are unpublished or
  deleted on the server disappear from the app too.

## Screens (taken from the reference video)

| Screen | What it does |
|---|---|
| Home | Logo, search and menu; category tabs (ALL + categories); Continue Reading card; 2-column grid with episode badge, "Read" badge, progress bar and heart; pull to refresh |
| Reader | Swipe between comic pages (each page is one finished image with its panels and speech bubbles drawn in); THE END + Next story; the story's YouTube video plays in-app on the last page; heart and download in the top bar; reading progress saved on every page |
| Favorites / Downloads | Bottom-nav tabs. Downloading caches all of a story's images so it can be read offline |
| Menu | Profile, Favorites, Downloads, Read stories, Settings, About, Privacy, Terms |
| Read stories | History with a progress bar for each story; can be cleared |
| Search | Live search over title, summary and category |
| Settings | Theme: System default (first launch, follows the phone), Light or Dark. Saved with DataStore and applied app-wide by `MainActivity` |
