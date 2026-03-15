# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Carculator ("KitnaDetiHai") is a native Android app for tracking car expenses and efficiency. Built with Kotlin, Room, and Material 3. Package: `com.kdh.carculator`.

## Build Commands

No Gradle wrapper is checked in. Use a system Gradle or open in Android Studio.

```bash
# Build debug APK
./gradlew assembleDebug

# Run all unit tests (Robolectric-based, no device needed)
./gradlew testDebugUnitTest

# Run a single test class
./gradlew testDebugUnitTest --tests "com.kdh.carculator.data.KdhDatabaseTest"

# Run instrumented tests (requires device/emulator)
./gradlew connectedDebugAndroidTest
```

## Architecture

**Single-module app** (`app/`) using MVVM + Repository pattern with no DI framework.

### Layers (all under `com.kdh.carculator`)

- **`data/`** — Room database (`KdhDatabase`, version 3, file `kdh.db`), entities, DAOs, database views, type converters, enums. Database uses `fallbackToDestructiveMigration()` — schema changes wipe data.
- **`repo/`** — Five repositories (`Repositories.kt`) wrapping DAOs. All in a single file. Repositories take `Context` and get the DB via `DatabaseProvider`.
- **`ui/`** — Activities using ViewBinding. `BaseDrawerActivity` provides shared navigation drawer. No Fragments or Jetpack Compose.
- **`util/`** — `Formatters.kt` (date, distance, currency) and `CsvUtils.kt` (CSV import/export).
- **`work/`** — `AmortizationWorker` (WorkManager) spreads acquisition costs over car lifespan.
- **`App.kt`** — Application class, applies Material 3 dynamic colors.

### Data Flow

Repositories expose `Flow<>` for reactive UI updates. Activities collect flows with lifecycle-aware coroutine scopes. No ViewModel classes exist — activities interact with repositories directly.

### Database

- 5 entities: `AppSettings`, `Car`, `Expense`, `ExpenseCategory`, `OdometerLog`
- 6 SQL views for aggregations (totals, cost-per-unit, distance)
- Views and triggers are created in `KdhDatabase.Companion` via raw SQL in `createViewsAndTriggers()`
- System expense categories (Fuel, Insurance, Service, etc.) are seeded on first run and re-ensured on every open
- Amounts stored as `Long` minor units (cents/paise); currency code stored per expense
- Distances stored in meters internally; converted to km/miles at display time

### Testing

Unit tests use Robolectric + in-memory Room database. Test assertions use Google Truth. Tests are in `app/src/test/`.

## Documentation

PRDs live in `docs/prd/`.
