package at.lukaechen.nplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID

private val Bg = Color(0xFF080808)
private val Panel = Color(0xFF191919)
private val Panel2 = Color(0xFF252525)
private val White = Color(0xFFE8E7E2)
private val Muted = Color(0xFF858585)
private val Red = Color(0xFFE03930)

data class Rule(val id: String, val name: String, val days: Set<Int>)

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("nplanner-v2", MODE_PRIVATE) }

    private fun loadRules(): List<Rule> = try {
        val raw = prefs.getString("rules", null)
        if (raw == null) listOf(
            Rule(UUID.randomUUID().toString(), "Morgenroutine", (1..7).toSet()),
            Rule(UUID.randomUUID().toString(), "30 Minuten lernen", setOf(1, 3, 5)),
            Rule(UUID.randomUUID().toString(), "Vitamine", (1..7).toSet())
        ) else JSONArray(raw).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val d = o.getJSONArray("days")
                Rule(o.getString("id"), o.getString("name"), (0 until d.length()).map { d.getInt(it) }.toSet())
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun saveRules(rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { r ->
            array.put(JSONObject().put("id", r.id).put("name", r.name).put("days", JSONArray(r.days.toList())))
        }
        prefs.edit().putString("rules", array.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ndot = FontFamily(androidx.compose.ui.text.font.Font(resId = R.font.ndot_47))
            MaterialTheme(
                colorScheme = darkColorScheme(primary = White, background = Bg, surface = Panel, onBackground = White, onSurface = White),
                typography = Typography()
            ) {
                NPlanner(ndot)
            }
        }
    }

    @Composable
    private fun NPlanner(ndot: FontFamily) {
        var page by remember { mutableIntStateOf(0) }
        var rules by remember { mutableStateOf(loadRules()) }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var tick by remember { mutableIntStateOf(0) }
        var addDialog by remember { mutableStateOf(false) }
        var newName by remember { mutableStateOf("") }

        LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); delay(30_000) } }
        fun doneKey(rule: Rule, d: LocalDate = date) = "done:$d:${rule.id}"
        fun completed(rule: Rule, d: LocalDate = date) = prefs.getBoolean(doneKey(rule, d), false)
        fun setCompleted(rule: Rule, value: Boolean) { prefs.edit().putBoolean(doneKey(rule), value).apply(); tick++ }

        Box(Modifier.fillMaxSize().background(Bg)) {
            when (page) {
                0 -> TodayScreen(ndot, date, rules.filter { date.dayOfWeek.value in it.days }, ::completed, ::setCompleted, tick)
                1 -> HistoryScreen(ndot, date, rules, ::completed, tick)
                2 -> StatsScreen(ndot, date, rules, ::completed, tick)
                else -> SetupScreen(ndot, rules,
                    onMove = { from, to -> rules = rules.toMutableList().also { val x = it.removeAt(from); it.add(to, x) }; saveRules(rules) },
                    onAdd = { addDialog = true }
                )
            }
            GlyphNav(page, { page = it }, Modifier.align(Alignment.BottomCenter))
        }

        if (addDialog) AlertDialog(
            onDismissRequest = { addDialog = false },
            containerColor = Panel,
            shape = RoundedCornerShape(28.dp),
            title = { Text("NEUE AUFGABE", fontFamily = ndot, fontSize = 24.sp) },
            text = { OutlinedTextField(newName, { newName = it.take(60) }, label = { Text("Bezeichnung") }, singleLine = true) },
            confirmButton = { TextButton(enabled = newName.isNotBlank(), onClick = {
                rules = rules + Rule(UUID.randomUUID().toString(), newName.trim(), (1..7).toSet())
                saveRules(rules); newName = ""; addDialog = false
            }) { Text("Anlegen") } },
            dismissButton = { TextButton(onClick = { addDialog = false }) { Text("Abbrechen") } }
        )
    }
}

@Composable private fun Screen(ndot: FontFamily, eyebrow: String, title: String, subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(top = 26.dp, bottom = 120.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Red, CircleShape)); Spacer(Modifier.width(9.dp)); Text("NPLANNER", fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 2.sp)
        }
        Spacer(Modifier.height(30.dp)); Text(eyebrow, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.8.sp)
        Text(title, fontFamily = ndot, fontWeight = FontWeight.Normal, fontSize = 42.sp, lineHeight = 45.sp, letterSpacing = 1.sp, modifier = Modifier.padding(top = 7.dp))
        Text(subtitle, color = Muted, fontSize = 13.sp); Spacer(Modifier.height(22.dp)); content()
    }
}

@Composable private fun TodayScreen(ndot: FontFamily, date: LocalDate, rules: List<Rule>, completed: (Rule) -> Boolean, setCompleted: (Rule, Boolean) -> Unit, tick: Int) {
    val done = remember(rules, date, tick) { rules.count(completed) }
    val progress by animateFloatAsState(if (rules.isEmpty()) 1f else done.toFloat() / rules.size, label = "progress")
    Screen(ndot, "DEIN RHYTHMUS.", "HEUTE", date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN))) {
        ProgressOrbit(progress, done, rules.size)
        Text(if (done == rules.size) "Dein Tag ist abgeschlossen." else "${rules.size - done} ${if (rules.size - done == 1) "Aufgabe wartet" else "Aufgaben warten"} noch auf dich.", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(28.dp))
        if (done == rules.size) {
            Text("FÜR HEUTE FERTIG.", fontFamily = ndot, fontWeight = FontWeight.Normal, fontSize = 29.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Alles erledigt. Der Rest des Tages gehört dir.", color = Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp, bottom = 25.dp))
        } else {
            SectionLabel("FÜR HEUTE GEPLANT")
            Column(Modifier.clip(RoundedCornerShape(26.dp)).background(Panel)) {
                rules.forEachIndexed { index, rule ->
                    TaskRow(rule, completed(rule)) { setCompleted(rule, !completed(rule)) }
                    if (index < rules.lastIndex) HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(start = 64.dp))
                }
            }
        }
        WeeklySummary()
    }
}

@Composable private fun ProgressOrbit(progress: Float, done: Int, total: Int) {
    Box(Modifier.fillMaxWidth().height(215.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(178.dp)) {
            val stroke = 5.dp.toPx(); val inset = stroke / 2
            drawArc(Color(0xFF242424), -125f, 360f, false, Offset(inset, inset), Size(size.width-stroke, size.height-stroke), style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(White, -125f, 360f * progress, false, Offset(inset, inset), Size(size.width-stroke, size.height-stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$done / $total", fontSize = 46.sp, fontWeight = FontWeight.Light)
            Text(if (done == total) "GESCHAFFT" else "ERLEDIGT", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 2.sp)
        }
    }
}

@Composable private fun TaskRow(rule: Rule, checked: Boolean, click: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = click).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(27.dp).background(if (checked) White else Color.Transparent, CircleShape).then(if (!checked) Modifier.background(Color.Transparent) else Modifier), contentAlignment = Alignment.Center) { Text(if (checked) "✓" else "○", color = if (checked) Bg else Muted, fontSize = 17.sp) }
        Spacer(Modifier.width(15.dp)); Column { Text(rule.name, color = if (checked) Muted else White, fontSize = 15.sp); Text(dayLabel(rule.days), color = Muted, fontSize = 10.sp) }
    }
}

@Composable private fun WeeklySummary() {
    HorizontalDivider(color = Color(0xFF242424), modifier = Modifier.padding(top = 24.dp, bottom = 17.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) { Text("Sport", fontSize = 14.sp); Text("Dein flexibler Wochenfortschritt", color = Muted, fontSize = 10.sp); Row(Modifier.padding(top = 9.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.size(5.dp).background(if (it < 3) White else Color(0xFF333333), CircleShape)) } } }
        Text("3 / 4", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 18.sp)
    }
}

@Composable private fun WeekScreen(ndot: FontFamily, date: LocalDate, rules: List<Rule>, prefs: android.content.SharedPreferences, tick: Int, refresh: () -> Unit) {
    var selected by remember { mutableIntStateOf(date.dayOfWeek.value) }
    val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
    Screen(ndot, "PLATZ FÜR FLEXIBILITÄT.", "DEINE WOCHE", "${monday.format(DateTimeFormatter.ofPattern("d. MMM"))} – ${monday.plusDays(6).format(DateTimeFormatter.ofPattern("d. MMM"))}") {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { listOf("Mo","Di","Mi","Do","Fr","Sa","So").forEachIndexed { i, d -> FilterChip(selected == i+1, { selected = i+1 }, { Text(d, fontSize=10.sp) }, modifier=Modifier.weight(1f)) } }
        SectionLabel(listOf("MONTAG","DIENSTAG","MITTWOCH","DONNERSTAG","FREITAG","SAMSTAG","SONNTAG")[selected-1])
        Column(Modifier.clip(RoundedCornerShape(26.dp)).background(Panel)) { rules.filter { selected in it.days }.forEach { TaskRow(it, false) {} } }
        SectionLabel("FLEXIBEL IN DIESER WOCHE")
        Surface(color = Panel, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(21.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Column { Text("Sport", fontSize = 18.sp); Text("Ziel: 4 Trainingstage", color = Muted, fontSize = 11.sp) }; Text("3/4", fontSize = 32.sp) }; Row(Modifier.padding(vertical = 20.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) { repeat(4) { Box(Modifier.weight(1f).height(6.dp).background(if(it<3) White else Color(0xFF393939), RoundedCornerShape(8.dp))) } }; Text("Bevorzugt Mo, Di, Do, Fr. Andere Tage zählen genauso.", color = Muted, fontSize = 11.sp) } }
    }
}

@Composable private fun HistoryScreen(ndot: FontFamily, date: LocalDate, rules: List<Rule>, completed: (Rule) -> Boolean, tick: Int) {
    Screen(ndot, "JEDER TAG ZÄHLT.", "RÜCKBLICK", date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))) {
        Text("Der native Monatskalender wird im nächsten Datenbank-Schritt mit echten Tageswerten befüllt.", color = Muted)
        Spacer(Modifier.height(18.dp)); Surface(color=Panel, shape=RoundedCornerShape(26.dp), modifier=Modifier.fillMaxWidth()) { Text("Aktueller Tag: ${rules.count(completed)} von ${rules.size} erledigt", modifier=Modifier.padding(22.dp)) }
    }
}

@Composable private fun StatsScreen(ndot: FontFamily, date: LocalDate, rules: List<Rule>, completed: (Rule) -> Boolean, tick: Int) {
    val done = rules.count(completed)
    Screen(ndot, "DRANBLEIBEN, NICHT PERFEKT SEIN.", "FORTSCHRITT", "Aktueller Datenstand") {
        ProgressOrbit(if(rules.isEmpty()) 1f else done.toFloat()/rules.size, done, rules.size)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { StatCard("HEUTE", "$done/${rules.size}", Modifier.weight(1f)); StatCard("SPORT", "3/4", Modifier.weight(1f)) }
    }
}

@Composable private fun StatCard(label: String, value: String, modifier: Modifier) { Surface(modifier, color=Panel, shape=RoundedCornerShape(24.dp)) { Column(Modifier.padding(20.dp)) { SectionLabel(label); Text(value, fontSize=34.sp) } } }

@Composable private fun SetupScreen(ndot: FontFamily, rules: List<Rule>, onMove: (Int, Int) -> Unit, onAdd: () -> Unit) {
    Screen(ndot, "DEINE REGELN.", "EINSTELLUNGEN", "Einmal planen. Im Alltag nur erledigen.") {
        SectionLabel("REGELMÄSSIGE AUFGABEN")
        rules.forEachIndexed { index, rule ->
            Surface(color=Panel, shape=RoundedCornerShape(22.dp), modifier=Modifier.fillMaxWidth().padding(vertical=5.dp)) {
                Row(Modifier.padding(12.dp), verticalAlignment=Alignment.CenterVertically) {
                    Text("⠿", color=Muted, fontSize=24.sp, modifier=Modifier.padding(horizontal=7.dp)); Column(Modifier.weight(1f)) { Text(rule.name); Text(dayLabel(rule.days), color=Muted, fontSize=10.sp) }
                    TextButton(enabled=index>0, onClick={onMove(index,index-1)}) { Text("↑") }; TextButton(enabled=index<rules.lastIndex, onClick={onMove(index,index+1)}) { Text("↓") }
                }
            }
        }
        Button(onClick=onAdd, modifier=Modifier.fillMaxWidth().padding(top=18.dp), shape=RoundedCornerShape(22.dp), colors=ButtonDefaults.buttonColors(containerColor=White, contentColor=Bg)) { Text("Neue Aufgabe anlegen") }
        SectionLabel("FLEXIBLES WOCHENZIEL")
        Surface(color=Panel, shape=RoundedCornerShape(22.dp), modifier=Modifier.fillMaxWidth()) { Row(Modifier.padding(19.dp)) { Column(Modifier.weight(1f)) { Text("Sport"); Text("4× pro Woche · Mo, Di, Do, Fr bevorzugt", color=Muted, fontSize=10.sp) }; Text("›", color=Muted, fontSize=22.sp) } }
        Text("Dieser Build portiert die visuelle Struktur des HTML-Prototyps. Bearbeiten, Drag-and-drop, Room-Verlauf und Widgets folgen auf dieser UI-Basis.", color=Muted, fontSize=11.sp, modifier=Modifier.padding(6.dp,20.dp))
    }
}

private enum class NavGlyph { TODAY, HISTORY, STATS, SETUP }

@Composable private fun GlyphNav(page: Int, select: (Int) -> Unit, modifier: Modifier) {
    val glyphs = listOf(NavGlyph.TODAY, NavGlyph.HISTORY, NavGlyph.STATS, NavGlyph.SETUP)
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp).fillMaxWidth().height(68.dp),
        color = Color(0xE6252525), shape = RoundedCornerShape(34.dp), shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            glyphs.forEachIndexed { i, glyph ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clip(CircleShape)
                        .background(if (page == i) White else Color.Transparent)
                        .clickable { select(i) },
                    contentAlignment = Alignment.Center
                ) { NavIcon(glyph, if (page == i) Bg else Color(0xFF969696)) }
            }
        }
    }
}

@Composable private fun NavIcon(glyph: NavGlyph, color: Color) {
    Canvas(Modifier.size(27.dp)) {
        val w = 1.8.dp.toPx(); val cx = size.width / 2f; val cy = size.height / 2f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1,y1), Offset(x2,y2), w, StrokeCap.Round)
        when (glyph) {
            NavGlyph.TODAY -> {
                drawCircle(color, size.width*.36f, Offset(cx,cy), style=Stroke(w))
                line(size.width*.30f,size.height*.52f,size.width*.44f,size.height*.66f)
                line(size.width*.44f,size.height*.66f,size.width*.72f,size.height*.35f)
            }
            NavGlyph.HISTORY -> {
                // Month calendar: immediately communicates month/year history.
                val l=size.width*.16f; val t=size.height*.22f; val r=size.width*.84f; val b=size.height*.82f
                drawRoundRect(color,Offset(l,t),Size(r-l,b-t),androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),style=Stroke(w))
                line(l,size.height*.40f,r,size.height*.40f)
                line(size.width*.34f,size.height*.15f,size.width*.34f,size.height*.29f)
                line(size.width*.66f,size.height*.15f,size.width*.66f,size.height*.29f)
                val xs=listOf(.31f,.50f,.69f); val ys=listOf(.54f,.69f)
                ys.forEach { y -> xs.forEach { x -> drawCircle(color,1.35.dp.toPx(),Offset(size.width*x,size.height*y)) } }
            }
            NavGlyph.STATS -> {
                val base=size.height*.81f
                line(size.width*.18f,base,size.width*.82f,base)
                listOf(.30f to .58f,.50f to .39f,.70f to .22f).forEach { (x,top) ->
                    drawRoundRect(color,Offset(size.width*x-2.dp.toPx(),size.height*top),Size(4.dp.toPx(),base-size.height*top),androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                }
            }
            NavGlyph.SETUP -> {
                // Simple gear: 8 teeth, outer ring and center hole.
                val outer=size.width*.34f; val tooth=size.width*.44f
                drawCircle(color,outer,Offset(cx,cy),style=Stroke(w))
                drawCircle(color,size.width*.13f,Offset(cx,cy),style=Stroke(w))
                repeat(8) { i ->
                    val a=Math.toRadians((i*45.0)-90.0)
                    val ca=kotlin.math.cos(a).toFloat(); val sa=kotlin.math.sin(a).toFloat()
                    line(cx+ca*outer,cy+sa*outer,cx+ca*tooth,cy+sa*tooth)
                }
            }
        }
    }
}

@Composable private fun SectionLabel(text: String) { Text(text, color=Muted, fontFamily=FontFamily.Monospace, fontSize=10.sp, letterSpacing=1.7.sp, modifier=Modifier.padding(top=26.dp, bottom=12.dp)) }
private fun dayLabel(days: Set<Int>): String = if(days.size==7) "Täglich" else days.sorted().joinToString(" · ") { listOf("Mo","Di","Mi","Do","Fr","Sa","So")[it-1] }
