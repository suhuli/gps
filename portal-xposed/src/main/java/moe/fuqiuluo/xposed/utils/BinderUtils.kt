@file:Suppress("LocalVariableName", "PrivateApi")
package moe.fuqiuluo.xposed.utils

import android.content.Context
import android.os.Binder
import android.os.Build
import de.robv.android.xposed.XposedBridge


object BinderUtils {
    private fun getActivityContext(): Context? {
        // public static ActivityManagerService self()
        // frameworks/base/services/java/com/android/server/am/ActivityManagerService.java
        try {
            val cam = Class.forName("com.android.server.am.ActivityManagerService")
            val am = cam.getMethod("self").invoke(null) ?: return null
            val mContext = cam.getDeclaredField("mContext")
            mContext.isAccessible = true
            return mContext.get(am) as? Context
        } catch (e: Throwable) {
            return null
        }
    }

    fun getSystemContext(): Context? {
        try {
            val cActivityThread = Class.forName("android.app.ActivityThread")
            val activityThread = cActivityThread.getMethod("currentActivityThread")
                .invoke(null) ?: return null
            return (cActivityThread.getMethod("getSystemContext").invoke(activityThread) as? Context) ?: getActivityContext()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        return null
    }

    fun getUidPackageNames(context: Context = getSystemContext()!!, uid: Int = getCallerUid()): Array<String>? {
        val packageManager = context.packageManager
        return packageManager.getPackagesForUid(uid)
    }

    fun getCallerUid(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            kotlin.runCatching { Binder.getCallingUidOrThrow() }.getOrNull() ?: -1
        } else {
            Binder.getCallingUid()
        }
    }

    /**
     * Check whether the `PortalService` is started properly
     */
    fun isLocationProviderEnabled(uid: Int): Boolean {
        if (uid == android.os.Process.SYSTEM_UID || uid == 0) return true
        return runCatching {
            val context = getSystemContext() ?: return false
            val pm = context.packageManager
            val packages = pm.getPackagesForUid(uid) ?: return false
            if (!packages.contains("com.yadea.smartmoto")) return false
            @Suppress("DEPRECATION")
            val signatures = pm.getPackageInfo("com.yadea.smartmoto", android.content.pm.PackageManager.GET_SIGNATURES).signatures ?: return false
            signatures.any {
                java.security.MessageDigest.getInstance("SHA-256").digest(it.toByteArray())
                    .joinToString("") { byte -> "%02x".format(byte.toInt() and 255) } ==
                    "9df4f4bdb3b9c53e1b148699c766dda446386f69000a42884aeab6630a21e485"
            }
        }.getOrDefault(false)
    }

    fun isSystemPackages(packageNames: String): Boolean {
        if (packageNames.contains("com.xiaomi.location.fused") ||
            packageNames.contains("com.xiaomi.metoknlp") ||
            //packageNames.contains("com.android.phone") ||
            packageNames.contains("com.android.location.fused")
            ) {
            return false
        }
        return packageNames.contains("com.android") ||
                packageNames.contains("com.miui") ||
                packageNames.contains("com.xiaomi") ||
                packageNames.contains("com.oplus") ||
                packageNames.contains("com.coloros") ||
                packageNames.contains("com.heytap") ||
                packageNames.contains("android.framework") ||
                packageNames.contains("com.qualcomm") ||
                packageNames.contains("com.google.android.permissioncontroller")
    }

    fun isSystemAppsCall(uid: Int = getCallerUid()): Boolean {
        if (uid > 10000) {
            val packageNames = kotlin.runCatching { getUidPackageNames(uid = uid)?.joinToString() }
                .getOrNull() ?: return true
            return isSystemPackages(packageNames)
        }
        return true
    }
}