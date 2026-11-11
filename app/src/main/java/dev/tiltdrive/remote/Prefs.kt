package dev.tiltdrive.remote

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow

data class Settings(
    val range: Float = 70f,       // degrees of tilt for full lock
    val deadzone: Float = 3f,     // percent
    val invert: Boolean = false,
    val analog: Boolean = false,  // pedals: slide up/down to modulate
    val haptics: Boolean = true,
)

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("tiltdrive", Context.MODE_PRIVATE)
    var lastCode: String
        get() = sp.getString("code", "") ?: ""
        set(v) = sp.edit().putString("code", v).apply()
    var lastHost: String
        get() = sp.getString("host", "") ?: ""
        set(v) = sp.edit().putString("host", v).apply()

    fun load() = Settings(
        range = sp.getFloat("range", 70f),
        deadzone = sp.getFloat("deadzone", 3f),
        invert = sp.getBoolean("invert", false),
        analog = sp.getBoolean("analog", false),
        haptics = sp.getBoolean("haptics", true),
    )

    fun save(s: Settings) = sp.edit()
        .putFloat("range", s.range).putFloat("deadzone", s.deadzone)
        .putBoolean("invert", s.invert).putBoolean("analog", s.analog).putBoolean("haptics", s.haptics)
        .apply()
}

class DriveViewModel(app: Application) : AndroidViewModel(app) {
    val link = BeamLink(app)
    private val prefs = Prefs(app)
    val settings = MutableStateFlow(prefs.load())
    var code by mutableStateOf(prefs.lastCode)
    var host by mutableStateOf(prefs.lastHost)

    fun update(s: Settings) {
        settings.value = s
        prefs.save(s)
    }

    fun connect() {
        prefs.lastCode = code
        prefs.lastHost = host
        link.connect(code, host.ifBlank { null })
    }

    override fun onCleared() {
        link.disconnect()
    }
}
