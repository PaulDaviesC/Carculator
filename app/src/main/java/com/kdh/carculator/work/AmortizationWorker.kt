package com.kdh.carculator.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kdh.carculator.data.DatabaseProvider
import com.kdh.carculator.data.entity.Expense
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar

class AmortizationWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val db = DatabaseProvider.get(applicationContext)
        val carDao = db.carDao()
        val expenseDao = db.expenseDao()
        val settings = db.appSettingsDao().get()
        val currency = settings?.currencyCode ?: "INR"

        val cars = carDao.listAll()
        val nowCal = Calendar.getInstance()

        for (car in cars) {
            val totalMonths = car.remainingLifeMonths ?: continue
            val acqCostMinor = car.acquisitionCostMinor ?: continue
            val acqDateMs = car.acquisitionDateEpochMs ?: continue

            if (totalMonths <= 0) continue

            val acqCal = Calendar.getInstance().apply { timeInMillis = acqDateMs }
            val monthsElapsed = AmortizationCalc.monthsBetween(acqCal, nowCal)
            if (monthsElapsed <= 0) continue

            // Count existing amortization entries for this car
            val existingCount = expenseDao.countAmortization(car.id)

            // Constant monthly amount
            val monthlyAmount = AmortizationCalc.amortizedAmountPerMonth(acqCostMinor, totalMonths)

            var m = existingCount + 1
            while (m <= monthsElapsed && m <= totalMonths) {
                val monthDate = AmortizationCalc.monthIndexToDate(acqCal, m)
                val expense = Expense(
                    id = java.util.UUID.randomUUID().toString(),
                    carId = car.id,
                    categoryId = "SYSTEM:DOWN_PAYMENT",
                    amountMinor = monthlyAmount,
                    currencyCode = currency,
                    occurredAtEpochMs = monthDate,
                    odometerAtMeters = null,
                    vendor = "Amortization",
                    notes = "Month $m of $totalMonths since acquisition",
                    attachmentUri = null,
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis()
                )
                try {
                    expenseDao.insert(expense)
                } catch (_: Throwable) {
                    // ignore dup errors if any
                }
                m++
            }
        }
        Result.success()
    }

}
