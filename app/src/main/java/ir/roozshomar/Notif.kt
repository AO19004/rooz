package ir.roozshomar

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.*
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

class DailyWorker(c: Context, p: WorkerParameters) : Worker(c, p) {
    override fun doWork(): Result {
        try { if (inputData.getString("k") == "r") Notif.runRem(applicationContext, inputData.getLong("id", -1L)) else Notif.run(applicationContext, inputData.getString("k") ?: "m") } catch (e: Exception) {}
        try { Widget.refresh(applicationContext) } catch (e: Exception) {}
        return Result.success()
    }
}

object Notif {
    private const val CH = "rz"
    private val TIPS = listOf("آب کافی بنوشید؛ به کاهش نفخ کمک می‌کند.", "پیاده‌روی سبک می‌تواند درد شکم را کمتر کند.",
        "در روزهای پریود از مصرف زیاد کافئین پرهیز کنید.", "خواب منظم روی تعادل خلق‌وخو اثر دارد.",
        "مصرف غذاهای غنی از آهن در روزهای پریود مفید است.", "گرما روی ناحیه‌ی شکم یا کمر دردها را آرام می‌کند.")

    fun schedule(ctx: Context) {
        fun go(name: String, hour: Int, k: String) {
            val n = LocalDateTime.now(); var t = n.toLocalDate().atTime(hour, 0); if (!t.isAfter(n)) t = t.plusDays(1)
            val req = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(n, t).toMillis(), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf("k" to k)).build()
            WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(name, ExistingPeriodicWorkPolicy.KEEP, req)
        }
        go("rz_m", 9, "m"); go("rz_e", 21, "e")
    }

    fun scheduleRem(ctx: Context, r: Rem) {
        val at = LocalDate.ofEpochDay(r.dn).atTime(r.hm / 60, r.hm % 60)
        val delay = Duration.between(LocalDateTime.now(), at).toMillis()
        if (delay <= 0) return
        val req = OneTimeWorkRequestBuilder<DailyWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .setInputData(workDataOf("k" to "r", "id" to r.id)).build()
        WorkManager.getInstance(ctx).enqueueUniqueWork("rem_${r.id}", ExistingWorkPolicy.KEEP, req)
    }
    fun cancelRem(ctx: Context, id: Long) { WorkManager.getInstance(ctx).cancelUniqueWork("rem_$id") }
    fun runRem(ctx: Context, id: Long) {
        val m = Model(ctx); if (!m.f("master") || !m.f("rem")) return
        val r = m.rems.firstOrNull { it.id == id } ?: return
        post(ctx, 1000 + (id % 100000).toInt(), "یادآور", r.text, m)
    }

    private fun post(ctx: Context, id: Int, title: String, text: String, m: Model) {
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH, "یادآورها", NotificationManager.IMPORTANCE_DEFAULT))
        val d = m.f("discreet")
        val pi = PendingIntent.getActivity(ctx, 0, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(id, Notification.Builder(ctx, CH).setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(if (d) "یادآور" else title).setContentText(if (d) "یک یادآوری دارید" else text)
            .setVisibility(Notification.VISIBILITY_SECRET).setContentIntent(pi).setAutoCancel(true).build())
    }

    fun run(ctx: Context, k: String) {
        val m = Model(ctx); if (!m.f("master")) return
        val today = LocalDate.now().toEpochDay(); val cc = m.calc(); val nx = cc.next
        if (m.mode == "preg" && k == "m") {
            val l = cc.last
            if (l != null && m.f("tips") && today > l && (today - l) % 7 == 0L) post(ctx, 5, "بارداری", "به هفته‌ی ${(today - l) / 7} بارداری رسیدید.", m)
            return
        }
        if (k == "m") {
            if (nx != null) {
                val diff = nx - today
                if (m.f("period") && (diff == 2L || diff == 0L))
                    post(ctx, 1, "پریود نزدیک است", if (diff == 0L) "امروز پریود پیش‌بینی می‌شود." else "پریود تا ۲ روز دیگر پیش‌بینی می‌شود.", m)
                if (m.f("ovu")) {
                    if (today == nx - 14) post(ctx, 2, "تخمک‌گذاری", "امروز روز تخمک‌گذاری تخمینی است.", m)
                    else if (today == nx - 19) post(ctx, 2, "بازه‌ی باروری", "بازه‌ی باروری از امروز شروع شد.", m)
                }
            }
            val nn = nx
            if (m.f("pms") && nn != null) { val ps = cc.pms.filter { it < nn }.minOrNull(); if (ps != null && today == ps) post(ctx, 6, "روزهای PMS", "روزهای PMS نزدیک است؛ به خودتان مهربان باشید.", m) }
            if (m.f("tips") && today % 3L == 0L) post(ctx, 3, "نکته‌ی سلامت", TIPS[((today / 3) % TIPS.size).toInt()], m)
        } else if (m.f("log") && m.notes[today].isNullOrEmpty()) post(ctx, 4, "ثبت روزانه", "علائم امروز را ثبت کنید.", m)
    }
}
