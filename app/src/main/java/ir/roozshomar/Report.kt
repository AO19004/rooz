package ir.roozshomar

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import java.time.LocalDate

object Report {
    fun build(m: Model): ByteArray {
        val c = m.calc(); val W = 595; val H = 842
        val doc = PdfDocument(); var n = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); var cv = page.canvas
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
        var y = 50f
        fun line(t: String, size: Float = 13f, bold: Boolean = false) {
            if (y > H - 50) { doc.finishPage(page); n++; page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, n).create()); cv = page.canvas; y = 50f }
            p.textSize = size; p.isFakeBoldText = bold; cv.drawText(t, W - 40f, y, p); y += size + 9
        }
        line("گزارش چرخه‌ی قاعدگی — روزشمار", 20f, true)
        line("تاریخ تهیه: ${jdate(LocalDate.now().toEpochDay())}", 11f); y += 8
        line("میانگین طول چرخه: ${fa(c.avg)} روز    طول پریود: ${fa(m.pl)} روز    تعداد دوره‌ی ثبت‌شده: ${fa(m.periods.size)}")
        if (c.cycles.isNotEmpty()) line("طول چرخه‌های اخیر: " + c.cycles.takeLast(8).joinToString("، ") { fa(it) })
        c.next?.let { line("پریود بعدی (پیش‌بینی): ${jdate(it)}") }
        y += 8; line("آخرین پریودها", 15f, true)
        m.periods.takeLast(8).reversed().forEach { line("• ${jdate(it.s)}" + (it.e?.let { e -> " تا ${jdate(e)}" } ?: "")) }
        val freq = m.notes.values.flatten().groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(6)
        if (freq.isNotEmpty()) { y += 8; line("علائم تکرارشونده", 15f, true); freq.forEach { line("• ${it.key}: ${fa(it.value)} بار") } }
        val vs = m.vit.entries.sortedByDescending { it.key }.take(12)
        if (vs.isNotEmpty()) { y += 8; line("وزن، دما و دارو (اخیر)", 15f, true)
            vs.forEach { line("• ${jdate(it.key)}: وزن ${it.value.w.ifBlank { "-" }}  دما ${it.value.t.ifBlank { "-" }}  دارو ${it.value.med.ifBlank { "-" }}") } }
        y += 12; line("این گزارش از اطلاعات ثبت‌شده توسط کاربر ساخته شده و جایگزین تشخیص پزشکی نیست.", 10f)
        doc.finishPage(page)
        val o = ByteArrayOutputStream(); doc.writeTo(o); doc.close(); return o.toByteArray()
    }
}
