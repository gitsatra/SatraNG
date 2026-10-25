package com.v2ray.ang.util

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import com.v2ray.ang.dto.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppManagerUtil {
    /**
     * Load launchable apps without QUERY_ALL_PACKAGES (Play Protect-friendly).
     * Uses MAIN/LAUNCHER visibility declared in the manifest `<queries>` block.
     */
    suspend fun loadNetworkAppList(context: Context): ArrayList<AppInfo> =
        withContext(Dispatchers.IO) {
            val packageManager = context.packageManager
            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val resolveFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PackageManager.MATCH_ALL
            } else {
                0
            }
            val resolveInfos = packageManager.queryIntentActivities(launcherIntent, resolveFlags)
            val seen = HashSet<String>()
            val apps = ArrayList<AppInfo>()

            for (info in resolveInfos) {
                val packageName = info.activityInfo?.packageName ?: continue
                if (!seen.add(packageName)) continue
                val applicationInfo = try {
                    packageManager.getApplicationInfo(packageName, 0)
                } catch (_: PackageManager.NameNotFoundException) {
                    continue
                }
                val appName = applicationInfo.loadLabel(packageManager).toString()
                val isSystemApp = applicationInfo.flags and ApplicationInfo.FLAG_SYSTEM > 0
                apps.add(AppInfo(appName, packageName, isSystemApp, 0, com.v2ray.ang.handler.AppCategories.of(applicationInfo)))
            }

            return@withContext apps
        }

    fun getLastUpdateTime(context: Context): Long =
        context.packageManager.getPackageInfo(context.packageName, 0).lastUpdateTime
}
