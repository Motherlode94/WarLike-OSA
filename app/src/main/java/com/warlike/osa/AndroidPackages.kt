package com.warlike.osa

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Curated APK registry. The bundled list is empty until official signed WarLike APKs exist.
 * Every entry requires official HTTPS release URL + expected SHA-256 + Android package ID.
 */
data class WarLikePackage(
    val id: String,
    val name: String,
    val packageName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val sha256: String
)

object AndroidPackages {
    private val officialRelease = Regex("^https://github\\.com/Motherlode94/WarLike-[A-Za-z0-9-]+/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+\\.apk$")
    private val idPattern = Regex("^com\\.warlike\\.[a-z0-9.]{2,80}$")
    private val digestPattern = Regex("^[a-f0-9]{64}$")

    fun validate(item: WarLikePackage): Boolean {
        val uri = Uri.parse(item.downloadUrl)
        return item.id.matches(Regex("^[a-z0-9-]{2,64}$")) &&
            item.packageName.matches(idPattern) &&
            item.versionCode > 0 &&
            uri.scheme == "https" &&
            uri.host == "github.com" &&
            item.downloadUrl.matches(officialRelease) &&
            item.sha256.matches(digestPattern)
    }

    fun fromJson(text: String): List<WarLikePackage> {
        val root = JSONObject(text)
        require(root.getString("schema") == "warlike.android.packages.v1")
        val packages = root.getJSONArray("packages")
        require(packages.length() <= 100)
        return (0 until packages.length()).map { i ->
            val raw = packages.getJSONObject(i)
            WarLikePackage(raw.getString("id"), raw.getString("name"), raw.getString("packageName"),
                raw.getLong("versionCode"), raw.getString("downloadUrl"), raw.getString("sha256"))
        }.also { all ->
            require(all.all(::validate) && all.distinctBy { it.packageName }.size == all.size) {
                "Catalogue APK non fiable"
            }
        }
    }

    fun installedVersion(context: Context, packageName: String): Long? = try {
        val info = if (Build.VERSION.SDK_INT >= 33)
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        else context.packageManager.getPackageInfo(packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (_: PackageManager.NameNotFoundException) { null }

    fun startDownload(context: Context, item: WarLikePackage): Long {
        require(validate(item))
        val request = DownloadManager.Request(Uri.parse(item.downloadUrl))
            .setTitle(item.name + " · APK vérifié après téléchargement")
            .setDescription("WarLike OSA — contrôle SHA-256 avant installation")
            .setAllowedOverMetered(true)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, item.id + "-" + item.versionCode + ".apk")
        val service = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return service.enqueue(request)
    }

    fun verifyCompleted(context: Context, id: Long, expected: WarLikePackage): Uri? {
        val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        manager.query(DownloadManager.Query().setFilterById(id)).use { cursor ->
            if (!cursor.moveToFirst()) return null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return null
        }
        val uri = manager.getUriForDownloadedFile(id) ?: return null
        val hash = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val bytes = ByteArray(32768)
            while (true) {
                val size = stream.read(bytes)
                if (size < 0) break
                hash.update(bytes, 0, size)
            }
        } ?: return null
        if (hash.digest().joinToString("") { "%02x".format(it) } != expected.sha256) return null
        // Check actual APK package metadata before passing it to Android's installer.
        val apkPath = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.resolve(expected.id + "-" + expected.versionCode + ".apk") ?: return null
        val archive = context.packageManager.getPackageArchiveInfo(apkPath.absolutePath, 0) ?: return null
        if (archive.packageName != expected.packageName) return null
        val archiveVersion = if (Build.VERSION.SDK_INT >= 28) archive.longVersionCode else archive.versionCode.toLong()
        if (archiveVersion != expected.versionCode) return null
        return uri
    }

    fun openAndroidInstaller(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
