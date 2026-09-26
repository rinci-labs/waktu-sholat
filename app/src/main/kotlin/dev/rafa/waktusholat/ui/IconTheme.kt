package dev.rafa.waktusholat.ui

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import dev.rafa.waktusholat.R

/**
 * Launcher icon themes. Each is an `<activity-alias>` of MainActivity with its own icon; exactly one
 * is enabled at a time. Switching happens only when the user picks a theme in Settings, never on a
 * timer: toggling launcher components makes some launchers drop the home-screen shortcut, which is
 * acceptable once on request but not several times a day.
 */
enum class IconTheme(val alias: String, val label: Int, val preview: Int) {
    DEFAULT(".LauncherDefault", R.string.icon_default, R.drawable.icon_bg_default),
    SUBUH(".LauncherSubuh", R.string.period_subuh, R.drawable.icon_bg_subuh),
    PAGI(".LauncherPagi", R.string.period_pagi, R.drawable.icon_bg_pagi),
    SIANG(".LauncherSiang", R.string.period_siang, R.drawable.icon_bg_siang),
    SORE(".LauncherSore", R.string.period_sore, R.drawable.icon_bg_sore),
    SENJA(".LauncherSenja", R.string.period_senja, R.drawable.icon_bg_senja),
    MALAM(".LauncherMalam", R.string.period_malam, R.drawable.icon_bg_malam);

    private fun component(context: Context) = ComponentName(context.packageName, "dev.rafa.waktusholat$alias")

    companion object {
        /** The theme whose alias is currently enabled (the manifest default counts as enabled). */
        fun current(context: Context): IconTheme {
            val pm = context.packageManager
            return entries.firstOrNull { theme ->
                when (pm.getComponentEnabledSetting(theme.component(context))) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> theme == DEFAULT
                    else -> false
                }
            } ?: DEFAULT
        }

        /** Enables [theme] first, then disables the rest, so there is never a moment with no icon. */
        fun apply(context: Context, theme: IconTheme) {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                theme.component(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP,
            )
            for (other in entries) {
                if (other == theme) continue
                pm.setComponentEnabledSetting(
                    other.component(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }
}
