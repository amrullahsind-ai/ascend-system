package com.ascendsystem.app.core.database

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import com.ascendsystem.app.feature.assessment.data.AssessmentDao
import com.ascendsystem.app.feature.verification.data.VerificationDao

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): AscendDatabase =
        Room.databaseBuilder(context, AscendDatabase::class.java, "ascend.db")
            .addMigrations(AscendDatabase.MIGRATION_1_2)
            .addMigrations(AscendDatabase.MIGRATION_2_3)
            .build()
    @Provides fun questDao(db: AscendDatabase): QuestDao = db.questDao()
    @Provides fun assessmentDao(db: AscendDatabase): AssessmentDao = db.assessmentDao()
    @Provides fun verificationDao(db: AscendDatabase): VerificationDao = db.verificationDao()
}
