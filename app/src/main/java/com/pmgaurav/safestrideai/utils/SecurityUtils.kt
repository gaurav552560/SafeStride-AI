package com.pmgaurav.safestrideai.utils

import java.io.File

object SecurityUtils {
    
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/working/bin/su",
            "/system/bin/failsafe/su",
            "/data/local/su"
        )
        try {
            for (path in paths) {
                if (File(path).exists()) return true
            }
        } catch (_: Exception) {

        }
        
        return checkRootMethod3()
    }

    private fun checkRootMethod3(): Boolean {
        var process: Process? = null
        return try {
            process = Runtime.getRuntime().exec(arrayOf("/system/xbin/which", "su"))
            val reader = process.inputStream.bufferedReader()
            reader.readLine() != null
        } catch (_: Throwable) {
            false
        } finally {
            process?.destroy()
        }
    }
}

