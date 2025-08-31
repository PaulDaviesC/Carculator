package com.kdh.carculator.data

import android.content.Context

object DatabaseProvider {
    @Volatile private var db: KdhDatabase? = null

    fun get(context: Context): KdhDatabase {
        val existing = db
        if (existing != null) return existing
        synchronized(this) {
            val again = db
            if (again != null) return again
            val created = KdhDatabase.getInstance(context)
            db = created
            return created
        }
    }
}
