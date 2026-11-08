package com.openweight.app

import android.Manifest
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

/** The app promises to work fully offline. This fails the build if any library adds network access. */
@RunWith(AndroidJUnit4::class)
class OfflineGuardTest {
  @Test
  fun mergedManifest_requestsNoInternetPermission() {
    val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    val info =
      context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
    val requested = info.requestedPermissions.orEmpty().toList()

    assertFalse("INTERNET permission found in $requested", Manifest.permission.INTERNET in requested)
    assertFalse(
      "ACCESS_NETWORK_STATE permission found in $requested",
      Manifest.permission.ACCESS_NETWORK_STATE in requested,
    )
  }
}
