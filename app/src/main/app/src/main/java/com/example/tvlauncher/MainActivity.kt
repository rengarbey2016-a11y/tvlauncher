package com.example.tvlauncher

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

class AppItem(val label: String, val pkg: String, val icon: Drawable)

class MainActivity : Activity() {
    private val apps = ArrayList<AppItem>()
    private lateinit var grid: GridView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        grid = GridView(this).apply {
            numColumns = 5
            setBackgroundColor(Color.parseColor("#111111"))
            val p = (32 * d).toInt()
            setPadding(p, p, p, p)
            verticalSpacing = (16 * d).toInt()
            horizontalSpacing = (16 * d).toInt()
            setOnItemClickListener { _, _, pos, _ -> open(apps[pos].pkg) }
        }
        setContentView(grid)
    }

    override fun onResume() {
        super.onResume()
        loadApps()
        grid.adapter = Adapter()
    }

    override fun onBackPressed() {
        // Ana ekranda geri tuşu hiçbir şey yapmasın
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
