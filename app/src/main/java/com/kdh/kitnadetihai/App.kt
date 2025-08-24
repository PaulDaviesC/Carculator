package com.kdh.kitnadetihai

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kdh.kitnadetihai.work.AmortizationWorker
import java.util.concurrent.TimeUnit

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val wm = WorkManager.getInstance(this)
        val periodic = PeriodicWorkRequestBuilder<AmortizationWorker>(24, TimeUnit.HOURS)
            .addTag(WORK_TAG)
            .build()
        wm.enqueueUniquePeriodicWork(WORK_TAG, ExistingPeriodicWorkPolicy.UPDATE, periodic)

        val oneOff = OneTimeWorkRequestBuilder<AmortizationWorker>().build()
        wm.enqueueUniqueWork(WORK_TAG + "_ONCE", ExistingWorkPolicy.KEEP, oneOff)
    }

    companion object {
        private const val WORK_TAG = "amortization_catchup"
    }
}
