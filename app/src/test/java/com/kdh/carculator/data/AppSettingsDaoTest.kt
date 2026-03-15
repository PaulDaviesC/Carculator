package com.kdh.carculator.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.AppSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppSettingsDaoTest {
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

    @Test
    fun get_returnsSeededDefaults() = runBlocking {
        val settings = db.appSettingsDao().get()
        assertThat(settings).isNotNull()
        assertThat(settings!!.id).isEqualTo(1)
        assertThat(settings.distanceUnit).isEqualTo(DistanceUnit.KM)
        assertThat(settings.currencyCode).isEqualTo("INR")
        assertThat(settings.schemaVersion).isEqualTo(1)
        assertThat(settings.createdAtEpochMs).isGreaterThan(0)
        assertThat(settings.updatedAtEpochMs).isGreaterThan(0)
    }

    @Test
    fun upsert_replacesExisting() = runBlocking {
        val now = System.currentTimeMillis()
        val replacement = AppSettings(
            id = 1,
            distanceUnit = DistanceUnit.MILE,
            currencyCode = "USD",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            schemaVersion = 2
        )
        db.appSettingsDao().upsert(replacement)
        val settings = db.appSettingsDao().get()
        assertThat(settings!!.distanceUnit).isEqualTo(DistanceUnit.MILE)
        assertThat(settings.currencyCode).isEqualTo("USD")
    }

    @Test
    fun update_changesDistanceUnit() = runBlocking {
        val settings = db.appSettingsDao().get()!!
        db.appSettingsDao().update(settings.copy(distanceUnit = DistanceUnit.MILE))
        val updated = db.appSettingsDao().get()
        assertThat(updated!!.distanceUnit).isEqualTo(DistanceUnit.MILE)
    }

    @Test
    fun observe_emitsOnChange() = runBlocking {
        val initial = db.appSettingsDao().observe().first()
        assertThat(initial).isNotNull()
        assertThat(initial!!.distanceUnit).isEqualTo(DistanceUnit.KM)

        db.appSettingsDao().update(initial.copy(distanceUnit = DistanceUnit.MILE))
        val updated = db.appSettingsDao().observe().first()
        assertThat(updated!!.distanceUnit).isEqualTo(DistanceUnit.MILE)
    }

    @Test
    fun upsert_withNewId_addsRow() = runBlocking {
        // Upsert uses REPLACE strategy, so inserting with id=1 replaces the seeded row
        val now = System.currentTimeMillis()
        val settings = AppSettings(
            id = 1,
            distanceUnit = DistanceUnit.KM,
            currencyCode = "EUR",
            createdAtEpochMs = now,
            updatedAtEpochMs = now,
            schemaVersion = 1
        )
        db.appSettingsDao().upsert(settings)
        assertThat(db.appSettingsDao().get()!!.currencyCode).isEqualTo("EUR")
    }
}
