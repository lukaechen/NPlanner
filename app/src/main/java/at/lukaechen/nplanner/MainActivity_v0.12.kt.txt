package at.lukaechen.nplanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Bg = Color(0xFF080808)
private val Panel = Color(0xFF191919)
private val Panel2 = Color(0xFF252525)
private val White = Color(0xFFE8E7E2)
private val Muted = Color(0xFF858585)
private val Red = Color(0xFFE03930)

/** FIXED = feste Uhrzeit, INTERVAL = Zeitabstand mit Von/Bis, ALLDAY = ohne Zeit, WEEKLY = Wochenziel (1..14x pro Woche). */
enum class RuleKind { FIXED, INTERVAL, ALLDAY, WEEKLY }

data class Rule(
    val id: String,
    val name: String,
    val days: Set<Int>,
    val kind: RuleKind = RuleKind.ALLDAY,
    val timeMin: Int = 8 * 60,
    val everyMin: Int = 120,
    val fromMin: Int = 8 * 60,
    val toMin: Int = 20 * 60,
    val weeklyTarget: Int = 1
)

data class WeeklyGoal(
    val name: String = "Sport",
    val targetPerWeek: Int = 4,
    val preferredDays: Set<Int> = setOf(1, 2, 4, 5),
    val hint: String = "Bevorzugt Mo, Di, Do, Fr. Andere Tage zählen genauso."
)

private val LocalDotHeadings = compositionLocalOf { false }

// ---------- Zeit- und Regel-Helfer ----------

private fun fmt(min: Int): String = String.format(Locale.GERMAN, "%02d:%02d", min / 60, min % 60)

private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun everyLabel(min: Int): String = when {
    min < 60 -> "$min Min."
    min % 60 == 0 -> "${min / 60} Std."
    else -> "${min / 60} Std. ${min % 60} Min."
}

private fun Rule.slots(): List<Int> {
    val step = everyMin.coerceAtLeast(15)
    val list = ArrayList<Int>()
    var t = fromMin
    while (t <= toMin && list.size < 48) { list.add(t); t += step }
    return if (list.isEmpty()) listOf(fromMin) else list
}

/** Anzahl der Haken, die fuer "erledigt" noetig sind. */
private fun totalOf(r: Rule): Int = when (r.kind) {
    RuleKind.INTERVAL -> r.slots().size
    RuleKind.WEEKLY -> r.weeklyTarget.coerceIn(1, 14)
    else -> 1
}

private fun nextTime(r: Rule, done: Int): Int? = when (r.kind) {
    RuleKind.FIXED -> if (done < 1) r.timeMin else null
    RuleKind.INTERVAL -> r.slots().getOrNull(done)
    else -> null
}

private fun sortKey(r: Rule): Int = when (r.kind) {
    RuleKind.FIXED -> r.timeMin
    RuleKind.INTERVAL -> r.fromMin
    else -> 100_000
}

private fun scheduleLabel(r: Rule): String = when (r.kind) {
    RuleKind.FIXED -> "${fmt(r.timeMin)} · ${dayLabel(r.days)}"
    RuleKind.INTERVAL -> "Alle ${everyLabel(r.everyMin)} · ${fmt(r.fromMin)}–${fmt(r.toMin)} · ${dayLabel(r.days)}"
    RuleKind.WEEKLY -> "${r.weeklyTarget.coerceIn(1, 14)}× pro Woche · flexibel"
    RuleKind.ALLDAY -> dayLabel(r.days)
}

private fun weekStartKey(d: LocalDate): String =
    d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("nplanner-v2", MODE_PRIVATE) }

    private fun loadRules(): List<Rule> = try {
        val raw = prefs.getString("rules", null)
        if (raw == null) {
            val defaults = listOf(
                Rule(UUID.randomUUID().toString(), "Morgenroutine", (1..7).toSet()),
                Rule(UUID.randomUUID().toString(), "30 Minuten lernen", setOf(1, 3, 5)),
                Rule(UUID.randomUUID().toString(), "Vitamine", (1..7).toSet())
            )
            saveRules(defaults)
            defaults
        } else JSONArray(raw).let { a ->
            (0 until a.length()).map { i ->
                val o = a.getJSONObject(i)
                val d = o.getJSONArray("days")
                Rule(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    days = (0 until d.length()).map { d.getInt(it) }.toSet(),
                    kind = runCatching { RuleKind.valueOf(o.optString("kind", "ALLDAY")) }.getOrDefault(RuleKind.ALLDAY),
                    timeMin = o.optInt("time", 8 * 60),
                    everyMin = o.optInt("every", 120),
                    fromMin = o.optInt("from", 8 * 60),
                    toMin = o.optInt("to", 20 * 60),
                    weeklyTarget = o.optInt("weeklyTarget", 1).coerceIn(1, 14)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun saveRules(rules: List<Rule>) {
        val array = JSONArray()
        rules.forEach { r ->
            array.put(
                JSONObject()
                    .put("id", r.id).put("name", r.name).put("days", JSONArray(r.days.toList()))
                    .put("kind", r.kind.name).put("time", r.timeMin).put("every", r.everyMin)
                    .put("from", r.fromMin).put("to", r.toMin)
                    .put("weeklyTarget", r.weeklyTarget.coerceIn(1, 14))
            )
        }
        prefs.edit().putString("rules", array.toString()).apply()
    }

    private fun loadWeeklyGoal(): WeeklyGoal = try {
        val raw = prefs.getString("weekly_goal", null)
        if (raw == null) {
            WeeklyGoal().also(::saveWeeklyGoal)
        } else {
            val o = JSONObject(raw)
            val arr = o.optJSONArray("preferredDays")
            val prefSet = if (arr != null) {
                (0 until arr.length()).mapNotNull { arr.optInt(it, -1).takeIf { v -> v in 1..7 } }.toSet()
            } else {
                setOf(1, 2, 4, 5)
            }
            WeeklyGoal(
                name = o.optString("name", "Sport").ifBlank { "Sport" },
                targetPerWeek = o.optInt("targetPerWeek", 4).coerceIn(1, 14),
                preferredDays = prefSet,
                hint = o.optString("hint", "Bevorzugt Mo, Di, Do, Fr. Andere Tage zählen genauso.")
            )
        }
    } catch (_: Exception) { WeeklyGoal() }

    private fun saveWeeklyGoal(goal: WeeklyGoal) {
        val obj = JSONObject()
            .put("name", goal.name)
            .put("targetPerWeek", goal.targetPerWeek.coerceIn(1, 14))
            .put("preferredDays", JSONArray(goal.preferredDays.toList().sorted()))
            .put("hint", goal.hint)
        prefs.edit().putString("weekly_goal", obj.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val ndot = FontFamily(androidx.compose.ui.text.font.Font(resId = R.font.ndot_47))
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = White,
                    onPrimary = Bg,
                    background = Bg,
                    onBackground = White,
                    surface = Panel,
                    onSurface = White,
                    surfaceVariant = Panel2,
                    onSurfaceVariant = White,
                    secondary = White,
                    onSecondary = Bg
                ),
                typography = Typography()
            ) {
                CompositionLocalProvider(LocalContentColor provides White) {
                    NPlanner(ndot)
                }
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun NPlanner(ndot: FontFamily) {
        var rules by remember { mutableStateOf(loadRules()) }
        var weeklyGoal by remember { mutableStateOf(loadWeeklyGoal()) }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var nowMin by remember { mutableIntStateOf(minuteOfDay()) }
        var tick by remember { mutableIntStateOf(0) }
        var addDialog by remember { mutableStateOf(false) }
        var editGoalDialog by remember { mutableStateOf(false) }
        var dotHeadings by remember { mutableStateOf(prefs.getBoolean("use_ndot_headings", false)) }
        var orbit by remember { mutableStateOf(prefs.getString("progress_style", "bars") == "orbit") }

        LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); nowMin = minuteOfDay(); delay(30_000) } }

        fun weekKey(rule: Rule, d: LocalDate = date) = "wk:${weekStartKey(d)}:${rule.id}"
        fun weekCountKey(rule: Rule, d: LocalDate = date) = "wk_cnt:${weekStartKey(d)}:${rule.id}"

        fun goalCount(d: LocalDate = date): Int =
            prefs.getInt("goal_cnt:${weekStartKey(d)}", 0).coerceIn(0, weeklyGoal.targetPerWeek.coerceAtLeast(1))

        fun setGoalCount(newCount: Int, d: LocalDate = date) {
            val clamped = newCount.coerceIn(0, weeklyGoal.targetPerWeek.coerceAtLeast(1))
            prefs.edit().putInt("goal_cnt:${weekStartKey(d)}", clamped).apply()
            tick++
        }

        fun doneCount(rule: Rule, d: LocalDate = date): Int = when (rule.kind) {
            RuleKind.INTERVAL -> prefs.getInt("cnt:$d:${rule.id}", 0).coerceIn(0, totalOf(rule))
            RuleKind.WEEKLY -> {
                val target = totalOf(rule)
                if (prefs.contains(weekCountKey(rule, d))) {
                    prefs.getInt(weekCountKey(rule, d), 0).coerceIn(0, target)
                } else if (prefs.getBoolean(weekKey(rule, d), false)) {
                    1
                } else {
                    0
                }
            }
            else -> if (prefs.getBoolean("done:$d:${rule.id}", false)) 1 else 0
        }

        fun stepWeeklyRule(rule: Rule, delta: Int) {
            val target = totalOf(rule)
            val next = (doneCount(rule) + delta).coerceIn(0, target)
            prefs.edit()
                .putInt(weekCountKey(rule), next)
                .putBoolean(weekKey(rule), next >= target)
                .apply()
            tick++
        }

        fun completed(rule: Rule, d: LocalDate = date): Boolean = doneCount(rule, d) >= totalOf(rule)

        fun toggle(rule: Rule) {
            val e = prefs.edit()
            when (rule.kind) {
                RuleKind.INTERVAL -> {
                    val c = doneCount(rule)
                    e.putInt("cnt:$date:${rule.id}", if (c >= totalOf(rule)) c - 1 else c + 1)
                }
                RuleKind.WEEKLY -> {
                    val c = doneCount(rule)
                    val target = totalOf(rule)
                    val next = if (c >= target) 0 else c + 1
                    e.putInt(weekCountKey(rule), next)
                    e.putBoolean(weekKey(rule), next >= target)
                }
                else -> e.putBoolean("done:$date:${rule.id}", !completed(rule))
            }
            e.apply()
            tick++
        }

        fun deleteRule(ruleId: String) {
            rules = rules.filterNot { it.id == ruleId }
            saveRules(rules)
            tick++
        }

        val dailyRules = remember(rules, date) {
            rules.filter { it.kind != RuleKind.WEEKLY && date.dayOfWeek.value in it.days }.sortedBy { sortKey(it) }
        }
        val weeklyRules = remember(rules) { rules.filter { it.kind == RuleKind.WEEKLY } }
        val currentGoalCount = remember(date, tick, weeklyGoal) { goalCount(date) }

        val pagerState = rememberPagerState(pageCount = { 4 })
        val scope = rememberCoroutineScope()
        fun selectPage(target: Int) {
            if (target in 0..3) scope.launch { pagerState.animateScrollToPage(target) }
        }

        CompositionLocalProvider(LocalDotHeadings provides dotHeadings) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { p ->
                    when (p) {
                        0 -> TodayScreen(
                            ndot = ndot,
                            date = date,
                            rules = dailyRules,
                            weekly = weeklyRules,
                            weeklyGoal = weeklyGoal,
                            goalCount = currentGoalCount,
                            onStepGoal = { delta -> setGoalCount(currentGoalCount + delta) },
                            count = ::doneCount,
                            onStepWeeklyRule = ::stepWeeklyRule,
                            onToggle = ::toggle,
                            nowMin = nowMin,
                            orbit = orbit,
                            tick = tick
                        )
                        1 -> HistoryScreen(ndot, date, dailyRules, ::completed, tick)
                        2 -> StatsScreen(
                            ndot = ndot,
                            date = date,
                            rules = dailyRules,
                            weeklyGoal = weeklyGoal,
                            goalCount = currentGoalCount,
                            completed = ::completed,
                            tick = tick
                        )
                        else -> SetupScreen(
                            ndot = ndot,
                            rules = rules,
                            weeklyGoal = weeklyGoal,
                            goalCount = currentGoalCount,
                            useDotHeadings = dotHeadings,
                            onHeadingStyleChange = { enabled ->
                                dotHeadings = enabled
                                prefs.edit().putBoolean("use_ndot_headings", enabled).apply()
                            },
                            orbit = orbit,
                            onProgressStyleChange = { useOrbit ->
                                orbit = useOrbit
                                prefs.edit().putString("progress_style", if (useOrbit) "orbit" else "bars").apply()
                            },
                            onMove = { from, to ->
                                if (from in rules.indices && to in rules.indices && from != to) {
                                    rules = rules.toMutableList().also {
                                        val item = it.removeAt(from)
                                        it.add(to, item)
                                    }
                                    saveRules(rules)
                                }
                            },
                            onDelete = ::deleteRule,
                            onAdd = { addDialog = true },
                            onEditWeeklyGoal = { editGoalDialog = true }
                        )
                    }
                }
                GlyphNav(pagerState.currentPage, ::selectPage, Modifier.align(Alignment.BottomCenter))
            }

            if (addDialog) NewTaskDialog(
                ndot = ndot,
                onDismiss = { addDialog = false },
                onCreate = { rule ->
                    rules = rules + rule
                    saveRules(rules)
                    addDialog = false
                }
            )

            if (editGoalDialog) WeeklyGoalDialog(
                ndot = ndot,
                initialGoal = weeklyGoal,
                onDismiss = { editGoalDialog = false },
                onSave = { updated ->
                    weeklyGoal = updated
                    saveWeeklyGoal(updated)
                    val clamped = goalCount(date).coerceIn(0, updated.targetPerWeek.coerceAtLeast(1))
                    prefs.edit().putInt("goal_cnt:${weekStartKey(date)}", clamped).apply()
                    editGoalDialog = false
                    tick++
                }
            )
        }
    }
}

@Composable
private fun DisplayTitle(
    text: String,
    ndot: FontFamily,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 38.sp
) {
    val useDot = LocalDotHeadings.current
    Text(
        text = text,
        modifier = modifier,
        color = White,
        fontFamily = if (useDot) ndot else FontFamily.Default,
        fontWeight = if (useDot) FontWeight.Normal else FontWeight.Light,
        fontSize = fontSize,
        lineHeight = fontSize * 1.12f,
        letterSpacing = if (useDot) 1.sp else (-0.8).sp
    )
}

/** Grosse Zahl "erledigt/gesamt" in der Ueberschriftenschrift, Nenner gedimmt. */
@Composable
private fun DisplayCount(done: Int, total: Int, ndot: FontFamily, fontSize: TextUnit) {
    val useDot = LocalDotHeadings.current
    Text(
        text = buildAnnotatedString {
            append("$done")
            withStyle(SpanStyle(color = Color(0xFF5A5A5A))) { append("/$total") }
        },
        color = White,
        fontFamily = if (useDot) ndot else FontFamily.Default,
        fontWeight = if (useDot) FontWeight.Normal else FontWeight.Light,
        fontSize = fontSize,
        lineHeight = fontSize,
        letterSpacing = if (useDot) 1.sp else (-0.8).sp
    )
}

@Composable
private fun Screen(
    ndot: FontFamily,
    eyebrow: String,
    title: String,
    subtitle: String,
    scrollable: Boolean = true,
    compactHeader: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = Modifier
        .fillMaxSize()
        .statusBarsPadding()
        .padding(horizontal = 22.dp)
        .padding(top = if (compactHeader) 8.dp else 12.dp, bottom = if (compactHeader) 92.dp else 102.dp)
    Column(if (scrollable) base.verticalScroll(rememberScrollState()) else base) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Red, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(eyebrow, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.8.sp)
        }
        DisplayTitle(
            text = title,
            ndot = ndot,
            modifier = Modifier.padding(top = if (compactHeader) 4.dp else 7.dp),
            fontSize = if (compactHeader) 34.sp else 38.sp
        )
        Text(subtitle, color = Muted, fontSize = if (compactHeader) 12.sp else 13.sp)
        Spacer(Modifier.height(if (compactHeader) 8.dp else 14.dp))
        content()
    }
}

@Composable
private fun TodayScreen(
    ndot: FontFamily,
    date: LocalDate,
    rules: List<Rule>,
    weekly: List<Rule>,
    weeklyGoal: WeeklyGoal,
    goalCount: Int,
    onStepGoal: (Int) -> Unit,
    count: (Rule) -> Int,
    onStepWeeklyRule: (Rule, Int) -> Unit,
    onToggle: (Rule) -> Unit,
    nowMin: Int,
    orbit: Boolean,
    tick: Int
) {
    val done = remember(rules, date, tick) { rules.count { count(it) >= totalOf(it) } }
    val progress by animateFloatAsState(if (rules.isEmpty()) 1f else done.toFloat() / rules.size, label = "progress")
    val open = rules.size - done
    val next = rules
        .filter { count(it) < totalOf(it) }
        .mapNotNull { r -> nextTime(r, count(r))?.let { t -> r to t } }
        .filter { it.second >= nowMin }
        .minByOrNull { it.second }
    val status = when {
        open == 0 -> "Dein Tag ist abgeschlossen."
        next != null -> "Als Nächstes um ${fmt(next.second)} · ${next.first.name}"
        else -> "$open ${if (open == 1) "Aufgabe wartet" else "Aufgaben warten"} noch auf dich."
    }

    Screen(
        ndot = ndot,
        eyebrow = "DEIN RHYTHMUS.",
        title = "HEUTE",
        subtitle = date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)),
        scrollable = false,
        compactHeader = orbit
    ) {
        if (orbit) {
            ProgressOrbit(
                progress = progress,
                done = done,
                total = rules.size,
                ndot = ndot,
                ringSize = 148.dp,
                boxHeight = 156.dp,
                strokeWidth = 6.dp,
                countFontSize = 38.sp,
                segmented = true
            )
            Text(
                status,
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 2.dp)
            )
        } else {
            Spacer(Modifier.height(12.dp))
            ProgressBars(done, rules.size, ndot)
            Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(if (orbit) 4.dp else 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (done == rules.size) {
                DisplayTitle(
                    "FÜR HEUTE FERTIG.",
                    ndot,
                    Modifier.align(Alignment.CenterHorizontally).padding(top = if (orbit) 10.dp else 18.dp),
                    28.sp
                )
                Text(
                    "Alles erledigt. Der Rest des Tages gehört dir.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 8.dp)
                )
            } else {
                SectionLabel(
                    "FÜR HEUTE GEPLANT",
                    topPadding = if (orbit) 10.dp else 26.dp,
                    bottomPadding = if (orbit) 8.dp else 12.dp
                )
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    rules.forEachIndexed { index, rule ->
                        val c = count(rule)
                        val total = totalOf(rule)
                        val subtitle = when (rule.kind) {
                            RuleKind.INTERVAL -> "Alle ${everyLabel(rule.everyMin)} · $c/$total"
                            else -> dayLabel(rule.days)
                        }
                        val chip = when (rule.kind) {
                            RuleKind.FIXED -> fmt(rule.timeMin)
                            RuleKind.INTERVAL -> rule.slots().getOrNull(c)?.let { fmt(it) }
                            else -> null
                        }
                        TaskRow(
                            rule = rule,
                            checked = c >= total,
                            subtitle = subtitle,
                            chip = chip,
                            chipHighlight = next?.first === rule,
                            compact = orbit
                        ) { onToggle(rule) }
                        if (index < rules.lastIndex) {
                            HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }
            WeeklyCard(
                weekly = weekly,
                goal = weeklyGoal,
                goalCount = goalCount,
                onStepGoal = onStepGoal,
                ruleCount = count,
                onStepRule = onStepWeeklyRule,
                compact = orbit
            )
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ProgressBars(done: Int, total: Int, ndot: FontFamily) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            DisplayCount(done, total, ndot, 84.sp)
            Text(
                if (done == total) "GESCHAFFT" else "ERLEDIGT",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (total > 10) 3.dp else 6.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (i < done) White else Color(0xFF2A2A2A))
                )
            }
        }
    }
}

@Composable
private fun ProgressOrbit(
    progress: Float,
    done: Int,
    total: Int,
    ndot: FontFamily,
    ringSize: Dp = 148.dp,
    boxHeight: Dp = 156.dp,
    strokeWidth: Dp = 6.dp,
    countFontSize: TextUnit = 38.sp,
    segmented: Boolean = true
) {
    Box(Modifier.fillMaxWidth().height(boxHeight), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)
            if (segmented && total > 1) {
                val gapDeg = if (total > 10) 4f else 7f
                val stepDeg = 360f / total
                val sweepDeg = (stepDeg - gapDeg).coerceAtLeast(3f)
                val startBase = -90f + gapDeg / 2f
                repeat(total) { i ->
                    val segStart = startBase + i * stepDeg
                    drawArc(
                        color = if (i < done) White else Color(0xFF2A2A2A),
                        startAngle = segStart,
                        sweepAngle = sweepDeg,
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = arcSize,
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            } else {
                drawArc(
                    color = Color(0xFF242424),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                drawArc(
                    color = White,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            DisplayCount(done, total, ndot, countFontSize)
            Text(
                if (done == total) "GESCHAFFT" else "ERLEDIGT",
                color = Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                letterSpacing = 2.sp
            )
        }
    }
}

@Composable
private fun CheckCircle(checked: Boolean) {
    Box(
        Modifier.size(34.dp).background(if (checked) White else Color.Transparent, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (checked) "✓" else "○",
            color = if (checked) Bg else Muted,
            fontSize = 20.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
        )
    }
}

@Composable
private fun TaskRow(
    rule: Rule,
    checked: Boolean,
    subtitle: String = dayLabel(rule.days),
    chip: String? = null,
    chipHighlight: Boolean = false,
    compact: Boolean = false,
    click: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 56.dp else 64.dp)
            .clickable(onClick = click)
            .padding(horizontal = 15.dp, vertical = if (compact) 6.dp else 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CheckCircle(checked)
        Spacer(Modifier.width(14.dp))
        Box(Modifier.weight(1f).heightIn(min = if (compact) 38.dp else 44.dp), contentAlignment = Alignment.CenterStart) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    rule.name,
                    color = if (checked) Muted else White,
                    fontSize = 17.sp,
                    lineHeight = 20.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                Text(
                    subtitle,
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
        if (chip != null) {
            Text(
                chip,
                color = if (chipHighlight) White else Muted,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = if (chipHighlight)
                    Modifier.clip(RoundedCornerShape(12.dp)).background(Panel2).padding(horizontal = 10.dp, vertical = 6.dp)
                else
                    Modifier.padding(end = 10.dp)
            )
        }
    }
}

@Composable
private fun StepCounter(
    count: Int,
    target: Int,
    onStep: (Int) -> Unit
) {
    val safeTarget = target.coerceAtLeast(1)
    val clamped = count.coerceIn(0, safeTarget)
    val canDec = clamped > 0
    val canInc = clamped < safeTarget

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Panel2)
                .clickable(enabled = canDec) { onStep(-1) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "−",
                color = if (canDec) White else Color(0xFF4C4C4C),
                fontSize = 18.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Text(
            "$clamped / $safeTarget",
            color = if (clamped >= safeTarget) Red else Color.LightGray,
            fontFamily = FontFamily.Monospace,
            fontSize = 15.sp,
            fontWeight = if (clamped >= safeTarget) FontWeight.Medium else FontWeight.Normal
        )
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (canInc) White else Panel2)
                .clickable(enabled = canInc) { onStep(1) },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "+",
                color = if (canInc) Bg else Color(0xFF4C4C4C),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
    }
}

@Composable
private fun WeeklyCard(
    weekly: List<Rule>,
    goal: WeeklyGoal,
    goalCount: Int,
    onStepGoal: (Int) -> Unit,
    ruleCount: (Rule) -> Int,
    onStepRule: (Rule, Int) -> Unit,
    compact: Boolean = false
) {
    val target = goal.targetPerWeek.coerceAtLeast(1)
    val clamped = goalCount.coerceIn(0, target)
    Surface(
        color = Panel,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier.fillMaxWidth().padding(top = if (compact) 10.dp else 14.dp)
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = if (compact) 11.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(goal.name, color = White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                    Text(
                        if (clamped >= target) "Wochenziel erreicht (${goal.targetPerWeek}×/Woche)"
                        else "Flexibles Wochenziel · ${goal.targetPerWeek}× pro Woche",
                        color = Muted,
                        fontSize = 10.sp
                    )
                    Row(
                        Modifier.padding(top = if (compact) 6.dp else 9.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(target) { idx ->
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(if (idx < clamped) Red else Color(0xFF333333), CircleShape)
                            )
                        }
                    }
                }
                StepCounter(count = clamped, target = target, onStep = onStepGoal)
            }
            weekly.forEach { rule ->
                val rTarget = totalOf(rule)
                val rCount = ruleCount(rule).coerceIn(0, rTarget)
                HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(start = 18.dp))
                WeeklyRow(
                    rule = rule,
                    count = rCount,
                    target = rTarget,
                    compact = compact,
                    onStep = { delta -> onStepRule(rule, delta) }
                )
            }
        }
    }
}

@Composable
private fun WeeklyRow(
    rule: Rule,
    count: Int,
    target: Int,
    compact: Boolean = false,
    onStep: (Int) -> Unit
) {
    val done = count >= target
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = if (compact) 54.dp else 62.dp)
            .clickable { onStep(if (done) -target else 1) }
            .padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                rule.name,
                color = if (done) Muted else White,
                fontSize = 16.sp,
                lineHeight = 20.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            Text(
                if (done) "$target× pro Woche · erreicht" else "$target× pro Woche · flexibel",
                color = Muted,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
            if (target > 1) {
                Row(
                    Modifier.padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    repeat(target) { idx ->
                        Box(
                            Modifier
                                .size(5.dp)
                                .background(if (idx < count) Red else Color(0xFF333333), CircleShape)
                        )
                    }
                }
            }
        }
        StepCounter(count = count, target = target, onStep = onStep)
    }
}

@Composable
private fun HistoryScreen(
    ndot: FontFamily,
    date: LocalDate,
    rules: List<Rule>,
    completed: (Rule) -> Boolean,
    tick: Int
) {
    Screen(
        ndot = ndot,
        eyebrow = "JEDER TAG ZÄHLT.",
        title = "RÜCKBLICK",
        subtitle = date.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
    ) {
        Text("Der native Monatskalender wird im nächsten Datenbank-Schritt mit echten Tageswerten befüllt.", color = Muted)
        Spacer(Modifier.height(18.dp))
        Surface(color = Panel, shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth()) {
            Text(
                "Aktueller Tag: ${rules.count(completed)} von ${rules.size} erledigt",
                modifier = Modifier.padding(22.dp)
            )
        }
    }
}

@Composable
private fun StatsScreen(
    ndot: FontFamily,
    date: LocalDate,
    rules: List<Rule>,
    weeklyGoal: WeeklyGoal,
    goalCount: Int,
    completed: (Rule) -> Boolean,
    tick: Int
) {
    val done = rules.count(completed)
    Screen(
        ndot = ndot,
        eyebrow = "DRANBLEIBEN, NICHT PERFEKT SEIN.",
        title = "FORTSCHRITT",
        subtitle = "Aktueller Datenstand"
    ) {
        ProgressOrbit(
            progress = if (rules.isEmpty()) 1f else done.toFloat() / rules.size,
            done = done,
            total = rules.size,
            ndot = ndot,
            ringSize = 176.dp,
            boxHeight = 194.dp,
            strokeWidth = 6.dp,
            countFontSize = 44.sp,
            segmented = true
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("HEUTE", "$done/${rules.size}", Modifier.weight(1f), ndot)
            StatCard(weeklyGoal.name.uppercase(Locale.GERMAN), "$goalCount/${weeklyGoal.targetPerWeek}", Modifier.weight(1f), ndot)
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier, ndot: FontFamily) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(20.dp)) {
            SectionLabel(label)
            DisplayTitle(value, ndot, fontSize = 34.sp)
        }
    }
}

@Composable
private fun MiniBars(filled: Color, track: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.width(22.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(filled))
        Box(Modifier.width(22.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(track))
        Box(Modifier.width(22.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(track))
    }
}

@Composable
private fun MiniRing(filled: Color, track: Color) {
    Canvas(Modifier.size(34.dp)) {
        val s = 3.dp.toPx()
        val inset = s / 2f
        val sz = Size(size.width - s, size.height - s)
        val gap = 14f
        val sweep = (360f / 3f) - gap
        repeat(3) { i ->
            drawArc(
                color = if (i == 0) filled else track,
                startAngle = -90f + gap / 2f + i * 120f,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = sz,
                style = Stroke(s, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun ProgressStyleTile(
    label: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
    preview: @Composable (Color, Color) -> Unit
) {
    val fg = if (selected) Bg else White
    Surface(
        modifier = modifier.height(86.dp).clickable(onClick = onClick),
        color = if (selected) White else Panel2,
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            preview(if (selected) Bg else White, if (selected) Color(0xFFB5B4AF) else Color(0xFF4A4A4A))
            Spacer(Modifier.height(10.dp))
            Text(label, color = fg, fontSize = 13.sp, fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal)
        }
    }
}

@Composable
private fun SetupScreen(
    ndot: FontFamily,
    rules: List<Rule>,
    weeklyGoal: WeeklyGoal,
    goalCount: Int,
    useDotHeadings: Boolean,
    onHeadingStyleChange: (Boolean) -> Unit,
    orbit: Boolean,
    onProgressStyleChange: (Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit,
    onEditWeeklyGoal: () -> Unit
) {
    var draggedIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val rowStepPx = 140f

    Screen(ndot, "DEINE REGELN.", "EINSTELLUNGEN", "Einmal planen. Im Alltag nur erledigen.") {
        SectionLabel("DARSTELLUNG")
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("ÜBERSCHRIFTEN", fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text("Normaler Text bleibt in der Systemschrift.", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to "Klassisch", true to "N-Dot").forEach { (dot, label) ->
                        Surface(
                            modifier = Modifier.weight(1f).clickable { onHeadingStyleChange(dot) },
                            color = if (useDotHeadings == dot) White else Panel2,
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Text(
                                text = (if (useDotHeadings == dot) "✓  " else "") + label,
                                color = if (useDotHeadings == dot) Bg else White,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(vertical = 12.dp, horizontal = 10.dp)
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                DisplayTitle("NPLANNER", ndot, fontSize = 25.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("FORTSCHRITT HEUTE", fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text("Anzeige auf dem Heute-Bildschirm.", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressStyleTile("Balken", !orbit, Modifier.weight(1f), { onProgressStyleChange(false) }) { fg, track -> MiniBars(fg, track) }
                    ProgressStyleTile("Kreis", orbit, Modifier.weight(1f), { onProgressStyleChange(true) }) { fg, track -> MiniRing(fg, track) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("REGELMÄSSIGE AUFGABEN", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.7.sp)
            TextButton(onClick = onAdd) {
                Text("+ NEU", color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.7.sp)
            }
        }

        if (rules.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    "Noch keine Aufgaben angelegt. Tippe oben auf „+ NEU“.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            rules.forEachIndexed { index, rule ->
                val isDragging = draggedIndex == index
                Surface(
                    color = if (isDragging) Panel2 else Panel,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                            scaleX = if (isDragging) 1.02f else 1f
                            scaleY = if (isDragging) 1.02f else 1f
                        }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(rule.id, rules.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffsetY = 0f
                                        },
                                        onDragEnd = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                            val currentIdx = draggedIndex ?: index
                                            val shift = (dragOffsetY / rowStepPx).roundToInt()
                                            val targetIdx = (currentIdx + shift).coerceIn(0, rules.lastIndex)
                                            if (targetIdx != currentIdx) {
                                                onMove(currentIdx, targetIdx)
                                                draggedIndex = targetIdx
                                                dragOffsetY -= shift * rowStepPx
                                            }
                                        }
                                    )
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "⠿",
                                color = if (isDragging) White else Muted,
                                fontSize = 22.sp,
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, color = White)
                            Text(scheduleLabel(rule), color = Muted, fontSize = 10.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(rule.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✕", color = Red, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
        }

        SectionLabel("FLEXIBLES WOCHENZIEL")
        Surface(
            color = Panel,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onEditWeeklyGoal)
        ) {
            Row(Modifier.padding(19.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${weeklyGoal.name} · $goalCount/${weeklyGoal.targetPerWeek}", color = White)
                    Text(
                        "${weeklyGoal.targetPerWeek}× pro Woche · ${dayLabel(weeklyGoal.preferredDays)} bevorzugt",
                        color = Muted,
                        fontSize = 10.sp
                    )
                }
                Text("›", color = Muted, fontSize = 22.sp)
            }
        }

        Text(
            "Halte „⠿“ gedrückt, um Aufgaben zu verschieben. Tippe auf „✕“, um eine Aufgabe zu entfernen.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 20.dp)
        )
        HorizontalDivider(color = Color(0xFF242424), modifier = Modifier.padding(top = 8.dp))
        Text(
            "NPLANNER · VERSION 0.12",
            color = Color(0xFFB6B6B6),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 1.3.sp,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun WeeklyGoalDialog(
    ndot: FontFamily,
    initialGoal: WeeklyGoal,
    onDismiss: () -> Unit,
    onSave: (WeeklyGoal) -> Unit
) {
    var name by remember { mutableStateOf(initialGoal.name) }
    var target by remember { mutableIntStateOf(initialGoal.targetPerWeek.coerceIn(1, 14)) }
    var preferredDays by remember { mutableStateOf(initialGoal.preferredDays) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle("WOCHENZIEL", ndot, fontSize = 24.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Bezeichnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("ANZAHL PRO WOCHE")
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    ChoiceChip("−", target > 1, Modifier.width(54.dp)) { target = (target - 1).coerceAtLeast(1) }
                    DisplayTitle("$target×", ndot, fontSize = 28.sp)
                    ChoiceChip("+", target < 14, Modifier.width(54.dp)) { target = (target + 1).coerceAtMost(14) }
                }

                SectionLabel("BEVORZUGTE TAGE")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, d ->
                        val dayNum = i + 1
                        val on = dayNum in preferredDays
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (on) White else Panel2)
                                .clickable {
                                    preferredDays = if (on) preferredDays - dayNum else preferredDays + dayNum
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(d, color = if (on) Bg else White, fontSize = 12.sp)
                        }
                    }
                }
                Text(
                    "Jede Einheit zählt für das Wochenziel – unabhängig vom Wochentag.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 12.dp)
                )

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            val cleanName = name.trim()
                            val prefText = if (preferredDays.isEmpty()) "Alle Tage" else dayLabel(preferredDays)
                            onSave(
                                WeeklyGoal(
                                    name = cleanName,
                                    targetPerWeek = target,
                                    preferredDays = preferredDays,
                                    hint = "Bevorzugt $prefText. Andere Tage zählen genauso."
                                )
                            )
                        }
                    ) { Text("Speichern") }
                }
            }
        }
    }
}

@Composable
private fun ChoiceChip(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        color = if (selected) White else Panel2,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(Modifier.heightIn(min = 44.dp).padding(horizontal = 4.dp), contentAlignment = Alignment.Center) {
            Text(
                label,
                color = if (selected) Bg else White,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TimeBox(label: String, minutes: Int, ndot: FontFamily, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable(onClick = onClick), color = Panel2, shape = RoundedCornerShape(22.dp)) {
        Column(Modifier.padding(horizontal = 19.dp, vertical = 14.dp)) {
            Text(label, color = Muted, fontSize = 10.sp)
            DisplayTitle(fmt(minutes), ndot, Modifier.padding(top = 4.dp), 28.sp)
        }
    }
}

@Composable
private fun NewTaskDialog(ndot: FontFamily, onDismiss: () -> Unit, onCreate: (Rule) -> Unit) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(RuleKind.ALLDAY) }
    var timeMin by remember { mutableIntStateOf(8 * 60) }
    var every by remember { mutableIntStateOf(120) }
    var startMin by remember { mutableIntStateOf(8 * 60) }
    var endMin by remember { mutableIntStateOf(20 * 60) }
    var weeklyTarget by remember { mutableIntStateOf(3) }
    var days by remember { mutableStateOf((1..7).toSet()) }

    fun pick(initial: Int, set: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, h, m -> set(h * 60 + m) },
            initial / 60, initial % 60, true
        ).show()
    }

    val valid = name.isNotBlank() &&
        (kind != RuleKind.INTERVAL || endMin >= startMin) &&
        (kind == RuleKind.WEEKLY || days.isNotEmpty())
    val slotCount = Rule("", "", days, kind = RuleKind.INTERVAL, everyMin = every, fromMin = startMin, toMin = endMin).slots().size

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle("NEUE AUFGABE", ndot, fontSize = 24.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Bezeichnung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("ART")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        RuleKind.FIXED to "Uhrzeit",
                        RuleKind.INTERVAL to "Intervall",
                        RuleKind.ALLDAY to "Ganztägig",
                        RuleKind.WEEKLY to "Wochenziel"
                    ).forEach { (k, label) -> ChoiceChip(label, kind == k, Modifier.weight(1f)) { kind = k } }
                }

                when (kind) {
                    RuleKind.FIXED -> {
                        SectionLabel("UHRZEIT")
                        TimeBox("Uhrzeit", timeMin, ndot, Modifier.fillMaxWidth()) { pick(timeMin) { timeMin = it } }
                    }
                    RuleKind.INTERVAL -> {
                        SectionLabel("ALLE")
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30 to "30 Min.", 60 to "1 Std.", 120 to "2 Std.", 240 to "4 Std.").forEach { (m, l) ->
                                ChoiceChip(l, every == m, Modifier.weight(1f)) { every = m }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeBox("Von", startMin, ndot, Modifier.weight(1f)) { pick(startMin) { startMin = it } }
                            TimeBox("Bis", endMin, ndot, Modifier.weight(1f)) { pick(endMin) { endMin = it } }
                        }
                        Text(
                            if (endMin >= startMin) "$slotCount Zeitpunkte am Tag" else "„Bis“ muss nach „Von“ liegen.",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    RuleKind.ALLDAY -> {}
                    RuleKind.WEEKLY -> {
                        SectionLabel("ZIEL PRO WOCHE")
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ChoiceChip("−", weeklyTarget > 1, Modifier.width(54.dp)) {
                                weeklyTarget = (weeklyTarget - 1).coerceAtLeast(1)
                            }
                            DisplayTitle("${weeklyTarget}×", ndot, fontSize = 28.sp)
                            ChoiceChip("+", weeklyTarget < 14, Modifier.width(54.dp)) {
                                weeklyTarget = (weeklyTarget + 1).coerceAtMost(14)
                            }
                        }
                        Text(
                            "Flexibel über die Woche hochzählen (1× bis 14×). Am Montag startet die Woche wieder bei 0.",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                if (kind != RuleKind.WEEKLY) {
                    SectionLabel("WOCHENTAGE")
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So").forEachIndexed { i, d ->
                            val on = (i + 1) in days
                            Box(
                                Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                                    .background(if (on) White else Panel2)
                                    .clickable { days = if (on) days - (i + 1) else days + (i + 1) },
                                contentAlignment = Alignment.Center
                            ) { Text(d, color = if (on) Bg else White, fontSize = 12.sp) }
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(
                        enabled = valid,
                        onClick = {
                            onCreate(
                                Rule(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    days = if (kind == RuleKind.WEEKLY) (1..7).toSet() else days,
                                    kind = kind,
                                    timeMin = timeMin,
                                    everyMin = every,
                                    fromMin = startMin,
                                    toMin = endMin,
                                    weeklyTarget = if (kind == RuleKind.WEEKLY) weeklyTarget else 1
                                )
                            )
                        }
                    ) { Text("Anlegen") }
                }
            }
        }
    }
}

private enum class NavGlyph { TODAY, HISTORY, STATS, SETUP }

@Composable
private fun GlyphNav(page: Int, select: (Int) -> Unit, modifier: Modifier) {
    val glyphs = listOf(NavGlyph.TODAY, NavGlyph.HISTORY, NavGlyph.STATS, NavGlyph.SETUP)
    Surface(
        modifier.navigationBarsPadding().padding(horizontal = 18.dp, vertical = 14.dp).fillMaxWidth().height(64.dp),
        color = Color(0xE6252525),
        shape = RoundedCornerShape(34.dp),
        shadowElevation = 14.dp
    ) {
        Row(Modifier.padding(6.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            glyphs.forEachIndexed { i, glyph ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(if (page == i) White else Color.Transparent)
                        .clickable { select(i) },
                    contentAlignment = Alignment.Center
                ) { NavIcon(glyph, if (page == i) Bg else Color(0xFF969696)) }
            }
        }
    }
}

@Composable
private fun NavIcon(glyph: NavGlyph, color: Color) {
    Canvas(Modifier.size(27.dp)) {
        val w = 1.8.dp.toPx()
        val cx = size.width / 2f
        val cy = size.height / 2f
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(color, Offset(x1, y1), Offset(x2, y2), w, StrokeCap.Round)
        when (glyph) {
            NavGlyph.TODAY -> {
                drawCircle(color, size.width * .36f, Offset(cx, cy), style = Stroke(w))
                line(size.width * .30f, size.height * .52f, size.width * .44f, size.height * .66f)
                line(size.width * .44f, size.height * .66f, size.width * .72f, size.height * .35f)
            }
            NavGlyph.HISTORY -> {
                val l = size.width * .16f
                val t = size.height * .22f
                val r = size.width * .84f
                val b = size.height * .82f
                drawRoundRect(color, Offset(l, t), Size(r - l, b - t), androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(w))
                line(l, size.height * .40f, r, size.height * .40f)
                line(size.width * .34f, size.height * .15f, size.width * .34f, size.height * .29f)
                line(size.width * .66f, size.height * .15f, size.width * .66f, size.height * .29f)
                val xs = listOf(.31f, .50f, .69f)
                val ys = listOf(.54f, .69f)
                ys.forEach { y -> xs.forEach { x -> drawCircle(color, 1.35.dp.toPx(), Offset(size.width * x, size.height * y)) } }
            }
            NavGlyph.STATS -> {
                val base = size.height * .81f
                line(size.width * .18f, base, size.width * .82f, base)
                listOf(.30f to .58f, .50f to .39f, .70f to .22f).forEach { (x, top) ->
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(size.width * x - 2.dp.toPx(), size.height * top),
                        size = Size(4.dp.toPx(), base - size.height * top),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx())
                    )
                }
            }
            NavGlyph.SETUP -> {
                val ring = size.width * .285f
                val hub = size.width * .105f
                drawCircle(color, ring, Offset(cx, cy), style = Stroke(w))
                drawCircle(color, hub, Offset(cx, cy), style = Stroke(w))
                repeat(8) { i ->
                    val a = Math.toRadians((i * 45.0) - 90.0)
                    val ca = cos(a).toFloat()
                    val sa = sin(a).toFloat()
                    val tangentX = -sa
                    val tangentY = ca
                    val inner = size.width * .31f
                    val outer = size.width * .405f
                    val half = 1.8.dp.toPx()
                    val p1 = Offset(cx + ca * inner + tangentX * half, cy + sa * inner + tangentY * half)
                    val p2 = Offset(cx + ca * outer + tangentX * half, cy + sa * outer + tangentY * half)
                    val p3 = Offset(cx + ca * outer - tangentX * half, cy + sa * outer - tangentY * half)
                    val p4 = Offset(cx + ca * inner - tangentX * half, cy + sa * inner - tangentY * half)
                    val path = Path().apply {
                        moveTo(p1.x, p1.y)
                        lineTo(p2.x, p2.y)
                        lineTo(p3.x, p3.y)
                        lineTo(p4.x, p4.y)
                        close()
                    }
                    drawPath(path, color)
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(
    text: String,
    topPadding: Dp = 26.dp,
    bottomPadding: Dp = 12.dp
) {
    Text(
        text,
        color = Muted,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        letterSpacing = 1.7.sp,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
}

private fun dayLabel(days: Set<Int>): String =
    if (days.size == 7) "Täglich" else days.sorted().joinToString(" · ") { listOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")[it - 1] }
