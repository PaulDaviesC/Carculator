package com.kdh.carculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.AppSettings
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.data.entity.Expense
import com.kdh.carculator.data.entity.OdometerLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DatabaseViewsTest {
    private lateinit var db: KdhDatabase
    private val carId = "car-view"
    private val now = 1_700_000_000_000L

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KdhDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val sdb = db.openHelper.writableDatabase
        val companion = KdhDatabase::class.java.getDeclaredField("Companion").get(null)
        KdhDatabase.Companion::class.java
            .getDeclaredMethod("createViewsAndTriggers", androidx.sqlite.db.SupportSQLiteDatabase::class.java)
            .apply { isAccessible = true }
            .invoke(companion, sdb)
        KdhDatabase.Companion::class.java
            .getDeclaredMethod("seedDefaults", androidx.sqlite.db.SupportSQLiteDatabase::class.java)
            .apply { isAccessible = true }
            .invoke(companion, sdb)
        runBlocking {
            db.carDao().insert(Car(id = carId, registrationNumber = "VIEW-1", name = null, createdAtEpochMs = now, archivedAtEpochMs = null))
        }
    }

    @After
    fun tearDown() { db.close() }

    private fun makeLog(id: String, meters: Long, ts: Long) =
        OdometerLog(id = id, carId = carId, readingMeters = meters, readingAtEpochMs = ts, source = "TEST", note = null, createdAtEpochMs = ts)

    private fun makeExpense(
        id: String,
        categoryId: String = "SYSTEM:FUEL",
        amount: Long = 5000,
        currency: String = "INR",
        ts: Long = now
    ) = Expense(
        id = id, carId = carId, categoryId = categoryId, amountMinor = amount,
        currencyCode = currency, occurredAtEpochMs = ts, odometerAtMeters = null,
        vendor = null, notes = null, attachmentUri = null,
        createdAtEpochMs = ts, updatedAtEpochMs = ts
    )

    // --- v_car_distance ---

    @Test
    fun vCarDistance_twoReadings_computesMaxMinusMin() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, now))
        db.odometerLogDao().insert(makeLog("log-2", 6000, now + 1000))
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isEqualTo(5000)
    }

    @Test
    fun vCarDistance_singleReading_returnsZero() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 5000, now))
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isEqualTo(0)
    }

    // --- v_expense_totals_by_car ---

    @Test
    fun expenseTotals_multipleCurrencies_separateRows() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-inr", amount = 1000, currency = "INR"))
        db.expenseDao().insert(makeExpense("exp-usd", amount = 2000, currency = "USD", ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCar(carId).first()
        assertThat(totals).hasSize(2)
        assertThat(totals.map { it.currencyCode }.toSet()).containsExactly("INR", "USD")
        Unit
    }

    @Test
    fun expenseTotals_sameCurrency_sums() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1", amount = 1000))
        db.expenseDao().insert(makeExpense("exp-2", amount = 3000, ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCar(carId).first()
        assertThat(totals).hasSize(1)
        assertThat(totals[0].totalAmountMinor).isEqualTo(4000)
    }

    // --- v_expense_totals_by_car_category ---

    @Test
    fun expenseTotalsByCategory_groupsByCategoryAndCurrency() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-fuel", categoryId = "SYSTEM:FUEL", amount = 1000))
        db.expenseDao().insert(makeExpense("exp-ins", categoryId = "SYSTEM:INSURANCE", amount = 2000, ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCarCategory(carId).first()
        assertThat(totals).hasSize(2)
    }

    @Test
    fun expenseTotalsByCategory_sameCategoryDifferentCurrency_separate() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-inr", categoryId = "SYSTEM:FUEL", amount = 1000, currency = "INR"))
        db.expenseDao().insert(makeExpense("exp-usd", categoryId = "SYSTEM:FUEL", amount = 2000, currency = "USD", ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCarCategory(carId).first()
        assertThat(totals).hasSize(2)
        assertThat(totals.map { it.currencyCode }.toSet()).containsExactly("INR", "USD")
        Unit
    }

    // --- v_cost_per_unit_by_car (KM) ---

    @Test
    fun costPerUnit_kmCalculation() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, now))
        db.odometerLogDao().insert(makeLog("log-2", 6000, now + 1000))
        db.expenseDao().insert(makeExpense("exp-1", amount = 5000)) // 50.00 INR
        // Distance = 5000m = 5km. cost = 5000 minor / 5km = 1000 per km
        val cpu = db.expenseDao().observeCostPerUnitByCar(carId).first()
        assertThat(cpu).hasSize(1)
        assertThat(cpu[0].unit).isEqualTo("KM")
        assertThat(cpu[0].costPerUnitMinor).isWithin(0.01).of(1000.0)
    }

    @Test
    fun costPerUnit_mileCalculation() = runBlocking {
        // Change settings to MILE
        val settings = db.appSettingsDao().get()!!
        db.appSettingsDao().update(settings.copy(distanceUnit = DistanceUnit.MILE))

        db.odometerLogDao().insert(makeLog("log-1", 0, now))
        db.odometerLogDao().insert(makeLog("log-2", 1609, now + 1000))
        db.expenseDao().insert(makeExpense("exp-1", amount = 5000))
        // Distance = 1609m ≈ 1 mile. cost per mile ≈ 5000 * 1609.344 / 1609 ≈ 5001.07
        val cpu = db.expenseDao().observeCostPerUnitByCar(carId).first()
        assertThat(cpu).hasSize(1)
        assertThat(cpu[0].unit).isEqualTo("MILE")
        assertThat(cpu[0].costPerUnitMinor).isWithin(5.0).of(5000.0)
    }

    // --- v_cost_per_unit_by_car_category ---

    @Test
    fun costPerUnitByCategory_splitsByCategory() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, now))
        db.odometerLogDao().insert(makeLog("log-2", 6000, now + 1000))
        db.expenseDao().insert(makeExpense("exp-fuel", categoryId = "SYSTEM:FUEL", amount = 3000))
        db.expenseDao().insert(makeExpense("exp-ins", categoryId = "SYSTEM:INSURANCE", amount = 2000, ts = now + 1))
        val cpu = db.expenseDao().observeCostPerUnitByCarCategory(carId).first()
        assertThat(cpu).hasSize(2)
        val fuelCpu = cpu.first { it.categoryId == "SYSTEM:FUEL" }
        // 3000 / 5km = 600 per km
        assertThat(fuelCpu.costPerUnitMinor).isWithin(0.01).of(600.0)
    }

    @Test
    fun costPerUnitByCategory_correctMathPerCategory() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 0, now))
        db.odometerLogDao().insert(makeLog("log-2", 10000, now + 1000)) // 10km
        db.expenseDao().insert(makeExpense("exp-fuel", categoryId = "SYSTEM:FUEL", amount = 10000))
        db.expenseDao().insert(makeExpense("exp-svc", categoryId = "SYSTEM:SERVICE", amount = 5000, ts = now + 1))
        val cpu = db.expenseDao().observeCostPerUnitByCarCategory(carId).first()
        val fuelCpu = cpu.first { it.categoryId == "SYSTEM:FUEL" }
        val svcCpu = cpu.first { it.categoryId == "SYSTEM:SERVICE" }
        // fuel: 10000 / 10km = 1000; service: 5000 / 10km = 500
        assertThat(fuelCpu.costPerUnitMinor).isWithin(0.01).of(1000.0)
        assertThat(svcCpu.costPerUnitMinor).isWithin(0.01).of(500.0)
    }
}
