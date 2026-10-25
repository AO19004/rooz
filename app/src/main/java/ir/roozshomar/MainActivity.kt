package ir.roozshomar

import android.Manifest
import android.os.Build
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
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
import java.time.LocalTime

val PINK = Color(0xFFD6336C); val GREEN = Color(0xFF2F9E6E); val PURPLE = Color(0xFF7048E8); val ORANGE = Color(0xFFF08C00)
val BLUE = Color(0xFF1C7ED6); val TEAL = Color(0xFF0CA678); val RED = Color(0xFFE03131)

class MainActivity : FragmentActivity() {
    lateinit var model: Model
    var locked by mutableStateOf(true)
    var changePin by mutableStateOf(false)
    var editP by mutableStateOf<Period?>(null)
    var delP by mutableStateOf<Period?>(null)
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
        model = Model(this); Notif.schedule(this); model.rems.forEach { Notif.scheduleRem(this, it) }
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
        editP?.let { EditDialog(it) }
        delP?.let { p -> AlertDialog(onDismissRequest = { delP = null }, title = { Text("حذف دوره") },
            text = { Text("همه‌ی اطلاعات این دوره (تاریخ‌ها، علائم، وزن، دما و دارو) برای همیشه حذف می‌شود. مطمئن هستید؟") },
            confirmButton = { CBtn("بله، حذف شود", RED) { model.deletePeriod(p); delP = null } },
            dismissButton = { TextButton({ delP = null }) { Text("انصراف") } }) }
    }

    @Composable fun Box2(content: @Composable ColumnScope.() -> Unit) = Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(20.dp)) { Column(Modifier.padding(16.dp), content = content) }

    fun toast(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()
    fun endAt(dn: Long): Boolean { val ok = model.endPeriod(dn); if (!ok) toast("ابتدا شروع پریود را (حداکثر ۱۴ روز قبل از این تاریخ) ثبت کنید"); return ok }
    fun periodAt(dn: Long) = model.periods.firstOrNull { dn >= it.s && dn <= (it.e ?: (it.s + model.pl - 1)) }

    @Composable fun CBtn(text: String, color: Color, modifier: Modifier = Modifier, fs: Int = 13, onClick: () -> Unit) {
        Button(onClick, modifier, shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 12.dp)) { Text(text, fontSize = fs.sp, fontWeight = FontWeight.Bold, maxLines = 1) }
    }

    @Composable fun Leg(label: String, color: Color, style: Int) {
        Row(Modifier.padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            val shape = Modifier.size(if (style == 3) 7.dp else 13.dp).clip(CircleShape)
            if (style == 4) Text("🔔", fontSize = 11.sp) else Box(when (style) { 1 -> shape.border(2.dp, color, CircleShape); 2 -> shape.background(color.copy(alpha = .3f)); else -> shape.background(color) })
            Spacer(Modifier.width(5.dp)); Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }

    @Composable fun Spin(label: String, value: String, up: () -> Unit, down: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontSize = 11.sp); TextButton(up) { Text("▲") }
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = PURPLE); TextButton(down) { Text("▼") } }
    }

    @Composable fun JalaliPicker(dn: Long, onChange: (Long) -> Unit) {
        val (y, m, d) = jal(dn)
        fun make(ny: Int, nm: Int, nd: Int): Long { val (st, len) = monthStart(ny, nm); return st + minOf(nd, len) - 1 }
        val len = monthStart(y, m).second
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Spin("روز", fa(d), { onChange(make(y, m, if (d + 1 > len) 1 else d + 1)) }, { onChange(make(y, m, if (d - 1 < 1) len else d - 1)) })
            Spin("ماه", MN[m - 1], { onChange(if (m == 12) make(y + 1, 1, d) else make(y, m + 1, d)) }, { onChange(if (m == 1) make(y - 1, 12, d) else make(y, m - 1, d)) })
            Spin("سال", fa(y), { onChange(make(y + 1, m, d)) }, { onChange(make(y - 1, m, d)) })
        }
    }

    fun hmText(hm: Int) = fa(hm / 60).padStart(2, '۰') + ":" + fa(hm % 60).padStart(2, '۰')
    fun removeRem(r: Rem) { model.delRem(r.id); Notif.cancelRem(this, r.id) }
    fun addReminder(dn: Long, hm: Int, text: String): Boolean {
        val today = LocalDate.now().toEpochDay(); val now = LocalTime.now().let { it.hour * 60 + it.minute }
        if (dn < today || (dn == today && hm <= now)) { toast("زمان انتخاب‌شده گذشته است"); return false }
        val r = model.addRem(dn, hm, text.trim()); Notif.scheduleRem(this, r); return true
    }

    fun saveRem(r: Rem, hm: Int, text: String): Boolean {
        val today = LocalDate.now().toEpochDay(); val now = LocalTime.now().let { it.hour * 60 + it.minute }
        if (r.dn < today || (r.dn == today && hm <= now)) { toast("زمان انتخاب‌شده گذشته است"); return false }
        model.editRem(r.id, hm, text.trim())?.let { Notif.scheduleRem(this, it) }; return true
    }

    @Composable fun RemDialog(dn: Long, close: () -> Unit) {
        var edit by remember { mutableStateOf<Long?>(null) }
        var txt by remember { mutableStateOf("") }; var hm by remember { mutableIntStateOf(540) }
        val list = model.rems.filter { it.dn == dn }.sortedBy { it.hm }
        AlertDialog(onDismissRequest = close, title = { Text("🔔 یادآورهای ${jdate(dn)}", fontSize = 16.sp) },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                if (list.isEmpty()) Text("یادآوری برای این روز ثبت نشده.", fontSize = 13.sp)
                list.forEach { r ->
                    if (edit == r.id) {
                        OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), label = { Text("متن یادآور", fontSize = 11.sp) }, singleLine = true)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                            Spin("دقیقه", fa(hm % 60).padStart(2, '۰'), { hm = hm / 60 * 60 + (hm % 60 + 5) % 60 }, { hm = hm / 60 * 60 + (hm % 60 + 55) % 60 })
                            Spin("ساعت", fa(hm / 60).padStart(2, '۰'), { hm = (hm / 60 + 1) % 24 * 60 + hm % 60 }, { hm = (hm / 60 + 23) % 24 * 60 + hm % 60 }) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CBtn("ذخیره", TEAL, Modifier.weight(1f), 12) { if (txt.isNotBlank() && saveRem(r, hm, txt)) edit = null }
                            TextButton({ edit = null }) { Text("انصراف") } }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${hmText(r.hm)} — ${r.text}", Modifier.weight(1f), fontSize = 14.sp)
                            CBtn("ویرایش", BLUE, fs = 11) { edit = r.id; txt = r.text; hm = r.hm }
                            Spacer(Modifier.width(4.dp)); CBtn("حذف", RED, fs = 11) { removeRem(r) } }
                    }
                    Spacer(Modifier.height(8.dp))
                } } },
            confirmButton = { TextButton(close) { Text("بستن") } })
    }

    @Composable fun TagChip(t: String, sel: Boolean, del: Boolean, onClick: () -> Unit) {
        val col = FilterChipDefaults.filterChipColors(selectedContainerColor = ORANGE, selectedLabelColor = Color.White)
        if (del) FilterChip(sel, onClick, { Text(t, fontSize = 12.sp) }, colors = col, trailingIcon = { Text("✕", Modifier.clickable { model.removeCustom(t) }, fontSize = 12.sp) })
        else FilterChip(sel, onClick, { Text(t, fontSize = 12.sp) }, colors = col)
    }

    @OptIn(ExperimentalLayoutApi::class)
    @Composable fun SymptomsPicker(dn: Long) {
        val tags = model.notes[dn] ?: emptySet()
        var open by remember(dn) { mutableStateOf(false) }
        var newT by remember(dn) { mutableStateOf("") }
        Text("علائم انتخاب‌شده", fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
        if (tags.isEmpty()) Text("هنوز علامتی انتخاب نشده", fontSize = 12.sp, color = MaterialTheme.colorScheme.outline)
        else FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { tags.forEach { t -> TagChip(t, true, false) { model.toggle(dn, t) } } }
        CBtn(if (open) "بستن فهرست علائم ▲" else "انتخاب علائم ▼", ORANGE, Modifier.fillMaxWidth().padding(top = 8.dp), 14) { open = !open }
        if (open) {
            FlowRow(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (TAGS + model.custom).forEach { t -> TagChip(t, t in tags, t in model.custom) { model.toggle(dn, t) } } }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newT, { newT = it }, Modifier.weight(1f), label = { Text("علامت جدید", fontSize = 11.sp) }, singleLine = true)
                Spacer(Modifier.width(6.dp))
                CBtn("افزودن", TEAL, fs = 12) { if (newT.isNotBlank()) { model.addCustom(newT.trim(), dn); newT = "" } } }
        }
    }

    @Composable fun NoteField(dn: Long) {
        var t by remember(dn) { mutableStateOf(model.dnotes[dn] ?: "") }
        OutlinedTextField(t, { t = it; model.setDnote(dn, it) }, Modifier.fillMaxWidth(), label = { Text("یادداشت این روز (اختیاری)", fontSize = 11.sp) }, minLines = 2)
    }

    @Composable fun RemindersContent(dn: Long) {
        var txt by remember(dn) { mutableStateOf("") }; var hm by remember(dn) { mutableIntStateOf(540) }
        Text("یادآور", fontWeight = FontWeight.SemiBold, color = BLUE, modifier = Modifier.padding(vertical = 4.dp))
        model.rems.filter { it.dn == dn }.sortedBy { it.hm }.forEach { r ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("🔔 ${hmText(r.hm)} — ${r.text}", Modifier.weight(1f), fontSize = 13.sp)
                TextButton({ removeRem(r) }) { Text("✕", color = RED) } } }
        OutlinedTextField(txt, { txt = it }, Modifier.fillMaxWidth(), label = { Text("متن یادآور (مثلاً وقت دکتر)", fontSize = 11.sp) }, singleLine = true)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Spin("دقیقه", fa(hm % 60).padStart(2, '۰'), { hm = hm / 60 * 60 + (hm % 60 + 5) % 60 }, { hm = hm / 60 * 60 + (hm % 60 + 55) % 60 })
            Spin("ساعت", fa(hm / 60).padStart(2, '۰'), { hm = (hm / 60 + 1) % 24 * 60 + hm % 60 }, { hm = (hm / 60 + 23) % 24 * 60 + hm % 60 }) }
        CBtn("افزودن یادآور", BLUE, Modifier.fillMaxWidth(), 13) { if (txt.isNotBlank() && addReminder(dn, hm, txt)) txt = "" }
    }

    @Composable fun MoreInfo(dn: Long) {
        var open by remember(dn) { mutableStateOf(false) }
        CBtn(if (open) "اطلاعات دیگر ▲" else "اطلاعات دیگر ▼", TEAL, Modifier.fillMaxWidth(), 14) { open = !open }
        if (open) { Spacer(Modifier.height(8.dp)); NoteField(dn); Spacer(Modifier.height(6.dp)); VitFields(dn) }
    }

    @Composable fun SymptomsContent(dn: Long, rem: Boolean = true) {
        SymptomsPicker(dn)
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        MoreInfo(dn)
        if (rem) { HorizontalDivider(Modifier.padding(vertical = 8.dp)); RemindersContent(dn) }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable fun SymSheet(today: Long, close: () -> Unit) {
        var day by remember { mutableLongStateOf(today) }
        ModalBottomSheet(onDismissRequest = close, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton({ day -= 1 }) { Text("→", fontSize = 20.sp) }
                    Text(jdate(day), fontWeight = FontWeight.Bold, color = ORANGE)
                    TextButton({ day += 1 }) { Text("←", fontSize = 20.sp) } }
                SymptomsContent(day, false)
                CBtn("تمام", ORANGE, Modifier.fillMaxWidth().padding(top = 12.dp), 14) { close() }
            }
        }
    }

    @Composable fun EndDialog(today: Long, close: () -> Unit) {
        var pick by remember { mutableLongStateOf(today) }
        AlertDialog(onDismissRequest = close, title = { Text("پایان پریود") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("پایان پریود را برای امروز یا یک تاریخ دلخواه ثبت کنید.", fontSize = 13.sp)
                JalaliPicker(pick) { pick = it }
                Text("تاریخ انتخابی: ${jdate(pick)}", fontSize = 12.sp, color = PURPLE, modifier = Modifier.padding(vertical = 6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CBtn("ثبت برای امروز", TEAL, Modifier.weight(1f), 12) { if (endAt(today)) close() }
                    CBtn("ثبت تاریخ انتخابی", PURPLE, Modifier.weight(1f), 12) { if (endAt(pick)) close() } } } },
            confirmButton = { TextButton(close) { Text("انصراف") } })
    }

    @Composable fun EditDialog(p: Period) {
        var s by remember(p) { mutableLongStateOf(p.s) }
        var hasEnd by remember(p) { mutableStateOf(p.e != null) }
        var e by remember(p) { mutableLongStateOf(p.e ?: (p.s + model.pl - 1)) }
        AlertDialog(onDismissRequest = { editP = null }, title = { Text("ویرایش دوره") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("تاریخ شروع: ${jdate(s)}", fontWeight = FontWeight.SemiBold, color = PINK)
                JalaliPicker(s) { s = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(hasEnd, { hasEnd = it }); Text("تاریخ پایان ثبت شود") }
                if (hasEnd) { Text("تاریخ پایان: ${jdate(e)}", fontWeight = FontWeight.SemiBold, color = PURPLE); JalaliPicker(e) { e = it } } } },
            confirmButton = { CBtn("ذخیره", TEAL) {
                if (hasEnd && e < s) toast("تاریخ پایان نمی‌تواند قبل از شروع باشد")
                else { model.editPeriod(p, s, if (hasEnd) e else null); editP = null } } },
            dismissButton = { TextButton({ editP = null }) { Text("انصراف") } })
    }

    @OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
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
        var showEnd by remember { mutableStateOf(false) }
        var remDay by remember { mutableStateOf<Long?>(null) }
        var symSheet by remember { mutableStateOf(false) }
        Row(Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CBtn("شروع پریود", PINK, Modifier.weight(1f)) { model.startPeriod(today) }
            CBtn("پایان پریود", PURPLE, Modifier.weight(1f)) { showEnd = true }
            CBtn("ثبت علائم", ORANGE, Modifier.weight(1f)) { symSheet = true }
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
                    val bg = when { dn in c.per -> PINK; dn in c.ovu -> GREEN; dn in c.fert -> GREEN.copy(alpha = .22f); dn in c.pms -> PURPLE.copy(alpha = .2f); else -> Color.Transparent }
                    val fg = if (dn in c.per || dn in c.ovu) Color.White else MaterialTheme.colorScheme.onSurface
                    var m = Modifier.fillMaxSize().padding(2.dp).clip(CircleShape).background(bg)
                    if (dn in c.pred && dn !in c.per) m = m.border(2.dp, PINK, CircleShape)
                    if (dn == today) m = m.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                    Box(Modifier.weight(1f).aspectRatio(1f)) { Box(m.clickable { sel = dn }, contentAlignment = Alignment.Center) {
                        Text(fa(jal(dn).third), color = fg, fontSize = 14.sp)
                        if (model.notes[dn] != null || model.vit[dn] != null || model.dnotes[dn] != null) Box(Modifier.align(Alignment.BottomCenter).padding(bottom = 3.dp).size(5.dp).clip(CircleShape).background(ORANGE))
                        }
                    if (model.rems.any { it.dn == dn }) Box(Modifier.align(Alignment.TopEnd).size(20.dp).clickable { remDay = dn }, contentAlignment = Alignment.Center) { Text("🔔", fontSize = 11.sp) } } } }
                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) } } }
            FlowRow(Modifier.padding(top = 8.dp)) {
                Leg("پریود", PINK, 0); Leg("پیش‌بینی پریود", PINK, 1); Leg("روزهای باروری", GREEN, 2)
                Leg("تخمک‌گذاری", GREEN, 0); Leg("PMS", PURPLE, 2); Leg("علائم ثبت‌شده", ORANGE, 3); Leg("یادآور (لمس برای مشاهده)", Color(0xFFB8860B), 4)
            }
        }
        sel?.let { dn ->
            val pa = periodAt(dn)
            AlertDialog(onDismissRequest = { sel = null }, confirmButton = { TextButton({ sel = null }) { Text("بستن") } },
                title = { Text(jdate(dn)) }, text = { Column(Modifier.verticalScroll(rememberScrollState())) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CBtn("شروع پریود", PINK, Modifier.weight(1f), 12) { model.startPeriod(dn) }
                        CBtn("پایان پریود", PURPLE, Modifier.weight(1f), 12) { endAt(dn) } }
                    if (pa != null) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CBtn("ویرایش دوره", BLUE, Modifier.weight(1f), 12) { editP = pa; sel = null }
                        CBtn("حذف دوره", RED, Modifier.weight(1f), 12) { delP = pa; sel = null } }
                    SymptomsContent(dn) } })
        }
        if (showEnd) EndDialog(today) { showEnd = false }
        remDay?.let { RemDialog(it) { remDay = null } }
        if (symSheet) SymSheet(today) { symSheet = false }
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
        Box2 { Text("دوره‌های ثبت‌شده", fontWeight = FontWeight.SemiBold)
            if (model.periods.isEmpty()) Text("هنوز دوره‌ای ثبت نشده", fontSize = 12.sp)
            model.periods.sortedByDescending { it.s }.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(jdate(p.s) + (p.e?.let { " تا ${jdate(it)}" } ?: " (بدون پایان)"), Modifier.weight(1f), fontSize = 13.sp)
                    CBtn("ویرایش", BLUE, fs = 12) { editP = p }; Spacer(Modifier.width(6.dp)); CBtn("حذف", RED, fs = 12) { delP = p } } } }
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
            Sw("یادآور کارها (متن‌های خودتان)", "rem", "اعلان در تاریخ و ساعتی که تعیین کرده‌اید", m)
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
            CBtn("تغییر رمز ورود", BLUE, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { changePin = true }
            CBtn("قفل کردن برنامه", PURPLE, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { locked = true }
            Text("اطلاعات روی دستگاه رمزنگاری‌شده ذخیره می‌شود و برنامه از اسکرین‌شات و پیش‌نمایش برنامه‌های اخیر محافظت شده است.", fontSize = 11.sp) }
        Box2 { Text("اطلاعات", fontWeight = FontWeight.SemiBold)
            CBtn("گزارش PDF برای پزشک", TEAL, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { pdf.launch("roozshomar-report.pdf") }
            CBtn("اشتراک پیش‌بینی با همسر (فقط تاریخ‌ها)", BLUE, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { sharePartner() }
            CBtn("گرفتن نسخه‌ی پشتیبان", GREEN, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { exp.launch("roozshomar-backup.json") }
            CBtn("بازیابی از نسخه‌ی پشتیبان", ORANGE, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { imp.launch(arrayOf("application/json", "*/*")) }
            CBtn("حذف همه‌ی اطلاعات", RED, Modifier.fillMaxWidth().padding(vertical = 3.dp), 14) { wipe = true } }
        Text("پیش‌بینی‌ها تخمینی‌اند و جایگزین مشورت پزشکی نیستند.", fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Text("تماس با سازنده: ao19004@yahoo.com", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .4f), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 8.dp))
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
