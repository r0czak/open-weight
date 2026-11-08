package com.openweight.app.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.openweight.app.core.model.ThemeMode
import com.openweight.app.core.model.WeightUnit
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) :
  ViewModel() {

  /** Null until the stored settings have been read, so the UI never flashes the wrong theme. */
  val settings: StateFlow<Settings?> =
    repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

  fun setWeightUnit(unit: WeightUnit) {
    viewModelScope.launch { repository.setWeightUnit(unit) }
  }

  fun setThemeMode(mode: ThemeMode) {
    viewModelScope.launch { repository.setThemeMode(mode) }
  }
}
