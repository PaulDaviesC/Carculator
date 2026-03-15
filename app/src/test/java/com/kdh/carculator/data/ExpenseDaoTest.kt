package com.kdh.carculator.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.data.entity.Expense
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseDaoTest {
    private lateinit var db: KdhDatabase
    private val carId = "car-exp"
    private val carId2 = "car-exp-2"
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
            db.carDao().insert(Car(id = carId, registrationNumber = "EXP-1", name = null, createdAtEpochMs = now, archivedAtEpochMs = null))
            db.carDao().insert(Car(id = carId2, registrationNumber = "EXP-2", name = null, createdAtEpochMs = now, archivedAtEpochMs = null))
        }
    }

    @After
    fun tearDown() { db.close() }

    private fun makeExpense(
        id: String,
        carId: String = this.carId,
        categoryId: String = "SYSTEM:FUEL",
        amount: Long = 5000,
        currency: String = "INR",
        ts: Long = now,
        vendor: String? = null,
        notes: String? = null
    ) = Expense(
        id = id, carId = carId, categoryId = categoryId, amountMinor = amount,
        currencyCode = currency, occurredAtEpochMs = ts, odometerAtMeters = null,
        vendor = vendor, notes = notes, attachmentUri = null,
        createdAtEpochMs = ts, updatedAtEpochMs = ts
    )

    // --- CRUD ---

    @Test
    fun insert_and_getById() = runBlocking {
        val expense = makeExpense("exp-1")
        db.expenseDao().insert(expense)
        val found = db.expenseDao().getById("exp-1")
        assertThat(found).isEqualTo(expense)
    }

    @Test
    fun update_changesFields() = runBlocking {
        val expense = makeExpense("exp-1")
        db.expenseDao().insert(expense)
        db.expenseDao().update(expense.copy(vendor = "Shell"))
        assertThat(db.expenseDao().getById("exp-1")!!.vendor).isEqualTo("Shell")
    }

    @Test
    fun delete_removesExpense() = runBlocking {
        val expense = makeExpense("exp-1")
        db.expenseDao().insert(expense)
        db.expenseDao().delete(expense)
        assertThat(db.expenseDao().getById("exp-1")).isNull()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicate_id_throws() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1"))
        db.expenseDao().insert(makeExpense("exp-1"))
    }

    // --- Queries ---

    @Test
    fun observeForCar_filtersByCar() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1", carId = carId))
        db.expenseDao().insert(makeExpense("exp-2", carId = carId2))
        val forCar1 = db.expenseDao().observeForCar(carId).first()
        assertThat(forCar1).hasSize(1)
        assertThat(forCar1[0].id).isEqualTo("exp-1")
    }

    @Test
    fun observeForCarAndCategory_filters() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1", categoryId = "SYSTEM:FUEL"))
        db.expenseDao().insert(makeExpense("exp-2", categoryId = "SYSTEM:INSURANCE", ts = now + 1))
        val fuelOnly = db.expenseDao().observeForCarAndCategory(carId, "SYSTEM:FUEL").first()
        assertThat(fuelOnly).hasSize(1)
        assertThat(fuelOnly[0].id).isEqualTo("exp-1")
    }

    @Test
    fun countAmortization_countsCorrectly() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1", categoryId = "SYSTEM:DOWN_PAYMENT", vendor = "Amortization"))
        db.expenseDao().insert(makeExpense("exp-2", categoryId = "SYSTEM:DOWN_PAYMENT", vendor = "Amortization", ts = now + 1))
        db.expenseDao().insert(makeExpense("exp-3", categoryId = "SYSTEM:FUEL", vendor = "Shell", ts = now + 2))
        assertThat(db.expenseDao().countAmortization(carId)).isEqualTo(2)
    }

    @Test
    fun observeForCar_orderedByTimeDesc() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-old", ts = now))
        db.expenseDao().insert(makeExpense("exp-new", ts = now + 1000))
        val list = db.expenseDao().observeForCar(carId).first()
        assertThat(list[0].id).isEqualTo("exp-new")
        assertThat(list[1].id).isEqualTo("exp-old")
    }

    // --- Pagination ---

    @Test
    fun pageInitialByCar_respectsLimit() = runBlocking {
        for (i in 1..5) {
            db.expenseDao().insert(makeExpense("exp-$i", ts = now + i))
        }
        val page = db.expenseDao().pageInitialByCar(carId, limit = 3)
        assertThat(page).hasSize(3)
        // Should be most recent first
        assertThat(page[0].id).isEqualTo("exp-5")
    }

    @Test
    fun pageAfterByCar_returnsNextPage() = runBlocking {
        for (i in 1..5) {
            db.expenseDao().insert(makeExpense("exp-$i", ts = now + i))
        }
        val page1 = db.expenseDao().pageInitialByCar(carId, limit = 2)
        val lastItem = page1.last()
        val page2 = db.expenseDao().pageAfterByCar(carId, lastItem.occurredAtEpochMs, lastItem.id, limit = 2)
        assertThat(page2).hasSize(2)
        assertThat(page2[0].id).isEqualTo("exp-3")
    }

    @Test
    fun pageAfterByCar_pastEnd_returnsEmpty() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1"))
        val page = db.expenseDao().pageAfterByCar(carId, 0L, "", limit = 10)
        assertThat(page).isEmpty()
    }

    // --- Monthly totals ---

    @Test
    fun observeMonthlyTotalsByCar_groupsByYearMonth() = runBlocking {
        // Nov 2023 and Dec 2023 (fixed timestamps)
        db.expenseDao().insert(makeExpense("exp-nov", ts = 1_700_000_000_000L, amount = 1000)) // Nov 14 2023
        db.expenseDao().insert(makeExpense("exp-dec", ts = 1_701_500_000_000L, amount = 2000)) // Dec 2 2023
        val monthly = db.expenseDao().observeMonthlyTotalsByCar(carId).first()
        assertThat(monthly).hasSize(2)
    }

    @Test
    fun observeMonthlyTotalsByCar_groupsByCurrency() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-inr", currency = "INR", amount = 1000))
        db.expenseDao().insert(makeExpense("exp-usd", currency = "USD", amount = 2000, ts = now + 1))
        val monthly = db.expenseDao().observeMonthlyTotalsByCar(carId).first()
        // Same month, different currencies -> separate rows
        assertThat(monthly).hasSize(2)
    }

    // --- View aggregations ---

    @Test
    fun observeTotalsByCar_sums() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-1", amount = 1000))
        db.expenseDao().insert(makeExpense("exp-2", amount = 2000, ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCar(carId).first()
        assertThat(totals).hasSize(1)
        assertThat(totals[0].totalAmountMinor).isEqualTo(3000)
    }

    @Test
    fun observeTotalsByCarCategory_groups() = runBlocking {
        db.expenseDao().insert(makeExpense("exp-fuel", categoryId = "SYSTEM:FUEL", amount = 1000))
        db.expenseDao().insert(makeExpense("exp-ins", categoryId = "SYSTEM:INSURANCE", amount = 2000, ts = now + 1))
        val totals = db.expenseDao().observeTotalsByCarCategory(carId).first()
        assertThat(totals).hasSize(2)
        val fuelTotal = totals.first { it.categoryId == "SYSTEM:FUEL" }
        assertThat(fuelTotal.totalAmountMinor).isEqualTo(1000)
    }
}
