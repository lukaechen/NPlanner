package at.lukaechen.nplanner

import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
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
import java.time.YearMonth
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

/** FIXED = feste Uhrzeit, INTERVAL = Zeitabstand mit Von/Bis, ALLDAY = ohne Zeit, WEEKLY = Flexibles Wochenziel (1..14x pro Woche). */
enum class RuleKind { FIXED, INTERVAL, ALLDAY, WEEKLY }

enum class HapticStyle { TASK_DONE, DAY_COMPLETE, UNDO, TICK }

data class Rule(
    val id: String,
    val name: String,
    val days: Set<Int>,
    val kind: RuleKind = RuleKind.ALLDAY,
    val timeMin: Int = 8 * 60,
    val everyMin: Int = 120,
    val fromMin: Int = 8 * 60,
    val toMin: Int = 20 * 60,
    val weeklyTarget: Int = 1,
    val createdDate: String = ""
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

private fun triggerHaptic(
    context: Context,
    haptic: HapticFeedback,
    style: HapticStyle
) {
    val vibratedViaSystem = runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null && vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                when (style) {
                    HapticStyle.DAY_COMPLETE -> {
                        val timings = longArrayOf(0L, 45L, 65L, 95L)
                        val amplitudes = intArrayOf(0, 175, 0, 255)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                    HapticStyle.TASK_DONE -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(26L, 100))
                    }
                    HapticStyle.UNDO -> {
                        val timings = longArrayOf(0L, 18L, 38L, 14L)
                        val amplitudes = intArrayOf(0, 80, 0, 50)
                        vibrator.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
                    }
                    HapticStyle.TICK -> {
                        vibrator.vibrate(VibrationEffect.createOneShot(12L, 55))
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                when (style) {
                    HapticStyle.DAY_COMPLETE -> vibrator.vibrate(longArrayOf(0L, 45L, 65L, 95L), -1)
                    HapticStyle.TASK_DONE -> vibrator.vibrate(26L)
                    HapticStyle.UNDO -> vibrator.vibrate(longArrayOf(0L, 18L, 38L, 14L), -1)
                    HapticStyle.TICK -> vibrator.vibrate(12L)
                }
            }
            true
        } else {
            false
        }
    }.getOrDefault(false)

    if (!vibratedViaSystem) {
        runCatching {
            haptic.performHapticFeedback(
                when (style) {
                    HapticStyle.DAY_COMPLETE -> HapticFeedbackType.LongPress
                    else -> HapticFeedbackType.TextHandleMove
                }
            )
        }
    }
}

class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("nplanner-v2", MODE_PRIVATE) }

    private fun loadRules(): List<Rule> = try {
        val raw = prefs.getString("rules", null)
        val todayStr = LocalDate.now().toString()
        val parsed = if (raw == null) {
            listOf(
                Rule(UUID.randomUUID().toString(), "Morgenroutine", (1..7).toSet(), createdDate = todayStr),
                Rule(UUID.randomUUID().toString(), "30 Minuten lernen", setOf(1, 3, 5), createdDate = todayStr),
                Rule(UUID.randomUUID().toString(), "Vitamine", (1..7).toSet(), createdDate = todayStr)
            )
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
                    weeklyTarget = o.optInt("weeklyTarget", 1).coerceIn(1, 14),
                    createdDate = o.optString("createdDate", "")
                )
            }
        }

        if (!prefs.getBoolean("migrated_sport_weekly_v1", false)) {
            val legacyGoalRaw = prefs.getString("weekly_goal", null)
            val legacyName = runCatching {
                legacyGoalRaw?.let { JSONObject(it).optString("name", "Sport").ifBlank { "Sport" } }
            }.getOrNull() ?: "Sport"
            val legacyTarget = runCatching {
                legacyGoalRaw?.let { JSONObject(it).optInt("targetPerWeek", 4).coerceIn(1, 14) }
            }.getOrNull() ?: 4

            val withSport = if (parsed.any { it.kind == RuleKind.WEEKLY && it.name.equals(legacyName, ignoreCase = true) }) {
                parsed
            } else {
                val sportRule = Rule(
                    id = "default-sport-weekly",
                    name = legacyName,
                    days = (1..7).toSet(),
                    kind = RuleKind.WEEKLY,
                    weeklyTarget = legacyTarget,
                    createdDate = todayStr
                )
                val wk = weekStartKey(LocalDate.now())
                val oldCnt = prefs.getInt("goal_cnt:$wk", 0).coerceIn(0, legacyTarget)
                prefs.edit()
                    .putInt("wk_cnt:$wk:${sportRule.id}", oldCnt)
                    .putBoolean("wk:$wk:${sportRule.id}", oldCnt >= legacyTarget)
                    .apply()
                parsed + sportRule
            }
            saveRules(withSport)
            prefs.edit().putBoolean("migrated_sport_weekly_v1", true).apply()
            withSport
        } else {
            if (raw == null) saveRules(parsed)
            parsed
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
                    .put("createdDate", r.createdDate)
            )
        }
        prefs.edit().putString("rules", array.toString()).apply()
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
        val context = LocalContext.current
        val haptic = LocalHapticFeedback.current
        var rules by remember { mutableStateOf(loadRules()) }
        var date by remember { mutableStateOf(LocalDate.now()) }
        var nowMin by remember { mutableIntStateOf(minuteOfDay()) }
        var tick by remember { mutableIntStateOf(0) }
        var addDialog by remember { mutableStateOf(false) }
        var dotHeadings by remember { mutableStateOf(prefs.getBoolean("use_ndot_headings", false)) }
        var orbit by remember { mutableStateOf(prefs.getString("progress_style", "bars") == "orbit") }
        var historyDots by remember { mutableStateOf(prefs.getString("history_style", "dots") == "dots") }
        var testMode by remember { mutableStateOf(prefs.getBoolean("test_mode_multi_weekly", false)) }

        LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); nowMin = minuteOfDay(); delay(30_000) } }

        fun weekKey(rule: Rule, d: LocalDate = date) = "wk:${weekStartKey(d)}:${rule.id}"
        fun weekCountKey(rule: Rule, d: LocalDate = date) = "wk_cnt:${weekStartKey(d)}:${rule.id}"
        fun weeklyDayKey(rule: Rule, d: LocalDate = date) = "wk_day:$d:${rule.id}"

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

        fun doneTodayForWeekly(rule: Rule, d: LocalDate = date): Boolean =
            prefs.getBoolean(weeklyDayKey(rule, d), false)

        val dailyRules = remember(rules, date) {
            rules.filter { it.kind != RuleKind.WEEKLY && date.dayOfWeek.value in it.days }.sortedBy { sortKey(it) }
        }
        val weeklyRules = remember(rules) { rules.filter { it.kind == RuleKind.WEEKLY } }

        // Tages-Status fuer ein beliebiges Datum d (aktuell + Historie)
        fun daySnapshot(d: LocalDate): DaySnapshot {
            val rulesForDay = rules.filter { r ->
                r.kind != RuleKind.WEEKLY &&
                    d.dayOfWeek.value in r.days &&
                    (r.createdDate.isBlank() || r.createdDate <= d.toString())
            }
            val weeklyDoneOnDay = weeklyRules.filter { doneTodayForWeekly(it, d) }
            val activeWeeklyOnDay = if (d == date) {
                weeklyRules.filter { doneTodayForWeekly(it, d) || doneCount(it, d) < totalOf(it) }
            } else {
                weeklyDoneOnDay
            }

            val doneDaily = rulesForDay.count { doneCount(it, d) >= totalOf(it) }
            val doneWeekly = if (d == date) {
                activeWeeklyOnDay.count { doneTodayForWeekly(it, d) }
            } else {
                weeklyDoneOnDay.size
            }
            val total = rulesForDay.size + activeWeeklyOnDay.size
            val done = doneDaily + doneWeekly
            return DaySnapshot(
                date = d,
                done = done,
                total = total,
                dailyRules = rulesForDay,
                weeklyDoneRules = weeklyDoneOnDay
            )
        }

        // Persistiere Tages-Snapshot in SharedPreferences, damit Vergangenes auch nach Regelaenderungen erhalten bleibt
        fun persistTodaySnapshot(d: LocalDate = date) {
            val snap = daySnapshot(d)
            val obj = JSONObject()
                .put("done", snap.done)
                .put("total", snap.total)
            prefs.edit().putString("snap:$d", obj.toString()).apply()
        }

        fun readDaySnapshot(d: LocalDate): DaySnapshot {
            val live = daySnapshot(d)
            if (d == date || d.isAfter(date)) return live
            val raw = prefs.getString("snap:$d", null)
            if (raw != null) {
                val parsed = runCatching {
                    val o = JSONObject(raw)
                    val sDone = o.optInt("done", live.done)
                    val sTotal = o.optInt("total", live.total)
                    live.copy(done = sDone, total = sTotal)
                }.getOrNull()
                if (parsed != null) return parsed
            }
            return live
        }

        fun fireFeedback(becameDone: Boolean, becameUndone: Boolean) {
            if (becameDone) {
                val snap = daySnapshot(date)
                triggerHaptic(
                    context = context,
                    haptic = haptic,
                    style = if (snap.total > 0 && snap.done >= snap.total) HapticStyle.DAY_COMPLETE else HapticStyle.TASK_DONE
                )
            } else if (becameUndone) {
                triggerHaptic(context = context, haptic = haptic, style = HapticStyle.UNDO)
            }
        }

        fun stepWeeklyRule(rule: Rule, delta: Int) {
            val target = totalOf(rule)
            val current = doneCount(rule)
            val checkedToday = doneTodayForWeekly(rule)
            val e = prefs.edit()

            if (testMode) {
                val next = (current + delta).coerceIn(0, target)
                if (next == current) return
                e.putInt(weekCountKey(rule), next)
                e.putBoolean(weekKey(rule), next >= target)
                if (delta > 0) {
                    e.putBoolean(weeklyDayKey(rule), true)
                } else if (next == 0) {
                    e.putBoolean(weeklyDayKey(rule), false)
                }
                e.apply()
                persistTodaySnapshot(date)
                tick++
                fireFeedback(becameDone = delta > 0, becameUndone = delta < 0)
            } else {
                if (delta > 0 && !checkedToday && current < target) {
                    val next = (current + 1).coerceIn(0, target)
                    e.putInt(weekCountKey(rule), next)
                    e.putBoolean(weekKey(rule), next >= target)
                    e.putBoolean(weeklyDayKey(rule), true)
                    e.apply()
                    persistTodaySnapshot(date)
                    tick++
                    fireFeedback(becameDone = true, becameUndone = false)
                } else if (delta < 0 && checkedToday && current > 0) {
                    val next = (current - 1).coerceIn(0, target)
                    e.putInt(weekCountKey(rule), next)
                    e.putBoolean(weekKey(rule), next >= target)
                    e.putBoolean(weeklyDayKey(rule), false)
                    e.apply()
                    persistTodaySnapshot(date)
                    tick++
                    fireFeedback(becameDone = false, becameUndone = true)
                }
            }
        }

        fun completed(rule: Rule, d: LocalDate = date): Boolean = when (rule.kind) {
            RuleKind.WEEKLY -> doneTodayForWeekly(rule, d) || doneCount(rule, d) >= totalOf(rule)
            else -> doneCount(rule, d) >= totalOf(rule)
        }

        fun toggle(rule: Rule) {
            val e = prefs.edit()
            var becameDone = false
            var becameUndone = false
            when (rule.kind) {
                RuleKind.INTERVAL -> {
                    val c = doneCount(rule)
                    val total = totalOf(rule)
                    val next = if (c >= total) c - 1 else c + 1
                    e.putInt("cnt:$date:${rule.id}", next)
                    becameDone = next > c
                    becameUndone = next < c
                }
                RuleKind.WEEKLY -> {
                    val checkedToday = doneTodayForWeekly(rule)
                    stepWeeklyRule(rule, if (checkedToday) -1 else 1)
                    return
                }
                else -> {
                    val wasDone = doneCount(rule) >= totalOf(rule)
                    e.putBoolean("done:$date:${rule.id}", !wasDone)
                    becameDone = !wasDone
                    becameUndone = wasDone
                }
            }
            e.apply()
            persistTodaySnapshot(date)
            tick++
            fireFeedback(becameDone = becameDone, becameUndone = becameUndone)
        }

        fun deleteRule(ruleId: String) {
            rules = rules.filterNot { it.id == ruleId }
            saveRules(rules)
            persistTodaySnapshot(date)
            triggerHaptic(context, haptic, HapticStyle.UNDO)
            tick++
        }

        val pagerState = rememberPagerState(pageCount = { 4 })
        val scope = rememberCoroutineScope()
        fun selectPage(target: Int) {
            if (target in 0..3 && target != pagerState.currentPage) {
                triggerHaptic(context, haptic, HapticStyle.TICK)
                scope.launch {
                    val distance = kotlin.math.abs(target - pagerState.currentPage)
                    if (distance > 1) {
                        val neighbor = if (target > pagerState.currentPage) target - 1 else target + 1
                        pagerState.scrollToPage(neighbor)
                    }
                    pagerState.animateScrollToPage(
                        page = target,
                        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
                    )
                }
            }
        }

        CompositionLocalProvider(LocalDotHeadings provides dotHeadings) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 3,
                    modifier = Modifier.fillMaxSize()
                ) { p ->
                    when (p) {
                        0 -> TodayScreen(
                            ndot = ndot,
                            date = date,
                            rules = dailyRules,
                            weekly = weeklyRules,
                            count = ::doneCount,
                            doneTodayWeekly = ::doneTodayForWeekly,
                            testMode = testMode,
                            onStepWeeklyRule = ::stepWeeklyRule,
                            onToggle = ::toggle,
                            nowMin = nowMin,
                            orbit = orbit,
                            tick = tick
                        )
                        1 -> HistoryScreen(
                            ndot = ndot,
                            today = date,
                            historyDots = historyDots,
                            snapshotFor = ::readDaySnapshot,
                            doneCountFor = ::doneCount,
                            onHapticTick = { triggerHaptic(context, haptic, HapticStyle.TICK) },
                            tick = tick
                        )
                        2 -> StatsScreen(
                            ndot = ndot,
                            date = date,
                            rules = dailyRules,
                            weekly = weeklyRules,
                            count = ::doneCount,
                            completed = ::completed,
                            tick = tick
                        )
                        else -> SetupScreen(
                            ndot = ndot,
                            rules = rules,
                            useDotHeadings = dotHeadings,
                            onHeadingStyleChange = { enabled ->
                                dotHeadings = enabled
                                prefs.edit().putBoolean("use_ndot_headings", enabled).apply()
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            },
                            orbit = orbit,
                            onProgressStyleChange = { useOrbit ->
                                orbit = useOrbit
                                prefs.edit().putString("progress_style", if (useOrbit) "orbit" else "bars").apply()
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            },
                            historyDots = historyDots,
                            onHistoryStyleChange = { useDots ->
                                historyDots = useDots
                                prefs.edit().putString("history_style", if (useDots) "dots" else "calendar").apply()
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            },
                            testMode = testMode,
                            onTestModeChange = { enabled ->
                                testMode = enabled
                                prefs.edit().putBoolean("test_mode_multi_weekly", enabled).apply()
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            },
                            onMove = { from, to ->
                                if (from in rules.indices && to in rules.indices && from != to) {
                                    rules = rules.toMutableList().also {
                                        val item = it.removeAt(from)
                                        it.add(to, item)
                                    }
                                    saveRules(rules)
                                    triggerHaptic(context, haptic, HapticStyle.TICK)
                                }
                            },
                            onDelete = ::deleteRule,
                            onAdd = {
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                                addDialog = true
                            }
                        )
                    }
                }
                GlyphNav(
                    pagerState = pagerState,
                    select = ::selectPage,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (addDialog) NewTaskDialog(
                ndot = ndot,
                onDismiss = { addDialog = false },
                onCreate = { rule ->
                    rules = rules + rule.copy(createdDate = date.toString())
                    saveRules(rules)
                    persistTodaySnapshot(date)
                    triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                    addDialog = false
                }
            )
        }
    }
}

private data class DaySnapshot(
    val date: LocalDate,
    val done: Int,
    val total: Int,
    val dailyRules: List<Rule>,
    val weeklyDoneRules: List<Rule>
) {
    val state: DayCompletionState
        get() = when {
            total <= 0 -> DayCompletionState.NONE
            done >= total -> DayCompletionState.ALL
            done > 0 -> DayCompletionState.PARTIAL
            else -> DayCompletionState.NONE
        }
}

private enum class DayCompletionState { ALL, PARTIAL, NONE }

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
    count: (Rule) -> Int,
    doneTodayWeekly: (Rule) -> Boolean,
    testMode: Boolean,
    onStepWeeklyRule: (Rule, Int) -> Unit,
    onToggle: (Rule) -> Unit,
    nowMin: Int,
    orbit: Boolean,
    tick: Int
) {
    val activeWeeklyForToday = remember(weekly, date, tick) {
        weekly.filter { doneTodayWeekly(it) || count(it) < totalOf(it) }
    }
    val dailyDone = remember(rules, date, tick) { rules.count { count(it) >= totalOf(it) } }
    val weeklyDoneToday = remember(activeWeeklyForToday, date, tick) { activeWeeklyForToday.count { doneTodayWeekly(it) } }

    val totalToday = rules.size + activeWeeklyForToday.size
    val doneToday = dailyDone + weeklyDoneToday
    val progress by animateFloatAsState(
        targetValue = if (totalToday == 0) 1f else doneToday.toFloat() / totalToday,
        label = "progress"
    )
    val open = totalToday - doneToday
    val next = remember(rules, date, tick, nowMin) {
        rules
            .filter { count(it) < totalOf(it) }
            .mapNotNull { r -> nextTime(r, count(r))?.let { t -> r to t } }
            .filter { it.second >= nowMin }
            .minByOrNull { it.second }
    }
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
                done = doneToday,
                total = totalToday,
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
            ProgressBars(doneToday, totalToday, ndot)
            Text(status, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
        }
        Spacer(Modifier.height(if (orbit) 4.dp else 8.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (doneToday == totalToday) {
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
            } else if (rules.isNotEmpty()) {
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
            if (weekly.isNotEmpty()) {
                WeeklyCard(
                    weekly = weekly,
                    tick = tick,
                    ruleCount = count,
                    doneTodayWeekly = doneTodayWeekly,
                    testMode = testMode,
                    onStepRule = onStepWeeklyRule,
                    compact = orbit
                )
            }
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
            repeat(total.coerceAtLeast(1)) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(if (total == 0 || i < done) White else Color(0xFF2A2A2A))
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
    canDec: Boolean,
    canInc: Boolean,
    onStep: (Int) -> Unit
) {
    val safeTarget = target.coerceAtLeast(1)
    val clamped = count.coerceIn(0, safeTarget)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            Modifier
                .size(34.dp)
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
            fontSize = 16.sp,
            fontWeight = if (clamped >= safeTarget) FontWeight.Medium else FontWeight.Normal
        )
        Box(
            Modifier
                .size(34.dp)
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
    tick: Int,
    ruleCount: (Rule) -> Int,
    doneTodayWeekly: (Rule) -> Boolean,
    testMode: Boolean,
    onStepRule: (Rule, Int) -> Unit,
    compact: Boolean = false
) {
    key(tick) {
        Surface(
            color = Panel,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier.fillMaxWidth().padding(top = if (compact) 10.dp else 14.dp)
        ) {
            Column {
                weekly.forEachIndexed { index, rule ->
                    val target = totalOf(rule)
                    val count = ruleCount(rule).coerceIn(0, target)
                    val checkedToday = doneTodayWeekly(rule)
                    val canDec = if (testMode) count > 0 else (checkedToday && count > 0)
                    val canInc = if (testMode) count < target else (!checkedToday && count < target)
                    WeeklyRow(
                        rule = rule,
                        count = count,
                        target = target,
                        checkedToday = checkedToday,
                        testMode = testMode,
                        canDec = canDec,
                        canInc = canInc,
                        compact = compact,
                        onStep = { delta -> onStepRule(rule, delta) }
                    )
                    if (index < weekly.lastIndex) {
                        HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(start = 19.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeeklyRow(
    rule: Rule,
    count: Int,
    target: Int,
    checkedToday: Boolean,
    testMode: Boolean,
    canDec: Boolean,
    canInc: Boolean,
    compact: Boolean = false,
    onStep: (Int) -> Unit
) {
    val weekDone = count >= target
    val statusText = when {
        weekDone -> "Wochenziel erreicht ($target×/Woche)"
        checkedToday && !testMode -> "Heute erledigt · $target× pro Woche"
        testMode -> "Test-Modus aktiv · $target× pro Woche"
        else -> "Flexibles Wochenziel · $target× pro Woche"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 19.dp, vertical = if (compact) 11.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                rule.name,
                color = if (weekDone || (checkedToday && !testMode)) Muted else White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                statusText,
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
                            .background(if (idx < count) Red else Color(0xFF333333), CircleShape)
                    )
                }
            }
        }
        StepCounter(
            count = count,
            target = target,
            canDec = canDec,
            canInc = canInc,
            onStep = onStep
        )
    }
}

// ---------- Rueckblick (Monat & Jahr, Punkte & Kalender) ----------

@Composable
private fun HistoryScreen(
    ndot: FontFamily,
    today: LocalDate,
    historyDots: Boolean,
    snapshotFor: (LocalDate) -> DaySnapshot,
    doneCountFor: (Rule, LocalDate) -> Int,
    onHapticTick: () -> Unit,
    tick: Int
) {
    var yearScope by remember { mutableStateOf(false) }
    var currentMonth by remember { mutableStateOf(YearMonth.from(today)) }
    var currentYear by remember { mutableIntStateOf(today.year) }
    var selectedDate by remember { mutableStateOf(today) }

    val subtitle = if (yearScope) {
        "Jahresübersicht · $currentYear"
    } else {
        currentMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN))
    }

    Screen(
        ndot = ndot,
        eyebrow = "JEDER TAG ZÄHLT.",
        title = "RÜCKBLICK",
        subtitle = subtitle
    ) {
        // Zeitraum-Switch: Monat vs. Jahr + Blaetter-Steuerung
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip("Monat", !yearScope, Modifier.width(82.dp)) {
                    if (yearScope) {
                        yearScope = false
                        onHapticTick()
                    }
                }
                ChoiceChip("Jahr", yearScope, Modifier.width(82.dp)) {
                    if (!yearScope) {
                        yearScope = true
                        currentYear = currentMonth.year
                        onHapticTick()
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Panel)
                        .clickable {
                            if (yearScope) currentYear -= 1 else currentMonth = currentMonth.minusMonths(1)
                            onHapticTick()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("‹", color = White, fontSize = 18.sp) }

                val canGoNext = if (yearScope) currentYear < today.year else currentMonth < YearMonth.from(today)
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (canGoNext) Panel else Color(0xFF101010))
                        .clickable(enabled = canGoNext) {
                            if (yearScope) currentYear += 1 else currentMonth = currentMonth.plusMonths(1)
                            onHapticTick()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("›", color = if (canGoNext) White else Color(0xFF3A3A3A), fontSize = 18.sp) }
            }
        }

        Spacer(Modifier.height(14.dp))

        if (!yearScope) {
            val monthSnapshots = remember(currentMonth, tick) {
                (1..currentMonth.lengthOfMonth()).map { day ->
                    snapshotFor(currentMonth.atDay(day))
                }
            }
            val perfectDays = monthSnapshots.count { !it.date.isAfter(today) && it.state == DayCompletionState.ALL }
            val activeDays = monthSnapshots.count { !it.date.isAfter(today) && it.state != DayCompletionState.NONE }

            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            currentMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.GERMAN)).uppercase(Locale.GERMAN),
                            color = White,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            letterSpacing = 1.6.sp
                        )
                        Text(
                            "$perfectDays perfekt · $activeDays aktiv",
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Wochentag-Koepfe Mo..So
                    Row(Modifier.fillMaxWidth()) {
                        listOf("MO", "DI", "MI", "DO", "FR", "SA", "SO").forEach { wd ->
                            Text(
                                wd,
                                color = Muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    val firstWeekdayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
                    val totalCells = firstWeekdayOffset + currentMonth.lengthOfMonth()
                    val rows = (totalCells + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(rows) { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(7) { col ->
                                    val cellIdx = row * 7 + col
                                    val dayNum = cellIdx - firstWeekdayOffset + 1
                                    if (dayNum in 1..currentMonth.lengthOfMonth()) {
                                        val cellDate = currentMonth.atDay(dayNum)
                                        val snap = monthSnapshots[dayNum - 1]
                                        val isFuture = cellDate.isAfter(today)
                                        val isSelected = cellDate == selectedDate
                                        val isToday = cellDate == today

                                        MonthDayCell(
                                            dayNum = dayNum,
                                            state = if (isFuture) null else snap.state,
                                            useDots = historyDots,
                                            isToday = isToday,
                                            isSelected = isSelected,
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable(enabled = !isFuture) {
                                                    selectedDate = cellDate
                                                    onHapticTick()
                                                }
                                        )
                                    } else {
                                        Spacer(Modifier.weight(1f).height(38.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HistoryLegend()
                }
            }

            // Detail-Karte fuer den gewaehlten Tag
            val selectedSnap = remember(selectedDate, tick) { snapshotFor(selectedDate) }
            Spacer(Modifier.height(14.dp))
            DayDetailCard(
                snap = selectedSnap,
                isToday = selectedDate == today,
                doneCountFor = doneCountFor,
                ndot = ndot
            )
        } else {
            // Jahresrueckblick (12 Monate kompakt im Nothing-Stil)
            val yearStats = remember(currentYear, tick) {
                var allCount = 0
                var partialCount = 0
                var noneCount = 0
                val months = (1..12).map { m ->
                    val ym = YearMonth.of(currentYear, m)
                    val snaps = (1..ym.lengthOfMonth()).map { d -> snapshotFor(ym.atDay(d)) }
                    snaps.forEach { s ->
                        if (!s.date.isAfter(today)) {
                            when (s.state) {
                                DayCompletionState.ALL -> allCount++
                                DayCompletionState.PARTIAL -> partialCount++
                                DayCompletionState.NONE -> noneCount++
                            }
                        }
                    }
                    ym to snaps
                }
                Triple(months, Triple(allCount, partialCount, noneCount), currentYear)
            }

            val (monthsList, counts, _) = yearStats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniYearStat("PERFEKT", "${counts.first}", Red, Modifier.weight(1f), ndot)
                MiniYearStat("TEILWEISE", "${counts.second}", White, Modifier.weight(1f), ndot)
                MiniYearStat("OFFEN", "${counts.third}", Muted, Modifier.weight(1f), ndot)
            }

            Spacer(Modifier.height(12.dp))

            // 2-spaltiges Grid mit allen 12 Monaten
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                monthsList.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        pair.forEach { (ym, snaps) ->
                            YearMonthCard(
                                ym = ym,
                                snaps = snaps,
                                today = today,
                                useDots = historyDots,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        currentMonth = ym
                                        val targetDay = today.dayOfMonth.coerceAtMost(ym.lengthOfMonth())
                                        val candidate = ym.atDay(targetDay)
                                        selectedDate = if (candidate.isAfter(today)) today else candidate
                                        yearScope = false
                                        onHapticTick()
                                    }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HistoryLegend()
        }
    }
}

@Composable
private fun MonthDayCell(
    dayNum: Int,
    state: DayCompletionState?,
    useDots: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    if (useDots) {
        // Minimalistische Punkt-Darstellung im Nothing-Stil
        val dotColor = when (state) {
            DayCompletionState.ALL -> Red
            DayCompletionState.PARTIAL -> White
            DayCompletionState.NONE -> Color(0xFF2D2D2D)
            null -> Color(0xFF151515)
        }
        val dotSize = when (state) {
            DayCompletionState.ALL -> 12.dp
            DayCompletionState.PARTIAL -> 10.dp
            DayCompletionState.NONE -> 7.dp
            null -> 5.dp
        }
        Box(
            modifier = modifier
                .height(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (isSelected) Panel2 else Color.Transparent)
                .then(
                    if (isToday) Modifier.border(1.dp, Color(0xFF4A4A4A), RoundedCornerShape(12.dp))
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Box(
                    Modifier
                        .size(dotSize)
                        .background(dotColor, CircleShape)
                )
                Text(
                    "$dayNum",
                    color = if (isSelected || isToday) White else Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
            }
        }
    } else {
        // Klassische Kalender-Darstellung mit klaren Status-Kacheln
        val bgColor = when (state) {
            DayCompletionState.ALL -> Red
            DayCompletionState.PARTIAL -> Panel2
            DayCompletionState.NONE -> Color(0xFF121212)
            null -> Color.Transparent
        }
        val textColor = when (state) {
            DayCompletionState.ALL -> White
            DayCompletionState.PARTIAL -> White
            DayCompletionState.NONE -> Muted
            null -> Color(0xFF333333)
        }
        Box(
            modifier = modifier
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(bgColor)
                .then(
                    when {
                        isSelected -> Modifier.border(1.5.dp, White, RoundedCornerShape(11.dp))
                        isToday -> Modifier.border(1.dp, Color(0xFF555555), RoundedCornerShape(11.dp))
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "$dayNum",
                    color = textColor,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = if (state == DayCompletionState.ALL || isToday) FontWeight.Medium else FontWeight.Normal,
                    style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
                )
                if (state == DayCompletionState.PARTIAL) {
                    Spacer(Modifier.height(2.dp))
                    Box(Modifier.size(4.dp).background(Red, CircleShape))
                }
            }
        }
    }
}

@Composable
private fun YearMonthCard(
    ym: YearMonth,
    snaps: List<DaySnapshot>,
    today: LocalDate,
    useDots: Boolean,
    modifier: Modifier = Modifier
) {
    val monthName = ym.atDay(1).format(DateTimeFormatter.ofPattern("MMM", Locale.GERMAN)).uppercase(Locale.GERMAN)
    val perfect = snaps.count { !it.date.isAfter(today) && it.state == DayCompletionState.ALL }
    val offset = ym.atDay(1).dayOfWeek.value - 1
    val totalCells = offset + ym.lengthOfMonth()
    val rows = (totalCells + 6) / 7

    Surface(
        color = Panel,
        shape = RoundedCornerShape(20.dp),
        modifier = modifier
    ) {
        Column(Modifier.padding(13.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    monthName,
                    color = White,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                )
                Text(
                    "$perfect/${ym.lengthOfMonth()}",
                    color = Muted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        repeat(7) { c ->
                            val idx = r * 7 + c
                            val d = idx - offset + 1
                            if (d in 1..ym.lengthOfMonth()) {
                                val s = snaps[d - 1]
                                val isFuture = s.date.isAfter(today)
                                val cellColor = when {
                                    isFuture -> Color(0xFF141414)
                                    s.state == DayCompletionState.ALL -> Red
                                    s.state == DayCompletionState.PARTIAL -> White
                                    else -> Color(0xFF2B2B2B)
                                }
                                Box(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        Modifier
                                            .fillMaxSize(if (useDots) 0.72f else 0.88f)
                                            .clip(if (useDots) CircleShape else RoundedCornerShape(2.5.dp))
                                            .background(cellColor)
                                    )
                                }
                            } else {
                                Spacer(Modifier.weight(1f).aspectRatio(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniYearStat(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier,
    ndot: FontFamily
) {
    Surface(modifier, color = Panel, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(5.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(label, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            Spacer(Modifier.height(6.dp))
            DisplayTitle(value, ndot, fontSize = 24.sp)
        }
    }
}

@Composable
private fun HistoryLegend() {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(Red, "Alle Ziele")
        LegendItem(White, "Teilweise")
        LegendItem(Color(0xFF2D2D2D), "Kein Ziel")
    }
}

@Composable
private fun LegendItem(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Text(text, color = Muted, fontSize = 10.sp)
    }
}

@Composable
private fun DayDetailCard(
    snap: DaySnapshot,
    isToday: Boolean,
    doneCountFor: (Rule, LocalDate) -> Int,
    ndot: FontFamily
) {
    val label = if (isToday) {
        "HEUTE · ${snap.date.format(DateTimeFormatter.ofPattern("d. MMMM", Locale.GERMAN)).uppercase(Locale.GERMAN)}"
    } else {
        snap.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)).uppercase(Locale.GERMAN)
    }
    val statusBadge = when (snap.state) {
        DayCompletionState.ALL -> "ALLE ERREICHT"
        DayCompletionState.PARTIAL -> "TEILWEISE"
        DayCompletionState.NONE -> "KEIN ZIEL"
    }
    val badgeColor = when (snap.state) {
        DayCompletionState.ALL -> Red
        DayCompletionState.PARTIAL -> White
        DayCompletionState.NONE -> Muted
    }

    Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(label, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(statusBadge, color = badgeColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.4.sp)
                }
                DisplayCount(snap.done, snap.total, ndot, 28.sp)
            }

            if (snap.dailyRules.isNotEmpty() || snap.weeklyDoneRules.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(vertical = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snap.dailyRules.forEach { r ->
                        val isDone = doneCountFor(r, snap.date) >= totalOf(r)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(if (isDone) Red else Color(0xFF333333), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                r.name,
                                color = if (isDone) White else Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (isDone) "✓" else "–",
                                color = if (isDone) Red else Muted,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                    }
                    snap.weeklyDoneRules.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(Red, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                "${w.name} (Wochenziel)",
                                color = White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text("✓", color = Red, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

// ---------- Statistik ----------

@Composable
private fun StatsScreen(
    ndot: FontFamily,
    date: LocalDate,
    rules: List<Rule>,
    weekly: List<Rule>,
    count: (Rule) -> Int,
    completed: (Rule) -> Boolean,
    tick: Int
) {
    val done = remember(rules, date, tick) { rules.count(completed) }
    val weeklyDoneCount = remember(weekly, date, tick) { weekly.count { count(it) >= totalOf(it) } }
    val primaryWeekly = weekly.firstOrNull()
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
            if (weekly.size == 1 && primaryWeekly != null) {
                StatCard(
                    primaryWeekly.name.uppercase(Locale.GERMAN),
                    "${count(primaryWeekly)}/${totalOf(primaryWeekly)}",
                    Modifier.weight(1f),
                    ndot
                )
            } else {
                StatCard(
                    "WOCHENZIELE",
                    "$weeklyDoneCount/${weekly.size}",
                    Modifier.weight(1f),
                    ndot
                )
            }
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

// ---------- Mini-Vorschauen & Einstellungen ----------

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
private fun MiniHistoryDots(filled: Color, track: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(2) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                repeat(4) { c ->
                    val active = (r == 0 && c < 3) || (r == 1 && c == 1)
                    Box(
                        Modifier
                            .size(7.dp)
                            .background(if (active) filled else track, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniHistoryCalendar(filled: Color, track: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        repeat(2) { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                repeat(4) { c ->
                    val active = (r == 0 && c < 3) || (r == 1 && c == 1)
                    Box(
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(2.5.dp))
                            .background(if (active) filled else track)
                    )
                }
            }
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
    useDotHeadings: Boolean,
    onHeadingStyleChange: (Boolean) -> Unit,
    orbit: Boolean,
    onProgressStyleChange: (Boolean) -> Unit,
    historyDots: Boolean,
    onHistoryStyleChange: (Boolean) -> Unit,
    testMode: Boolean,
    onTestModeChange: (Boolean) -> Unit,
    onMove: (Int, Int) -> Unit,
    onDelete: (String) -> Unit,
    onAdd: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    var draggedId by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableFloatStateOf(with(density) { 68.dp.toPx() }) }
    val itemSpacingPx = with(density) { 10.dp.toPx() }

    val draggedFromIndex = remember(draggedId, rules) {
        draggedId?.let { id -> rules.indexOfFirst { it.id == id }.takeIf { it >= 0 } }
    }
    val targetDropIndex = remember(draggedFromIndex, dragOffsetY, itemHeightPx, rules.size) {
        if (draggedFromIndex == null) null
        else {
            val step = (itemHeightPx + itemSpacingPx).coerceAtLeast(1f)
            val deltaSlots = (dragOffsetY / step).roundToInt()
            (draggedFromIndex + deltaSlots).coerceIn(0, rules.lastIndex)
        }
    }

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
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text("RÜCKBLICK-DARSTELLUNG", fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text("Minimalistische Punkte oder Kalender-Ansicht.", fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressStyleTile("Punkte", historyDots, Modifier.weight(1f), { onHistoryStyleChange(true) }) { fg, track -> MiniHistoryDots(fg, track) }
                    ProgressStyleTile("Kalender", !historyDots, Modifier.weight(1f), { onHistoryStyleChange(false) }) { fg, track -> MiniHistoryCalendar(fg, track) }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("AUFGABEN & WOCHENZIELE", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, letterSpacing = 1.7.sp)
            TextButton(onClick = onAdd) {
                Text("+ NEU", color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.7.sp)
            }
        }

        if (rules.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    "Noch keine Aufgaben oder Wochenziele angelegt. Tippe oben auf „+ NEU“.",
                    color = Muted,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(18.dp)
                )
            }
        } else {
            rules.forEachIndexed { index, rule ->
                val isDragging = draggedId == rule.id
                val stepPx = itemHeightPx + itemSpacingPx

                val targetShiftPx = when {
                    isDragging || draggedFromIndex == null || targetDropIndex == null -> 0f
                    draggedFromIndex < targetDropIndex && index in (draggedFromIndex + 1)..targetDropIndex -> -stepPx
                    draggedFromIndex > targetDropIndex && index in targetDropIndex until draggedFromIndex -> stepPx
                    else -> 0f
                }
                val animatedShiftY by animateFloatAsState(
                    targetValue = targetShiftPx,
                    animationSpec = tween(durationMillis = 160, easing = FastOutSlowInEasing),
                    label = "reorder_shift"
                )

                Surface(
                    color = if (isDragging) Panel2 else Panel,
                    shape = RoundedCornerShape(22.dp),
                    shadowElevation = if (isDragging) 12.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp)
                        .onSizeChanged { size ->
                            if (size.height > 0) itemHeightPx = size.height.toFloat()
                        }
                        .zIndex(if (isDragging) 10f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else animatedShiftY
                            scaleX = if (isDragging) 1.025f else 1f
                            scaleY = if (isDragging) 1.025f else 1f
                        }
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .pointerInput(rule.id) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedId = rule.id
                                            dragOffsetY = 0f
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        },
                                        onDragEnd = {
                                            val fromIdx = rules.indexOfFirst { it.id == rule.id }
                                            if (fromIdx >= 0) {
                                                val step = (itemHeightPx + itemSpacingPx).coerceAtLeast(1f)
                                                val toIdx = (fromIdx + (dragOffsetY / step).roundToInt()).coerceIn(0, rules.lastIndex)
                                                if (toIdx != fromIdx) {
                                                    onMove(fromIdx, toIdx)
                                                }
                                            }
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedId = null
                                            dragOffsetY = 0f
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
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

        SectionLabel("ENTWICKLER & TEST")
        Surface(
            color = Panel,
            shape = RoundedCornerShape(22.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onTestModeChange(!testMode) }
        ) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text("TEST-MODUS WOCHENZIELE", fontSize = 15.sp, color = White, fontWeight = FontWeight.Medium)
                    Text(
                        if (testMode) "Aktiv: Wochenziele lassen sich beliebig oft am selben Tag hoch- und runterzählen."
                        else "Aus: Wochenziele sind regulär maximal 1× pro Tag abhakbar.",
                        fontSize = 11.sp,
                        color = Muted
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = testMode,
                    onCheckedChange = onTestModeChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Bg,
                        checkedTrackColor = Red,
                        uncheckedThumbColor = White,
                        uncheckedTrackColor = Panel2
                    )
                )
            }
        }

        Text(
            "Halte „⠿“ gedrückt, ziehe den Eintrag an die gewünschte Position und lasse los.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
        )
        HorizontalDivider(color = Color(0xFF242424), modifier = Modifier.padding(top = 6.dp))
        Text(
            "NPLANNER · VERSION 0.16",
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
    val haptic = LocalHapticFeedback.current
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
                    ).forEach { (k, label) ->
                        ChoiceChip(label, kind == k, Modifier.weight(1f)) {
                            kind = k
                            triggerHaptic(context, haptic, HapticStyle.TICK)
                        }
                    }
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
                                ChoiceChip(l, every == m, Modifier.weight(1f)) {
                                    every = m
                                    triggerHaptic(context, haptic, HapticStyle.TICK)
                                }
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
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            }
                            DisplayTitle("${weeklyTarget}×", ndot, fontSize = 28.sp)
                            ChoiceChip("+", weeklyTarget < 14, Modifier.width(54.dp)) {
                                weeklyTarget = (weeklyTarget + 1).coerceAtMost(14)
                                triggerHaptic(context, haptic, HapticStyle.TICK)
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
                                    .clickable {
                                        days = if (on) days - (i + 1) else days + (i + 1)
                                        triggerHaptic(context, haptic, HapticStyle.TICK)
                                    },
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

// ---------- Animierte Navigationsleiste ----------

private enum class NavGlyph { TODAY, HISTORY, STATS, SETUP }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphNav(
    pagerState: androidx.compose.foundation.pager.PagerState,
    select: (Int) -> Unit,
    modifier: Modifier
) {
    val glyphs = listOf(NavGlyph.TODAY, NavGlyph.HISTORY, NavGlyph.STATS, NavGlyph.SETUP)
    val pageFraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 3f)

    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .fillMaxWidth()
            .height(64.dp),
        color = Color(0xE6252525),
        shape = RoundedCornerShape(34.dp),
        shadowElevation = 14.dp
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(6.dp)
        ) {
            val spacing = 4.dp
            val pillWidth = (maxWidth - spacing * 3) / 4f
            val pillOffset = (pillWidth + spacing) * pageFraction

            // Gleitende aktive Pille, die sowohl dem Finger beim Wischen als auch dem Tippen folgt
            Box(
                modifier = Modifier
                    .offset(x = pillOffset)
                    .width(pillWidth)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(White)
            )

            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(spacing)
            ) {
                glyphs.forEachIndexed { i, glyph ->
                    val interactionSource = remember { MutableInteractionSource() }
                    val distance = kotlin.math.abs(pageFraction - i).coerceIn(0f, 1f)
                    val activeWeight = 1f - distance
                    val iconScale by animateFloatAsState(
                        targetValue = if (pagerState.currentPage == i) 1.06f else 0.96f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                        label = "nav_icon_scale"
                    )
                    val iconColor = androidx.compose.ui.graphics.lerp(
                        Color(0xFF969696),
                        Bg,
                        activeWeight
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { select(i) },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier.graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            }
                        ) {
                            NavIcon(glyph, iconColor)
                        }
                    }
                }
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
