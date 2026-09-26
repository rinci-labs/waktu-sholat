package dev.rafa.waktusholat.ui

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import dev.rafa.waktusholat.R
import java.util.Locale

/** App language choice. `in` is Android's resource code for Indonesian. */
enum class AppLanguage(val tag: String?, val label: Int) {
    SYSTEM(null, R.string.language_system),
    ENGLISH("en", R.string.language_english),
    INDONESIAN("in", R.string.language_indonesian),
}

/**
 * Per-app language. On Android 13+ the platform owns it ([LocaleManager]), so the choice also shows
 * up in, and follows, the system's per-app language screen. Below 13 the choice is stored and every
 * context the app renders with (activities, widgets, notifications) is wrapped with [wrap].
 */
object Language {

    private const val FILE = "language"
    private const val KEY = "tag"

    fun current(context: Context): AppLanguage {
        val tag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0)?.language
        } else {
            stored(context)
        }
        return when (tag) {
            "en" -> AppLanguage.ENGLISH
            "in", "id" -> AppLanguage.INDONESIAN
            else -> AppLanguage.SYSTEM
        }
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // The platform recreates visible activities itself.
            activity.getSystemService(LocaleManager::class.java)?.applicationLocales =
                language.tag?.let { LocaleList.forLanguageTags(it) } ?: LocaleList.getEmptyLocaleList()
        } else {
            activity.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(KEY, language.tag).apply()
            activity.recreate()
        }
    }

    /** [base] with the chosen language applied; a no-op on Android 13+ or when following the system. */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = stored(base) ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocale(Locale(tag))
        return base.createConfigurationContext(config)
    }

    /** A token that changes whenever the effective language does, for recreating stale screens. */
    fun token(context: Context): String = context.resources.configuration.locales[0].toLanguageTag()

    private fun stored(context: Context): String? =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null)
}

/** Base for every screen: applies the app language and recreates itself if it changed meanwhile. */
open class BaseActivity : Activity() {

    private var languageToken: String? = null

    override fun attachBaseContext(newBase: Context) = super.attachBaseContext(Language.wrap(newBase))

    override fun onStart() {
        super.onStart()
        val token = Language.token(Language.wrap(applicationContext))
        val previous = languageToken
        languageToken = token
        if (previous != null && previous != token && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) recreate()
    }
}
