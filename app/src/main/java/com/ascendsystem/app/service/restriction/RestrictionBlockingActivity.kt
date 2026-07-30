package com.ascendsystem.app.service.restriction

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import com.ascendsystem.app.MainActivity
import com.ascendsystem.app.core.designsystem.*
import com.ascendsystem.app.core.domain.OverrideRepository
import com.ascendsystem.app.core.domain.OverrideRequest
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class RestrictionBlockingActivity : ComponentActivity() {
    @Inject lateinit var overrides: OverrideRepository
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val blockedPackage = intent.getStringExtra(EXTRA_PACKAGE).orEmpty()
        setContent {
            AscendTheme {
                AscendBackground {
                    Column(
                        Modifier.fillMaxSize().padding(AscendSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AscendSpacing.md)
                    ) {
                        SystemHeader("Strict protocol", "Akses dibatasi", blockedPackage)
                        SystemPanel(Modifier.fillMaxWidth(), accent = AscendColors.Critical) {
                            Text("Aplikasi ini masuk daftar pembatasan.")
                            Text("Selesaikan quest aktif atau gunakan Emergency Override.")
                        }
                        PrimarySystemButton("Buka quest") { openAscend() }
                        EmergencyOverrideButton { activateEmergencyOverride() }
                        SafetyNoticeCard("Consumer mode bersifat best-effort. Izin dapat dicabut melalui pengaturan Android.")
                    }
                }
            }
        }
    }

    override fun onBackPressed() {
        moveTaskToBack(true)
    }

    private fun openAscend() {
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }

    private fun activateEmergencyOverride() {
        lifecycleScope.launch {
            overrides.activate(
                OverrideRequest(
                    reason = "EMERGENCY_FROM_BLOCKING_SCREEN",
                    note = "Emergency access requested from blocking screen",
                    durationMinutes = 5
                ),
                activeQuestId = null,
                nowMillis = System.currentTimeMillis()
            )
            finish()
        }
    }

    companion object { const val EXTRA_PACKAGE = "blocked_package" }
}
