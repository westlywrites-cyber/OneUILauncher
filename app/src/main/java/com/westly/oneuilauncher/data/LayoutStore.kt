package com.westly.oneuilauncher.data

import android.content.Context

/** Saves the order of home screen items after the user rearranges them. */
object LayoutStore {

    private const val PREFS = "home_layout"
    private const val KEY_ORDER = "order"
    private const val SEPARATOR = "\n"

    fun loadOrder(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ORDER, null) ?: return emptyList()
        return raw.split(SEPARATOR).filter { it.isNotEmpty() }
    }

    fun saveOrder(context: Context, ids: List<String>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY_ORDER, ids.joinToString(SEPARATOR))
            .apply()
    }

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .remove(KEY_ORDER)
            .apply()
    }
}
