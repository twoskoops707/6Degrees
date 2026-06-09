package com.twoskoops707.sixdegrees.data

import android.content.Context

object AppSettings {
    const val PREFS_NAME = "app_settings"
    const val KEY_INVESTIGATOR_MODE = "investigator_mode"

    fun isInvestigatorMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_INVESTIGATOR_MODE, false)
}
