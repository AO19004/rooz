package ir.roozshomar

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import java.time.LocalDate

class RzWidget : AppWidgetProvider() {
    override fun onUpdate(c: Context, mgr: AppWidgetManager, ids: IntArray) { ids.forEach { mgr.updateAppWidget(it, Widget.views(c)) } }
}

object Widget {
    fun views(c: Context): RemoteViews {
        val m = Model(c); val rv = RemoteViews(c.packageName, R.layout.widget)
        val today = LocalDate.now().toEpochDay(); val cc = m.calc(); val nx = cc.next; val last = cc.last
        val (a, b) = when {
            !m.f("wdet") -> "روزشمار" to "🌸"
            m.mode == "preg" && last != null -> "بارداری" to "هفته ${fa(((today - last) / 7).toInt())}"
            nx == null -> "روزشمار" to "ثبت نشده"
            nx - today > 0 -> "تا پریود بعدی" to "${fa((nx - today).toInt())} روز"
            nx == today -> "امروز" to "پریود پیش‌بینی می‌شود"
            else -> "تاخیر" to "${fa((today - nx).toInt())} روز"
        }
        rv.setTextViewText(R.id.t1, a); rv.setTextViewText(R.id.t2, b)
        val tr = m.rems.filter { it.dn == today }.sortedBy { it.hm }
        rv.setTextViewText(R.id.t3, if (tr.isEmpty()) "" else if (m.f("wdet")) "🔔 " + tr.joinToString("، ") { it.text } else "🔔 یادآور دارید")
        rv.setViewVisibility(R.id.t3, if (tr.isEmpty()) View.GONE else View.VISIBLE)
        rv.setOnClickPendingIntent(R.id.root, PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE))
        return rv
    }
    fun refresh(c: Context) {
        val mgr = AppWidgetManager.getInstance(c)
        val ids = mgr.getAppWidgetIds(ComponentName(c, RzWidget::class.java))
        if (ids.isNotEmpty()) { val v = views(c); ids.forEach { mgr.updateAppWidget(it, v) } }
    }
}
