package ir.roozshomar

import android.content.Context
import android.icu.util.Calendar
import android.icu.util.PersianCalendar
import android.icu.util.TimeZone
import androidx.compose.runtime.*
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import kotlin.math.roundToInt

val MN = listOf("فروردین","اردیبهشت","خرداد","تیر","مرداد","شهریور","مهر","آبان","آذر","دی","بهمن","اسفند")
val TAGS = listOf("درد شکم","کمردرد","سردرد","نفخ","بی‌حالی","تغییر خلق","جوش","هوس خوراکی","حال خوب","خونریزی کم","خونریزی متوسط","خونریزی زیاد")
val FL = listOf("master","period","ovu","log","tips","discreet","bio","pms","wdet")
fun fa(n: Int) = n.toString().map { if (it in '0'..'9') '۰' + (it - '0') else it }.joinToString("")

private val UTC = TimeZone.getTimeZone("UTC")
fun jal(dn: Long): Triple<Int, Int, Int> {
    val c = PersianCalendar(UTC); c.timeInMillis = dn * 86400000L + 43200000L
    return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
}
fun jdate(dn: Long): String { val (y, m, d) = jal(dn); return "${fa(d)} ${MN[m - 1]} ${fa(y)}" }
fun monthStart(y: Int, m: Int): Pair<Long, Int> {
    val c = PersianCalendar(UTC); c.clear(); c.set(y, m - 1, 1)
    return Pair(c.timeInMillis / 86400000L, c.getActualMaximum(Calendar.DAY_OF_MONTH))
}

data class Period(val s: Long, val e: Long?)
data class Vit(val w: String, val t: String, val med: String)
class Calc(val per: Set<Long>, val pred: Set<Long>, val fert: Set<Long>, val ovu: Set<Long>,
           val avg: Int, val last: Long?, val next: Long?, val cycles: List<Int>, val pms: Set<Long>)

class Model(ctx: Context) {
    private val app = ctx.applicationContext
    private val sp = EncryptedSharedPreferences.create(ctx, "rz_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
    var periods by mutableStateOf(listOf<Period>()); private set
    var notes by mutableStateOf(mapOf<Long, Set<String>>()); private set
    var cy by mutableStateOf(28); private set
    var pl by mutableStateOf(5); private set
    var vit by mutableStateOf(mapOf<Long, Vit>()); private set
    var mode by mutableStateOf("normal"); private set
    val flags = mutableStateMapOf<String, Boolean>()
    init { load() }

    fun f(k: String) = flags[k] ?: false
    fun setF(k: String, v: Boolean) { flags[k] = v; sp.edit().putBoolean("f_$k", v).apply(); Widget.refresh(app) }
    private fun load() {
        try {
            val j = JSONObject(sp.getString("data", "{}")!!)
            val pa = j.optJSONArray("p") ?: JSONArray()
            periods = (0 until pa.length()).map { val a = pa.getJSONArray(it); Period(a.getLong(0), a.getLong(1).takeIf { x -> x >= 0 }) }
            val o = j.optJSONObject("n")
            notes = o?.keys()?.asSequence()?.associate { k ->
                val a = o.getJSONArray(k); k.toLong() to (0 until a.length()).map { i -> a.getString(i) }.toSet() } ?: mapOf()
            cy = j.optInt("cy", 28); pl = j.optInt("pl", 5)
            val vo = j.optJSONObject("v")
            vit = vo?.keys()?.asSequence()?.associate { k -> val a = vo.getJSONArray(k); k.toLong() to Vit(a.getString(0), a.getString(1), a.getString(2)) } ?: mapOf()
        } catch (e: Exception) {}
        FL.forEach { flags[it] = sp.getBoolean("f_$it", it !in setOf("discreet", "bio", "wdet")) }
        mode = sp.getString("mode", "normal") ?: "normal"
    }
    private fun save() {
        val j = JSONObject()
        j.put("p", JSONArray(periods.map { JSONArray(listOf(it.s, it.e ?: -1L)) }))
        j.put("n", JSONObject(notes.entries.associate { it.key.toString() to JSONArray(it.value.toList()) }))
        j.put("cy", cy); j.put("pl", pl)
        j.put("v", JSONObject(vit.entries.associate { it.key.toString() to JSONArray(listOf(it.value.w, it.value.t, it.value.med)) }))
        sp.edit().putString("data", j.toString()).apply(); try { Widget.refresh(app) } catch (e: Exception) {}
    }
    fun setVit(dn: Long, v: Vit) { vit = if (v.w.isBlank() && v.t.isBlank() && v.med.isBlank()) vit - dn else vit + (dn to v); save() }
    fun setMode(s: String) { mode = s; sp.edit().putString("mode", s).apply(); Widget.refresh(app) }
    fun export(): String = sp.getString("data", "{}") ?: "{}"
    fun import(s: String) { try { JSONObject(s); sp.edit().putString("data", s).apply(); load() } catch (e: Exception) {} }
    fun setLens(c: Int, p: Int) { cy = c.coerceIn(20, 45); pl = p.coerceIn(2, 10); save() }
    fun startPeriod(dn: Long) { if (periods.none { it.s == dn }) { periods = (periods + Period(dn, null)).sortedBy { it.s }; save() } }
    fun endPeriod(dn: Long): Boolean {
        val p = periods.lastOrNull { it.s <= dn } ?: return false
        if (dn - p.s > 14) return false
        periods = periods.map { if (it == p) Period(p.s, dn) else it }; save(); return true
    }
    fun removeAt(dn: Long) { periods = periods.filterNot { dn >= it.s && dn <= (it.e ?: (it.s + pl - 1)) }; save() }
    fun toggle(dn: Long, t: String) {
        val cur = notes[dn] ?: emptySet(); val n = if (t in cur) cur - t else cur + t
        notes = if (n.isEmpty()) notes - dn else notes + (dn to n); save()
    }
    private fun h(p: String) = MessageDigest.getInstance("SHA-256").digest("rz:$p".toByteArray()).joinToString("") { "%02x".format(it) }
    fun hasPin() = sp.contains("pin")
    fun setPin(p: String) { sp.edit().putString("pin", h(p)).apply() }
    fun check(p: String) = sp.getString("pin", null) == h(p)
    fun wipe() { sp.edit().clear().apply(); periods = listOf(); notes = mapOf(); cy = 28; pl = 5; vit = mapOf(); load() }

    fun calc(): Calc {
        val ps = periods.sortedBy { it.s }
        val d = ps.zipWithNext { a, b -> (b.s - a.s).toInt() }.filter { it in 15..60 }
        val avg = if (d.isNotEmpty()) d.takeLast(6).average().roundToInt() else cy
        val per = HashSet<Long>(); ps.forEach { for (x in it.s..(it.e ?: (it.s + pl - 1))) per.add(x) }
        val pred = HashSet<Long>(); val fert = HashSet<Long>(); val ovu = HashSet<Long>(); val pms = HashSet<Long>()
        val offs = ps.mapNotNull { p -> (1..7).filter { o -> !notes[p.s - o].isNullOrEmpty() }.maxOrNull() }
        val pmsLen = if (offs.isNotEmpty()) offs.average().roundToInt().coerceIn(2, 7) else 5
        val last = ps.lastOrNull()?.s
        if (last != null) for (k in 1..4) {
            val ns = last + avg * k
            for (x in 0 until pl) pred.add(ns + x)
            ovu.add(ns - 14); for (x in -19..-13) fert.add(ns + x); for (x in 1..pmsLen) pms.add(ns - x)
        }
        return Calc(per, pred, fert, ovu, avg, last, last?.plus(avg), d, pms)
    }
}
