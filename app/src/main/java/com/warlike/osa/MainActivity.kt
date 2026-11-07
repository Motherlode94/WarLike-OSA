package com.warlike.osa

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject

/**
 * WarLike OSA — launcher's UI is 100% native Android Views.
 * Web apps remain external services; no WebView, fake APKs or arbitrary downloads.
 */
class MainActivity : Activity() {
    private data class HubApp(
        val id: String,
        val name: String,
        val category: String,
        val description: String,
        val url: String,
        val delivery: String,
    )
    private val backdrop = Color.rgb(13, 17, 28)
    private val panel = Color.rgb(25, 31, 48)
    private val border = Color.rgb(55, 64, 91)
    private val violet = Color.rgb(171, 153, 255)
    private val muted = Color.rgb(172, 185, 210)
    private val white = Color.rgb(244, 245, 255)
    private val teal = Color.rgb(110, 223, 206)
    private val prefs by lazy { getSharedPreferences("warlike.androidhub.v1", MODE_PRIVATE) }
    private var apps = listOf<HubApp>()
    private var nativePackages = listOf<WarLikePackage>()
    private val downloads = mutableMapOf<String, Long>()
    private var filter = "Tout"
    private var query = ""
    private var favoritesOnly = false
    private var recentOnly = false
    private lateinit var results: LinearLayout
    private lateinit var counter: TextView
    private lateinit var tabs: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = backdrop
        window.navigationBarColor = backdrop
        filter = savedInstanceState?.getString("filter") ?: "Tout"
        query = savedInstanceState?.getString("query") ?: ""
        favoritesOnly = savedInstanceState?.getBoolean("favoritesOnly") ?: false
        recentOnly = savedInstanceState?.getBoolean("recentOnly") ?: false
        apps = loadBundledApps()
        nativePackages = try { AndroidPackages.fromJson(assets.open("android-apps.json").bufferedReader().use { it.readText() }) } catch (_: Exception) { emptyList() }
        draw()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("filter", filter)
        outState.putString("query", query)
        outState.putBoolean("favoritesOnly", favoritesOnly)
        outState.putBoolean("recentOnly", recentOnly)
        super.onSaveInstanceState(outState)
    }

    private fun dp(n: Int): Int = (resources.displayMetrics.density * n + .5f).toInt()

    private fun shape(color: Int, radius: Int = 16, stroke: Int? = null): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (stroke != null) setStroke(dp(1), stroke)
        }
    }

    private fun text(value: String, size: Float, color: Int = white, bold: Boolean = false) =
        TextView(this).apply {
            this.text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(null, Typeface.BOLD)
        }

    private fun column(pad: Int = 0) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        if (pad > 0) setPadding(dp(pad), dp(pad), dp(pad), dp(pad))
    }

    private fun addGap(parent: LinearLayout, height: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(height)))
    }

    private fun isAllowedService(url: String): Boolean {
        val uri = Uri.parse(url)
        val host = uri.host ?: return false
        return uri.scheme == "https" && uri.userInfo == null && (uri.port == -1 || uri.port == 443) &&
            (host.endsWith(".pages.dev") || host == "warlike-mail.vikktor7.chatgpt.site" ||
                host == "warlike-server.vikktor7.chatgpt.site" ||
                (host == "github.com" && (uri.path == "/Motherlode94/WarLike-OSA" || uri.path == "/Motherlode94/WarLike-OSW")))
    }

    private fun loadBundledApps(): List<HubApp> = try {
        val source = assets.open("apps.json").bufferedReader().use { it.readText() }
        val catalog = JSONObject(source)
        require(catalog.getString("schema") == "warlike.android.hub.catalog.v1")
        val list = catalog.getJSONArray("apps")
        (0 until list.length()).map { i ->
            val x = list.getJSONObject(i)
            HubApp(
                x.getString("id"), x.getString("name"),
                x.getString("category"), x.getString("description"),
                x.getString("url"), x.getString("delivery")
            )
        }.filter { app ->
            isAllowedService(app.url) && app.delivery == "web"
        }
    } catch (e: Exception) {
        emptyList()
    }

    private fun favorite(id: String) = prefs.getBoolean("favorite:$id", false)
    private fun toggleFavorite(id: String) {
        prefs.edit().putBoolean("favorite:$id", !favorite(id)).apply()
        displayApps()
    }

    private fun draw() {
        val scroll = ScrollView(this).apply { setBackgroundColor(backdrop); isFillViewport = true }
        val root = column(18)
        scroll.addView(root)
        setContentView(scroll)

        val brand = text("W  WARLIKE", 16f, violet, true)
        brand.letterSpacing = .17f
        root.addView(brand)
        addGap(root, 16)
        root.addView(text("WARLIKE OSA", 32f, white, true))
        root.addView(text("Ton espace Android, tes applications et tes services WarLike.", 14f, muted))
        addGap(root, 20)

        val intro = column(15).apply { background = shape(panel, 18, border) }
        intro.addView(text("ÉCOSYSTÈME WARLIKE", 12f, teal, true))
        addGap(intro, 7)
        intro.addView(text("${apps.size} liens WarLike référencés", 22f, white, true))
        intro.addView(text("Les services web s'ouvrent dans ton navigateur. Les APK Android seront affichés séparément lorsqu'ils existeront.", 13f, muted))
        root.addView(intro)
        addGap(root, 13)
        val deviceApps = Button(this).apply {
            text = "Applications installées"
            isAllCaps = false
            setOnClickListener { showDeviceApps() }
        }
        root.addView(deviceApps)
        addGap(root, 13)
        val homeSettings = Button(this).apply {
            text = "Choisir l’application d’accueil Android"
            isAllCaps = false
            setOnClickListener {
                try {
                    startActivity(Intent(android.provider.Settings.ACTION_HOME_SETTINGS))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "Ouvre les paramètres Android pour choisir l’accueil.", Toast.LENGTH_LONG).show()
                }
            }
        }
        root.addView(homeSettings)
        addGap(root, 18)
        val nativeHeader = text("APPLICATIONS ANDROID NATIVES", 17f, white, true)
        root.addView(nativeHeader)
        addGap(root, 8)
        val nativeArea = column(12).apply { background = shape(panel, 14, border) }
        if (nativePackages.isEmpty()) {
            nativeArea.addView(text("Aucun APK Android WarLike officiel n'est encore publié dans le catalogue.", 14f, muted))
            addGap(nativeArea, 6)
            nativeArea.addView(text("Les applications Web ci-dessous restent accessibles. Les prochaines versions APK apparaîtront ici après validation des signatures et des empreintes.", 12f, muted))
        } else {
            for (pkg in nativePackages) {
                nativeArea.addView(text(pkg.name + " · v" + pkg.versionCode, 16f, white, true))
                val installed = AndroidPackages.installedVersion(this, pkg.packageName)
                nativeArea.addView(text(if (installed == null) "Version Android à installer" else "Version système détectée : " + installed, 12f, muted))
                val download = Button(this).apply {
                    text = "Télécharger APK officiel"
                    isAllCaps = false
                    setOnClickListener {
                        try {
                            val downloadId = AndroidPackages.startDownload(this@MainActivity, pkg)
                            downloads[pkg.id] = downloadId
                            prefs.edit().putLong("download:${pkg.id}:${pkg.versionCode}", downloadId).apply()
                            Toast.makeText(this@MainActivity, "Téléchargement lancé : " + pkg.name, Toast.LENGTH_LONG).show()
                        } catch (error: Exception) {
                            Toast.makeText(this@MainActivity, "Téléchargement impossible : " + error.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                nativeArea.addView(download)
                val verify = Button(this).apply {
                    text = "Vérifier et installer"
                    isAllCaps = false
                    setOnClickListener {
                        val id = downloads[pkg.id] ?: prefs.getLong("download:${pkg.id}:${pkg.versionCode}", -1L).takeIf { it >= 0 }
                        if (id == null) {
                            Toast.makeText(this@MainActivity, "Télécharge d'abord l'APK.", Toast.LENGTH_SHORT).show()
                            return@setOnClickListener
                        }
                        try {
                            val uri = AndroidPackages.verifyCompleted(this@MainActivity, id, pkg)
                            if (uri == null) {
                                Toast.makeText(this@MainActivity, "Téléchargement en cours ou APK invalide.", Toast.LENGTH_LONG).show()
                            } else {
                                AndroidPackages.openAndroidInstaller(this@MainActivity, uri)
                            }
                        } catch (error: Exception) {
                            Toast.makeText(this@MainActivity, "Installation impossible : " + error.message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                nativeArea.addView(verify)
                addGap(nativeArea, 14)
            }
        }
        root.addView(nativeArea)
        addGap(root, 20)

        val search = EditText(this).apply {
            hint = "Rechercher une application…"
            setSingleLine(true)
            textSize = 15f
            setTextColor(white)
            setHintTextColor(muted)
            background = shape(panel, 12, border)
            setPadding(dp(15), dp(9), dp(15), dp(9))
            setText(query)
            contentDescription = "Rechercher dans les services WarLike"
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    query = s?.toString().orEmpty()
                    displayApps()
                }
                override fun afterTextChanged(s: Editable?) {}
            })
        }
        root.addView(search, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))
        addGap(root, 13)

        val tabScroll = HorizontalScrollView(this).apply {
            isHorizontalScrollBarEnabled = false
        }
        tabs = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabScroll.addView(tabs)
        root.addView(tabScroll)
        addGap(root, 13)

        counter = text("", 13f, muted)
        root.addView(counter)
        addGap(root, 10)
        results = column()
        root.addView(results)
        renderTabs()
        displayApps()
    }

    private fun renderTabs() {
        tabs.removeAllViews()
        for (name in listOf("Tout", "Favoris", "Récents", "Jeux", "Création", "Musique", "Communication", "Outils")) {
            val active = when (name) {
                "Favoris" -> favoritesOnly
                "Récents" -> recentOnly
                else -> !favoritesOnly && !recentOnly && filter == name
            }
            val button = Button(this).apply {
                text = name
                isAllCaps = false
                textSize = 12f
                setTextColor(if (active) backdrop else white)
                background = shape(if (active) violet else panel, 11, border)
                setPadding(dp(12), 0, dp(12), 0)
                setOnClickListener {
                    favoritesOnly = name == "Favoris"
                    recentOnly = name == "Récents"
                    if (!favoritesOnly && !recentOnly) filter = name
                    renderTabs()
                    displayApps()
                }
            }
            val params = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42))
            params.marginEnd = dp(8)
            tabs.addView(button, params)
        }
    }

    private fun displayApps() {
        if (!::results.isInitialized) return
        val found = apps.filter { a ->
            val matchesFilter = when {
                favoritesOnly -> favorite(a.id)
                recentOnly -> prefs.getLong("recent:${a.id}", 0L) > 0L
                else -> filter == "Tout" || a.category == filter
            }
            matchesFilter && (query.isBlank() ||
                (a.name + " " + a.category + " " + a.description).contains(query.trim(), ignoreCase = true))
        }.let { items ->
            if (recentOnly) items.sortedByDescending { prefs.getLong("recent:${it.id}", 0L) }
            else items.sortedBy { it.name.lowercase() }
        }
        counter.text = "${found.size} résultat(s)"
        results.removeAllViews()
        if (found.isEmpty()) {
            val message = when {
                recentOnly && query.isBlank() -> "Les services que tu ouvres apparaîtront ici."
                favoritesOnly && query.isBlank() -> "Ajoute des services à tes favoris avec l’étoile."
                else -> "Aucun résultat. Essaie un autre mot ou une autre catégorie."
            }
            results.addView(text(message, 14f, muted))
            return
        }
        for (app in found) results.addView(appCard(app))
    }

    private fun appCard(app: HubApp): View {
        val card = column(16)
        card.background = shape(panel, 17, border)
        val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        lp.bottomMargin = dp(12)
        card.layoutParams = lp

        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val titleGroup = column()
        titleGroup.addView(text(app.name, 19f, white, true))
        titleGroup.addView(text(app.category.uppercase() + if (Uri.parse(app.url).host == "github.com") " · PROJET GITHUB" else " · SERVICE WEB", 11f, teal, true))
        header.addView(titleGroup, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val star = Button(this).apply {
            text = if (favorite(app.id)) "★" else "☆"
            contentDescription = if (favorite(app.id)) "Retirer ${app.name} des favoris" else "Ajouter ${app.name} aux favoris"
            setTextColor(violet)
            setBackgroundColor(Color.TRANSPARENT)
            textSize = 25f
            setOnClickListener { toggleFavorite(app.id) }
        }
        header.addView(star, LinearLayout.LayoutParams(dp(54), dp(48)))
        card.addView(header)
        addGap(card, 6)
        card.addView(text(app.description, 14f, muted))
        addGap(card, 12)

        val launch = Button(this).apply {
            text = if (Uri.parse(app.url).host == "github.com") "Voir le projet GitHub" else "Ouvrir le service"
            isAllCaps = false
            setTextColor(backdrop)
            background = shape(violet, 12)
            setOnClickListener { openService(app) }
        }
        card.addView(launch, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)))
        return card
    }

    @Suppress("DEPRECATION")
    private fun showDeviceApps() {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val launchers = packageManager.queryIntentActivities(query, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }
        if (launchers.isEmpty()) {
            Toast.makeText(this, "Aucune application disponible.", Toast.LENGTH_SHORT).show()
            return
        }
        val labels = launchers.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        android.app.AlertDialog.Builder(this)
            .setTitle("Applications installées")
            .setItems(labels) { _, which ->
                val item = launchers[which].activityInfo
                try {
                    startActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                        .setClassName(item.packageName, item.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (_: Exception) {
                    Toast.makeText(this, "Impossible d’ouvrir cette application.", Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton("Fermer", null).show()
    }

    private fun openService(app: HubApp) {
        val uri = Uri.parse(app.url)
        if (!isAllowedService(app.url)) {
            Toast.makeText(this, "Destination non autorisée", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE))
            prefs.edit().putLong("recent:${app.id}", System.currentTimeMillis()).apply()
            if (recentOnly) displayApps()
        } catch (e: Exception) {
            Toast.makeText(this, "Aucun navigateur disponible", Toast.LENGTH_SHORT).show()
        }
    }
}
