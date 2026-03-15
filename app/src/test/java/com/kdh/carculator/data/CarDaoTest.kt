package com.kdh.carculator.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.Car
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CarDaoTest {
    private lateinit var db: KdhDatabase

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
    }

    @After
    fun tearDown() { db.close() }

    private fun makeCar(id: String = "car-1", reg: String = "ABC123", name: String? = null, ts: Long = 1_700_000_000_000L) =
        Car(id = id, registrationNumber = reg, name = name, createdAtEpochMs = ts, archivedAtEpochMs = null)

    @Test
    fun insert_and_getById_roundtrip() = runBlocking {
        val car = makeCar()
        db.carDao().insert(car)
        val found = db.carDao().getById("car-1")
        assertThat(found).isEqualTo(car)
    }

    @Test
    fun update_changesFields() = runBlocking {
        val car = makeCar()
        db.carDao().insert(car)
        val updated = car.copy(name = "My Car")
        db.carDao().update(updated)
        assertThat(db.carDao().getById("car-1")!!.name).isEqualTo("My Car")
    }

    @Test
    fun getById_nonExistent_returnsNull() = runBlocking {
        assertThat(db.carDao().getById("nope")).isNull()
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicate_registrationNumber_throws() = runBlocking {
        db.carDao().insert(makeCar(id = "car-1", reg = "SAME"))
        db.carDao().insert(makeCar(id = "car-2", reg = "SAME"))
    }

    @Test
    fun observeAll_emitsReactively() = runBlocking {
        val cars0 = db.carDao().observeAll().first()
        assertThat(cars0).isEmpty()
        db.carDao().insert(makeCar())
        val cars1 = db.carDao().observeAll().first()
        assertThat(cars1).hasSize(1)
    }

    @Test
    fun listAll_orderedByCreatedAtDesc() = runBlocking {
        db.carDao().insert(makeCar(id = "old", reg = "OLD", ts = 1_000L))
        db.carDao().insert(makeCar(id = "new", reg = "NEW", ts = 2_000L))
        val list = db.carDao().listAll()
        assertThat(list[0].id).isEqualTo("new")
        assertThat(list[1].id).isEqualTo("old")
    }

    @Test
    fun getByRegistration_caseInsensitive() = runBlocking {
        db.carDao().insert(makeCar(reg = "ABC123"))
        assertThat(db.carDao().getByRegistration("abc123")).isNotNull()
        assertThat(db.carDao().getByRegistration("ABC123")).isNotNull()
    }

    @Test
    fun delete_removesCar() = runBlocking {
        val car = makeCar()
        db.carDao().insert(car)
        db.carDao().delete(car)
        assertThat(db.carDao().getById("car-1")).isNull()
    }
}
