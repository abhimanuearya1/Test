package io.github.abhimanuearya1.expensetracker.data

import android.content.Context

/** Small app preferences kept on the phone. */
class Settings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** ISO currency code chosen by the user ("INR"), or null to follow the phone's region. */
    var currencyCode: String?
        get() = prefs.getString(KEY_CURRENCY, null)
        set(value) {
            prefs.edit().apply {
                if (value == null) remove(KEY_CURRENCY) else putString(KEY_CURRENCY, value)
            }.apply()
        }

    private companion object {
        const val KEY_CURRENCY = "currency"
    }
}
