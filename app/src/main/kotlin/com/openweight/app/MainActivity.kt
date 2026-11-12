package com.openweight.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.openweight.app.core.Today
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.ui.theme.OpenWeightTheme
import com.openweight.app.settings.SettingsViewModel
import com.openweight.app.transfer.ImportSession
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
  private val settingsViewModel: SettingsViewModel by viewModels()

  @Inject lateinit var importSession: ImportSession

  @Inject lateinit var today: Today

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    // Only a fresh start: after rotation the same intent was already handled.
    if (savedInstanceState == null) openSharedFile(intent)

    enableEdgeToEdge()
    setContent {
      val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
      val darkTheme =
        when (settings?.themeMode ?: ThemeMode.SYSTEM) {
          ThemeMode.SYSTEM -> isSystemInDarkTheme()
          ThemeMode.LIGHT -> false
          ThemeMode.DARK -> true
        }
      OpenWeightTheme(darkTheme = darkTheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          OpenWeightAppUi(importOpens = importSession.openCount)
        }
      }
    }
  }

  override fun onStart() {
    super.onStart()
    // Back in front, possibly on a later day than when the app was last used.
    today.refresh()
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    openSharedFile(intent)
  }

  /** A .json file opened or shared from another app goes to the import preview. */
  private fun openSharedFile(intent: Intent?) {
    val uri =
      when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
      } ?: return
    lifecycleScope.launch { importSession.open(uri) }
  }
}
