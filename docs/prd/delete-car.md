# PRD: Delete Car

## 1. Problem Statement

Users currently have no way to remove a car from the app. Once added, a car remains forever — cluttering the home list and navigation drawer. If a user sells a vehicle, makes a data entry mistake, or simply no longer wants to track a car, they are stuck.

## 2. Goal

Allow users to permanently delete a car and all its associated data (expenses, odometer logs) with a clear confirmation flow that communicates the destructive nature of the action.

## 3. User Stories

| # | Story | Priority |
|---|-------|----------|
| US-1 | As a user, I want to delete a car I no longer own so my car list stays clean. | P0 |
| US-2 | As a user, I want to be warned about what will be lost before confirming deletion. | P0 |
| US-3 | As a user, I want to cancel if I change my mind. | P0 |

## 4. Scope

### In Scope
- Delete car via a **delete icon on each car card** in `MainActivity`
- Delete car via the **CarDetailActivity** overflow menu
- Two-step confirmation dialog before deletion
- Cascade deletion of all related expenses and odometer logs (handled by Room FK constraints already in place)
- Auto-update of home car list and navigation drawer (handled by existing `Flow` observation)
- Navigate back to `MainActivity` after successful deletion from `CarDetailActivity`

### Out of Scope (future consideration)
- Soft delete / archive (the `archivedAtEpochMs` field exists on `Car` but is unused)
- Undo via Snackbar (would require soft-delete or temp backup)
- Bulk delete of multiple cars
- Export-before-delete prompt

## 5. Detailed Design

### 5.1 Entry Points

There are **two** entry points for deleting a car.

#### 5.1.1 Entry Point A — Delete Icon on Car Card (`MainActivity`)

Each car card in the home list shows a **delete icon button** at the **bottom-right** of the card.

**Card layout (`item_car_row.xml`):**

```
┌───────────────────────────────────────────┐
│  ┌─────────┐                              │
│  │ MH 01 AB│   My Civic                  │
│  └─────────┘                              │
│  Cost / Unit  [ ₹4.50 / km ]  1,200 km 🗑️│
└───────────────────────────────────────────┘
```

The bottom row changes from:

```
[ tvCpuLabel ]  [ chipCpu ]  [ tvDistance (weight=1, gravity=end) ]
```

to:

```
[ tvCpuLabel ]  [ chipCpu ]  [ tvDistance (weight=1, gravity=end) ]  [ btnDeleteCar ]
```

`tvDistance` retains `weight=1` so the delete icon is pinned to the far right. A `marginStart="8dp"` separates the icon from the distance text.

**Icon specs:**
- Material icon: `ic_delete_outline_24`
- Size: 24dp icon inside a 40dp touch target (`ImageButton` with `?attr/selectableItemBackgroundBorderless`)
- Tint: `?attr/colorOnSurfaceVariant` — subtle, not red, to avoid visual clutter on every card
- Content description: `"Delete car"` for accessibility

**Behavior:**
- Tapping the delete icon shows the confirmation dialog (does **not** navigate to car detail)
- Tapping the card body still navigates to `CarDetailActivity` (delete button does not intercept card click)
- After deletion, the car disappears from the list via the existing `observeCars()` Flow — no `finish()` needed

#### 5.1.2 Entry Point B — Overflow Menu in `CarDetailActivity`

Add a **"Delete car"** menu item to `menu_car_detail.xml`, below the existing export item.

```
┌─────────────────────────┐
│ Import expenses (CSV)   │
│ Export expenses (CSV)   │
│ ──────────────────────  │
│ Delete car              │
└─────────────────────────┘
```

**Behavior:**
- Tapping "Delete car" shows the confirmation dialog
- After deletion: show Toast, call `finish()` → returns to `MainActivity`

### 5.2 Confirmation Dialog

Both entry points show the **same** confirmation dialog.

```
┌──────────────────────────────────────┐
│  Delete <Car Name / Reg>?           │
│                                      │
│  This will permanently delete this   │
│  car and all its data, including     │
│  X expenses and Y odometer logs.     │
│  This action cannot be undone.       │
│                                      │
│              [Cancel]    [Delete]     │
└──────────────────────────────────────┘
```

- **Title:** "Delete `<car.name ?: car.registrationNumber>`?"
- **Message:** Dynamic — queries expense count and odometer log count for this car to show the user exactly what they'll lose.
- **Positive button:** "Delete"
- **Negative button:** "Cancel"
- Dialog is **not cancellable by outside touch** (user must explicitly tap Cancel or Delete).

To avoid code duplication, extract the dialog into a shared utility function:

```kotlin
fun showDeleteCarConfirmation(
    context: Context,
    car: Car,
    expenseCount: Int,
    odometerCount: Int,
    onConfirm: () -> Unit
)
```

### 5.3 Deletion Flow

```
User taps delete icon (card) or "Delete car" (menu)
        │
        ▼
  Fetch expense count + odometer log count
        │
        ▼
  Show confirmation dialog
  (with car name and data counts)
        │
   ┌────┴────┐
   │         │
 Cancel    Delete
   │         │
   ▼         ▼
 Dismiss   Execute deletion:
 dialog      1. carRepo.deleteCar(car)
               → CASCADE deletes expenses
               → CASCADE deletes odometer logs
               → Views auto-clear
             2. Show Toast: "Car deleted"
             3a. If MainActivity: list auto-updates (Flow)
             3b. If CarDetailActivity: finish() → back to MainActivity
```

### 5.4 Edge Cases

| Scenario | Behavior |
|----------|----------|
| Car has zero expenses/logs | Dialog says "0 expenses and 0 odometer logs" — still allow delete |
| Deletion fails (DB error) | Show Toast with error message, do not navigate away |
| Last car deleted | MainActivity shows existing empty state |
| AmortizationWorker running for this car | Worker queries car data on next run; car not found → no-op (already safe) |

## 6. Data Layer Changes

### 6.1 CarRepository (`Repositories.kt`)

Add one method (DAO method `CarDao.delete()` already exists):

```kotlin
suspend fun deleteCar(car: Car) = carDao.delete(car)
```

### 6.2 Count Queries (for dialog message)

Add to `ExpenseDao`:
```kotlin
@Query("SELECT COUNT(*) FROM expense WHERE carId = :carId")
suspend fun countByCarId(carId: String): Int
```

Add to `OdometerLogDao`:
```kotlin
@Query("SELECT COUNT(*) FROM odometer_log WHERE carId = :carId")
suspend fun countByCarId(carId: String): Int
```

Expose through respective repositories.

## 7. UI Layer Changes

### 7.1 `item_car_row.xml`

Add `ImageButton` (`btnDeleteCar`) to the bottom row `LinearLayout`, after `tvDistance`.

### 7.2 `menu_car_detail.xml`

Add:
```xml
<item
    android:id="@+id/action_delete_car"
    android:title="@string/delete_car" />
```

### 7.3 String Resources (`strings.xml`)

```xml
<string name="delete_car">Delete car</string>
<string name="confirm_delete_car_title">Delete %s?</string>
<string name="confirm_delete_car_message">This will permanently delete this car and all its data, including %d expenses and %d odometer logs. This action cannot be undone.</string>
<string name="car_deleted">Car deleted</string>
```

### 7.4 `MainActivity.kt`

- `CarAdapter` accepts a second callback: `onDeleteClick: (Car) -> Unit`
- `CarVH` binds `btnDeleteCar` click to the callback
- `MainActivity` wires `onDeleteClick` to `showDeleteCarConfirmation()`

### 7.5 `CarDetailActivity.kt`

- Handle `R.id.action_delete_car` in `onOptionsItemSelected()`
- Fetch counts, show confirmation dialog, delete + `finish()` on confirm

## 8. Testing

| Test | Type | Description |
|------|------|-------------|
| `deleteCar_cascadesExpenses` | Unit (Robolectric + Room) | Insert car + 3 expenses → delete car → assert expenses table empty |
| `deleteCar_cascadesOdometerLogs` | Unit (Robolectric + Room) | Insert car + 2 logs → delete car → assert odometer_log table empty |
| `countByCarId_returnsCorrectCount` | Unit | Insert car + N expenses → assert `countByCarId` returns N |
| `deleteCar_viewsReturnEmpty` | Unit | Insert car + data → delete car → assert views return no rows for that carId |

## 9. Acceptance Criteria

- [ ] Each car card on `MainActivity` shows a delete icon button at the bottom-right
- [ ] Tapping the delete icon shows the confirmation dialog (does **not** navigate to car detail)
- [ ] Tapping the card body still navigates to `CarDetailActivity` (delete button does not intercept card click)
- [ ] "Delete car" option appears in `CarDetailActivity` overflow menu
- [ ] Both entry points use the same confirmation dialog with car name and data counts
- [ ] Tapping "Cancel" dismisses the dialog with no side effects
- [ ] Tapping "Delete" removes the car, all its expenses, and all its odometer logs
- [ ] After deletion from card, the car disappears from the list without navigation
- [ ] After deletion from car detail, user lands on `MainActivity` with car gone
- [ ] A "Car deleted" toast confirms the action
- [ ] Navigation drawer auto-updates to remove the deleted car
- [ ] All 4 unit tests pass
