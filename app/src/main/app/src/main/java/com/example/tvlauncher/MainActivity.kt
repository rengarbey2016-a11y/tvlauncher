package com.example.tvlauncher

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private lateinit var grid: GridView
    private lateinit var ramText: TextView
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val p = (32 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#111111"))
            setPadding(p, p, p, p)
        }

        ramText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER_VERTICAL
        }
        val btn = Button(this).apply {
            text = "Belleği Boşalt"
            setOnClickListener { cleanMemory() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(ramText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(btn)
        }
        root.addView(header)

        grid = GridView(this).apply {
            numColumns = 5
            verticalSpacing = (16 * d).toInt()
            horizontalSpacing = (16 * d).toInt()
            setPadding(0, (16 * d).toInt(), 0, 0)
            setOnItemClickListener { _, _, pos, _ -> open(apps[pos].pkg) }
        }
        root.addView(grid, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        grid.adapter = Adapter()
        updateRam(null)
    }

    override fun onBackPressed() {
        // Ana ekranda geri tuşu hiçbir şey yapmasın
    }

    private fun usedPercent(): Int {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        return (100 - (mi.availMem * 100 / mi.totalMem)).toInt()
    }

    private fun updateRam(before: Int?) {
        val now = usedPercent()
        ramText.text = if (before == null) "RAM: %$now dolu"
        else "RAM: %$before → %$now dolu"
    }

    private fun cleanMemory() {
        val before = usedPercent()
        ramText.text = "Temizleniyor..."
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (ai in packageManager.getInstalledApplications(0)) {
            if (ai.packageName != packageName) {
                try { am.killBackgroundProcesses(ai.packageName) } catch (e: Exception) {}
            }
        }
        handler.postDelayed({ updateRam(before) }, 1500)
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
