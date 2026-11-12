package com.openweight.app.core

import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Today as screens show it. The app can stay open (or in the background) past midnight, so screens
 * that mark "today" follow [date] instead of reading it once; the app calls [refresh] whenever it
 * comes back to the front.
 */
@Singleton
class Today @Inject constructor(private val clock: TimeAndIds) {
  private val _date = MutableStateFlow(clock.today())
  val date: StateFlow<LocalDate> = _date.asStateFlow()

  fun refresh() {
    _date.value = clock.today()
  }
}
