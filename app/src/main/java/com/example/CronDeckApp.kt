package com.example

import android.app.Application
import com.example.data.CronDatabase
import com.example.data.CronRepository
import com.example.data.SettingsPreferences
import com.example.security.CryptoVault

class CronDeckApp : Application() {
    val database: CronDatabase by lazy { CronDatabase.getInstance(this) }
    val cryptoVault: CryptoVault by lazy { CryptoVault() }
    val repository: CronRepository by lazy { CronRepository(database.cronDao(), cryptoVault) }
    val settingsPreferences: SettingsPreferences by lazy { SettingsPreferences(this) }
}
