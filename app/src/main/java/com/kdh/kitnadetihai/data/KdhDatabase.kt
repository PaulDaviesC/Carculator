package com.kdh.kitnadetihai.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.kdh.kitnadetihai.data.dao.*
import com.kdh.kitnadetihai.data.entity.*
import com.kdh.kitnadetihai.data.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        AppSettings::class,
        Car::class,
        OdometerLog::class,
        ExpenseCategory::class,
        Expense::class
    ],
    views = [
        VCurrentOdometerByCar::class,
        VCarDistance::class,
        VExpenseTotalsByCar::class,
        VExpenseTotalsByCarCategory::class,
        VCostPerUnitByCar::class,
        VCostPerUnitByCarCategory::class
    ],
    version = 3,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class KdhDatabase : RoomDatabase() {
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun carDao(): CarDao
    abstract fun odometerLogDao(): OdometerLogDao
    abstract fun expenseCategoryDao(): ExpenseCategoryDao
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile private var INSTANCE: KdhDatabase? = null

        fun getInstance(context: Context): KdhDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: buildDatabase(context.applicationContext).also { INSTANCE = it }
        }

        private fun buildDatabase(context: Context): KdhDatabase {
            return Room.databaseBuilder(context, KdhDatabase::class.java, "kdh.db")
                .addCallback(object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        createViewsAndTriggers(db)
                        seedDefaults(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        createViewsAndTriggers(db) // idempotent
                        ensureSystemCategories(db) // make sure any new system categories are present
                    }
                })
                .fallbackToDestructiveMigration()
                .build()
        }

        private fun createViewsAndTriggers(db: SupportSQLiteDatabase) {
            // Indexes for odometer_log
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_odolog_car_time ON odometer_log(carId, readingAtEpochMs DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_odolog_car_reading ON odometer_log(carId, readingMeters DESC)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS uq_odolog_car_time ON odometer_log(carId, readingAtEpochMs)")

            // Unique active category names
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS uq_category_name_active ON expense_category(name) WHERE isDeleted = 0")

            // Expense indexes
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expense_car_time ON expense(carId, occurredAtEpochMs DESC)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expense_category ON expense(categoryId)")
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_expense_car_category_time ON expense(carId, categoryId, occurredAtEpochMs DESC)")

            // Touch updatedAt trigger
            db.execSQL(
                """
                CREATE TRIGGER IF NOT EXISTS expense_touch_updatedAt
                AFTER UPDATE ON expense
                FOR EACH ROW BEGIN
                  UPDATE expense
                     SET updatedAtEpochMs = CAST(strftime('%s','now') AS INTEGER) * 1000
                   WHERE id = NEW.id;
                END;
                """.trimIndent()
            )

            // Views
            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_current_odometer_by_car AS
                SELECT carId,
                       MAX(readingMeters) AS currentReadingMeters,
                       MAX(readingAtEpochMs) AS readingAtEpochMs
                FROM odometer_log
                GROUP BY carId
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_car_distance AS
                WITH bounds AS (
                  SELECT carId,
                         MIN(readingMeters) AS startMeters,
                         MAX(readingMeters) AS endMeters
                  FROM odometer_log
                  GROUP BY carId
                )
                SELECT carId,
                       startMeters,
                       endMeters,
                       MAX(endMeters - startMeters, 0) AS distanceMeters
                FROM bounds
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_expense_totals_by_car AS
                SELECT carId, currencyCode, SUM(amountMinor) AS totalAmountMinor
                FROM expense
                GROUP BY carId, currencyCode
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_expense_totals_by_car_category AS
                SELECT carId, categoryId, currencyCode, SUM(amountMinor) AS totalAmountMinor
                FROM expense
                GROUP BY carId, categoryId, currencyCode
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_cost_per_unit_by_car AS
                WITH settings AS (SELECT distanceUnit FROM app_settings WHERE id = 1),
                     distance AS (SELECT * FROM v_car_distance),
                     totals   AS (SELECT * FROM v_expense_totals_by_car)
                SELECT
                  t.carId,
                  t.currencyCode,
                  CASE s.distanceUnit WHEN 'KM' THEN 'KM' ELSE 'MILE' END AS unit,
                  CASE s.distanceUnit
                    WHEN 'KM'  THEN (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1000.0
                    ELSE            (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1609.344
                  END AS costPerUnitMinor
                FROM totals t
                JOIN distance d ON d.carId = t.carId
                CROSS JOIN settings s
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE VIEW IF NOT EXISTS v_cost_per_unit_by_car_category AS
                WITH settings AS (SELECT distanceUnit FROM app_settings WHERE id = 1),
                     distance AS (SELECT * FROM v_car_distance),
                     totals   AS (SELECT * FROM v_expense_totals_by_car_category)
                SELECT
                  t.carId,
                  t.categoryId,
                  t.currencyCode,
                  CASE s.distanceUnit WHEN 'KM' THEN 'KM' ELSE 'MILE' END AS unit,
                  CASE s.distanceUnit
                    WHEN 'KM'  THEN (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1000.0
                    ELSE            (t.totalAmountMinor * 1.0) / NULLIF(d.distanceMeters, 0) * 1609.344
                  END AS costPerUnitMinor
                FROM totals t
                JOIN distance d ON d.carId = t.carId
                CROSS JOIN settings s
                """.trimIndent()
            )
        }

        private fun seedDefaults(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            db.execSQL(
                "INSERT OR IGNORE INTO app_settings (id, distanceUnit, currencyCode, createdAtEpochMs, updatedAtEpochMs, schemaVersion) VALUES (1, 'KM', 'INR', ?, ?, 1)",
                arrayOf(now, now)
            )
            ensureSystemCategories(db)
        }

        private fun ensureSystemCategories(db: SupportSQLiteDatabase) {
            val now = System.currentTimeMillis()
            var order = 10
            listOf(
                "SYSTEM:FUEL" to "Fuel",
                "SYSTEM:INSURANCE" to "Insurance",
                "SYSTEM:SERVICE" to "Service",
                "SYSTEM:REPAIR" to "Repair",
                "SYSTEM:BODY_PAINT" to "Body & Paint",
                "SYSTEM:TAX" to "Tax",
                "SYSTEM:TOLLS" to "Tolls",
                "SYSTEM:DOWN_PAYMENT" to "Down payment",
                "SYSTEM:EMI" to "EMI",
                "SYSTEM:ACCESSORIES" to "Accessories"
            ).forEach { (id, name) ->
                db.execSQL(
                    "INSERT OR IGNORE INTO expense_category (id, name, kind, isDeleted, sortOrder, createdAtEpochMs) VALUES (?, ?, 'SYSTEM', 0, ?, ?)",
                    arrayOf(id, name, order, now)
                )
                order += 10
            }
        }
    }
}
