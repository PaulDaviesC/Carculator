# Carculator

Track your car’s running costs and efficiency in one lightweight Android app. Log odometer readings and expenses, then see totals and cost-per-unit breakdowns so you know how much it costs to run your car and what you’re getting per km/l (or mile/gal).

## What it does
- Record odometer readings for each car
- Add expenses with category (fuel, service, insurance, etc.), amount, vendor, and notes
- Summaries by car and by category
- Current odometer and distance driven
- Cost-per-unit/efficiency calculations (e.g., cost per km)
- Basic unit and currency preferences

## Data model (Room)
- Entities: `Car`, `Expense`, `ExpenseCategory`, `OdometerLog`, `AppSettings`
- Views: `VCurrentOdometerByCar`, `VCarDistance`, `VExpenseTotalsByCar`, `VExpenseTotalsByCarCategory`, `VCostPerUnitByCar`, `VCostPerUnitByCarCategory`

## Tech stack
- Kotlin, AndroidX, Material Components
- Room (runtime, KTX, KSP compiler)
- ViewBinding
- Tests with Robolectric and AndroidX Test

## Build & run
1. Open the project in Android Studio (Hedgehog or newer)
2. Sync Gradle
3. Run the `app` module on a device/emulator (minSdk 26)

## Package
- Application ID: `com.kdh.carculator`
