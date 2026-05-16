package com.pmgaurav.safestrideai.di

import android.content.Context
import android.os.Vibrator
import androidx.room.Room
import com.pmgaurav.safestrideai.alert.AlertManager
import com.pmgaurav.safestrideai.data.AppDatabase
import com.pmgaurav.safestrideai.map.TileDao
import com.pmgaurav.safestrideai.map.TileDatabase
import com.pmgaurav.safestrideai.gesture.HapticLanguage
import com.pmgaurav.safestrideai.wear.WearSyncManager
import com.pmgaurav.safestrideai.utils.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FeatureModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        return NetworkSecurityManager.createPinnedOkHttpClient()
    }

    @Provides
    @Singleton
    fun provideTileDatabase(@ApplicationContext context: Context): TileDatabase {
        return Room.databaseBuilder(
            context,
            TileDatabase::class.java,
            "tile_database",
        ).build()
    }

    @Provides
    fun provideTileDao(database: TileDatabase): TileDao {
        return database.tileDao()
    }

    @Provides
    @Singleton
    fun provideVibrator(@ApplicationContext context: Context): Vibrator {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return AppDatabase.getDatabase(context)
    }

    @Provides
    @Singleton
    fun provideAlertManager(
        @ApplicationContext context: Context,
        wearSyncManager: WearSyncManager,
        hapticLanguage: HapticLanguage
    ): AlertManager {
        return AlertManager(context, wearSyncManager, hapticLanguage).apply {
            initialize()
        }
    }

    @Provides
    @Singleton
    fun provideAppErrorHandler(): AppErrorHandler {
        return AppErrorHandler()
    }

    @Provides
    @Singleton
    fun providePerformanceMonitor(@ApplicationContext context: Context): PerformanceMonitor {
        return PerformanceMonitor(context)
    }
}

