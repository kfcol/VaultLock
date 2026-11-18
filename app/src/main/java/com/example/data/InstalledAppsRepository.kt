package com.example.data

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
  val packageName: String,
  val label: String,
  val isSystemApp: Boolean,
)

class InstalledAppsRepository(private val context: Context) {

  suspend fun loadLaunchableApps(): List<InstalledAppInfo> =
    withContext(Dispatchers.IO) {
      val pm = context.packageManager
      val ownPackage = context.packageName
      val mainIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      val resolveInfos = pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)

      val seenPackages = HashSet<String>()
      val results = ArrayList<InstalledAppInfo>(resolveInfos.size)

      for (info in resolveInfos) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (pkg == ownPackage || !seenPackages.add(pkg)) continue

        val label = info.loadLabel(pm)?.toString()?.trim().takeUnless { it.isNullOrBlank() } ?: pkg
        val appInfo =
          runCatching { pm.getApplicationInfo(pkg, 0) }.getOrNull()
        val isSystem =
          appInfo != null &&
            ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
              (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0)

        results.add(
          InstalledAppInfo(
            packageName = pkg,
            label = label,
            isSystemApp = isSystem,
          )
        )
      }

      results.sortBy { it.label.lowercase() }
      results
    }
}
