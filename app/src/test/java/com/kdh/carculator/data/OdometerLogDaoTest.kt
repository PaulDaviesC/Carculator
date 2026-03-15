package com.kdh.carculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.Car
import com.kdh.carculator.data.entity.OdometerLog
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OdometerLogDaoTest {
    private lateinit var db: KdhDatabase
    private val carId = "car-odo"

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
        // Insert parent car
        runBlocking {
            db.carDao().insert(Car(id = carId, registrationNumber = "ODO-1", name = null, createdAtEpochMs = 1_700_000_000_000L, archivedAtEpochMs = null))
        }
    }

    @After
    fun tearDown() { db.close() }

    private fun makeLog(id: String, meters: Long, ts: Long) =
        OdometerLog(id = id, carId = carId, readingMeters = meters, readingAtEpochMs = ts, source = "TEST", note = null, createdAtEpochMs = ts)

    @Test
    fun insert_and_observe() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, 1_700_000_000_000L))
        val logs = db.odometerLogDao().observeForCar(carId).first()
        assertThat(logs).hasSize(1)
        assertThat(logs[0].readingMeters).isEqualTo(1000)
    }

    @Test
    fun delete_removesLog() = runBlocking {
        val log = makeLog("log-1", 1000, 1_700_000_000_000L)
        db.odometerLogDao().insert(log)
        db.odometerLogDao().delete(log)
        val logs = db.odometerLogDao().observeForCar(carId).first()
        assertThat(logs).isEmpty()
    }

    @Test
    fun getLatestForCar_returnsHighestTimestamp() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, 1_700_000_000_000L))
        db.odometerLogDao().insert(makeLog("log-2", 5000, 1_700_000_001_000L))
        db.odometerLogDao().insert(makeLog("log-3", 3000, 1_700_000_000_500L))
        val latest = db.odometerLogDao().getLatestForCar(carId)
        assertThat(latest!!.id).isEqualTo("log-2")
    }

    @Test
    fun observeForCar_orderedDesc() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, 1_700_000_000_000L))
        db.odometerLogDao().insert(makeLog("log-2", 5000, 1_700_000_002_000L))
        db.odometerLogDao().insert(makeLog("log-3", 3000, 1_700_000_001_000L))
        val logs = db.odometerLogDao().observeForCar(carId).first()
        assertThat(logs.map { it.id }).containsExactly("log-2", "log-3", "log-1").inOrder()
    }

    @Test
    fun getTotalDistanceMeters_threeReadings() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, 1_700_000_000_000L))
        db.odometerLogDao().insert(makeLog("log-2", 5000, 1_700_000_001_000L))
        db.odometerLogDao().insert(makeLog("log-3", 3000, 1_700_000_002_000L))
        // MAX(5000) - MIN(1000) = 4000
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isEqualTo(4000)
    }

    @Test
    fun getTotalDistanceMeters_singleReading_returnsZero() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 5000, 1_700_000_000_000L))
        // MAX(5000) - MIN(5000) = 0
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isEqualTo(0)
    }

    @Test
    fun getTotalDistanceMeters_noReadings_returnsNull() = runBlocking {
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isNull()
    }

    @Test
    fun getTotalDistanceMeters_initialOdometerZero_returnsReadingValue() = runBlocking {
        // Simulate a brand-new car with initial odometer = 0
        db.odometerLogDao().insert(makeLog("log-init", 0, 1_700_000_000_000L))
        // User later adds a reading of 50,000,000 meters (50,000 km)
        db.odometerLogDao().insert(makeLog("log-1", 50_000_000, 1_700_000_001_000L))
        // MAX(50,000,000) - MIN(0) = 50,000,000
        val distance = db.odometerLogDao().getTotalDistanceMeters(carId)
        assertThat(distance).isEqualTo(50_000_000)
    }

    @Test
    fun duplicate_carId_readingAtEpochMs_throws() = runBlocking {
        db.odometerLogDao().insert(makeLog("log-1", 1000, 1_700_000_000_000L))
        try {
            db.odometerLogDao().insert(makeLog("log-2", 2000, 1_700_000_000_000L)) // same timestamp
            throw AssertionError("Expected exception for duplicate (carId, readingAtEpochMs)")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // expected
        }
    }
}
