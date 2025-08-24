package com.kdh.kitnadetihai.repo

import android.content.Context
import com.kdh.kitnadetihai.data.DatabaseProvider
import com.kdh.kitnadetihai.data.entity.Car
import com.kdh.kitnadetihai.data.entity.Expense
import com.kdh.kitnadetihai.data.entity.ExpenseCategory
import com.kdh.kitnadetihai.data.entity.OdometerLog
import com.kdh.kitnadetihai.data.entity.AppSettings
import kotlinx.coroutines.flow.Flow

class CarRepository(context: Context) {
    private val db = DatabaseProvider.get(context)
    private val carDao = db.carDao()

    fun observeCars(): Flow<List<Car>> = carDao.observeAll()

    suspend fun addCar(car: Car) = carDao.insert(car)
    suspend fun updateCar(car: Car) = carDao.update(car)
    suspend fun getById(id: String) = carDao.getById(id)
}

class OdometerRepository(context: Context) {
    private val db = DatabaseProvider.get(context)
    private val dao = db.odometerLogDao()

    fun observeForCar(carId: String): Flow<List<OdometerLog>> = dao.observeForCar(carId)
    suspend fun add(log: OdometerLog) = dao.insert(log)
    suspend fun latest(carId: String) = dao.getLatestForCar(carId)
    suspend fun totalDistanceMeters(carId: String) = dao.getTotalDistanceMeters(carId) ?: 0L
}

class ExpenseRepository(context: Context) {
    private val db = DatabaseProvider.get(context)
    private val dao = db.expenseDao()

    fun observeForCar(carId: String) = dao.observeForCar(carId)
    fun observeForCarAndCategory(carId: String, categoryId: String) = dao.observeForCarAndCategory(carId, categoryId)

    fun observeTotalsByCar(carId: String) = dao.observeTotalsByCar(carId)
    fun observeTotalsByCarCategory(carId: String) = dao.observeTotalsByCarCategory(carId)
    fun observeCostPerUnitByCar(carId: String) = dao.observeCostPerUnitByCar(carId)
    fun observeCostPerUnitByCarCategory(carId: String) = dao.observeCostPerUnitByCarCategory(carId)

    suspend fun add(expense: Expense) = dao.insert(expense)

    // Pagination
    suspend fun pageInitialByCar(carId: String, limit: Int): List<Expense> = dao.pageInitialByCar(carId, limit)
    suspend fun pageAfterByCar(carId: String, beforeTs: Long, beforeId: String, limit: Int): List<Expense> =
        dao.pageAfterByCar(carId, beforeTs, beforeId, limit)
}

class ExpenseCategoryRepository(context: Context) {
    private val db = DatabaseProvider.get(context)
    private val dao = db.expenseCategoryDao()

    fun observeActive(): Flow<List<ExpenseCategory>> = dao.observeActive()
}

class SettingsRepository(context: Context) {
    private val db = DatabaseProvider.get(context)
    private val dao = db.appSettingsDao()

    fun observe(): Flow<AppSettings?> = dao.observe()
    suspend fun get(): AppSettings? = dao.get()
    suspend fun upsert(settings: AppSettings) = dao.upsert(settings)
}
