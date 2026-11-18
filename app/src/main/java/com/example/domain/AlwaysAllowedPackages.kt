package com.example.domain

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager

object AlwaysAllowedPackages {

  private val staticSystemPackages =
    setOf(
      "android",
      "com.android.systemui",
      "com.android.emergency",
      "com.android.phone",
      "com.android.server.telecom",
      "com.android.dialer",
      "com.google.android.dialer",
      "com.samsung.android.dialer",
      "com.samsung.android.incallui",
      "com.android.incallui",
      "com.google.android.permissioncontroller",
      "com.android.permissioncontroller",
    )

  fun resolveAlwaysAllowedPackages(context: Context): Set<String> {
    val allowed = HashSet<String>(staticSystemPackages)
    allowed.add(context.packageName)

    val pm = context.packageManager

    // 1. Installed & default launchers (HOME category)
    val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    runCatching { pm.resolveActivity(homeIntent, PackageManager.MATCH_DEFAULT_ONLY) }
      .getOrNull()
      ?.activityInfo
      ?.packageName
      ?.let { allowed.add(it) }

    runCatching { pm.queryIntentActivities(homeIntent, PackageManager.MATCH_ALL) }
      .getOrNull()
      ?.forEach { info -> info.activityInfo?.packageName?.let { allowed.add(it) } }

    // 2. Enabled keyboards (IMEs)
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    runCatching { imm?.enabledInputMethodList }
      .getOrNull()
      ?.forEach { ime -> ime.packageName?.let { allowed.add(it) } }

    // 3. Default dialer & dialer handlers
    val telecom = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
    runCatching { telecom?.defaultDialerPackage }
      .getOrNull()
      ?.takeIf { it.isNotBlank() }
      ?.let { allowed.add(it) }

    val dialIntent = Intent(Intent.ACTION_DIAL)
    runCatching { pm.queryIntentActivities(dialIntent, PackageManager.MATCH_ALL) }
      .getOrNull()
      ?.forEach { info -> info.activityInfo?.packageName?.let { allowed.add(it) } }

    return allowed
  }

  fun isAlwaysAllowed(context: Context, packageName: String?): Boolean {
    if (packageName.isNullOrBlank()) return true
    if (packageName == context.packageName) return true
    return resolveAlwaysAllowedPackages(context).contains(packageName)
  }
}
