package com.example.tvlauncher

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
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
import android.media.MediaPlayer
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
    private var animT = 0f
    private var lastNs = 0L
    private var boostStart = 0L
    private var boostDur = 0L

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
        lastNs = 0L
        invalidate()
    }

    fun stopAnim() {
        running = false
    }

    fun boost(ms: Long) {
        boostStart = System.nanoTime()
        boostDur = ms * 1_000_000L
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nowNs = System.nanoTime()
        if (lastNs == 0L) lastNs = nowNs
        var dt = (nowNs - lastNs) / 1_000_000_000f
        if (dt > 0.1f) dt = 0.1f
        lastNs = nowNs

        var mul = 1f
        if (boostDur > 0L) {
            val f = ((nowNs - boostStart).toFloat() / boostDur.toFloat()).coerceIn(0f, 1f)
            mul = 1f + 9f * (1f - f) * (1f - f)
            if (f >= 1f) boostDur = 0L
        }
        animT += dt * mul

        val w = width.toFloat()
        val h = height.toFloat()
        for (p in items) {
            val cx = p.x * w
            val cy = p.y * h
            val r = p.size * w * 0.9f
            val angle = p.phase + p.speed * animT
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

class ConfettiView(ctx: Context) : View(ctx) {
    private class C(
        var x: Float, var y: Float,
        var vx: Float, var vy: Float,
        val size: Float, val color: Int,
        var rot: Float, val vr: Float
    )

    private val list = ArrayList<C>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private var running = false
    private var lastNs = 0L
    private val palette = intArrayOf(
        0xFFFF5252.toInt(), 0xFFFFD740.toInt(), 0xFF69F0AE.toInt(),
        0xFF40C4FF.toInt(), 0xFFB388FF.toInt(), 0xFFFFFFFF.toInt()
    )

    fun burst(originX: Float, originY: Float, count: Int) {
        val r = java.util.Random()
        val d = resources.displayMetrics.density
        for (i in 0 until count) {
            val ang = Math.PI + r.nextDouble() * Math.PI
            val sp = (450 + r.nextInt(700)) * d
            list.add(
                C(
                    originX, originY,
                    (Math.cos(ang) * sp).toFloat(), (Math.sin(ang) * sp).toFloat(),
                    (4 + r.nextInt(6)) * d,
                    palette[r.nextInt(palette.size)],
                    r.nextFloat() * 360f,
                    (r.nextFloat() - 0.5f) * 720f
                )
            )
        }
        if (!running) {
            running = true
            lastNs = 0L
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val nowNs = System.nanoTime()
        if (lastNs == 0L) lastNs = nowNs
        var dt = (nowNs - lastNs) / 1_000_000_000f
        if (dt > 0.05f) dt = 0.05f
        lastNs = nowNs
        val d = resources.displayMetrics.density
        val g = 1400f * d
        val h = height.toFloat()

        val it = list.iterator()
        while (it.hasNext()) {
            val c = it.next()
            c.vy += g * dt
            c.vx *= (1f - 0.7f * dt)
            c.x += c.vx * dt
            c.y += c.vy * dt
            c.rot += c.vr * dt
            if (c.y > h + 80f) {
                it.remove()
                continue
            }
            canvas.save()
            canvas.translate(c.x, c.y)
            canvas.rotate(c.rot)
            paint.color = c.color
            canvas.drawRect(-c.size, -c.size * 0.5f, c.size, c.size * 0.5f, paint)
            canvas.restore()
        }
        if (list.isNotEmpty()) {
            postInvalidateDelayed(33)
        } else {
            running = false
        }
    }
}

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private var grid: GridView? = null
    private var ramText: TextView? = null
    private var updateBtn: Button? = null
    private var cleanBtn: Button? = null
    private var statusText: TextView? = null
    private var pinwheels: PinwheelView? = null
    private var confetti: ConfettiView? = null
    private val handler = Handler(Looper.getMainLooper())
    private var ready = false
    private var showingError = false
    private var lastCheck = 0L
    private var cleaning = false
    private var player: MediaPlayer? = null

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
        cleanBtn = cb

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

        val cf = ConfettiView(this)
        confetti = cf
        frame.addView(cf, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

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
            if (!cleaning) updateRam(null)
            checkUpdate()
        } catch (e: Throwable) {
            showError(Log.getStackTraceString(e))
        }
    }

    override fun onPause() {
        super.onPause()
        pinwheels?.stopAnim()
        stopTellocan()
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

    private fun memInfo(): ActivityManager.MemoryInfo {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return mi
    }

    private fun usedPercent(): Int {
        val mi = memInfo()
        return (100 - (mi.availMem * 100 / mi.totalMem)).toInt()
    }

    private fun availMb(): Long = memInfo().availMem / (1024 * 1024)

    private fun updateRam(before: Int?) {
        val now = usedPercent()
        ramText?.text = if (before == null) "RAM: %$now dolu"
        else "RAM: %$before → %$now dolu"
    }

    private fun cleanMemory() {
        if (cleaning) return
        try {
            cleaning = true
            val before = usedPercent()
            val availBefore = availMb()
            ramText?.text = "Temizleniyor..."
            pinwheels?.boost(3500)
            playTellocan()

            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            for (ai in packageManager.getInstalledApplications(0)) {
                if (ai.packageName != packageName) {
                    try { am.killBackgroundProcesses(ai.packageName) } catch (e: Exception) {}
                }
            }

            handler.postDelayed({
                try {
                    val after = usedPercent()
                    val freed = availMb() - availBefore
                    fireConfetti(if (before - after >= 5) 90 else 30)
                    animateRam(before, after, freed)
                } catch (e: Throwable) {
                    cleaning = false
                    showError(Log.getStackTraceString(e))
                }
            }, 1500)
        } catch (e: Throwable) {
            cleaning = false
            showError(Log.getStackTraceString(e))
        }
    }

    private fun playTellocan() {
        stopTellocan()
        try {
            val mp = MediaPlayer.create(this, R.raw.tellocan)
            if (mp == null) {
                Toast.makeText(this, "Şarkı açılamadı (create null)", Toast.LENGTH_LONG).show()
                return
            }
            mp.setVolume(1f, 1f)
            mp.setOnErrorListener { _, what, extra ->
                Toast.makeText(this, "Ses hatası: $what / $extra", Toast.LENGTH_LONG).show()
                stopTellocan()
                true
            }
            mp.setOnCompletionListener { stopTellocan() }
            player = mp
            mp.start()
            Toast.makeText(this, "Şarkı başladı", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Hata: " + e.toString().take(100), Toast.LENGTH_LONG).show()
        }
    }

    private fun stopTellocan() {
        try { player?.release() } catch (e: Exception) {}
        player = null
    }

    private fun fireConfetti(count: Int) {
        val cf = confetti ?: return
        val btn = cleanBtn ?: return
        val a = IntArray(2)
        val b = IntArray(2)
        btn.getLocationOnScreen(a)
        cf.getLocationOnScreen(b)
        val x = (a[0] - b[0]) + btn.width / 2f
        val y = (a[1] - b[1]) + btn.height / 2f
        cf.burst(x, y, count)
    }

    private fun animateRam(before: Int, after: Int, freedMb: Long) {
        val va = ValueAnimator.ofInt(before, after)
        va.duration = if (before == after) 300L else 1400L
        va.addUpdateListener { ramText?.text = "RAM: %${it.animatedValue} dolu" }
        va.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                val extra = if (freedMb > 0) " · $freedMb MB boşaldı" else ""
                ramText?.text = "RAM: %$before → %$after dolu$extra"
                cleaning = false
            }
        })
        va.start()
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
