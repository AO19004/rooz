package ir.roozshomar

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import java.time.LocalDate

val PINK = Color(0xFFD6336C); val GREEN = Color(0xFF2F9E6E)

class MainActivity : FragmentActivity() {
    lateinit var model: Model
    var locked by mutableStateOf(true)
    var changePin by mutableStateOf(false)
    val askNotif = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    val exp = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { u ->
        u?.let { contentResolver.openOutputStream(it)?.use { o -> o.write(model.export().toByteArray()) } } }
    val imp = registerForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        u?.let { contentResolver.openInputStream(it)?.use { i -> model.import(i.readBytes().decodeToString()) } } }

    val pdf = registerForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { u ->
        u?.let { contentResolver.openOutputStream(it)?.use { o -> o.write(Report.build(model)) } } }
    fun sharePartner() {
        val nx = model.calc().next ?: return; val c = model.calc()
        val ps = c.pms.filter { it < nx }.minOrNull() ?: (nx - 5)
        val t = "خلاصه‌ی روزشمار:\nپریود بعدی حدود ${jdate(nx)} پیش‌بینی می‌شود.\nروزهای حساس (PMS) از ${jdate(ps)} شروع می‌شود."
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, t), "اشتراک با همسر"))
    }
    @Composable fun VitFields(dn: Long) {
        val v = model.vit[dn]
        var w by remember(dn) { mutableStateOf(v?.w ?: "") }; var t by remember(dn) { mutableStateOf(v?.t ?: "") }; var md by remember(dn) { mutableStateOf(v?.med ?: "") }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedTextField(w, { w = it; model.setVit(dn, Vit(w, t, md)) }, Modifier.weight(1f), label = { Text("وزن (kg)", fontSize = 11.sp) }, singleLine = true)
            OutlinedTextField(t, { t = it; model.setVit(dn, Vit(w, t, md)) }, Modifier.weight(1f), label = { Text("دما (°C)", fontSize = 11.sp) }, singleLine = true) }
        OutlinedTextField(md, { md = it; model.setVit(dn, Vit(w, t, md)) }, Modifier.fillMaxWidth(), label = { Text("داروی مصرفی", fontSize = 11.sp) }, singleLine = true)
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE) // hide in recents / no screenshots
        model = Model(this); Notif.schedule(this)
        if (Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS)
        setContent { App() }
    }
    override fun onStop() { super.onStop(); locked = true }

    fun canBio() = BiometricManager.from(this).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG) == BiometricManager.BIOMETRIC_SUCCESS
    fun bio() {
        val p = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(r: BiometricPrompt.AuthenticationResult) { locked = false }
        })
        p.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle("ورود به روزشمار").setNegativeButtonText("استفاده از رمز")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG).build())
    }

    @Composable fun App() {
        val cs = if (isSystemInDarkTheme()) darkColorScheme(primary = Color(0xFFF06595)) else lightColorScheme(primary = PINK, background = Color(0xFFFFF5F8))
        MaterialTheme(colorScheme = cs) {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (locked || changePin) LockScreen(changePin) else Main()
                }
            }
        }
    }

    @Composable fun LockScreen(change: Boolean) {
        var mode by remember { mutableStateOf(if (change || !model.hasPin()) "set1" else "enter") }
        var buf by remember { mutableStateOf("") }; var tmp by remember { mutableStateOf("") }
        var msg by remember { mutableStateOf("") }; var fails by remember { mutableIntStateOf(0) }
        var until by remember { mutableLongStateOf(0L) }; var forgot by remember { mutableStateOf(false) }
        val bioOk = mode == "enter" && model.f("bio") && canBio()
        LaunchedEffect(Unit) { if (bioOk) bio() }
        fun press(k: String) {
            if (k == "b") { if (bioOk) bio(); return }
            if (System.currentTimeMillis() < until) { msg = "کمی صبر کنید و دوباره امتحان کنید"; return }
            if (k == "d") { buf = buf.dropLast(1); return }
            if (buf.length >= 4) return
            buf += k; if (buf.length < 4) return
            val b = buf; buf = ""
            when (mode) {
                "enter" -> if (model.check(b)) { locked = false; fails = 0 } else {
                    fails++; msg = "رمز اشتباه است"
                    if (fails >= 5) { fails = 0; until = System.currentTimeMillis() + 30000; msg = "۳۰ ثانیه صبر کنید" } }
                "set1" -> { tmp = b; mode = "set2"; msg = "" }
                else -> if (b == tmp) { model.setPin(b); locked = false; changePin = false } else { mode = "set1"; msg = "رمزها یکسان نبودند" }
            }
        }
        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text("🔒", fontSize = 44.sp)
            Text(when (mode) { "enter" -> "رمز ورود را وارد کنید"; "set1" -> "یک رمز ۴ رقمی بسازید"; else -> "رمز را دوباره وارد کنید" }, fontSize = 18.sp)
            Text((0 until 4).joinToString(" ") { if (it < buf.length) "●" else "○" }, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(16.dp))
            Text(msg, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, modifier = Modifier.height(22.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("b", "0", "d")).forEach { row ->
                        Row { row.forEach { k ->
                            Box(Modifier.padding(6.dp).size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant).clickable { press(k) }, contentAlignment = Alignment.Center) {
                                Text(when (k) { "d" -> "⌫"; "b" -> if (bioOk) "👆" else ""; else -> fa(k.toInt()) }, fontSize = 24.sp) } } }
                    }
                }
            }
            if (mode == "enter") TextButton({ forgot = true }) { Text("رمز را فراموش کرده‌ام") }
            if (change) TextButton({ changePin = false }) { Text("انصراف") }
        }
        if (forgot) AlertDialog(onDismissRequest = { forgot = false },
            confirmButton = { TextButton({ model.wipe(); forgot = false; mode = "set1"; msg = "" }) { Text("حذف همه و ادامه") } },
            dismissButton = { TextButton({ forgot = false }) { Text("انصراف") } },
            text = { Text("با فراموشی رمز، همه‌ی اطلاعات ثبت‌شده حذف می‌شود. ادامه می‌دهید؟") })
    }

    @Composable fun Main() {
        var tab by remember { mutableIntStateOf(0) }
        Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = {
            NavigationBar { listOf("🏠 خانه", "📊 آمار", "⚙️ تنظیمات").forEachIndexed { i, t ->
                NavigationBarItem(selected = tab == i, onClick = { tab = i }, icon = { Text(t.take(2)) }, label = { Text(t.drop(3), fontSize = 11.sp) }) } }
        }) { pad -> Column(Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(14.dp)) {
            when (tab) { 0 -> Home(); 1 -> Stats(); else -> Settings() } } }
    }

    @Composable fun Box2(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), content = content) }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun Home() {
        val today = remember { LocalDate.now().toEpochDay() }
        val c = model.calc(); val nx = c.next
        val cur = model.periods.lastOrNull { it.s <= today }
        val inP = cur != null && today <= (cur.e ?: (cur.s + model.pl - 1))
        val (h1, h2, h3) = when {
            nx == null || cur == null -> Triple("خوش آمدید", "🌸", "اولین روز آخرین پریودتان را ثبت کنید")
            model.mode == "preg" -> { val dd = (today - cur.s).toInt(); Triple("بارداری · ثلث ${fa(minOf(dd / 91 + 1, 3))}", "هفته ${fa(dd / 7)} و ${fa(dd % 7)} روز", "موعد تقریبی: ${jdate(cur.s + 280)}") }
            model.mode == "try" && !inP && today <= nx - 14 -> Triple("تلاش برای بارداری", "${fa((nx - 14 - today).toInt())} روز تا تخمک‌گذاری", if (today in c.fert) "در بازه‌ی باروری هستید" else "بازه‌ی باروری: ${jdate(nx - 19)}")
            inP -> Triple("در حال پریود", "روز ${fa((today - cur.s + 1).toInt())}", "پریود بعدی: ${jdate(nx)}")
            nx - today > 0 -> Triple("تا پریود بعدی", "${fa((nx - today).toInt())} روز",
                if (today in c.fert) "در بازه‌ی باروری هستید" else if (today in c.ovu) "روز تخمک‌گذاری (تخمینی)" else jdate(nx))
            nx == today -> Triple("امروز", "پریود پیش‌بینی می‌شود", "در صورت شروع، ثبت کنید")
            else -> Triple("تاخیر نسبت به پیش‌بینی", "${fa((today - nx).toInt())} روز", "پیش‌بینی: ${jdate(nx)}")
        }
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = PINK)) {
            Column(Modifier.padding(20.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(h1, color = Color.White); Text(h2, color = Color.White, fontSize = 36.sp, fontWeight = FontWeight.ExtraBold); Text(h3, color = Color.White, fontSize = 14.sp) } }
        val warn = (c.last != null && c.avg !in 21..35) || (c.cycles.size >= 2 && c.cycles.max() - c.cycles.min() > 9) || (nx != null && today - nx > 7)
        if (warn) Box2 { Text("⚠️ الگوی چرخه‌ی شما نامنظم به نظر می‌رسد. در صورت تکرار، با پزشک مشورت کنید.", fontSize = 13.sp) }
        var sel by remember { mutableStateOf<Long?>(null) }
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button({ model.startPeriod(today) }, Modifier.weight(1f)) { Text("شروع پریود امروز", fontSize = 13.sp) }
            FilledTonalButton({ sel = today }, Modifier.weight(1f)) { Text("ثبت علائم امروز", fontSize = 13.sp) }
        }
        var vy by remember { mutableIntStateOf(jal(today).first) }; var vm by remember { mutableIntStateOf(jal(today).second) }
        Box2 {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton({ if (vm == 12) { vm = 1; vy++ } else vm++ }) { Text("›", fontSize = 22.sp) }
                Text("${MN[vm - 1]} ${fa(vy)}", fontWeight = FontWeight.SemiBold)
                TextButton({ if (vm == 1) { vm = 12; vy-- } else vm-- }) { Text("‹", fontSize = 22.sp) } }
            Row { listOf("ش", "ی", "د", "س", "چ", "پ", "ج").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp) } }
            val (st, len) = monthStart(vy, vm); val off = (((st + 4) % 7 + 1) % 7).toInt()
            val cells = List<Long?>(off) { null } + (0 until len).map { st + it }
            cells.chunked(7).forEach { row -> Row {
                row.forEach { dn -> if (dn == null) Spacer(Modifier.weight(1f)) else {
                    val bg = when { dn in c.per -> PINK; dn in c.ovu -> GREEN; dn in c.fert -> GREEN.copy(alpha = .22f); dn in c.pms -> PINK.copy(alpha = .15f); else -> Color.Transparent }
                    val fg = if (dn in c.per || dn in c.ovu) Color.White else MaterialTheme.colorScheme.onSurface
                    var m = Modifier.weight(1f).padding(2.dp).aspectRatio(1f).clip(CircleShape).background(bg)
                    if (dn in c.pred && dn !in c.per) m = m.border(2.dp, PINK, CircleShape)
                    if (dn == today) m = m.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    Box(m.clickable { sel = dn }, contentAlignment = Alignment.Center) { Text(fa(jal(dn).third) + if (model.notes[dn] != null) "·" else "", color = fg, fontSize = 14.sp) } } }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) } } }
            Text("● پریود   ◌ پیش‌بینی   ◍ باروری/تخمک‌گذاری   · علائم   ◔ PMS", fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }
        sel?.let { dn ->
            val inDay = model.periods.any { dn >= it.s && dn <= (it.e ?: (it.s + model.pl - 1)) }
            val tags = model.notes[dn] ?: emptySet()
            AlertDialog(onDismissRequest = { sel = null }, confirmButton = { TextButton({ sel = null }) { Text("بستن") } },
                title = { Text(jdate(dn)) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button({ model.startPeriod(dn) }) { Text("شروع پریود", fontSize = 12.sp) }
                        FilledTonalButton({ model.endPeriod(dn) }) { Text("پایان پریود", fontSize = 12.sp) } }
                    if (inDay) TextButton({ model.removeAt(dn) }) { Text("حذف این دوره‌ی پریود") }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TAGS.forEach { t -> FilterChip(selected = t in tags, onClick = { model.toggle(dn, t) }, label = { Text(t, fontSize = 12.sp) }) } }
                    VitFields(dn) } })
        }
    }

    @Composable fun Stats() {
        val c = model.calc()
        Box2 { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            listOf("میانگین چرخه" to c.avg, "طول پریود" to model.pl, "دوره ثبت‌شده" to model.periods.size).forEach { (l, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(fa(v), fontSize = 22.sp, color = PINK, fontWeight = FontWeight.Bold); Text(l, fontSize = 11.sp) } } } }
        Box2 { Text("طول چرخه‌های اخیر (روز)", fontWeight = FontWeight.SemiBold)
            if (c.cycles.isEmpty()) Text("هنوز داده‌ی کافی نیست", fontSize = 12.sp)
            Row(Modifier.fillMaxWidth().height(130.dp).padding(top = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                c.cycles.takeLast(8).forEach { v -> Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(fa(v), fontSize = 10.sp); Box(Modifier.fillMaxWidth().height((v * 2).dp).clip(RoundedCornerShape(6.dp)).background(PINK)) } } } }
        val freq = model.notes.values.flatten().groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(5)
        Box2 { Text("علائم تکرارشونده", fontWeight = FontWeight.SemiBold)
            if (freq.isEmpty()) Text("هنوز علائمی ثبت نشده", fontSize = 12.sp)
            freq.forEach { Text("• ${it.key}: ${fa(it.value)} بار", fontSize = 13.sp) } }
    }

    @Composable fun Sw(label: String, key: String, sub: String? = null, enabled: Boolean = true, on: ((Boolean) -> Unit)? = null) {
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(label); if (sub != null) Text(sub, fontSize = 11.sp, color = MaterialTheme.colorScheme.outline) }
            Switch(checked = model.f(key), enabled = enabled, onCheckedChange = { model.setF(key, it); on?.invoke(it) }) }
    }

    @Composable fun Settings() {
        var wipe by remember { mutableStateOf(false) }
        Box2 { Text("اعلان‌ها و پیام‌ها", fontWeight = FontWeight.SemiBold)
            Sw("همه‌ی اعلان‌ها", "master", "کلید اصلی؛ با خاموش کردن، هیچ اعلانی ارسال نمی‌شود") { if (it && Build.VERSION.SDK_INT >= 33) askNotif.launch(Manifest.permission.POST_NOTIFICATIONS) }
            val m = model.f("master")
            Sw("یادآوری پریود", "period", "۲ روز قبل و روز پیش‌بینی‌شده", m)
            Sw("یادآوری تخمک‌گذاری و بازه‌ی باروری", "ovu", null, m)
            Sw("یادآوری روزهای PMS", "pms", "بر اساس علائم ثبت‌شده‌ی خودتان تنظیم می‌شود", m)
            Sw("یادآوری ثبت روزانه‌ی علائم", "log", "ساعت ۲۱ اگر چیزی ثبت نشده باشد", m)
            Sw("نکته‌های سلامت", "tips", "هر چند روز یک نکته", m)
            Sw("اعلان مخفی", "discreet", "متن اعلان‌ها عمومی نمایش داده می‌شود", m) }
        Box2 { Text("حالت استفاده", fontWeight = FontWeight.SemiBold)
            listOf("normal" to "عادی (پیگیری چرخه)", "try" to "تلاش برای بارداری", "preg" to "بارداری").forEach { (k, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) { RadioButton(model.mode == k, { model.changeMode(k) }); Text(l) } }
            if (model.mode == "preg") Text("آخرین پریود ثبت‌شده مبنای هفته‌ی بارداری است و یادآوری‌های پریود متوقف می‌شود.", fontSize = 11.sp) }
        Box2 { Text("چرخه", fontWeight = FontWeight.SemiBold)
            Stepper("طول چرخه", model.cy) { model.setLens(it, model.pl) }
            Stepper("طول پریود", model.pl) { model.setLens(model.cy, it) } }
        Box2 { Text("امنیت", fontWeight = FontWeight.SemiBold)
            Sw("نمایش جزئیات در ویجت", "wdet", "پیش‌فرض: ویجت فقط نام برنامه را نشان می‌دهد")
            Sw("ورود با اثر انگشت", "bio", if (canBio()) null else "روی این دستگاه در دسترس نیست", canBio())
            OutlinedButton({ changePin = true }, Modifier.fillMaxWidth()) { Text("تغییر رمز ورود") }
            OutlinedButton({ locked = true }, Modifier.fillMaxWidth()) { Text("قفل کردن برنامه") }
            Text("اطلاعات روی دستگاه رمزنگاری‌شده ذخیره می‌شود و برنامه از اسکرین‌شات و پیش‌نمایش برنامه‌های اخیر محافظت شده است.", fontSize = 11.sp) }
        Box2 { Text("اطلاعات", fontWeight = FontWeight.SemiBold)
            OutlinedButton({ pdf.launch("roozshomar-report.pdf") }, Modifier.fillMaxWidth()) { Text("گزارش PDF برای پزشک") }
            OutlinedButton({ sharePartner() }, Modifier.fillMaxWidth()) { Text("اشتراک پیش‌بینی با همسر (فقط تاریخ‌ها)") }
            OutlinedButton({ exp.launch("roozshomar-backup.json") }, Modifier.fillMaxWidth()) { Text("گرفتن نسخه‌ی پشتیبان") }
            OutlinedButton({ imp.launch(arrayOf("application/json", "*/*")) }, Modifier.fillMaxWidth()) { Text("بازیابی از نسخه‌ی پشتیبان") }
            TextButton({ wipe = true }, Modifier.fillMaxWidth()) { Text("حذف همه‌ی اطلاعات", color = MaterialTheme.colorScheme.error) } }
        Text("پیش‌بینی‌ها تخمینی‌اند و جایگزین مشورت پزشکی نیستند.", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        if (wipe) AlertDialog(onDismissRequest = { wipe = false },
            confirmButton = { TextButton({ model.wipe(); wipe = false; locked = true }) { Text("حذف") } },
            dismissButton = { TextButton({ wipe = false }) { Text("انصراف") } }, text = { Text("همه‌ی اطلاعات و رمز حذف شود؟") })
    }

    @Composable fun Stepper(label: String, v: Int, set: (Int) -> Unit) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, Modifier.weight(1f)); OutlinedButton({ set(v - 1) }) { Text("−") }
            Text(fa(v), Modifier.padding(horizontal = 12.dp)); OutlinedButton({ set(v + 1) }) { Text("+") } }
    }
}
