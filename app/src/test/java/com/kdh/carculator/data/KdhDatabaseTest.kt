package com.kdh.carculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.dao.CarCostPerUnitRow
import com.kdh.carculator.data.dao.CarTotalRow
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.data.entity.Expense
import com.kdh.carculator.data.entity.OdometerLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.util.UUID

class KdhDatabaseTest {
    private lateinit var context: Context
    private lateinit var db: KdhDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, KdhDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // Create views and seed defaults manually since in-memory builder won't call onCreate callback
        KdhDatabase.Companion::class.java.getDeclaredMethod("createViewsAndTriggers", androidx.sqlite.db.SupportSQLiteDatabase::class.java)
            .apply { isAccessible = true }
            .invoke(null, db.openHelper.writableDatabase)
        KdhDatabase.Companion::class.java.getDeclaredMethod("seedDefaults", androidx.sqlite.db.SupportSQLiteDatabase::class.java)
            .apply { isAccessible = true }
            .invoke(null, db.openHelper.writableDatabase)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun appSettings_seeded() = runBlocking {
        val settings = db.appSettingsDao().get()
        assertThat(settings).isNotNull()
        assertThat(settings!!.distanceUnit).isEqualTo(DistanceUnit.KM)
        assertThat(settings.currencyCode).isNotEmpty()
    }

    @Test
    fun categories_seeded() = runBlocking {
        val cats = db.expenseCategoryDao().observeActive().first()
        assertThat(cats).isNotEmpty()
        assertThat(cats.map { it.id }).contains("SYSTEM:FUEL")
    }

    @Test
    fun costPerUnit_view_calculates() = runBlocking {
        val carId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        db.carDao().insert(
            Car(id = carId, registrationNumber = "TEST123", name = null, createdAtEpochMs = now, archivedAtEpochMs = null)
        )
        db.odometerLogDao().insert(
            OdometerLog(id = UUID.randomUUID().toString(), carId = carId, readingMeters = 1000, readingAtEpochMs = now - 1000, source = "TEST", note = null, createdAtEpochMs = now - 1000)
        )
        db.odometerLogDao().insert(
            OdometerLog(id = UUID.randomUUID().toString(), carId = carId, readingMeters = 6000, readingAtEpochMs = now, source = "TEST", note = null, createdAtEpochMs = now)
        )
        db.expenseDao().insert(
            Expense(
                id = UUID.randomUUID().toString(),
                carId = carId,
                categoryId = "SYSTEM:FUEL",
                amountMinor = 5000, // 50.00
                currencyCode = "INR",
                occurredAtEpochMs = now,
                odometerAtMeters = 6000,
                vendor = null,
                notes = null,
                attachmentUri = null,
                createdAtEpochMs = now,
                updatedAtEpochMs = now
            )
        )

        val totals: List<CarTotalRow> = db.expenseDao().observeTotalsByCar(carId).first()
        assertThat(totals).isNotEmpty()
        assertThat(totals.first().totalAmountMinor).isEqualTo(5000)

        val cpu: List<CarCostPerUnitRow> = db.expenseDao().observeCostPerUnitByCar(carId).first()
        assertThat(cpu).isNotEmpty()
        val value = cpu.first().costPerUnitMinor
        // Distance = 5000 meters; 50.00 / 5km => 10.00 per KM in minor units -> 1000
        assertThat(value).isWithin(0.001).of(1000.0)
    }
}
