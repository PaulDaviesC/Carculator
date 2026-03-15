package com.kdh.carculator.data

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kdh.carculator.data.entity.ExpenseCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExpenseCategoryDaoTest {
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
    fun observeActive_excludesSoftDeleted() = runBlocking {
        val now = System.currentTimeMillis()
        db.expenseCategoryDao().insert(
            ExpenseCategory(id = "user-1", name = "Custom", kind = CategoryKind.USER, isDeleted = false, sortOrder = 999, createdAtEpochMs = now)
        )
        db.expenseCategoryDao().insert(
            ExpenseCategory(id = "user-2", name = "Deleted", kind = CategoryKind.USER, isDeleted = true, sortOrder = 1000, createdAtEpochMs = now)
        )
        val active = db.expenseCategoryDao().observeActive().first()
        assertThat(active.map { it.id }).contains("user-1")
        assertThat(active.map { it.id }).doesNotContain("user-2")
    }

    @Test
    fun getById_returnsSeededCategory() = runBlocking {
        val fuel = db.expenseCategoryDao().getById("SYSTEM:FUEL")
        assertThat(fuel).isNotNull()
        assertThat(fuel!!.name).isEqualTo("Fuel")
        assertThat(fuel.kind).isEqualTo(CategoryKind.SYSTEM)
    }

    @Test
    fun getActiveByName_findsExisting() = runBlocking {
        val cat = db.expenseCategoryDao().getActiveByName("Fuel")
        assertThat(cat).isNotNull()
        assertThat(cat!!.id).isEqualTo("SYSTEM:FUEL")
    }

    @Test
    fun getActiveByName_onDeleted_returnsNull() = runBlocking {
        val now = System.currentTimeMillis()
        db.expenseCategoryDao().insert(
            ExpenseCategory(id = "del-1", name = "GhostCat", kind = CategoryKind.USER, isDeleted = true, sortOrder = 999, createdAtEpochMs = now)
        )
        assertThat(db.expenseCategoryDao().getActiveByName("GhostCat")).isNull()
    }

    @Test
    fun getByIds_returnsSubset() = runBlocking {
        val result = db.expenseCategoryDao().getByIds(listOf("SYSTEM:FUEL", "SYSTEM:INSURANCE", "NONEXISTENT"))
        assertThat(result.map { it.id }).containsExactly("SYSTEM:FUEL", "SYSTEM:INSURANCE")
        Unit
    }

    @Test(expected = SQLiteConstraintException::class)
    fun duplicate_activeName_throws() = runBlocking {
        val now = System.currentTimeMillis()
        // "Fuel" already exists as active seeded category
        db.expenseCategoryDao().insert(
            ExpenseCategory(id = "user-dup", name = "Fuel", kind = CategoryKind.USER, isDeleted = false, sortOrder = 999, createdAtEpochMs = now)
        )
    }

    @Test
    fun observeActive_orderedBySortOrder() = runBlocking {
        val active = db.expenseCategoryDao().observeActive().first()
        // System categories are seeded with sortOrder 10, 20, 30, ...
        val sortOrders = active.map { it.sortOrder }
        assertThat(sortOrders).isInOrder()
    }
}
