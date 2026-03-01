package com.kdh.carculator

import android.app.Application
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.material.color.DynamicColors
import com.kdh.carculator.work.AmortizationWorker

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
        val wm = WorkManager.getInstance(this)
        wm.cancelUniqueWork(WORK_TAG)
        val oneOff = OneTimeWorkRequestBuilder<AmortizationWorker>().build()
        wm.enqueueUniqueWork(WORK_TAG + "_ONCE", ExistingWorkPolicy.KEEP, oneOff)
    }

    companion object {
        private const val WORK_TAG = "amortization_catchup"
    }
}
