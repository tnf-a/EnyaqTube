package de.enyaqtube

import android.content.Context

class Prefs(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("enyaqtube", Context.MODE_PRIVATE)

    /** Im Auto die Desktop-Version von YouTube statt der mobilen anzeigen. */
    var desktopInCar: Boolean
        get() = sp.getBoolean("desktop_in_car", false)
        set(v) = sp.edit().putBoolean("desktop_in_car", v).apply()

    /** Zoomfaktor der Seite im Auto in Prozent. */
    var carZoom: Int
        get() = sp.getInt("car_zoom", 100)
        set(v) = sp.edit().putInt("car_zoom", v).apply()

    var recentSearches: List<String>
        get() = sp.getString("recent_searches", "")!!.split('\n').filter { it.isNotBlank() }
        set(v) = sp.edit().putString("recent_searches", v.take(8).joinToString("\n")).apply()

    fun addSearch(q: String) {
        recentSearches = listOf(q) + recentSearches.filter { it != q }
    }
}
