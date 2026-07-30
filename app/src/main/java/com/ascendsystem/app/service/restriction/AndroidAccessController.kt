package com.ascendsystem.app.service.restriction

import android.content.Context
import com.ascendsystem.app.feature.verification.domain.AccessController
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class AndroidAccessController @Inject constructor(
    @ApplicationContext private val context: Context
) : AccessController {
    override fun unlockAfterVerifiedQuest() {
        RestrictionMonitorService.stop(context)
    }
}
