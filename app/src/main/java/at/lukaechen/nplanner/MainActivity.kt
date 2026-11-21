package at.lukaechen.nplanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.Year
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

/** Sport-Uebungstypen: WEIGHT = Gewicht x Wdh., BODYWEIGHT = Eigengewicht x Wdh., TIMED = Zeit / X Wdh. in Y Sek. */
enum class ExerciseKind { WEIGHT, BODYWEIGHT, TIMED }

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
    val createdDate: String = "",
    val optional: Boolean = false
)

data class Exercise(
    val id: String,
    val name: String,
    val muscle: String = "Ganzkörper",
    val kind: ExerciseKind = ExerciseKind.WEIGHT,
    val description: String = "",
    val steps: List<String> = emptyList(),
    val mediaPath: String = "",
    val mediaIsVideo: Boolean = false,
    val targetWeightKg: Float = 40f,
    val targetReps: Int = 10,
    val targetSeconds: Int = 45
)

data class ExerciseLog(
    val id: String,
    val exerciseId: String,
    val date: String,
    val weightKg: Float = 0f,
    val reps: Int = 0,
    val seconds: Int = 0
)

private val LocalDotHeadings = compositionLocalOf { false }

// ---------- Zeit- und Regel-Helfer ----------

private fun fmt(min: Int): String = String.format(Locale.GERMAN, "%02d:%02d", min / 60, min % 60)

private fun fmtKg(kg: Float): String =
    if (kg % 1f == 0f) "${kg.toInt()} kg" else String.format(Locale.GERMAN, "%.1f kg", kg)

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

private fun scheduleLabel(r: Rule): String {
    val base = when (r.kind) {
        RuleKind.FIXED -> "${fmt(r.timeMin)} · ${dayLabel(r.days)}"
        RuleKind.INTERVAL -> "Alle ${everyLabel(r.everyMin)} · ${fmt(r.fromMin)}–${fmt(r.toMin)} · ${dayLabel(r.days)}"
        RuleKind.WEEKLY -> "${r.weeklyTarget.coerceIn(1, 14)}× pro Woche · flexibel"
        RuleKind.ALLDAY -> dayLabel(r.days)
    }
    return if (r.optional) "$base · Optional" else base
}

private fun exerciseKindLabel(kind: ExerciseKind): String = when (kind) {
    ExerciseKind.WEIGHT -> "Gewicht"
    ExerciseKind.BODYWEIGHT -> "Körpergewicht"
    ExerciseKind.TIMED -> "Zeit / Intervall"
}

private fun exerciseSummary(ex: Exercise): String = when (ex.kind) {
    ExerciseKind.WEIGHT -> "${ex.muscle} · Gewicht · Ziel ${fmtKg(ex.targetWeightKg)} × ${ex.targetReps}"
    ExerciseKind.BODYWEIGHT -> "${ex.muscle} · Körpergewicht · Ziel ${ex.targetReps} Wdh."
    ExerciseKind.TIMED -> if (ex.targetReps > 1) {
        "${ex.muscle} · Zeit · Ziel ${ex.targetReps} Wdh. in ${ex.targetSeconds} Sek."
    } else {
        "${ex.muscle} · Zeit · Ziel ${ex.targetSeconds} Sek."
    }
}

private fun logSummary(ex: Exercise, log: ExerciseLog): String = when (ex.kind) {
    ExerciseKind.WEIGHT -> "${fmtKg(log.weightKg)} × ${log.reps} Wdh."
    ExerciseKind.BODYWEIGHT -> "${log.reps} Wdh."
    ExerciseKind.TIMED -> if (log.reps > 1) "${log.reps} Wdh. in ${log.seconds} Sek." else "${log.seconds} Sek."
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

// ---------- NPlanner Exercise JSON Format (NPEF-1) & KI-Prompt ----------

private const val NPEF_FORMAT_ID = "nplanner.exercise.v1"

private fun buildAiExercisePrompt(): String = """
Erstelle für meine Fitness-App NPlanner eine oder mehrere Übungen im unverwechselbaren JSON-Format "$NPEF_FORMAT_ID".
Antworte ausschließlich mit einem gültigen JSON-Codeblock (ohne zusätzlichen Text davor oder danach), damit ich ihn direkt in die App importieren kann.

Regeln für "kind":
- "WEIGHT": Übung mit Zusatzgewicht (z. B. Bankdrücken, Kniebeugen mit Langhantel) -> setze "targetWeightKg" (Zahl) und "targetReps" (Ganzzahl), "targetSeconds": 0.
- "BODYWEIGHT": Übung mit eigenem Körpergewicht (z. B. Liegestütze, Klimmzüge) -> setze "targetReps" (Ganzzahl), "targetWeightKg": 0, "targetSeconds": 0.
- "TIMED": Zeitbasierte Übung oder Challenge (z. B. Plank 60 Sek. oder 30 Liegestütze in 45 Sek.) -> setze "targetSeconds" (Ganzzahl) und optional "targetReps" (1 bei reinem Halten, oder Ziel-Wiederholungen in dieser Zeit), "targetWeightKg": 0.

Schema:
```json
{
  "format": "$NPEF_FORMAT_ID",
  "exercises": [
    {
      "name": "Name der Übung",
      "muscle": "Hauptmuskelgruppe (z. B. Brust, Rücken, Beine, Schultern, Core)",
      "kind": "WEIGHT",
      "description": "Kurze, präzise Zusammenfassung der Übung und Zielsetzung.",
      "steps": [
        "Schritt 1: Ausgangsposition einnehmen.",
        "Schritt 2: Kontrollierte Ausführung.",
        "Schritt 3: Saubere Rückkehr in die Startposition."
      ],
      "targetWeightKg": 60.0,
      "targetReps": 8,
      "targetSeconds": 0
    }
  ]
}
```

Erstelle jetzt bitte den JSON-Block für folgende Übung(en):
[HIER ÜBUNGSNAME(N) EINFÜGEN]
""".trimIndent()

private fun parseNpefExercises(rawInput: String): Result<List<Exercise>> = runCatching {
    val cleaned = rawInput
        .trim()
        .removePrefix("```json")
        .removePrefix("```JSON")
        .removePrefix("```")
        .removeSuffix("```")
        .trim()

    val rootObj = if (cleaned.startsWith("[")) {
        JSONObject().put("format", NPEF_FORMAT_ID).put("exercises", JSONArray(cleaned))
    } else {
        val obj = JSONObject(cleaned)
        if (!obj.has("exercises") && obj.has("name")) {
            JSONObject().put("format", NPEF_FORMAT_ID).put("exercises", JSONArray().put(obj))
        } else {
            obj
        }
    }

    val format = rootObj.optString("format", NPEF_FORMAT_ID)
    require(format == NPEF_FORMAT_ID) {
        "Unbekanntes Dateiformat „$format“. Erwartet wird „$NPEF_FORMAT_ID“."
    }

    val arr = rootObj.getJSONArray("exercises")
    require(arr.length() > 0) { "Keine Übungen im JSON gefunden." }

    (0 until arr.length()).map { idx ->
        val o = arr.getJSONObject(idx)
        val name = o.optString("name", "").trim()
        require(name.isNotBlank()) { "Übung #${idx + 1} hat keinen Namen." }
        val kind = runCatching {
            ExerciseKind.valueOf(o.optString("kind", "WEIGHT").uppercase(Locale.ROOT))
        }.getOrDefault(ExerciseKind.WEIGHT)
        val stepsArr = o.optJSONArray("steps")
        val stepsList = if (stepsArr != null) {
            (0 until stepsArr.length()).mapNotNull { i -> stepsArr.optString(i, "").trim().takeIf { it.isNotEmpty() } }
        } else {
            emptyList()
        }
        Exercise(
            id = UUID.randomUUID().toString(),
            name = name.take(60),
            muscle = o.optString("muscle", "Ganzkörper").ifBlank { "Ganzkörper" }.take(32),
            kind = kind,
            description = o.optString("description", "").trim().take(400),
            steps = stepsList,
            targetWeightKg = o.optDouble("targetWeightKg", 20.0).toFloat().coerceIn(0f, 500f),
            targetReps = o.optInt("targetReps", 10).coerceIn(1, 500),
            targetSeconds = o.optInt("targetSeconds", 45).coerceIn(1, 3600)
        )
    }
}

private fun exportNpefJson(exercises: List<Exercise>): String {
    val arr = JSONArray()
    exercises.forEach { ex ->
        arr.put(
            JSONObject()
                .put("name", ex.name)
                .put("muscle", ex.muscle)
                .put("kind", ex.kind.name)
                .put("description", ex.description)
                .put("steps", JSONArray(ex.steps))
                .put("targetWeightKg", ex.targetWeightKg.toDouble())
                .put("targetReps", ex.targetReps)
                .put("targetSeconds", ex.targetSeconds)
        )
    }
    return JSONObject()
        .put("format", NPEF_FORMAT_ID)
        .put("exercises", arr)
        .toString(2)
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
                    createdDate = o.optString("createdDate", ""),
                    optional = o.optBoolean("optional", false)
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
                    .put("optional", r.optional)
            )
        }
        prefs.edit().putString("rules", array.toString()).apply()
    }

    private fun loadExercises(): List<Exercise> = try {
        val raw = prefs.getString("exercises_v1", null)
        if (raw == null) {
            val defaults = listOf(
                Exercise(
                    id = "ex-bench-press",
                    name = "Bankdrücken",
                    muscle = "Brust",
                    kind = ExerciseKind.WEIGHT,
                    description = "Klassische Grundübung für Brust, vordere Schulter und Trizeps auf der Flachbank.",
                    steps = listOf(
                        "Schulterblätter fest auf der Bank zusammenziehen und Füße stabil aufstellen.",
                        "Langhantel etwas breiter als schulterbreit greifen und kontrolliert bis zur unteren Brust absenken.",
                        "Explosiv nach oben drücken, ohne die Ellenbogen komplett durchzuschlagen."
                    ),
                    targetWeightKg = 60f,
                    targetReps = 8
                ),
                Exercise(
                    id = "ex-pushups",
                    name = "Liegestütze",
                    muscle = "Brust & Core",
                    kind = ExerciseKind.BODYWEIGHT,
                    description = "Saubere Liegestütze mit eigenem Körpergewicht und voller Körperspannung.",
                    steps = listOf(
                        "Hände knapp unterhalb der Schultern aufsetzen, Körper bildet eine gerade Linie.",
                        "Brust kontrolliert Richtung Boden senken, Ellenbogen ca. 45° zum Oberkörper.",
                        "Kraftvoll zurück in die Ausgangsposition drücken."
                    ),
                    targetReps = 20
                ),
                Exercise(
                    id = "ex-speed-pushups",
                    name = "Liegestütze auf Zeit",
                    muscle = "Kraftausdauer",
                    kind = ExerciseKind.TIMED,
                    description = "Möglichst viele saubere Liegestütze innerhalb eines festen Zeitfensters.",
                    steps = listOf(
                        "Timer starten und direkt in die Plank-Position gehen.",
                        "Gleichmäßiges Tempo halten, ohne die Körperspannung zu verlieren."
                    ),
                    targetReps = 25,
                    targetSeconds = 45
                )
            )
            saveExercises(defaults)
            defaults
        } else {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val sArr = o.optJSONArray("steps")
                val steps = if (sArr != null) {
                    (0 until sArr.length()).mapNotNull { idx -> sArr.optString(idx, "").takeIf { it.isNotBlank() } }
                } else emptyList()
                Exercise(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    muscle = o.optString("muscle", "Ganzkörper"),
                    kind = runCatching { ExerciseKind.valueOf(o.optString("kind", "WEIGHT")) }.getOrDefault(ExerciseKind.WEIGHT),
                    description = o.optString("description", ""),
                    steps = steps,
                    mediaPath = o.optString("mediaPath", ""),
                    mediaIsVideo = o.optBoolean("mediaIsVideo", false),
                    targetWeightKg = o.optDouble("targetWeightKg", 40.0).toFloat(),
                    targetReps = o.optInt("targetReps", 10),
                    targetSeconds = o.optInt("targetSeconds", 45)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun saveExercises(list: List<Exercise>) {
        val arr = JSONArray()
        list.forEach { ex ->
            arr.put(
                JSONObject()
                    .put("id", ex.id)
                    .put("name", ex.name)
                    .put("muscle", ex.muscle)
                    .put("kind", ex.kind.name)
                    .put("description", ex.description)
                    .put("steps", JSONArray(ex.steps))
                    .put("mediaPath", ex.mediaPath)
                    .put("mediaIsVideo", ex.mediaIsVideo)
                    .put("targetWeightKg", ex.targetWeightKg.toDouble())
                    .put("targetReps", ex.targetReps)
                    .put("targetSeconds", ex.targetSeconds)
            )
        }
        prefs.edit().putString("exercises_v1", arr.toString()).apply()
    }

    private fun loadExerciseLogs(): List<ExerciseLog> = try {
        val raw = prefs.getString("exercise_logs_v1", null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            ExerciseLog(
                id = o.getString("id"),
                exerciseId = o.getString("exerciseId"),
                date = o.getString("date"),
                weightKg = o.optDouble("weightKg", 0.0).toFloat(),
                reps = o.optInt("reps", 0),
                seconds = o.optInt("seconds", 0)
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun saveExerciseLogs(logs: List<ExerciseLog>) {
        val arr = JSONArray()
        logs.takeLast(500).forEach { log ->
            arr.put(
                JSONObject()
                    .put("id", log.id)
                    .put("exerciseId", log.exerciseId)
                    .put("date", log.date)
                    .put("weightKg", log.weightKg.toDouble())
                    .put("reps", log.reps)
                    .put("seconds", log.seconds)
            )
        }
        prefs.edit().putString("exercise_logs_v1", arr.toString()).apply()
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
        var exercises by remember { mutableStateOf(loadExercises()) }
        var exerciseLogs by remember { mutableStateOf(loadExerciseLogs()) }
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

        fun daySnapshot(d: LocalDate): DaySnapshot {
            val allDailyForDay = rules.filter { r ->
                r.kind != RuleKind.WEEKLY &&
                    d.dayOfWeek.value in r.days &&
                    (r.createdDate.isBlank() || r.createdDate <= d.toString())
            }
            val mandatoryDailyForDay = allDailyForDay.filterNot { it.optional }

            val weeklyDoneOnDay = weeklyRules.filter { doneTodayForWeekly(it, d) }
            val mandatoryWeeklyDoneOnDay = weeklyDoneOnDay.filterNot { it.optional }

            val activeMandatoryWeeklyOnDay = if (d == date) {
                weeklyRules.filter { !it.optional && (doneTodayForWeekly(it, d) || doneCount(it, d) < totalOf(it)) }
            } else {
                mandatoryWeeklyDoneOnDay
            }

            val doneDaily = mandatoryDailyForDay.count { doneCount(it, d) >= totalOf(it) }
            val doneWeekly = if (d == date) {
                activeMandatoryWeeklyOnDay.count { doneTodayForWeekly(it, d) }
            } else {
                mandatoryWeeklyDoneOnDay.size
            }
            val total = mandatoryDailyForDay.size + activeMandatoryWeeklyOnDay.size
            val done = doneDaily + doneWeekly
            return DaySnapshot(
                date = d,
                done = done,
                total = total,
                dailyRules = allDailyForDay,
                weeklyDoneRules = weeklyDoneOnDay
            )
        }

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
        var navTargetPage by remember { mutableIntStateOf(0) }
        var isNavAnimating by remember { mutableStateOf(false) }

        fun selectPage(target: Int) {
            if (target in 0..3 && target != pagerState.currentPage) {
                triggerHaptic(context, haptic, HapticStyle.TICK)
                navTargetPage = target
                isNavAnimating = true
                scope.launch {
                    try {
                        pagerState.animateScrollToPage(
                            page = target,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        )
                    } finally {
                        isNavAnimating = false
                    }
                }
            }
        }

        LaunchedEffect(pagerState.isScrollInProgress) {
            if (!pagerState.isScrollInProgress && !isNavAnimating) {
                navTargetPage = pagerState.currentPage
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
                        2 -> TrainingScreen(
                            ndot = ndot,
                            today = date,
                            exercises = exercises,
                            logs = exerciseLogs,
                            onAddExercise = { newEx ->
                                exercises = exercises + newEx
                                saveExercises(exercises)
                                triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                            },
                            onImportExercises = { imported ->
                                exercises = exercises + imported
                                saveExercises(exercises)
                                triggerHaptic(context, haptic, HapticStyle.DAY_COMPLETE)
                            },
                            onDeleteExercise = { exId ->
                                exercises.find { it.id == exId }?.mediaPath?.takeIf { it.isNotBlank() }?.let { path ->
                                    runCatching { File(path).delete() }
                                }
                                exercises = exercises.filterNot { it.id == exId }
                                saveExercises(exercises)
                                exerciseLogs = exerciseLogs.filterNot { it.exerciseId == exId }
                                saveExerciseLogs(exerciseLogs)
                                triggerHaptic(context, haptic, HapticStyle.UNDO)
                            },
                            onLogSet = { newLog ->
                                exerciseLogs = exerciseLogs + newLog
                                saveExerciseLogs(exerciseLogs)
                                triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                            },
                            onDeleteLog = { logId ->
                                exerciseLogs = exerciseLogs.filterNot { it.id == logId }
                                saveExerciseLogs(exerciseLogs)
                                triggerHaptic(context, haptic, HapticStyle.UNDO)
                            },
                            onHapticTick = { triggerHaptic(context, haptic, HapticStyle.TICK) }
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
                    directTarget = if (isNavAnimating) navTargetPage else null,
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
    val mandatoryDaily = remember(rules) { rules.filterNot { it.optional } }
    val optionalDaily = remember(rules) { rules.filter { it.optional } }

    val activeMandatoryWeeklyForToday = remember(weekly, date, tick) {
        weekly.filter { !it.optional && (doneTodayWeekly(it) || count(it) < totalOf(it)) }
    }
    val dailyDone = remember(mandatoryDaily, date, tick) { mandatoryDaily.count { count(it) >= totalOf(it) } }
    val weeklyDoneToday = remember(activeMandatoryWeeklyForToday, date, tick) {
        activeMandatoryWeeklyForToday.count { doneTodayWeekly(it) }
    }

    val totalToday = mandatoryDaily.size + activeMandatoryWeeklyForToday.size
    val doneToday = dailyDone + weeklyDoneToday
    val progress by animateFloatAsState(
        targetValue = if (totalToday == 0) 1f else doneToday.toFloat() / totalToday,
        label = "progress"
    )
    val open = totalToday - doneToday
    val next = remember(mandatoryDaily, date, tick, nowMin) {
        mandatoryDaily
            .filter { count(it) < totalOf(it) }
            .mapNotNull { r -> nextTime(r, count(r))?.let { t -> r to t } }
            .filter { it.second >= nowMin }
            .minByOrNull { it.second }
    }
    val status = when {
        open == 0 -> "Dein Tag ist abgeschlossen."
        next != null -> "Als Nächstes um ${fmt(next.second)} · ${next.first.name}"
        else -> "$open ${if (open == 1) "Pflichtaufgabe wartet" else "Pflichtaufgaben warten"} noch auf dich."
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
                    "Alle Pflichtziele erreicht. Der Rest des Tages gehört dir.",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 6.dp, bottom = 8.dp)
                )
            } else if (mandatoryDaily.isNotEmpty()) {
                SectionLabel(
                    "FÜR HEUTE GEPLANT",
                    topPadding = if (orbit) 10.dp else 26.dp,
                    bottomPadding = if (orbit) 8.dp else 12.dp
                )
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    mandatoryDaily.forEachIndexed { index, rule ->
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
                        if (index < mandatoryDaily.lastIndex) {
                            HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }

            if (optionalDaily.isNotEmpty()) {
                SectionLabel(
                    "OPTIONAL · ZÄHLT NICHT ZUM TAGESSCORE",
                    topPadding = if (orbit) 12.dp else 20.dp,
                    bottomPadding = if (orbit) 8.dp else 10.dp
                )
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    optionalDaily.forEachIndexed { index, rule ->
                        val c = count(rule)
                        val total = totalOf(rule)
                        val subtitle = when (rule.kind) {
                            RuleKind.INTERVAL -> "Optional · Alle ${everyLabel(rule.everyMin)} · $c/$total"
                            else -> "Optional · ${dayLabel(rule.days)}"
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
                            chipHighlight = false,
                            compact = orbit
                        ) { onToggle(rule) }
                        if (index < optionalDaily.lastIndex) {
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
private fun CheckCircle(checked: Boolean, optional: Boolean = false) {
    val fillProgress by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "check_circle_anim"
    )
    Canvas(Modifier.size(32.dp)) {
        val strokePx = 1.7.dp.toPx()
        val radius = (size.minDimension - strokePx) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        if (checked) {
            drawCircle(
                color = White,
                radius = (size.minDimension / 2f) * fillProgress.coerceAtLeast(0.85f),
                center = center
            )
            val checkPath = Path().apply {
                moveTo(size.width * 0.30f, size.height * 0.52f)
                lineTo(size.width * 0.44f, size.height * 0.65f)
                lineTo(size.width * 0.71f, size.height * 0.36f)
            }
            drawPath(
                path = checkPath,
                color = Bg,
                style = Stroke(
                    width = 2.3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        } else {
            drawCircle(
                color = if (optional) Color(0xFF454545) else Color(0xFF5A5A5A),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )
            if (optional) {
                drawCircle(
                    color = Color(0xFF5A5A5A),
                    radius = 2.dp.toPx(),
                    center = center
                )
            }
        }
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
        CheckCircle(checked = checked, optional = rule.optional)
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
    val optSuffix = if (rule.optional) " · Optional" else ""
    val statusText = when {
        weekDone -> "Wochenziel erreicht ($target×/Woche)$optSuffix"
        checkedToday && !testMode -> "Heute erledigt · $target× pro Woche$optSuffix"
        testMode -> "Test-Modus aktiv · $target× pro Woche$optSuffix"
        else -> "Flexibles Wochenziel · $target× pro Woche$optSuffix"
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

// ---------- Rueckblick (Monat & Jahr: stumpfe 365-Punkte-Matrix im Punkte-Modus) ----------

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

                    if (!historyDots) {
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
                    }

                    val firstWeekdayOffset = currentMonth.atDay(1).dayOfWeek.value - 1
                    val totalCells = firstWeekdayOffset + currentMonth.lengthOfMonth()
                    val rows = (totalCells + 6) / 7

                    Column(verticalArrangement = Arrangement.spacedBy(if (historyDots) 10.dp else 8.dp)) {
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
                                        Spacer(Modifier.weight(1f).height(if (historyDots) 30.dp else 38.dp))
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(14.dp))
                    HistoryLegend()
                }
            }

            val selectedSnap = remember(selectedDate, tick) { snapshotFor(selectedDate) }
            Spacer(Modifier.height(14.dp))
            DayDetailCard(
                snap = selectedSnap,
                isToday = selectedDate == today,
                doneCountFor = doneCountFor,
                ndot = ndot
            )
        } else {
            // Jahresrueckblick
            val yearData = remember(currentYear, tick) {
                var allCount = 0
                var partialCount = 0
                var noneCount = 0
                val daysInYear = Year.of(currentYear).length()
                val allYearSnaps = (1..daysInYear).map { dayOfYear ->
                    val d = LocalDate.ofYearDay(currentYear, dayOfYear)
                    val s = snapshotFor(d)
                    if (!d.isAfter(today)) {
                        when (s.state) {
                            DayCompletionState.ALL -> allCount++
                            DayCompletionState.PARTIAL -> partialCount++
                            DayCompletionState.NONE -> noneCount++
                        }
                    }
                    s
                }
                val months = (1..12).map { m ->
                    val ym = YearMonth.of(currentYear, m)
                    val snaps = (1..ym.lengthOfMonth()).map { d -> snapshotFor(ym.atDay(d)) }
                    ym to snaps
                }
                Triple(allYearSnaps, months, Triple(allCount, partialCount, noneCount))
            }

            val (allYearSnaps, monthsList, counts) = yearData
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MiniYearStat("PERFEKT", "${counts.first}", Red, Modifier.weight(1f), ndot)
                MiniYearStat("TEILWEISE", "${counts.second}", White, Modifier.weight(1f), ndot)
                MiniYearStat("OFFEN", "${counts.third}", Muted, Modifier.weight(1f), ndot)
            }

            Spacer(Modifier.height(12.dp))

            if (historyDots) {
                // Stumpfe 365/366-Punkte-Matrix ohne Monate, ohne Zahlen und ohne Kalender-Raster
                val cols = 19
                val rows = (allYearSnaps.size + cols - 1) / cols
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(rows) { r ->
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    repeat(cols) { c ->
                                        val idx = r * cols + c
                                        if (idx < allYearSnaps.size) {
                                            val snap = allYearSnaps[idx]
                                            val isFuture = snap.date.isAfter(today)
                                            val isSelected = snap.date == selectedDate
                                            val isToday = snap.date == today
                                            val dotColor = when {
                                                isFuture -> Color(0xFF141414)
                                                snap.state == DayCompletionState.ALL -> Red
                                                snap.state == DayCompletionState.PARTIAL -> White
                                                else -> Color(0xFF2B2B2B)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .then(
                                                        when {
                                                            isSelected -> Modifier.border(1.dp, White, CircleShape)
                                                            isToday -> Modifier.border(1.dp, Color(0xFF5A5A5A), CircleShape)
                                                            else -> Modifier
                                                        }
                                                    )
                                                    .clickable(enabled = !isFuture) {
                                                        selectedDate = snap.date
                                                        onHapticTick()
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Box(
                                                    Modifier
                                                        .fillMaxSize(0.76f)
                                                        .background(dotColor, CircleShape)
                                                )
                                            }
                                        } else {
                                            Spacer(Modifier.weight(1f).aspectRatio(1f))
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        HistoryLegend()
                    }
                }

                // Detail-Karte auch beim Tippen auf einen Punkt in der Jahres-Matrix
                val selectedYearSnap = remember(selectedDate, tick) {
                    snapshotFor(if (selectedDate.year == currentYear) selectedDate else today)
                }
                Spacer(Modifier.height(12.dp))
                DayDetailCard(
                    snap = selectedYearSnap,
                    isToday = selectedYearSnap.date == today,
                    doneCountFor = doneCountFor,
                    ndot = ndot
                )
            } else {
                // Kalender-Ansicht nach Monaten
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    monthsList.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            pair.forEach { (ym, snaps) ->
                                YearMonthCard(
                                    ym = ym,
                                    snaps = snaps,
                                    today = today,
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
        val dotColor = when (state) {
            DayCompletionState.ALL -> Red
            DayCompletionState.PARTIAL -> White
            DayCompletionState.NONE -> Color(0xFF2D2D2D)
            null -> Color(0xFF141414)
        }
        val dotSize = when (state) {
            DayCompletionState.ALL -> 13.dp
            DayCompletionState.PARTIAL -> 11.dp
            DayCompletionState.NONE -> 8.dp
            null -> 5.dp
        }
        Box(
            modifier = modifier
                .height(30.dp)
                .clip(CircleShape)
                .background(if (isSelected) Panel2 else Color.Transparent)
                .then(
                    if (isToday) Modifier.border(1.dp, Color(0xFF525252), CircleShape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(dotSize)
                    .background(dotColor, CircleShape)
            )
        }
    } else {
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
                                            .fillMaxSize(0.88f)
                                            .clip(RoundedCornerShape(2.5.dp))
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
        LegendItem(Red, "Alle Pflichtziele")
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
        DayCompletionState.ALL -> "ALLE PFLICHTZIELE ERREICHT"
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
                                if (r.optional) "${r.name} (Optional)" else r.name,
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
                                if (w.optional) "${w.name} (Wochenziel · Optional)" else "${w.name} (Wochenziel)",
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

// ---------- Sport-Uebungen & Tracking-Seite (Reiter 3) ----------

@Composable
private fun TrainingScreen(
    ndot: FontFamily,
    today: LocalDate,
    exercises: List<Exercise>,
    logs: List<ExerciseLog>,
    onAddExercise: (Exercise) -> Unit,
    onImportExercises: (List<Exercise>) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onLogSet: (ExerciseLog) -> Unit,
    onDeleteLog: (String) -> Unit,
    onHapticTick: () -> Unit
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf<ExerciseKind?>(null) }
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var showNewDialog by remember { mutableStateOf(false) }
    var showAiImportDialog by remember { mutableStateOf(false) }

    val todayStr = today.toString()
    val todayLogs = remember(logs, todayStr) { logs.filter { it.date == todayStr } }
    val filteredExercises = remember(exercises, selectedFilter) {
        if (selectedFilter == null) exercises else exercises.filter { it.kind == selectedFilter }
    }

    fun shareAiPromptToExternalApp() {
        onHapticTick()
        val prompt = buildAiExercisePrompt()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("NPlanner KI-Vorlage", prompt))
        Toast.makeText(context, "KI-Vorlage kopiert – wähle eine KI-App", Toast.LENGTH_SHORT).show()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NPlanner Übung erstellen ($NPEF_FORMAT_ID)")
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        val chooser = Intent.createChooser(sendIntent, "Übung mithilfe KI anlegen").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    Screen(
        ndot = ndot,
        eyebrow = "KRAFT, AUSDAUER & TECHNIK.",
        title = "TRAINING",
        subtitle = "${exercises.size} Übungen · ${todayLogs.size} Sätze heute"
    ) {
        // Aktionsleiste: Manuell anlegen + KI-Prompt senden + JSON importieren
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clickable {
                        onHapticTick()
                        showNewDialog = true
                    },
                color = White,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "+ ÜBUNG",
                        color = Bg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.2.sp
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .weight(1.35f)
                    .height(48.dp)
                    .clickable { shareAiPromptToExternalApp() },
                color = Panel,
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(Modifier.size(6.dp).background(Red, CircleShape))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "MIT KI ANLEGEN",
                        color = White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.1.sp,
                        maxLines = 1
                    )
                }
            }

            Surface(
                modifier = Modifier
                    .height(48.dp)
                    .clickable {
                        onHapticTick()
                        showAiImportDialog = true
                    },
                color = Panel2,
                shape = RoundedCornerShape(16.dp)
            ) {
                Box(Modifier.padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "JSON",
                        color = White,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        letterSpacing = 1.1.sp
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Filter-Chips nach Uebungsart
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChoiceChip("Alle", selectedFilter == null, Modifier.weight(1f)) {
                selectedFilter = null
                onHapticTick()
            }
            ChoiceChip("Gewicht", selectedFilter == ExerciseKind.WEIGHT, Modifier.weight(1f)) {
                selectedFilter = ExerciseKind.WEIGHT
                onHapticTick()
            }
            ChoiceChip("Körper", selectedFilter == ExerciseKind.BODYWEIGHT, Modifier.weight(1f)) {
                selectedFilter = ExerciseKind.BODYWEIGHT
                onHapticTick()
            }
            ChoiceChip("Zeit", selectedFilter == ExerciseKind.TIMED, Modifier.weight(1f)) {
                selectedFilter = ExerciseKind.TIMED
                onHapticTick()
            }
        }

        SectionLabel("ÜBUNGSKATALOG", topPadding = 18.dp, bottomPadding = 10.dp)

        if (filteredExercises.isEmpty()) {
            Surface(
                color = Panel,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(20.dp)) {
                    Text("Noch keine Übungen in dieser Kategorie.", color = White, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Erstelle eine eigene Übung mit Foto/Video oder generiere sie per KI-Vorlage.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                filteredExercises.forEach { ex ->
                    val exLogs = logs.filter { it.exerciseId == ex.id }
                    val todaySets = exLogs.count { it.date == todayStr }
                    val lastLog = exLogs.lastOrNull()

                    Surface(
                        color = Panel,
                        shape = RoundedCornerShape(22.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onHapticTick()
                                activeExercise = ex
                            }
                    ) {
                        Row(
                            Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Kompakte Vorschau (Media-Indikator oder Typ-Badge)
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Panel2),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = when {
                                        ex.mediaPath.isNotBlank() && ex.mediaIsVideo -> "▶"
                                        ex.mediaPath.isNotBlank() -> "▣"
                                        ex.kind == ExerciseKind.WEIGHT -> "KG"
                                        ex.kind == ExerciseKind.BODYWEIGHT -> "BW"
                                        else -> "⏱"
                                    },
                                    color = if (todaySets > 0) Red else White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        ex.name,
                                        color = White,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (todaySets > 0) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${todaySets}× HEUTE",
                                            color = Red,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.sp,
                                            letterSpacing = 1.sp
                                        )
                                    }
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(
                                    exerciseSummary(ex),
                                    color = Muted,
                                    fontSize = 11.sp
                                )
                                if (lastLog != null) {
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        "Letzter Satz: ${logSummary(ex, lastLog)}",
                                        color = Color(0xFFB4B4B0),
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                            Text("›", color = Muted, fontSize = 22.sp, modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }

        // Heutiges Trainingsprotokoll
        if (todayLogs.isNotEmpty()) {
            SectionLabel("HEUTE GELOCKT", topPadding = 22.dp, bottomPadding = 10.dp)
            Surface(color = Panel, shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    todayLogs.asReversed().forEachIndexed { idx, log ->
                        val ex = exercises.find { it.id == log.exerciseId }
                        if (ex != null) {
                            Row(
                                Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.size(6.dp).background(Red, CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(ex.name, color = White, fontSize = 14.sp)
                                    Text(
                                        logSummary(ex, log),
                                        color = Muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(CircleShape)
                                        .clickable { onDeleteLog(log.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("✕", color = Muted, fontSize = 13.sp)
                                }
                            }
                            if (idx < todayLogs.lastIndex) {
                                HorizontalDivider(color = Color(0xFF282828))
                            }
                        }
                    }
                }
            }
        }
    }

    // Detail- & Tracking-Dialog fuer ausgewaehlte Uebung
    activeExercise?.let { currentEx ->
        val freshEx = exercises.find { it.id == currentEx.id }
        if (freshEx == null) {
            activeExercise = null
        } else {
            ExerciseDetailDialog(
                ndot = ndot,
                today = today,
                exercise = freshEx,
                logs = logs.filter { it.exerciseId == freshEx.id },
                onDismiss = { activeExercise = null },
                onLogSet = onLogSet,
                onDeleteExercise = {
                    onDeleteExercise(freshEx.id)
                    activeExercise = null
                },
                onHapticTick = onHapticTick
            )
        }
    }

    if (showNewDialog) {
        NewExerciseDialog(
            ndot = ndot,
            onDismiss = { showNewDialog = false },
            onCreate = { created ->
                onAddExercise(created)
                showNewDialog = false
            }
        )
    }

    if (showAiImportDialog) {
        AiExerciseImportDialog(
            ndot = ndot,
            existingExercises = exercises,
            onDismiss = { showAiImportDialog = false },
            onSharePrompt = { shareAiPromptToExternalApp() },
            onImport = { imported ->
                onImportExercises(imported)
                showAiImportDialog = false
            }
        )
    }
}

@Composable
private fun ExerciseDetailDialog(
    ndot: FontFamily,
    today: LocalDate,
    exercise: Exercise,
    logs: List<ExerciseLog>,
    onDismiss: () -> Unit,
    onLogSet: (ExerciseLog) -> Unit,
    onDeleteExercise: () -> Unit,
    onHapticTick: () -> Unit
) {
    val lastLog = logs.lastOrNull()
    var weightText by remember(exercise.id) {
        mutableStateOf(
            (lastLog?.weightKg?.takeIf { it > 0f } ?: exercise.targetWeightKg)
                .let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }
        )
    }
    var repsText by remember(exercise.id) {
        mutableStateOf((lastLog?.reps?.takeIf { it > 0 } ?: exercise.targetReps).toString())
    }
    var secondsText by remember(exercise.id) {
        mutableStateOf((lastLog?.seconds?.takeIf { it > 0 } ?: exercise.targetSeconds).toString())
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${exercise.muscle.uppercase(Locale.GERMAN)} · ${exerciseKindLabel(exercise.kind).uppercase(Locale.GERMAN)}",
                        color = Red,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                    TextButton(onClick = onDeleteExercise) {
                        Text("Löschen", color = Red, fontSize = 12.sp)
                    }
                }

                DisplayTitle(exercise.name, ndot, fontSize = 26.sp)

                if (exercise.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(exercise.description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }

                // Foto- oder Video-Anzeige falls hinterlegt
                if (exercise.mediaPath.isNotBlank()) {
                    Spacer(Modifier.height(14.dp))
                    ExerciseMediaViewer(
                        mediaPath = exercise.mediaPath,
                        isVideo = exercise.mediaIsVideo
                    )
                }

                if (exercise.steps.isNotEmpty()) {
                    SectionLabel("AUSFÜHRUNG", topPadding = 16.dp, bottomPadding = 8.dp)
                    Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            exercise.steps.forEachIndexed { i, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        "${i + 1}.",
                                        color = Red,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(step, color = White, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                // Satz tracken je nach Uebungstyp
                SectionLabel("SATZ EINTRAGEN", topPadding = 18.dp, bottomPadding = 10.dp)
                when (exercise.kind) {
                    ExerciseKind.WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it.take(6) },
                                label = { Text("Gewicht (kg)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = repsText,
                                onValueChange = { repsText = it.filter(Char::isDigit).take(4) },
                                label = { Text("Wdh.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    ExerciseKind.BODYWEIGHT -> {
                        OutlinedTextField(
                            value = repsText,
                            onValueChange = { repsText = it.filter(Char::isDigit).take(4) },
                            label = { Text("Wiederholungen (Körpergewicht)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ExerciseKind.TIMED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = repsText,
                                onValueChange = { repsText = it.filter(Char::isDigit).take(4) },
                                label = { Text("Wdh. (1 = Halten)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondsText,
                                onValueChange = { secondsText = it.filter(Char::isDigit).take(5) },
                                label = { Text("Zeit (Sek.)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clickable {
                            val w = weightText.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 500f) ?: 0f
                            val r = repsText.toIntOrNull()?.coerceIn(1, 500) ?: 1
                            val s = secondsText.toIntOrNull()?.coerceIn(1, 3600) ?: 30
                            onLogSet(
                                ExerciseLog(
                                    id = UUID.randomUUID().toString(),
                                    exerciseId = exercise.id,
                                    date = today.toString(),
                                    weightKg = if (exercise.kind == ExerciseKind.WEIGHT) w else 0f,
                                    reps = if (exercise.kind == ExerciseKind.TIMED) r else r,
                                    seconds = if (exercise.kind == ExerciseKind.TIMED) s else 0
                                )
                            )
                        },
                    color = White,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "SATZ SPEICHERN",
                            color = Bg,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.4.sp
                        )
                    }
                }

                if (logs.isNotEmpty()) {
                    SectionLabel("VERLAUF (${logs.size} SÄTZE)", topPadding = 18.dp, bottomPadding = 8.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        logs.takeLast(5).asReversed().forEach { log ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Panel2)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(log.date, color = Muted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                                Text(logSummary(exercise, log), color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        onHapticTick()
                        onDismiss()
                    }) { Text("Schließen") }
                }
            }
        }
    }
}

@Composable
private fun ExerciseMediaViewer(mediaPath: String, isVideo: Boolean) {
    val file = remember(mediaPath) { File(mediaPath) }
    if (!file.exists()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg)
    ) {
        if (isVideo) {
            AndroidView(
                factory = { ctx ->
                    VideoView(ctx).apply {
                        setVideoURI(Uri.fromFile(file))
                        val controller = MediaController(ctx)
                        controller.setAnchorView(this)
                        setMediaController(controller)
                        setOnPreparedListener { mp ->
                            mp.isLooping = true
                            start()
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bitmap = remember(mediaPath) {
                runCatching { android.graphics.BitmapFactory.decodeFile(mediaPath)?.asImageBitmap() }.getOrNull()
            }
            if (bitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = bitmap,
                    contentDescription = "Übungsbild",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun NewExerciseDialog(
    ndot: FontFamily,
    onDismiss: () -> Unit,
    onCreate: (Exercise) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }
    var muscle by remember { mutableStateOf("Brust") }
    var kind by remember { mutableStateOf(ExerciseKind.WEIGHT) }
    var description by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("40") }
    var targetReps by remember { mutableStateOf("10") }
    var targetSeconds by remember { mutableStateOf("45") }
    var copiedMediaPath by remember { mutableStateOf("") }
    var copiedMediaIsVideo by remember { mutableStateOf(false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val isVid = mime.startsWith("video/")
                val ext = if (isVid) "mp4" else "jpg"
                val dir = File(context.filesDir, "exercise_media").apply { mkdirs() }
                val dest = File(dir, "media_${UUID.randomUUID()}.$ext")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                copiedMediaPath = dest.absolutePath
                copiedMediaIsVideo = isVid
                triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle("NEUE ÜBUNG", ndot, fontSize = 24.sp)
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name der Übung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = muscle,
                    onValueChange = { muscle = it.take(32) },
                    label = { Text("Muskelgruppe (z. B. Brust, Rücken)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("ÜBUNGSTYP", topPadding = 16.dp, bottomPadding = 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        ExerciseKind.WEIGHT to "Gewicht",
                        ExerciseKind.BODYWEIGHT to "Körpergewicht",
                        ExerciseKind.TIMED to "Zeit"
                    ).forEach { (k, label) ->
                        ChoiceChip(label, kind == k, Modifier.weight(1f)) {
                            kind = k
                            triggerHaptic(context, haptic, HapticStyle.TICK)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                when (kind) {
                    ExerciseKind.WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = targetWeight,
                                onValueChange = { targetWeight = it.take(6) },
                                label = { Text("Ziel-Gewicht (kg)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetReps,
                                onValueChange = { targetReps = it.filter(Char::isDigit).take(4) },
                                label = { Text("Ziel-Wdh.") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    ExerciseKind.BODYWEIGHT -> {
                        OutlinedTextField(
                            value = targetReps,
                            onValueChange = { targetReps = it.filter(Char::isDigit).take(4) },
                            label = { Text("Ziel-Wiederholungen") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ExerciseKind.TIMED -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = targetReps,
                                onValueChange = { targetReps = it.filter(Char::isDigit).take(4) },
                                label = { Text("Wdh. (1 = Halten)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetSeconds,
                                onValueChange = { targetSeconds = it.filter(Char::isDigit).take(5) },
                                label = { Text("Zeit (Sekunden)") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it.take(400) },
                    label = { Text("Beschreibung & Ausführungshinweise") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("FOTO ODER VIDEO", topPadding = 16.dp, bottomPadding = 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = if (copiedMediaPath.isNotBlank() && !copiedMediaIsVideo) "✓ Foto gewählt" else "+ Foto wählen",
                        selected = copiedMediaPath.isNotBlank() && !copiedMediaIsVideo,
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaPicker.launch("image/*")
                    }
                    ChoiceChip(
                        label = if (copiedMediaPath.isNotBlank() && copiedMediaIsVideo) "✓ Video gewählt" else "+ Video wählen",
                        selected = copiedMediaPath.isNotBlank() && copiedMediaIsVideo,
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaPicker.launch("video/*")
                    }
                }

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(
                        enabled = name.isNotBlank(),
                        onClick = {
                            onCreate(
                                Exercise(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    muscle = muscle.trim().ifBlank { "Ganzkörper" },
                                    kind = kind,
                                    description = description.trim(),
                                    mediaPath = copiedMediaPath,
                                    mediaIsVideo = copiedMediaIsVideo,
                                    targetWeightKg = targetWeight.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 500f) ?: 20f,
                                    targetReps = targetReps.toIntOrNull()?.coerceIn(1, 500) ?: 10,
                                    targetSeconds = targetSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 45
                                )
                            )
                        }
                    ) { Text("Anlegen") }
                }
            }
        }
    }
}

@Composable
private fun AiExerciseImportDialog(
    ndot: FontFamily,
    existingExercises: List<Exercise>,
    onDismiss: () -> Unit,
    onSharePrompt: () -> Unit,
    onImport: (List<Exercise>) -> Unit
) {
    val context = LocalContext.current
    var jsonInput by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val fileImporter = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.onSuccess { content ->
                if (!content.isNullOrBlank()) {
                    jsonInput = content
                    errorMsg = null
                }
            }.onFailure {
                errorMsg = "Datei konnte nicht gelesen werden."
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle("KI-ÜBUNG IMPORT", ndot, fontSize = 24.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Format: $NPEF_FORMAT_ID · Sende die Vorlage an Gemini, ChatGPT oder Claude und füge die JSON-Antwort hier ein.",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("An KI-App senden", true, Modifier.weight(1.2f)) {
                        onSharePrompt()
                    }
                    ChoiceChip(".json Datei", false, Modifier.weight(0.8f)) {
                        fileImporter.launch(arrayOf("application/json", "text/*"))
                    }
                }

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = jsonInput,
                    onValueChange = {
                        jsonInput = it
                        errorMsg = null
                    },
                    label = { Text("JSON-Antwort hier einfügen") },
                    minLines = 5,
                    maxLines = 9,
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(errorMsg!!, color = Red, fontSize = 11.sp)
                }

                Spacer(Modifier.height(10.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        clipboard?.setPrimaryClip(ClipData.newPlainText("NPEF Export", exportNpefJson(existingExercises)))
                        Toast.makeText(context, "Katalog als JSON kopiert", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Katalog kopieren", color = Muted, fontSize = 11.sp)
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Abbrechen") }
                        TextButton(
                            enabled = jsonInput.isNotBlank(),
                            onClick = {
                                parseNpefExercises(jsonInput)
                                    .onSuccess { parsed -> onImport(parsed) }
                                    .onFailure { err -> errorMsg = err.message ?: "Ungültiges JSON-Format." }
                            }
                        ) { Text("Importieren") }
                    }
                }
            }
        }
    }
}

// ---------- Animierte Navigationsleiste ----------

private enum class NavGlyph { TODAY, HISTORY, TRAINING, SETUP }

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GlyphNav(
    pagerState: androidx.compose.foundation.pager.PagerState,
    directTarget: Int?,
    select: (Int) -> Unit,
    modifier: Modifier
) {
    val glyphs = listOf(NavGlyph.TODAY, NavGlyph.HISTORY, NavGlyph.TRAINING, NavGlyph.SETUP)

    val targetFraction = if (directTarget != null) {
        directTarget.toFloat()
    } else {
        (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 3f)
    }
    val pillFraction by animateFloatAsState(
        targetValue = targetFraction,
        animationSpec = if (directTarget != null) {
            tween(durationMillis = 260, easing = FastOutSlowInEasing)
        } else {
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
        },
        label = "nav_pill_slide"
    )

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
            val pillOffset = (pillWidth + spacing) * pillFraction

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
                    val distance = kotlin.math.abs(pillFraction - i).coerceIn(0f, 1f)
                    val activeWeight = 1f - distance
                    val activeTab = directTarget ?: pagerState.currentPage
                    val iconScale by animateFloatAsState(
                        targetValue = if (activeTab == i) 1.06f else 0.96f,
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
                drawRoundRect(color, Offset(l, t), Size(r - l, b - t), CornerRadius(4.dp.toPx()), style = Stroke(w))
                line(l, size.height * .40f, r, size.height * .40f)
                line(size.width * .34f, size.height * .15f, size.width * .34f, size.height * .29f)
                line(size.width * .66f, size.height * .15f, size.width * .66f, size.height * .29f)
                val xs = listOf(.31f, .50f, .69f)
                val ys = listOf(.54f, .69f)
                ys.forEach { y -> xs.forEach { x -> drawCircle(color, 1.35.dp.toPx(), Offset(size.width * x, size.height * y)) } }
            }
            NavGlyph.TRAINING -> {
                // Minimalistische Hantel-Glyphe
                line(size.width * .30f, cy, size.width * .70f, cy)
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .21f, size.height * .32f),
                    size = Size(3.2.dp.toPx(), size.height * .36f),
                    cornerRadius = CornerRadius(1.6.dp.toPx())
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .13f, size.height * .38f),
                    size = Size(2.6.dp.toPx(), size.height * .24f),
                    cornerRadius = CornerRadius(1.3.dp.toPx())
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .79f - 3.2.dp.toPx(), size.height * .32f),
                    size = Size(3.2.dp.toPx(), size.height * .36f),
                    cornerRadius = CornerRadius(1.6.dp.toPx())
                )
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * .87f - 2.6.dp.toPx(), size.height * .38f),
                    size = Size(2.6.dp.toPx(), size.height * .24f),
                    cornerRadius = CornerRadius(1.3.dp.toPx())
                )
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
                        }
                ) {
                    Row(
                        Modifier.padding(start = 18.dp, end = 12.dp, top = 14.dp, bottom = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(rule.name, color = White, fontSize = 15.sp)
                            Spacer(Modifier.height(2.dp))
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
            "Halte eine Aufgabe gedrückt, ziehe sie an die gewünschte Position und lasse los.",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
        )
        HorizontalDivider(color = Color(0xFF242424), modifier = Modifier.padding(top = 6.dp))
        Text(
            "NPLANNER · VERSION 0.18",
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
    var optional by remember { mutableStateOf(false) }

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

                SectionLabel("WERTUNG")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Pflichtziel", !optional, Modifier.weight(1f)) {
                        optional = false
                        triggerHaptic(context, haptic, HapticStyle.TICK)
                    }
                    ChoiceChip("Optional (Bonus)", optional, Modifier.weight(1f)) {
                        optional = true
                        triggerHaptic(context, haptic, HapticStyle.TICK)
                    }
                }
                Text(
                    if (optional) "Zählt nicht in den Perfect-Score – der Tag gilt auch ohne diese Aufgabe als perfekt."
                    else "Muss erledigt sein, damit der Tag als perfekt (rot) gewertet wird.",
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

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
                                    weeklyTarget = if (kind == RuleKind.WEEKLY) weeklyTarget else 1,
                                    optional = optional
                                )
                            )
                        }
                    ) { Text("Anlegen") }
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
