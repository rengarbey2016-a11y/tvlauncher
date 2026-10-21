package com.example.tvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
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

class Card(ctx: Context) : LinearLayout(ctx) {
    override fun setSelected(selected: Boolean) {
        super.setSelected(selected)
        val s = if (selected) 1.1f else 1f
        animate().scaleX(s).scaleY(s).translationZ(if (selected) 20f else 0f).setDuration(120).start()
    }
}

class PinwheelView(ctx: Context) : View(ctx) {
    private class P(val x: Float, val y: Float, val size: Float, val speed: Float, val phase: Float)

    private val items = ArrayList<P>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val colors = intArrayOf(0x38FF5252, 0x38FFD740, 0x3869F0AE, 0x3840C4FF)
    private var running = false
    private val startTime = System.nanoTime()

    init {
        val r = java.util.Random(11)
        for (i in 0 until 7) {
            val dir = if (i % 2 == 0) 1f else -1f
            items.add(
                P(
                    r.nextFloat(),
                    r.nextFloat(),
                    0.10f + r.nextFloat() * 0.14f,
                    (18f + r.nextFloat() * 32f) * dir,
                    r.nextFloat() * 360f
                )
            )
        }
    }

    fun startAnim() {
        running = true
        invalidate()
    }

    fun stopAnim() {
        running = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val t = (System.nanoTime() - startTime) / 1_000_000_000f
        for (p in items) {
            val cx = p.x * w
            val cy = p.y * h
            val r = p.size * w * 0.9f
            val angle = p.phase + p.speed * t
            for (b in 0 until 4) {
                canvas.save()
                canvas.translate(cx, cy)
                canvas.rotate(angle + b * 90f)
                paint.color = colors[b]
                path.reset()
                path.moveTo(0f, 0f)
                path.lineTo(0f, -r)
                path.lineTo(r * 0.52f, -r * 0.72f)
                path.close()
                canvas.drawPath(path, paint)
                canvas.restore()
            }
        }
        if (running) postInvalidateDelayed(40)
    }
}

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private var grid: GridView? = null
    private var ramText: TextView? = null
    private var updateBtn: Button? = null
    private var statusText: TextView? = null
    private var pinwheels: PinwheelView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var showingError = false
    private var lastCheck = 0L

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun pillBg(normal: Int, focused: Int): StateListDrawable {
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_focused), GradientDrawable().apply {
            setColor(focused); cornerRadius = dp(24).toFloat()
        })
        sl.addState(intArrayOf(), GradientDrawable().apply {
            setColor(normal); cornerRadius = dp(24).toFloat()
        })
        return sl
    }

    private fun cardBg(): StateListDrawable {
        val sl = StateListDrawable()
        sl.addState(intArrayOf(android.R.attr.state_selected), GradientDrawable().apply {
            setColor(Color.parseColor("#40FFFFFF"))
            cornerRadius = dp(18).toFloat()
            setStroke(dp(2), Color.parseColor("#B3FFFFFF"))
        })
        sl.addState(intArrayOf(), GradientDrawable().apply {
            setColor(Color.parseColor("#14FFFFFF"))
            cornerRadius = dp(18).toFloat()
        })
        return sl
    }

    private fun styledButton(label: String, normal: Int, focused: Int, onClick: () -> Unit): Button {
        return Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 15f
            setTextColor(Color.WHITE)
            background = pillBg(normal, focused)
            setPadding(dp(22), dp(10), dp(22), dp(10))
            setOnClickListener { onClick() }
        }
    }

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
        val bg = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.parseColor("#0B1020"), Color.parseColor("#1B1F4B"), Color.parseColor("#3A1C71"))
        )
        val frame = FrameLayout(this).apply { background = bg }

        val pw = PinwheelView(this)
        pinwheels = pw
        frame.addView(pw, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(48), dp(28), dp(48), dp(20))
        }

        val clock = TextClock(this).apply {
            format12Hour = "HH:mm"
            format24Hour = "HH:mm"
            textSize = 44f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        }
        val date = TextClock(this).apply {
            format12Hour = "d MMMM EEEE"
            format24Hour = "d MMMM EEEE"
            textSize = 16f
            setTextColor(Color.parseColor("#B0B8D9"))
        }
        val rt = TextView(this).apply {
            setTextColor(Color.parseColor("#D0D6F0"))
            textSize = 15f
            setPadding(0, dp(8), 0, 0)
        }
        ramText = rt
        val st = TextView(this).apply {
            setTextColor(Color.parseColor("#7C84A8"))
            textSize = 11f
        }
        statusText = st

        val left = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(clock)
            addView(date)
            addView(rt)
            addView(st)
        }

        val ub = styledButton("Güncelle", Color.parseColor("#2E7D32"), Color.parseColor("#00C853")) { startUpdate() }
        ub.visibility = View.GONE
        updateBtn = ub
        val cb = styledButton("Belleği Boşalt", Color.parseColor("#26FFFFFF"), Color.parseColor("#7C4DFF")) { cleanMemory() }

        val right = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.leftMargin = dp(12)
            addView(ub, lp)
            addView(cb, lp)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP
            addView(left, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(right)
        }
        root.addView(header)

        val g = GridView(this).apply {
            numColumns = 5
            verticalSpacing = dp(20)
            horizontalSpacing = dp(20)
            clipChildren = false
            clipToPadding = false
            setPadding(dp(8), dp(24), dp(8), dp(16))
            setSelector(ColorDrawable(Color.TRANSPARENT))
            setOnItemClickListener { _, _, pos, _ -> open(apps[pos].pkg) }
        }
        grid = g
        root.addView(g, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        frame.addView(root, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        setContentView(frame)
    }

    override fun onResume() {
        super.onResume()
        if (!ready) return
        try {
            pinwheels?.startAnim()
            loadApps()
            grid?.adapter = Adapter()
            grid?.requestFocus()
            updateRam(null)
            checkUpdate()
        } catch (e: Throwable) {
            showError(Log.getStackTraceString(e))
        }
    }

    override fun onPause() {
        super.onPause()
        pinwheels?.stopAnim()
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
            val item = apps[p]
            val box = Card(this@MainActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                background = cardBg()
                setPadding(dp(12), dp(16), dp(12), dp(14))
            }
            val img = ImageView(this@MainActivity).apply {
                setImageDrawable(item.icon)
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(72))
            }
            val txt = TextView(this@MainActivity).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 14f
                gravity = Gravity.CENTER
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                setPadding(0, dp(8), 0, 0)
            }
            box.addView(img)
            box.addView(txt)
            return box
        }
    }
}
