package com.example.tvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

const val API_URL = "https://api.github.com/repos/rengarbey2016-a11y/tvlauncher/releases/tags/latest"
const val APK_URL = "https://github.com/rengarbey2016-a11y/tvlauncher/releases/download/latest/app-debug.apk"

class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private var grid: GridView? = null
    private var ramText: TextView? = null
    private var updateBtn: Button? = null
    private var statusText: TextView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var showingError = false
    private var lastCheck = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("crash", Context.MODE_PRIVATE)
        val old = prefs.getString("trace", null)

        Thread.setDefaultUncaughtExceptionHandler { _, e ->
            prefs.edit().putString("trace", Log.getStackTraceString(e)).commit()
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(1)
        }

        if (old != null) {
            prefs.edit().remove("trace").commit()
            showError(old)
            return
        }
        try {
            build()
            ready = true
        } catch (e: Throwable) {
            showError(Log.getStackTraceString(e))
        }
    }

    private fun showError(msg: String) {
        showingError = true
        val tv = TextView(this).apply {
            text = "HATA (fotoğrafını çek):\n\n" + msg.take(1800)
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(40, 40, 40, 40)
        }
        val sv = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#400000"))
            addView(tv)
        }
        setContentView(sv)
    }

    private fun build() {
        val d = resources.displayMetrics.density
        val p = (32 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(p, p, p, p)
        }

        val rt = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
        }
        ramText = rt
        val ub = Button(this).apply {
            text = "Güncelle"
            visibility = View.GONE
            setOnClickListener { startUpdate() }
        }
        updateBtn = ub
        val btn = Button(this).apply {
            text = "Belleği Boşalt"
            setOnClickListener { cleanMemory() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(rt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(ub)
            addView(btn)
        }
        root.addView(header)

        val st = TextView(this).apply {
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
        }
        statusText = st
        root.addView(st)

        val g = GridView(this).apply {
            numColumns = 5
            verticalSpacing = (16 * d).toInt()
            horizontalSpacing = (16 * d).toInt()
            setPadding(0, (16 * d).toInt(), 0, 0)
            setOnItemClickListener { _, _, pos, _ -> open(apps[pos].pkg) }
        }
        grid = g
        root.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        try {
            loadApps()
            grid?.adapter = Adapter()
            updateRam(null)
            checkUpdate()
        } catch (e: Throwable) {
            showError(Log.getStackTraceString(e))
        }
    }

    override fun onBackPressed() {
        if (showingError) finish()
    }

    @Suppress("DEPRECATION")
    private fun currentVersion(): Int =
        packageManager.getPackageInfo(packageName, 0).versionCode

    private fun checkUpdate() {
        val now = System.currentTimeMillis()
        if (now - lastCheck < 10 * 60 * 1000) return
        lastCheck = now
        statusText?.text = "Sürüm kontrol ediliyor..."
        thread {
            try {
                val c = URL(API_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 8000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", "Firfir")
                val json = c.inputStream.bufferedReader().use { it.readText() }
                val latest = JSONObject(json).optString("body").trim().toIntOrNull()
                val cur = currentVersion()
                handler.post {
                    statusText?.text = "Kutudaki: v$cur · GitHub'daki: v${latest ?: "?"}"
                    if (latest != null && latest > cur) {
                        updateBtn?.apply {
                            text = "Güncelle (v$latest)"
                            visibility = View.VISIBLE
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w("Firfir", "update check failed", e)
                handler.post { statusText?.text = "Kontrol hatası: " + e.toString().take(120) }
            }
        }
    }

    private fun startUpdate() {
        if (Build.VERSION.SDK_INT >= 26 && !packageManager.canRequestPackageInstalls()) {
            Toast.makeText(this, "Fırfır'a kurulum izni ver, sonra Güncelle'ye tekrar bas", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName")))
            return
        }
        updateBtn?.apply {
            isEnabled = false
            text = "İndiriliyor..."
        }
        thread {
            try {
                val dir = File(cacheDir, "updates").apply { mkdirs() }
                val f = File(dir, "update.apk")
                val c = URL(APK_URL).openConnection() as HttpURLConnection
                c.connectTimeout = 15000
                c.readTimeout = 30000
                c.setRequestProperty("User-Agent", "Firfir")
                c.inputStream.use { i -> f.outputStream().use { o -> i.copyTo(o) } }
                handler.post { install(f) }
            } catch (e: Exception) {
                Log.w("Firfir", "download failed", e)
                handler.post {
                    updateBtn?.apply {
                        isEnabled = true
                        text = "Tekrar dene"
                    }
                    Toast.makeText(this, "İndirme başarısız", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun install(f: File) {
        try {
            val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", f)
            val i = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        } catch (e: Exception) {
            showError(Log.getStackTraceString(e))
        }
        updateBtn?.apply {
            isEnabled = true
            text = "Güncelle"
        }
    }

    private fun usedPercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (100 - (mi.availMem * 100 / mi.totalMem)).toInt()
    }

    private fun updateRam(before: Int?) {
        val now = usedPercent()
        ramText?.text = if (before == null) "RAM: %$now dolu"
        else "RAM: %$before → %$now dolu"
    }

    private fun cleanMemory() {
        try {
            val before = usedPercent()
            ramText?.text = "Temizleniyor..."
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (ai in packageManager.getInstalledApplications(0)) {
                if (ai.packageName != packageName) {
                    try { am.killBackgroundProcesses(ai.packageName) } catch (e: Exception) {}
                }
            }
            handler.postDelayed({ updateRam(before) }, 1500)
        } catch (e: Throwable) {
            showError(Log.getStackTraceString(e))
        }
    }

    private fun open(pkg: String) {
        val i = packageManager.getLeanbackLaunchIntentForPackage(pkg)
            ?: packageManager.getLaunchIntentForPackage(pkg)
        if (i != null) startActivity(i)
    }

    private fun loadApps() {
        apps.clear()
        val seen = HashSet<String>()
        for (cat in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val q = Intent(Intent.ACTION_MAIN).addCategory(cat)
            for (r in packageManager.queryIntentActivities(q, 0)) {
                val pkg = r.activityInfo.packageName
                if (pkg == packageName || !seen.add(pkg)) continue
                apps.add(AppItem(r.loadLabel(packageManager).toString(), pkg, r.loadIcon(packageManager)))
            }
        }
        apps.sortBy { it.label.lowercase() }
    }

    inner class Adapter : BaseAdapter() {
        override fun getCount() = apps.size
        override fun getItem(p: Int) = apps[p]
        override fun getItemId(p: Int) = p.toLong()
        override fun getView(p: Int, v: View?, parent: ViewGroup?): View {
            val d = resources.displayMetrics.density
            val item = apps[p]
            val box = LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                val pad = (12 * d).toInt()
                setPadding(pad, pad, pad, pad)
            }
            val img = ImageView(this@MainActivity).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams((72 * d).toInt(), (72 * d).toInt())
            }
            val txt = TextView(this@MainActivity).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
            }
            box.addView(img)
            box.addView(txt)
            return box
        }
    }
}
