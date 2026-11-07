package com.warlike.osa

import androidx.test.ext.junit.runners.AndroidJUnit4
import android.app.Activity
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HubSmokeTest {
    @Test fun activityLaunchesOnAndroid() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val intent = android.content.Intent(instrumentation.targetContext, MainActivity::class.java)
        intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(intent)
        try { assertTrue(activity is MainActivity) }
        finally { activity.finish() }
    }
    @Test fun startupAndBundledCatalog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val catalog = context.assets.open("apps.json").bufferedReader().use { it.readText() }
        assertTrue(catalog.contains("warlike.android.hub.catalog.v1"))
        val packages = context.assets.open("android-apps.json").bufferedReader().use { it.readText() }
        assertTrue(AndroidPackages.fromJson(packages).isEmpty())
        val invalid = WarLikePackage("test", "Fake", "com.warlike.fake", 1,
            "https://malicious.example/installer.apk", "0".repeat(64))
        assertFalse(AndroidPackages.validate(invalid))
        val valid = WarLikePackage("future-soldier", "WarLike Soldier",
            "com.warlike.soldier", 10,
            "https://github.com/Motherlode94/WarLike-Soldier/releases/download/v1.0.0/warlike-soldier.apk",
            "a".repeat(64))
        assertTrue(AndroidPackages.validate(valid))
        assertFalse(AndroidPackages.validate(valid.copy(sha256 = "invalid")))
        assertFalse(AndroidPackages.validate(valid.copy(packageName = "com.other.app")))
        assertFalse(AndroidPackages.validate(valid.copy(downloadUrl = "https://github.com/outsider/fake/releases/download/v1/test.apk")))
    }
}
