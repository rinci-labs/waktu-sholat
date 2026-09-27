package dev.rafa.waktusholat.update

import android.app.DownloadManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import dev.rafa.waktusholat.R
import dev.rafa.waktusholat.ui.Language
import dev.rafa.waktusholat.ui.SettingsActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * In-app updates from this project's GitHub Releases, for a sideloaded app with no store.
 *
 * - [check] reads the latest release over HTTPS and compares its tag (`v1.2.3`) with the installed
 *   version; the release's `.apk.sha256` checksum is fetched with it.
 * - [download] hands the APK to the system DownloadManager (resumable, with its own progress
 *   notification) into the app's private external folder, so no storage permission is needed.
 * - When the download completes, the file's SHA-256 must match the published checksum before the
 *   system installer is offered; a mismatch deletes the file. The installer itself also refuses an
 *   APK not signed with the same key as the installed app.
 * - [autoCheck] runs at most once a day when the app opens and posts a notification when a newer
 *   version exists. Nothing runs in the background otherwise.
 */
object Updater {

    private const val REPO = "rinci-labs/waktu-sholat"
    private const val API = "https://api.github.com/repos/$REPO/releases/latest"
    const val RELEASES_PAGE = "https://github.com/$REPO/releases/latest"

    private const val FILE = "updates"
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_DOWNLOAD_ID = "download_id"
    private const val KEY_EXPECTED_SHA = "expected_sha"
    private const val KEY_DOWNLOAD_TAG = "download_tag"
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val TIMEOUT_MILLIS = 10_000

    private const val CHANNEL = "updates"
    private const val NOTIFY_AVAILABLE = 900
    private const val NOTIFY_READY = 901

    class Release(
        val version: String,
        val notes: String,
        val apkUrl: String,
        val apkSize: Long,
        val sha256: String?,
    )

    sealed interface Result {
        class UpToDate(val version: String) : Result
        class Available(val release: Release) : Result
        object Failed : Result
    }

    private fun prefs(context: Context) = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun currentVersion(context: Context): String =
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Debug builds install as a separate package, so updating them from releases makes no sense. */
    private fun isDebuggable(context: Context) =
        context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    var Context.autoCheckEnabled: Boolean
        get() = prefs(this).getBoolean(KEY_AUTO, true)
        set(value) = prefs(this).edit().putBoolean(KEY_AUTO, value).apply()

    /** Fetches the latest release on a background thread; calls back on the main thread. */
    fun check(context: Context, onResult: (Result) -> Unit) {
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        Thread {
            val result = runCatching { fetchLatest(app) }.getOrElse { Result.Failed }
            prefs(app).edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
            main.post { onResult(result) }
        }.start()
    }

    private fun fetchLatest(context: Context): Result {
        val json = JSONObject(get(API, accept = "application/vnd.github+json") ?: return Result.Failed)
        val version = json.optString("tag_name").removePrefix("v")
        if (version.isEmpty()) return Result.Failed
        val installed = currentVersion(context)
        if (compare(version, installed) <= 0) return Result.UpToDate(installed)

        val assets = json.optJSONArray("assets") ?: return Result.Failed
        var apk: JSONObject? = null
        var checksumUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.getJSONObject(i)
            val name = asset.optString("name")
            when {
                name.endsWith(".apk") -> apk = asset
                name.endsWith(".apk.sha256") -> checksumUrl = asset.optString("browser_download_url")
            }
        }
        apk ?: return Result.Failed
        val sha = checksumUrl?.let { get(it) }?.trim()?.split(Regex("\\s+"))?.firstOrNull()?.lowercase()
            ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) }
        return Result.Available(
            Release(
                version = version,
                notes = plainNotes(json.optString("body")),
                apkUrl = apk.optString("browser_download_url"),
                apkSize = apk.optLong("size"),
                sha256 = sha,
            ),
        )
    }

    /**
     * Release notes as plain text for a dialog: markdown emphasis and headings stripped, bullets
     * drawn as dots, and GitHub's "Full Changelog" link line dropped.
     */
    private fun plainNotes(markdown: String): String = markdown.lines()
        .map { it.trim() }
        .filterNot { it.startsWith("**Full Changelog**") || it.startsWith("<!--") }
        .map { line ->
            line.replace("**", "").replace("__", "").replace("`", "")
                .replace(Regex("^#+\\s*"), "")
                .replace(Regex("^[-*]\\s+"), "• ")
        }
        .joinToString("\n")
        .replace(Regex("\n{3,}"), "\n\n")
        .trim()

    private fun get(url: String, accept: String? = null): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", "waktu-sholat-android")
            accept?.let { connection.setRequestProperty("Accept", it) }
            if (connection.responseCode !in 200..299) null else connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /** Numeric, part-by-part comparison of `1.10.0` against `1.9.3`. */
    fun compare(a: String, b: String): Int {
        val x = a.split('.', '-').map { it.toIntOrNull() ?: 0 }
        val y = b.split('.', '-').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(x.size, y.size)) {
            val d = x.getOrElse(i) { 0 }.compareTo(y.getOrElse(i) { 0 })
            if (d != 0) return d
        }
        return 0
    }

    /** Once a day at most, when the app opens: a quiet notification if a newer version exists. */
    fun autoCheck(context: Context) {
        val app = context.applicationContext
        if (isDebuggable(app) || !app.autoCheckEnabled) return
        if (System.currentTimeMillis() - prefs(app).getLong(KEY_LAST_CHECK, 0L) < DAY_MILLIS) return
        check(app) { result -> if (result is Result.Available) notifyAvailable(app, result.release) }
    }

    /** Starts the download; returns false when DownloadManager is unavailable. */
    fun download(context: Context, release: Release): Boolean {
        val app = context.applicationContext
        val manager = app.getSystemService(DownloadManager::class.java) ?: return false
        val name = "waktu-sholat-${release.version}.apk"
        app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.resolve(name)?.delete()
        val request = DownloadManager.Request(Uri.parse(release.apkUrl))
            .setTitle(app.getString(R.string.update_downloading, release.version))
            .setMimeType(MIME_APK)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, name)
        val id = manager.enqueue(request)
        prefs(app).edit()
            .putLong(KEY_DOWNLOAD_ID, id)
            .putString(KEY_EXPECTED_SHA, release.sha256)
            .putString(KEY_DOWNLOAD_TAG, release.version)
            .apply()
        return true
    }

    /** Id of the download in flight, or -1. */
    fun pendingDownload(context: Context): Long = prefs(context).getLong(KEY_DOWNLOAD_ID, -1L)

    /**
     * Called when a download finishes. Verifies the checksum off the main thread, then either opens
     * the installer ([fromForeground]) or posts a "ready to install" notification.
     */
    fun onDownloadComplete(context: Context, id: Long, fromForeground: Boolean, onDone: () -> Unit = {}) {
        val app = context.applicationContext
        val prefs = prefs(app)
        // Only our own download: anything else (or a spoofed broadcast) is ignored.
        if (id != prefs.getLong(KEY_DOWNLOAD_ID, -1L)) return onDone()
        val manager = app.getSystemService(DownloadManager::class.java) ?: return onDone()
        val expected = prefs.getString(KEY_EXPECTED_SHA, null)
        val version = prefs.getString(KEY_DOWNLOAD_TAG, "").orEmpty()
        val main = Handler(Looper.getMainLooper())
        Thread {
            val uri = manager.getUriForDownloadedFile(id)
            val ok = uri != null && (expected == null || sha256(app, uri) == expected)
            main.post {
                prefs.edit().remove(KEY_DOWNLOAD_ID).apply()
                if (!ok) {
                    manager.remove(id)
                    onDone()
                    return@post
                }
                val install = installIntent(uri!!)
                if (fromForeground) {
                    runCatching { app.startActivity(install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                } else {
                    notifyReady(app, version, install)
                }
                onDone()
            }
        }.start()
    }

    private fun sha256(context: Context, uri: Uri): String? = runCatching {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        } ?: return null
        digest.digest().joinToString("") { "%02x".format(it) }
    }.getOrNull()

    private fun installIntent(uri: Uri): Intent =
        Intent(Intent.ACTION_VIEW).setDataAndType(uri, MIME_APK).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, context.getString(R.string.update_channel), NotificationManager.IMPORTANCE_LOW),
        )
    }

    private fun notifyAvailable(context: Context, release: Release) {
        val app = Language.wrap(context)
        val open = PendingIntent.getActivity(
            app, NOTIFY_AVAILABLE,
            Intent(app, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(app, NOTIFY_AVAILABLE, app.getString(R.string.update_available_title, release.version), app.getString(R.string.update_available_text), open)
    }

    private fun notifyReady(context: Context, version: String, install: Intent) {
        val app = Language.wrap(context)
        val open = PendingIntent.getActivity(
            app, NOTIFY_READY, install.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        post(app, NOTIFY_READY, app.getString(R.string.update_ready_title, version), app.getString(R.string.update_ready_text), open)
    }

    private fun post(context: Context, id: Int, title: String, text: String, intent: PendingIntent) {
        if (!dev.rafa.waktusholat.notify.Notifications.canPostNotifications(context)) return
        ensureChannel(context)
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        context.getSystemService(NotificationManager::class.java)?.notify(id, notification)
    }

    private const val MIME_APK = "application/vnd.android.package-archive"
}

/** DownloadManager announces completion to the requesting package; this finishes the update. */
class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        // Checksum verification runs off the main thread; keep the broadcast alive until it is done.
        val pending = goAsync()
        Updater.onDownloadComplete(context, id, fromForeground = SettingsActivity.visible) { pending.finish() }
    }
}
