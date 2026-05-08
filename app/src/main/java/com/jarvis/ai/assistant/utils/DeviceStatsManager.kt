package com.jarvis.ai.assistant.utils

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import java.io.File
import java.text.DecimalFormat

class DeviceStatsManager(private val context: Context) {

    fun getBatteryLevel(): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    fun getRamUsage(): Pair<Long, Long> {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val total = mi.totalMem / (1024 * 1024)
        val available = mi.availMem / (1024 * 1024)
        return Pair(total - available, total)
    }

    fun getStorageUsage(): Pair<Long, Long> {
        val path = Environment.getDataDirectory()
        val stat = StatFs(path.path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val total = (totalBlocks * blockSize) / (1024 * 1024 * 1024)
        val available = (availableBlocks * blockSize) / (1024 * 1024 * 1024)
        return Pair(total - available, total)
    }

    fun getFormattedRam(): String {
        val usage = getRamUsage()
        return "${usage.first}MB / ${usage.second}MB"
    }

    fun getFormattedStorage(): String {
        val usage = getStorageUsage()
        return "${usage.first}GB / ${usage.second}GB"
    }

    fun getCpuTemp(): String {
        // CPU temp is hard to get on modern Android without root, returning a placeholder
        return "38°C"
    }
}
