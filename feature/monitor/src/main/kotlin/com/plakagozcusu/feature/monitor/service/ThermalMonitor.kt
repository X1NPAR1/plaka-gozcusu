package com.plakagozcusu.feature.monitor.service

import android.content.Context
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.plakagozcusu.feature.monitor.session.ThermalLevel

/** PowerManager thermal status (API 29+) → [ThermalLevel]. */
internal class ThermalMonitor(
    context: Context,
    private val onChange: (ThermalLevel) -> Unit,
) {
    private val power = context.getSystemService(PowerManager::class.java)
    private val executor = ContextCompat.getMainExecutor(context)
    private val listener = PowerManager.OnThermalStatusChangedListener { onChange(map(it)) }

    fun start() {
        power.addThermalStatusListener(executor, listener)
        onChange(map(power.currentThermalStatus))
    }

    fun stop() = power.removeThermalStatusListener(listener)

    companion object {
        fun map(status: Int): ThermalLevel =
            when (status) {
                PowerManager.THERMAL_STATUS_NONE, PowerManager.THERMAL_STATUS_LIGHT -> ThermalLevel.NORMAL
                PowerManager.THERMAL_STATUS_MODERATE -> ThermalLevel.MODERATE
                PowerManager.THERMAL_STATUS_SEVERE -> ThermalLevel.SEVERE
                else -> ThermalLevel.CRITICAL
            }
    }
}
