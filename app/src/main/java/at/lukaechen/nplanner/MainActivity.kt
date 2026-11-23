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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.horizontalScroll
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
enum class ExerciseKind { WEIGHT, BODYWEIGHT, TIMED, CARDIO, AMRAP }

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

private data class MuscleOption(val id: String, val label: String, val category: String)

private val muscleCatalogue = listOf(
    MuscleOption("chest", "Brust", "Brust"),
    MuscleOption("back", "Rücken", "Rücken"),
    MuscleOption("shoulders", "Schultern", "Schultern"),
    MuscleOption("arms", "Arme", "Arme"),
    MuscleOption("core", "Bauch & Core", "Bauch & Core"),
    MuscleOption("legs", "Beine", "Beine"),
    MuscleOption("glutes", "Gesäß", "Gesäß"),
    MuscleOption("calves", "Waden", "Waden"),
    MuscleOption("chest_upper", "Obere Brust", "Brust"),
    MuscleOption("chest_middle", "Mittlere Brust", "Brust"),
    MuscleOption("chest_lower", "Untere Brust", "Brust"),
    MuscleOption("lats", "Latissimus", "Rücken"),
    MuscleOption("traps", "Trapezmuskel", "Rücken"),
    MuscleOption("rhomboids", "Rhomboiden", "Rücken"),
    MuscleOption("lower_back", "Unterer Rücken", "Rücken"),
    MuscleOption("front_delts", "Vordere Schulter", "Schultern"),
    MuscleOption("side_delts", "Seitliche Schulter", "Schultern"),
    MuscleOption("rear_delts", "Hintere Schulter", "Schultern"),
    MuscleOption("biceps", "Bizeps", "Arme"),
    MuscleOption("triceps", "Trizeps", "Arme"),
    MuscleOption("brachialis", "Brachialis", "Arme"),
    MuscleOption("forearms", "Unterarme", "Arme"),
    MuscleOption("abs", "Gerader Bauchmuskel", "Bauch & Core"),
    MuscleOption("obliques", "Seitliche Bauchmuskeln", "Bauch & Core"),
    MuscleOption("deep_core", "Tiefe Bauchmuskulatur", "Bauch & Core"),
    MuscleOption("quads", "Quadrizeps", "Beine"),
    MuscleOption("hamstrings", "Beinbeuger", "Beine"),
    MuscleOption("adductors", "Adduktoren", "Beine"),
    MuscleOption("abductors", "Abduktoren", "Beine"),
    MuscleOption("glutes_max", "Großer Gesäßmuskel", "Gesäß"),
    MuscleOption("glutes_med", "Mittlerer Gesäßmuskel", "Gesäß"),
    MuscleOption("glutes_min", "Kleiner Gesäßmuskel", "Gesäß"),
    MuscleOption("gastrocnemius", "Gastrocnemius", "Waden"),
    MuscleOption("soleus", "Soleus", "Waden"),
    MuscleOption("neck", "Nackenmuskulatur", "Nacken"),
    MuscleOption("full_body", "Ganzkörper", "Sonstiges")
)

private fun muscleName(id: String): String = muscleCatalogue.firstOrNull { it.id == id }?.label ?: id

data class Exercise(
    val id: String,
    val name: String,
    val muscle: String = "Ganzkörper",
    val primaryMuscleId: String = "",
    val secondaryMuscleIds: List<String> = emptyList(),
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
    val seconds: Int = 0,
    val workoutId: String = "",
    val distanceKm: Float = 0f
)

enum class WorkoutKind { CLASSIC, AMRAP, FOR_TIME, EMOM }

data class Workout(
    val id: String,
    val name: String,
    val exerciseIds: List<String> = emptyList(),
    val note: String = "",
    val kind: WorkoutKind = WorkoutKind.CLASSIC,
    val durationSeconds: Int = 0
)

private val LocalDotHeadings = compositionLocalOf { false }
private val LocalClassicHeadings = compositionLocalOf { FontFamily.Default }

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
    ExerciseKind.CARDIO -> "Cardio"
    ExerciseKind.AMRAP -> "AMRAP / Runden"
}

private fun exerciseSummary(ex: Exercise): String = when (ex.kind) {
    ExerciseKind.CARDIO -> "${ex.muscle} · Cardio · Ziel ${ex.targetSeconds / 60} Min."
    ExerciseKind.AMRAP -> "${ex.muscle} · AMRAP · ${ex.targetSeconds / 60} Min. · ${ex.targetReps} Runden"
    ExerciseKind.WEIGHT -> "${ex.muscle} · Gewicht · Ziel ${fmtKg(ex.targetWeightKg)} × ${ex.targetReps}"
    ExerciseKind.BODYWEIGHT -> "${ex.muscle} · Körpergewicht · Ziel ${ex.targetReps} Wdh."
    ExerciseKind.TIMED -> if (ex.targetReps > 1) {
        "${ex.muscle} · Zeit · Ziel ${ex.targetReps} Wdh. in ${ex.targetSeconds} Sek."
    } else {
        "${ex.muscle} · Zeit · Ziel ${ex.targetSeconds} Sek."
    }
}

private fun logSummary(ex: Exercise, log: ExerciseLog): String = when (ex.kind) {
    ExerciseKind.CARDIO -> "${log.seconds / 60} Min. ${log.seconds % 60} Sek." +
        if (log.distanceKm > 0f) " · ${fmtKg(log.distanceKm).replace(" kg", " km")}" else ""
    ExerciseKind.AMRAP -> "${log.reps} Runden in ${log.seconds / 60} Min."
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
            primaryMuscleId = o.optString("primaryMuscleId", ""),
            secondaryMuscleIds = o.optJSONArray("secondaryMuscleIds")?.let { arr ->
                (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList(),
            kind = kind,
            description = o.optString("description", "").trim().take(400),
            steps = stepsList,
            targetWeightKg = o.optDouble("targetWeightKg", 20.0).toFloat().coerceIn(0f, 500f),
            targetReps = o.optInt("targetReps", 10).coerceIn(1, 500),
            targetSeconds = o.optInt("targetSeconds", 45).coerceIn(1, 3600),
            mediaPath = o.optString("mediaUrl", "").takeIf { validatedMediaUrl(it) != null } ?: "",
            mediaIsVideo = o.optBoolean("mediaIsVideo", false)
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
                    .put("primaryMuscleId", ex.primaryMuscleId)
                    .put("secondaryMuscleIds", JSONArray(ex.secondaryMuscleIds))
                .put("kind", ex.kind.name)
                .put("description", ex.description)
                .put("steps", JSONArray(ex.steps))
                .put("targetWeightKg", ex.targetWeightKg.toDouble())
                .put("targetReps", ex.targetReps)
                .put("targetSeconds", ex.targetSeconds)
                .put("mediaUrl", ex.mediaPath.takeIf { validatedMediaUrl(it) != null } ?: "")
                .put("mediaIsVideo", ex.mediaIsVideo)
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
                Rule(UUID.randomUUID().toString(), "30 Minuten lernen", (1..7).toSet(), createdDate = todayStr),
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
            val fixedDefaults = if (!prefs.getBoolean("migrated_default_30min_daily_v1", false)) {
                val updated = parsed.map { r ->
                    if (r.name == "30 Minuten lernen" && r.days == setOf(1, 3, 5)) {
                        r.copy(days = (1..7).toSet())
                    } else {
                        r
                    }
                }
                if (updated != parsed) saveRules(updated)
                prefs.edit().putBoolean("migrated_default_30min_daily_v1", true).apply()
                updated
            } else {
                parsed
            }
            fixedDefaults
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

    private fun starterExerciseCatalog(): List<Exercise> = listOf(
        Exercise("ex-pullups", "Klimmzüge", "Rücken", primaryMuscleId = "lats", secondaryMuscleIds = listOf("biceps"), kind = ExerciseKind.BODYWEIGHT, targetReps = 5),
        Exercise("ex-squats", "Kniebeugen", "Beine", primaryMuscleId = "quads", secondaryMuscleIds = listOf("glutes_max"), kind = ExerciseKind.BODYWEIGHT, targetReps = 15),
        Exercise("ex-rowing", "Rudern", "Rücken", primaryMuscleId = "back", kind = ExerciseKind.WEIGHT, targetWeightKg = 35f, targetReps = 10),
        Exercise("ex-shoulder-press", "Schulterdrücken", "Schultern", primaryMuscleId = "front_delts", kind = ExerciseKind.WEIGHT, targetWeightKg = 20f, targetReps = 10),
        Exercise("ex-deadlift", "Kreuzheben", "Rücken", primaryMuscleId = "lower_back", secondaryMuscleIds = listOf("hamstrings"), kind = ExerciseKind.WEIGHT, targetWeightKg = 50f, targetReps = 8),
        Exercise("ex-lunges", "Ausfallschritte", "Beine", primaryMuscleId = "quads", kind = ExerciseKind.BODYWEIGHT, targetReps = 12),
        Exercise("ex-dips", "Dips", "Arme", primaryMuscleId = "triceps", kind = ExerciseKind.BODYWEIGHT, targetReps = 10),
        Exercise("ex-plank", "Plank", "Bauch & Core", primaryMuscleId = "core", kind = ExerciseKind.TIMED, targetReps = 1, targetSeconds = 60),
        Exercise("ex-crunches", "Crunches", "Bauch & Core", primaryMuscleId = "abs", kind = ExerciseKind.BODYWEIGHT, targetReps = 20),
        Exercise("ex-calf-raises", "Wadenheben", "Waden", primaryMuscleId = "calves", kind = ExerciseKind.BODYWEIGHT, targetReps = 20),
        Exercise("ex-running", "Laufen", "Beine", primaryMuscleId = "legs", kind = ExerciseKind.CARDIO, targetSeconds = 1200),
        Exercise("ex-cycling", "Radfahren", "Beine", primaryMuscleId = "legs", kind = ExerciseKind.CARDIO, targetSeconds = 1800),
        Exercise("ex-row-erg", "Ruderergometer", "Rücken", primaryMuscleId = "back", kind = ExerciseKind.CARDIO, targetSeconds = 1200),
        Exercise("ex-swimming", "Schwimmen", "Ganzkörper", primaryMuscleId = "full_body", kind = ExerciseKind.CARDIO, targetSeconds = 1200),
        Exercise("ex-jump-rope", "Seilspringen", "Waden", primaryMuscleId = "calves", kind = ExerciseKind.CARDIO, targetSeconds = 600),
        Exercise("ex-burpees", "Burpees", "Ganzkörper", primaryMuscleId = "full_body", kind = ExerciseKind.BODYWEIGHT, targetReps = 12)
    )

    private fun loadExercises(): List<Exercise> {
        val loaded = try {
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
                    primaryMuscleId = o.optString("primaryMuscleId", ""),
                    secondaryMuscleIds = o.optJSONArray("secondaryMuscleIds")?.let { arr ->
                        (0 until arr.length()).mapNotNull { arr.optString(it).takeIf(String::isNotBlank) }
                    } ?: emptyList(),
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
        if (prefs.getBoolean("seed_exercises_v31", false)) return loaded
        val knownNames = loaded.map { it.name.trim().lowercase(Locale.GERMAN) }.toSet()
        val additions = starterExerciseCatalog().filter { it.name.lowercase(Locale.GERMAN) !in knownNames }
        val merged = loaded + additions
        if (additions.isNotEmpty()) saveExercises(merged)
        prefs.edit().putBoolean("seed_exercises_v31", true).apply()
        return merged
    }

    private fun saveExercises(list: List<Exercise>) {
        val arr = JSONArray()
        list.forEach { ex ->
            arr.put(
                JSONObject()
                    .put("id", ex.id)
                    .put("name", ex.name)
                    .put("muscle", ex.muscle)
                    .put("primaryMuscleId", ex.primaryMuscleId)
                    .put("secondaryMuscleIds", JSONArray(ex.secondaryMuscleIds))
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
                seconds = o.optInt("seconds", 0),
                workoutId = o.optString("workoutId", ""),
                distanceKm = o.optDouble("distanceKm", 0.0).toFloat()
            )
        }
    } catch (_: Exception) { emptyList() }

    private fun saveExerciseLogs(logs: List<ExerciseLog>) {
        val arr = JSONArray()
        logs.forEach { log ->
            arr.put(
                JSONObject()
                    .put("id", log.id)
                    .put("exerciseId", log.exerciseId)
                    .put("date", log.date)
                    .put("weightKg", log.weightKg.toDouble())
                    .put("reps", log.reps)
                    .put("seconds", log.seconds)
                    .put("workoutId", log.workoutId)
                    .put("distanceKm", log.distanceKm.toDouble())
            )
        }
        prefs.edit().putString("exercise_logs_v1", arr.toString()).apply()
    }

    private fun loadWorkouts(availableExercises: List<Exercise>): List<Workout> = try {
        val raw = prefs.getString("workouts_v1", null)
        if (raw == null) {
            val defaultIds = availableExercises.map { it.id }
            val defaults = if (defaultIds.isNotEmpty()) {
                listOf(
                    Workout(
                        id = "wk-default-1",
                        name = "Workout #1 · Oberkörper & Core",
                        exerciseIds = defaultIds,
                        note = "Basis-Routine"
                    )
                )
            } else {
                emptyList()
            }
            saveWorkouts(defaults)
            defaults
        } else {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val idsArr = o.optJSONArray("exerciseIds")
                val ids = if (idsArr != null) {
                    (0 until idsArr.length()).mapNotNull { idx -> idsArr.optString(idx, "").takeIf { it.isNotBlank() } }
                } else emptyList()
                Workout(
                    id = o.getString("id"),
                    name = o.optString("name", "Workout").ifBlank { "Workout" },
                    exerciseIds = ids,
                    note = o.optString("note", ""),
                    kind = runCatching { WorkoutKind.valueOf(o.optString("kind", "CLASSIC")) }.getOrDefault(WorkoutKind.CLASSIC),
                    durationSeconds = o.optInt("durationSeconds", 0)
                )
            }
        }
    } catch (_: Exception) { emptyList() }

    private fun saveWorkouts(list: List<Workout>) {
        val arr = JSONArray()
        list.forEach { w ->
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("name", w.name)
                    .put("exerciseIds", JSONArray(w.exerciseIds))
                    .put("note", w.note)
                    .put("kind", w.kind.name)
                    .put("durationSeconds", w.durationSeconds)
            )
        }
        prefs.edit().putString("workouts_v1", arr.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NPlannerRoomBridge.start(applicationContext, prefs)
        setContent {
            val ndot = FontFamily(androidx.compose.ui.text.font.Font(resId = R.font.ndot_47))
            // NType 82 is supplied separately as app/src/main/res/font/ntype_82.ttf.
            // Resolve dynamically so an absent file does not break compilation.
            val ntypeId = resources.getIdentifier("ntype_82", "font", packageName)
            val classicHeadings = if (ntypeId != 0) {
                FontFamily(androidx.compose.ui.text.font.Font(resId = ntypeId))
            } else FontFamily.Default
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
                CompositionLocalProvider(
                    LocalContentColor provides White,
                    LocalClassicHeadings provides classicHeadings
                ) {
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
        var workouts by remember { mutableStateOf(loadWorkouts(exercises)) }
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
            val items = JSONArray()
            snap.dailyRules.forEach { r ->
                items.put(JSONArray().put(r.name).put(doneCount(r, d)).put(totalOf(r)).put(if (r.optional) 1 else 0))
            }
            snap.weeklyDoneRules.forEach { r ->
                items.put(JSONArray().put("${r.name} (Wochenziel)").put(1).put(1).put(if (r.optional) 1 else 0))
            }
            val obj = JSONObject().put("done", snap.done).put("total", snap.total).put("items", items)
            prefs.edit().putString("snap:$d", obj.toString()).apply()
        }

        fun freezeHistoryBeforeRuleChange() {
            val yesterday = date.minusDays(1)
            val dates = rules.mapNotNull { runCatching { LocalDate.parse(it.createdDate) }.getOrNull() }
            val earliest = dates.minOrNull() ?: return
            var d = maxOf(earliest, yesterday.minusYears(10))
            while (!d.isAfter(yesterday)) {
                val old = prefs.getString("snap:$d", null)
                if (old == null) {
                    persistTodaySnapshot(d)
                } else {
                    // Enrich old aggregate-only snapshots, preserving their original totals.
                    val obj = runCatching { JSONObject(old) }.getOrNull()
                    if (obj != null && !obj.has("items")) {
                        val snap = daySnapshot(d)
                        val items = JSONArray()
                        snap.dailyRules.forEach { r ->
                            items.put(JSONArray().put(r.name).put(doneCount(r, d)).put(totalOf(r)).put(if (r.optional) 1 else 0))
                        }
                        snap.weeklyDoneRules.forEach { r ->
                            items.put(JSONArray().put("${r.name} (Wochenziel)").put(1).put(1).put(if (r.optional) 1 else 0))
                        }
                        prefs.edit().putString("snap:$d", obj.put("items", items).toString()).apply()
                    }
                }
                d = d.plusDays(1)
            }
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
                    val arr = o.optJSONArray("items")
                    val items = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                        runCatching {
                            val a = arr.getJSONArray(i)
                            HistoricalTask(a.getString(0), a.getInt(1), a.getInt(2), a.optInt(3) == 1)
                        }.getOrNull()
                    }
                    live.copy(done = sDone, total = sTotal, historicalTasks = items)
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
            freezeHistoryBeforeRuleChange()
            rules = rules.filterNot { it.id == ruleId }
            saveRules(rules)
            persistTodaySnapshot(date)
            triggerHaptic(context, haptic, HapticStyle.UNDO)
            tick++
        }

        val pagerState = rememberPagerState(pageCount = { 4 })
        val scope = rememberCoroutineScope()
        var directTarget by remember { mutableStateOf<Int?>(null) }
        var overlayPage by remember { mutableIntStateOf(0) }
        var directSwitching by remember { mutableStateOf(false) }
        var directSource by remember { mutableIntStateOf(0) }
        val directProgress = remember { Animatable(0f) }

        fun selectPage(target: Int) {
            if (target !in 0..3 || directSwitching || target == pagerState.currentPage) return
            triggerHaptic(context, haptic, HapticStyle.TICK)
            directSource = pagerState.currentPage
            directTarget = target
            directSwitching = true
            scope.launch {
                try {
                    directProgress.snapTo(0f)
                    // Pager jumps behind the overlay. The user only sees source and target.
                    pagerState.scrollToPage(target)
                    directProgress.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
                } finally {
                    directTarget = null
                    directSwitching = false
                    directProgress.snapTo(0f)
                }
            }
        }

        @Composable
        fun PageContent(p: Int) {
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
                            workouts = workouts,
                            exercises = exercises,
                            logs = exerciseLogs,
                            onCreateWorkout = { newWorkout ->
                                workouts = workouts + newWorkout
                                saveWorkouts(workouts)
                                triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                            },
                            onUpdateWorkout = { updated ->
                                workouts = workouts.map { if (it.id == updated.id) updated else it }
                                saveWorkouts(workouts)
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                            },
                            onDeleteWorkout = { workoutId ->
                                workouts = workouts.filterNot { it.id == workoutId }
                                saveWorkouts(workouts)
                                triggerHaptic(context, haptic, HapticStyle.UNDO)
                            },
                            onAddExercise = { newEx, targetWorkoutId ->
                                exercises = exercises + newEx
                                saveExercises(exercises)
                                if (targetWorkoutId != null) {
                                    workouts = workouts.map { w ->
                                        if (w.id == targetWorkoutId && newEx.id !in w.exerciseIds) {
                                            w.copy(exerciseIds = w.exerciseIds + newEx.id)
                                        } else w
                                    }
                                    saveWorkouts(workouts)
                                }
                                triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                            },
                            onImportExercises = { imported, targetWorkoutId ->
                                exercises = exercises + imported
                                saveExercises(exercises)
                                if (targetWorkoutId != null) {
                                    val newIds = imported.map { it.id }
                                    workouts = workouts.map { w ->
                                        if (w.id == targetWorkoutId) {
                                            w.copy(exerciseIds = (w.exerciseIds + newIds).distinct())
                                        } else w
                                    }
                                    saveWorkouts(workouts)
                                }
                                triggerHaptic(context, haptic, HapticStyle.DAY_COMPLETE)
                            },
                            onDeleteExercise = { exId ->
                                exercises.find { it.id == exId }?.mediaPath?.takeIf { it.isNotBlank() }?.let { path ->
                                    runCatching { File(path).delete() }
                                }
                                exercises = exercises.filterNot { it.id == exId }
                                saveExercises(exercises)
                                workouts = workouts.map { w -> w.copy(exerciseIds = w.exerciseIds.filterNot { it == exId }) }
                                saveWorkouts(workouts)
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

        CompositionLocalProvider(LocalDotHeadings provides dotHeadings) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    userScrollEnabled = !directSwitching,
                    modifier = Modifier.fillMaxSize()
                ) { p ->
                    PageContent(p)
                }
                directTarget?.let { target ->
                    val forward = target > directSource
                    BoxWithConstraints(Modifier.fillMaxSize().background(Bg)) {
                        val widthPx = with(LocalDensity.current) { maxWidth.toPx() }
                        val shift = directProgress.value * widthPx
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            translationX = if (forward) -shift else shift
                        }) { PageContent(directSource) }
                        Box(Modifier.fillMaxSize().graphicsLayer {
                            translationX = if (forward) widthPx - shift else -widthPx + shift
                        }) { PageContent(target) }
                    }
                }
                GlyphNav(
                    pagerState = pagerState,
                    directTarget = directTarget,
                    select = ::selectPage,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (addDialog) NewTaskDialog(
                ndot = ndot,
                onDismiss = { addDialog = false },
                onCreate = { rule ->
                    freezeHistoryBeforeRuleChange()
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

private data class HistoricalTask(val name: String, val done: Int, val target: Int, val optional: Boolean)

private data class DaySnapshot(
    val date: LocalDate,
    val done: Int,
    val total: Int,
    val dailyRules: List<Rule>,
    val weeklyDoneRules: List<Rule>,
    val historicalTasks: List<HistoricalTask> = emptyList()
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
        fontFamily = if (useDot) ndot else LocalClassicHeadings.current,
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
        fontFamily = if (useDot) ndot else LocalClassicHeadings.current,
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

            if (snap.historicalTasks.isNotEmpty()) {
                HorizontalDivider(color = Color(0xFF282828), modifier = Modifier.padding(vertical = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snap.historicalTasks.forEach { item ->
                        val isDone = item.done >= item.target
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (isDone) Red else Color(0xFF333333), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(item.name + if (item.optional) " (Optional)" else "", color = if (isDone) White else Muted,
                                fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(if (isDone) "✓" else "${item.done}/${item.target}",
                                color = if (isDone) Red else Muted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (snap.dailyRules.isNotEmpty() || snap.weeklyDoneRules.isNotEmpty()) {
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

// ---------- Workouts, Uebungen & Rekorde (Reiter 3) ----------

private fun suggestWorkoutName(existing: List<Workout>, selectedExercises: List<Exercise>): String {
    val now = java.time.LocalDateTime.now()
    val day = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")[now.dayOfWeek.value - 1]
    val time = when (now.hour) {
        in 5..9 -> "Morgens"
        in 10..11 -> "Vormittags"
        12 -> "Mittags"
        in 13..16 -> "Nachmittags"
        else -> "Abends"
    }
    val base = "Workout • $day $time"
    val used = existing.map { it.name.trim() }.toSet()
    if (base !in used) return base
    var number = 2
    while ("$base ($number)" in used) number++
    return "$base ($number)"
}

private fun bestRecordText(ex: Exercise, logs: List<ExerciseLog>): Pair<String, String>? {
    if (logs.isEmpty()) return null
    return when (ex.kind) {
        ExerciseKind.WEIGHT -> {
            val best = logs.maxWithOrNull(compareBy<ExerciseLog> { it.weightKg }.thenBy { it.reps }) ?: return null
            "${fmtKg(best.weightKg)} × ${best.reps}" to best.date
        }
        ExerciseKind.BODYWEIGHT -> {
            val best = logs.maxByOrNull { it.reps } ?: return null
            "${best.reps} Wdh." to best.date
        }
        ExerciseKind.CARDIO -> {
            val best = logs.maxByOrNull { it.seconds } ?: return null
            "${best.seconds / 60} Min." to best.date
        }
        ExerciseKind.AMRAP -> {
            val best = logs.maxByOrNull { it.reps } ?: return null
            "${best.reps} Runden" to best.date
        }
        ExerciseKind.TIMED -> {
            val best = if (ex.targetReps > 1) {
                logs.maxWithOrNull(compareBy<ExerciseLog> { it.reps }.thenBy { -it.seconds })
            } else {
                logs.maxByOrNull { it.seconds }
            } ?: return null
            val txt = if (best.reps > 1) "${best.reps} Wdh. · ${best.seconds}s" else "${best.seconds} Sek."
            txt to best.date
        }
    }
}

@Composable
private fun ExerciseKindIcon(kind: ExerciseKind) {
    Canvas(Modifier.size(27.dp)) {
        val stroke = 2.dp.toPx()
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(White, Offset(size.width*x1, size.height*y1), Offset(size.width*x2, size.height*y2), stroke, StrokeCap.Round)
        when (kind) {
            ExerciseKind.WEIGHT -> {
                line(.25f,.5f,.75f,.5f)
                listOf(.17f,.30f,.70f,.83f).forEach { x -> line(x,.29f,x,.71f) }
            }
            ExerciseKind.BODYWEIGHT -> {
                drawCircle(White, size.width*.09f, Offset(size.width*.5f,size.height*.19f))
                line(.5f,.3f,.5f,.62f); line(.5f,.4f,.24f,.56f); line(.5f,.4f,.76f,.56f)
                line(.5f,.62f,.29f,.88f); line(.5f,.62f,.71f,.88f)
            }
            ExerciseKind.TIMED -> {
                drawCircle(White,size.width*.31f,Offset(size.width*.5f,size.height*.55f),style=Stroke(stroke))
                line(.5f,.55f,.5f,.35f); line(.5f,.55f,.64f,.64f); line(.4f,.13f,.6f,.13f)
                line(.5f,.13f,.5f,.23f)
            }
            ExerciseKind.CARDIO -> {
                val path = Path().apply {
                    moveTo(size.width*.10f,size.height*.55f)
                    lineTo(size.width*.31f,size.height*.55f)
                    lineTo(size.width*.40f,size.height*.31f)
                    lineTo(size.width*.53f,size.height*.77f)
                    lineTo(size.width*.64f,size.height*.49f)
                    lineTo(size.width*.90f,size.height*.49f)
                }
                drawPath(path, White, style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
            }
            ExerciseKind.AMRAP -> {
                drawCircle(White,size.width*.34f,style=Stroke(stroke))
                line(.5f,.28f,.5f,.52f); line(.5f,.52f,.68f,.62f)
            }
        }
    }
}

@Composable
private fun TrainingScreen(
    ndot: FontFamily,
    today: LocalDate,
    workouts: List<Workout>,
    exercises: List<Exercise>,
    logs: List<ExerciseLog>,
    onCreateWorkout: (Workout) -> Unit,
    onUpdateWorkout: (Workout) -> Unit,
    onDeleteWorkout: (String) -> Unit,
    onAddExercise: (Exercise, String?) -> Unit,
    onImportExercises: (List<Exercise>, String?) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onLogSet: (ExerciseLog) -> Unit,
    onDeleteLog: (String) -> Unit,
    onHapticTick: () -> Unit
) {
    val context = LocalContext.current
    // 0 = Workouts (Hauptfokus), 1 = Uebungen, 2 = Rekorde
    var subPage by remember { mutableIntStateOf(0) }
    val sessionPrefs = remember { context.getSharedPreferences("nplanner-training-session", Context.MODE_PRIVATE) }
    var activeWorkoutSessionId by remember { mutableStateOf(sessionPrefs.getString("active_workout", null)) }
    var sessionStartedAt by remember { mutableStateOf(sessionPrefs.getLong("started_at", 0L)) }
    var showAddExistingExercise by remember { mutableStateOf(false) }
    var openWorkoutId by remember { mutableStateOf<String?>(null) }
    fun beginWorkout(id: String) {
        activeWorkoutSessionId = id
        sessionStartedAt = System.currentTimeMillis()
        sessionPrefs.edit().putString("active_workout", id).putLong("started_at", sessionStartedAt).apply()
        openWorkoutId = id
    }
    fun finishWorkout() {
        activeWorkoutSessionId = null
        sessionStartedAt = 0L
        sessionPrefs.edit().remove("active_workout").remove("started_at").apply()
        openWorkoutId = null
    }
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var showNewWorkoutDialog by remember { mutableStateOf(false) }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var showNewExerciseDialog by remember { mutableStateOf(false) }
    var showAiImportDialog by remember { mutableStateOf(false) }
    var pendingExerciseExport by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExerciseExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExerciseExport) }
            }.onFailure { Toast.makeText(context, "Export fehlgeschlagen.", Toast.LENGTH_SHORT).show() }
        }
    }
    fun shareExercise(ex: Exercise) {
        val payload = exportNpefJson(listOf(ex))
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload)
            }, "Übung teilen"))
        }
    }
    var targetWorkoutForNewEx by remember { mutableStateOf<String?>(null) }

    val todayStr = today.toString()
    val todayLogs = remember(logs, todayStr) { logs.filter { it.date == todayStr } }

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
        eyebrow = "WORKOUTS & PROGRESSION.",
        title = "TRAINING",
        subtitle = "${workouts.size} ${if (workouts.size == 1) "Workout" else "Workouts"} · ${todayLogs.size} Sätze heute"
    ) {
        // Subtile Unterseiten-Navigation: Workouts | Übungen | Rekorde
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChoiceChip("Workouts", subPage == 0, Modifier.weight(1f)) {
                if (subPage != 0) {
                    subPage = 0
                    onHapticTick()
                }
            }
            ChoiceChip("Übungen", subPage == 1, Modifier.weight(1f)) {
                if (subPage != 1) {
                    subPage = 1
                    onHapticTick()
                }
            }
            ChoiceChip("Rekorde", subPage == 2, Modifier.weight(1f)) {
                if (subPage != 2) {
                    subPage = 2
                    onHapticTick()
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        activeWorkoutSessionId?.let { id ->
            val running = workouts.firstOrNull { it.id == id }
            if (running != null) {
                Surface(
                    color = Panel2, shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth().clickable { openWorkoutId = id }
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).background(Red, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("TRAINING LÄUFT", color = Red, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                            Text(running.name, color = White, fontSize = 14.sp)
                        }
                        Text("ÖFFNEN ›", color = White, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        when (subPage) {
            // ================= UNTERSEITE 0: WORKOUTS (HAUPTFOKUS) =================
            0 -> {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "DEINE WORKOUTS",
                        color = Muted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.6.sp
                    )
                    TextButton(onClick = {
                        onHapticTick()
                        showNewWorkoutDialog = true
                    }) {
                        Text("+ WORKOUT", color = White, fontFamily = FontFamily.Monospace, fontSize = 11.sp, letterSpacing = 1.4.sp)
                    }
                }

                if (workouts.isEmpty()) {
                    Surface(
                        color = Panel,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onHapticTick()
                                showNewWorkoutDialog = true
                            }
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text("Noch kein Workout erstellt", color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Erstelle ein Workout mit eigenem oder vorgeschlagenem Namen und tippe darauf, um deine Übungen zu verwalten.",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(White)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    "+ WORKOUT ANLEGEN",
                                    color = Bg,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        workouts.forEach { workout ->
                            val wExercises = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
                            val todaySetsInWorkout = todayLogs.count { it.exerciseId in workout.exerciseIds }

                            Surface(
                                color = Panel,
                                shape = RoundedCornerShape(24.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onHapticTick()
                                        openWorkoutId = workout.id
                                    }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Minimalistischer Status-Indikator
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(if (todaySetsInWorkout > 0) Red else Color(0xFF3A3A3A), CircleShape)
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                workout.name,
                                                color = White,
                                                fontSize = 17.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (todaySetsInWorkout > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "${todaySetsInWorkout}× HEUTE",
                                                    color = Red,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    letterSpacing = 1.sp
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val previewNames = if (wExercises.isEmpty()) {
                                            "Tippen, um Übungen hinzuzufügen"
                                        } else {
                                            wExercises.take(3).joinToString(" · ") { it.name } +
                                                if (wExercises.size > 3) " +${wExercises.size - 3}" else ""
                                        }
                                        Text(
                                            previewNames,
                                            color = Muted,
                                            fontSize = 11.sp,
                                            maxLines = 1
                                        )
                                    }
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "${wExercises.size}",
                                        color = Muted,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("›", color = Muted, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

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

            // ================= UNTERSEITE 1: ALLE ÜBUNGEN =================
            1 -> {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clickable {
                                onHapticTick()
                                targetWorkoutForNewEx = null
                                showNewExerciseDialog = true
                            },
                        color = White,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "+ ÜBUNG",
                                color = Bg,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.1.sp
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .weight(1.3f)
                            .height(44.dp)
                            .clickable { shareAiPromptToExternalApp() },
                        color = Panel,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Box(Modifier.size(6.dp).background(Red, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "MIT KI ANLEGEN",
                                color = White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier
                            .height(44.dp)
                            .clickable {
                                onHapticTick()
                                targetWorkoutForNewEx = null
                                showAiImportDialog = true
                            },
                        color = Panel2,
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "JSON",
                                color = White,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                }

                SectionLabel("ALLE ÜBUNGEN (${exercises.size})", topPadding = 18.dp, bottomPadding = 10.dp)

                if (exercises.isEmpty()) {
                    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            "Noch keine Übungen vorhanden.",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        exercises.forEach { ex ->
                            val exLogs = logs.filter { it.exerciseId == ex.id }
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
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(13.dp))
                                            .background(Panel2),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        ExerciseKindIcon(ex.kind)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(2.dp))
                                        Text(exerciseSummary(ex), color = Muted, fontSize = 11.sp)
                                        if (lastLog != null) {
                                            Spacer(Modifier.height(2.dp))
                                            Text(
                                                "Zuletzt: ${logSummary(ex, lastLog)}",
                                                color = Color(0xFFB4B4B0),
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                    Text("›", color = Muted, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }
            }

            // ================= UNTERSEITE 2: REKORDE (PRs) =================
            else -> {
                val records = remember(exercises, logs) {
                    exercises.mapNotNull { ex ->
                        val exLogs = logs.filter { it.exerciseId == ex.id }
                        bestRecordText(ex, exLogs)?.let { pr -> Triple(ex, pr.first, pr.second) }
                    }
                }

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(Red, CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text("REKORDE", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.4.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            DisplayTitle("${records.size}", ndot, fontSize = 32.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(White, CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text("SÄTZE GESAMT", color = Muted, fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.4.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            DisplayTitle("${logs.size}", ndot, fontSize = 32.sp)
                        }
                    }
                }

                SectionLabel("PERSÖNLICHE BESTLEISTUNGEN", topPadding = 20.dp, bottomPadding = 10.dp)

                if (records.isEmpty()) {
                    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(18.dp)) {
                            Text("Noch keine Rekorde eingetragen", color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Öffne ein Workout und trage einen Satz bei einer Übung ein – deine Bestleistung erscheint automatisch hier.",
                                color = Muted,
                                fontSize = 12.sp,
                                lineHeight = 17.sp
                            )
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        records.forEach { (ex, prValue, prDate) ->
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
                                    Modifier.padding(horizontal = 18.dp, vertical = 15.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.name, color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            "${ex.muscle} · $prDate",
                                            color = Muted,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp
                                        )
                                    }
                                    Text(
                                        prValue,
                                        color = Red,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Subtiles Overlay-Dialog-Fenster fuer das geoeffnete Workout (hier erscheinen die Uebungen)
    openWorkoutId?.let { wId ->
        val workout = workouts.find { it.id == wId }
        if (workout == null) {
            openWorkoutId = null
        } else {
            WorkoutSheetDialog(
                ndot = ndot,
                todayStr = todayStr,
                workout = workout,
                exercises = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } },
                logs = logs,
                onDismiss = { openWorkoutId = null },
                isActive = activeWorkoutSessionId == workout.id,
                startedAt = sessionStartedAt,
                onStartWorkout = { beginWorkout(workout.id) },
                onFinishWorkout = { finishWorkout() },
                onAddExistingExercise = { showAddExistingExercise = true },
                onSelectExercise = { ex ->
                    onHapticTick()
                    activeExercise = ex
                },
                onAddNewExercise = {
                    onHapticTick()
                    targetWorkoutForNewEx = workout.id
                    showNewExerciseDialog = true
                },
                onImportAiExercise = {
                    onHapticTick()
                    targetWorkoutForNewEx = workout.id
                    showAiImportDialog = true
                },
                onEditWorkout = {
                    onHapticTick()
                    editingWorkout = workout
                },
                onDeleteWorkout = {
                    onDeleteWorkout(workout.id)
                    openWorkoutId = null
                }
            )
        }
    }

    if (showAddExistingExercise) {
        val current = openWorkoutId?.let { id -> workouts.firstOrNull { it.id == id } }
        if (current == null) {
            showAddExistingExercise = false
        } else {
            AlertDialog(
                onDismissRequest = { showAddExistingExercise = false },
                title = { Text("ÜBUNG HINZUFÜGEN", color = White) },
                text = {
                    Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                        exercises.filterNot { it.id in current.exerciseIds }.forEach { ex ->
                            Text(
                                ex.name,
                                color = White,
                                modifier = Modifier.fillMaxWidth().clickable {
                                    onUpdateWorkout(current.copy(exerciseIds = current.exerciseIds + ex.id))
                                    showAddExistingExercise = false
                                }.padding(vertical = 12.dp)
                            )
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { showAddExistingExercise = false }) { Text("Schließen") } },
                containerColor = Panel
            )
        }
    }

    if (showNewWorkoutDialog) {
        WorkoutEditorDialog(
            ndot = ndot,
            title = "NEUES WORKOUT",
            initialName = suggestWorkoutName(workouts, emptyList()),
            initialNote = "",
            initialSelectedIds = emptySet(),
            initialKind = WorkoutKind.CLASSIC,
            initialDurationSeconds = 0,
            existingWorkouts = workouts,
            allExercises = exercises,
            isNew = true,
            onAddPresetExercises = { missing -> onImportExercises(missing, null) },
            onDismiss = { showNewWorkoutDialog = false },
            onSave = { finalName, note, selectedIds, kind, durationSeconds ->
                val created = Workout(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    exerciseIds = selectedIds,
                    note = note,
                    kind = kind,
                    durationSeconds = durationSeconds
                )
                onCreateWorkout(created)
                showNewWorkoutDialog = false
                beginWorkout(created.id)
            }
        )
    }

    editingWorkout?.let { wToEdit ->
        WorkoutEditorDialog(
            ndot = ndot,
            title = "WORKOUT BEARBEITEN",
            initialName = wToEdit.name,
            initialNote = wToEdit.note,
            initialSelectedIds = wToEdit.exerciseIds.toSet(),
            initialKind = wToEdit.kind,
            initialDurationSeconds = wToEdit.durationSeconds,
            existingWorkouts = workouts,
            allExercises = exercises,
            isNew = false,
            onAddPresetExercises = { missing -> onImportExercises(missing, null) },
            onDismiss = { editingWorkout = null },
            onSave = { finalName, note, selectedIds, kind, durationSeconds ->
                onUpdateWorkout(
                    wToEdit.copy(
                        name = finalName,
                        note = note,
                        exerciseIds = selectedIds,
                        kind = kind,
                        durationSeconds = durationSeconds
                    )
                )
                editingWorkout = null
            }
        )
    }

    activeExercise?.let { currentEx ->
        val freshEx = exercises.find { it.id == currentEx.id }
        if (freshEx == null) {
            activeExercise = null
        } else {
            val activeWorkoutObj = openWorkoutId?.let { wid -> workouts.find { it.id == wid } }
            ExerciseDetailDialog(
                ndot = ndot,
                today = today,
                exercise = freshEx,
                logs = logs.filter { it.exerciseId == freshEx.id },
                onDismiss = { activeExercise = null },
                onLogSet = { log -> onLogSet(log.copy(workoutId = activeWorkoutSessionId.orEmpty())) },
                onRemoveFromWorkout = activeWorkoutObj?.let { w ->
                    {
                        onUpdateWorkout(w.copy(exerciseIds = w.exerciseIds.filterNot { it == freshEx.id }))
                        activeExercise = null
                    }
                },
                onShareExercise = { shareExercise(freshEx) },
                onExportExercise = {
                    pendingExerciseExport = exportNpefJson(listOf(freshEx))
                    exportLauncher.launch("${freshEx.name.replace(Regex("[^A-Za-z0-9_-]"), "_")}.npex.json")
                },
                onDeleteExercise = {
                    onDeleteExercise(freshEx.id)
                    activeExercise = null
                },
                onHapticTick = onHapticTick
            )
        }
    }

    if (showNewExerciseDialog) {
        val targetName = targetWorkoutForNewEx?.let { wid -> workouts.find { it.id == wid }?.name }
        NewExerciseDialog(
            ndot = ndot,
            targetWorkoutName = targetName,
            onDismiss = { showNewExerciseDialog = false },
            onCreate = { created ->
                onAddExercise(created, targetWorkoutForNewEx)
                showNewExerciseDialog = false
            }
        )
    }

    if (showAiImportDialog) {
        val targetName = targetWorkoutForNewEx?.let { wid -> workouts.find { it.id == wid }?.name }
        AiExerciseImportDialog(
            ndot = ndot,
            targetWorkoutName = targetName,
            existingExercises = exercises,
            onDismiss = { showAiImportDialog = false },
            onSharePrompt = { shareAiPromptToExternalApp() },
            onImport = { imported ->
                onImportExercises(imported, targetWorkoutForNewEx)
                showAiImportDialog = false
            }
        )
    }
}

@Composable
private fun WorkoutSheetDialog(
    ndot: FontFamily,
    todayStr: String,
    workout: Workout,
    exercises: List<Exercise>,
    logs: List<ExerciseLog>,
    onDismiss: () -> Unit,
    onSelectExercise: (Exercise) -> Unit,
    isActive: Boolean,
    startedAt: Long,
    onStartWorkout: () -> Unit,
    onFinishWorkout: () -> Unit,
    onAddExistingExercise: () -> Unit,
    onAddNewExercise: () -> Unit,
    onImportAiExercise: () -> Unit,
    onEditWorkout: () -> Unit,
    onDeleteWorkout: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(
                Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "WORKOUT · ${exercises.size} ÜBUNGEN",
                        color = Red,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        letterSpacing = 1.5.sp
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Panel2)
                                .clickable(onClick = onEditWorkout),
                            contentAlignment = Alignment.Center
                        ) { Text("✎", color = White, fontSize = 13.sp) }
                        Box(
                            Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Panel2)
                                .clickable(onClick = onDeleteWorkout),
                            contentAlignment = Alignment.Center
                        ) { Text("✕", color = Red, fontSize = 13.sp) }
                    }
                }

                Spacer(Modifier.height(4.dp))
                DisplayTitle(workout.name, ndot, fontSize = 24.sp)
                if (workout.note.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(workout.note, color = Muted, fontSize = 12.sp)
                }

                Spacer(Modifier.height(14.dp))

                Surface(
                    color = if (isActive) Panel2 else White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp).clickable {
                        if (isActive) onFinishWorkout() else onStartWorkout()
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(if (isActive) "TRAINING BEENDEN" else "WORKOUT STARTEN",
                            color = if (isActive) White else Bg, fontSize = 13.sp)
                    }
                }
                if (isActive) {
                    Text("Training gestartet: " + java.text.SimpleDateFormat("HH:mm", Locale.GERMAN).format(java.util.Date(startedAt)),
                        color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp))
                }
                Spacer(Modifier.height(12.dp))

                // Subtile Aktionsleiste fuer neue Uebungen im Workout
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ChoiceChip("+ Vorhandene", true, Modifier.weight(1f)) { onAddExistingExercise() }
                    ChoiceChip("+ Neu", false, Modifier.weight(1f)) { onAddNewExercise() }
                    ChoiceChip("KI / JSON", false, Modifier.weight(1f)) { onImportAiExercise() }
                }

                Spacer(Modifier.height(14.dp))

                if (exercises.isEmpty()) {
                    Surface(
                        color = Panel2,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Noch keine Übungen in diesem Workout. Tippe oben auf „+ Übung“, „KI / JSON“ oder „✎“.",
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        exercises.forEach { ex ->
                            val exLogs = logs.filter { it.exerciseId == ex.id }
                            val todaySets = exLogs.count { it.date == todayStr }
                            val lastLog = exLogs.lastOrNull()

                            Surface(
                                color = Panel2,
                                shape = RoundedCornerShape(18.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectExercise(ex) }
                            ) {
                                Row(
                                    Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier
                                            .size(8.dp)
                                            .background(if (todaySets > 0) Red else Color(0xFF454545), CircleShape)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                ex.name,
                                                color = White,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f, fill = false)
                                            )
                                            if (todaySets > 0) {
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    "${todaySets}×",
                                                    color = Red,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            if (lastLog != null) "Zuletzt: ${logSummary(ex, lastLog)}" else exerciseSummary(ex),
                                            color = Muted,
                                            fontSize = 11.sp
                                        )
                                    }
                                    Text("›", color = Muted, fontSize = 20.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Schließen") }
                }
            }
        }
    }
}

private data class WorkoutPreset(
    val title: String,
    val kind: WorkoutKind,
    val minutes: Int,
    val exerciseNames: List<String>,
    val description: String
)

private fun presetExercise(name: String): Exercise? {
    val options = listOf(
        Exercise("ex-pullups", "Klimmzüge", "Rücken", primaryMuscleId = "lats", kind = ExerciseKind.BODYWEIGHT, targetReps = 5),
        Exercise("ex-squats", "Kniebeugen", "Beine", primaryMuscleId = "quads", kind = ExerciseKind.BODYWEIGHT, targetReps = 15),
        Exercise("ex-pushups", "Liegestütze", "Brust", primaryMuscleId = "chest", kind = ExerciseKind.BODYWEIGHT, targetReps = 10),
        Exercise("ex-bench-press", "Bankdrücken", "Brust", primaryMuscleId = "chest", kind = ExerciseKind.WEIGHT, targetWeightKg = 60f, targetReps = 8),
        Exercise("ex-rowing", "Rudern", "Rücken", primaryMuscleId = "back", kind = ExerciseKind.WEIGHT, targetWeightKg = 35f, targetReps = 10),
        Exercise("ex-shoulder-press", "Schulterdrücken", "Schultern", primaryMuscleId = "front_delts", kind = ExerciseKind.WEIGHT, targetWeightKg = 20f, targetReps = 10),
        Exercise("ex-deadlift", "Kreuzheben", "Rücken", primaryMuscleId = "lower_back", kind = ExerciseKind.WEIGHT, targetWeightKg = 50f, targetReps = 8),
        Exercise("ex-lunges", "Ausfallschritte", "Beine", primaryMuscleId = "quads", kind = ExerciseKind.BODYWEIGHT, targetReps = 12),
        Exercise("ex-calf-raises", "Wadenheben", "Waden", primaryMuscleId = "calves", kind = ExerciseKind.BODYWEIGHT, targetReps = 20),
        Exercise("ex-running", "Laufen", "Beine", primaryMuscleId = "legs", kind = ExerciseKind.CARDIO, targetSeconds = 1200),
        Exercise("ex-row-erg", "Ruderergometer", "Rücken", primaryMuscleId = "back", kind = ExerciseKind.CARDIO, targetSeconds = 1200),
        Exercise("ex-cycling", "Radfahren", "Beine", primaryMuscleId = "legs", kind = ExerciseKind.CARDIO, targetSeconds = 1800)
    )
    return options.firstOrNull { it.name.equals(name, ignoreCase = true) }
}

private val workoutPresets = listOf(
    WorkoutPreset("Cindy", WorkoutKind.AMRAP, 20,
        listOf("Klimmzüge", "Liegestütze", "Kniebeugen"),
        "20 Min. AMRAP: je Runde 5 Klimmzüge, 10 Liegestütze, 15 Kniebeugen."),
    WorkoutPreset("Oberkörper", WorkoutKind.CLASSIC, 0,
        listOf("Bankdrücken", "Rudern", "Schulterdrücken", "Klimmzüge"),
        "Oberkörper: Drücken und Ziehen im Wechsel."),
    WorkoutPreset("Unterkörper", WorkoutKind.CLASSIC, 0,
        listOf("Kniebeugen", "Ausfallschritte", "Kreuzheben", "Wadenheben"),
        "Unterkörper mit Kraft- und Stabilitätsübungen."),
    WorkoutPreset("Cardio-Mix", WorkoutKind.CLASSIC, 0,
        listOf("Laufen", "Ruderergometer", "Radfahren"),
        "Ausdauertraining mit drei Cardio-Stationen.")
)

@Composable
private fun WorkoutEditorDialog(
    ndot: FontFamily,
    title: String,
    initialName: String,
    initialNote: String,
    initialSelectedIds: Set<String>,
    initialKind: WorkoutKind,
    initialDurationSeconds: Int,
    existingWorkouts: List<Workout>,
    allExercises: List<Exercise>,
    isNew: Boolean,
    onAddPresetExercises: (List<Exercise>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, WorkoutKind, Int) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf(initialName) }
    var nameManuallyEdited by remember { mutableStateOf(!isNew) }
    var note by remember { mutableStateOf(initialNote) }
    var workoutKind by remember { mutableStateOf(initialKind) }
    var durationText by remember { mutableStateOf((initialDurationSeconds / 60).takeIf { it > 0 }?.toString() ?: "20") }
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var presetNotice by remember { mutableStateOf("") }

    // Wenn noch kein eigener Name eingegeben wurde, passt sich der Vorschlag an die gewaehlten Uebungen an
    LaunchedEffect(selectedIds) {
        if (isNew && !nameManuallyEdited) {
            val chosen = allExercises.filter { it.id in selectedIds }
            name = suggestWorkoutName(existingWorkouts, chosen)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle(title, ndot, fontSize = 24.sp)
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(48)
                        nameManuallyEdited = true
                    },
                    label = { Text("Workout-Name (Vorschlag anpassbar)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(80) },
                    label = { Text("Notiz / Fokus (optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel("WORKOUT-ART", topPadding = 14.dp, bottomPadding = 8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        WorkoutKind.CLASSIC to "Klassisch",
                        WorkoutKind.AMRAP to "AMRAP",
                        WorkoutKind.FOR_TIME to "For Time",
                        WorkoutKind.EMOM to "EMOM"
                    ).forEach { (kind, label) ->
                        ChoiceChip(label, workoutKind == kind, Modifier.width(108.dp)) { workoutKind = kind }
                    }
                }
                if (workoutKind != WorkoutKind.CLASSIC) {
                    OutlinedTextField(durationText, { durationText = it.filter(Char::isDigit).take(3) },
                        label = { Text(if (workoutKind == WorkoutKind.FOR_TIME) "Zeitlimit (Minuten)" else "Dauer (Minuten)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Surface(
                    color = Panel2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showPresetPicker = true }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("WORKOUT-VORLAGE WÄHLEN", color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("›", color = Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Panel2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showExercisePicker = true }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("ÜBUNGEN AUSWÄHLEN", color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${selectedIds.size}  ›", color = Muted)
                    }
                }
                if (presetNotice.isNotBlank()) Text(presetNotice, color = Muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp))

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(
                        onClick = {
                            val fallback = suggestWorkoutName(
                                existingWorkouts,
                                allExercises.filter { it.id in selectedIds }
                            )
                            val finalName = name.trim().ifBlank { fallback }
                            val orderedIds = allExercises.filter { it.id in selectedIds }.map { it.id }
                            onSave(finalName, note.trim(), orderedIds, workoutKind,
                                if (workoutKind == WorkoutKind.CLASSIC) 0 else (durationText.toIntOrNull() ?: 20).coerceIn(1, 480) * 60)
                        }
                    ) { Text(if (isNew) "Erstellen" else "Speichern") }
                }
            }
        }
    }
    if (showPresetPicker) {
        AlertDialog(
            onDismissRequest = { showPresetPicker = false },
            title = { Text("WORKOUT-VORLAGEN", color = White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workoutPresets.forEach { preset ->
                        Surface(color = Panel2, shape = RoundedCornerShape(15.dp),
                            modifier = Modifier.fillMaxWidth().clickable {
                                workoutKind = preset.kind
                                durationText = (preset.minutes.takeIf { it > 0 } ?: 20).toString()
                                name = preset.title
                                nameManuallyEdited = true
                                note = preset.description
                                val existing = allExercises.associateBy { it.name.trim().lowercase(Locale.GERMAN) }
                                val missing = preset.exerciseNames.mapNotNull { n ->
                                    if (n.trim().lowercase(Locale.GERMAN) in existing) null
                                    else presetExercise(n)?.copy(id = UUID.randomUUID().toString())
                                }
                                if (missing.isNotEmpty()) onAddPresetExercises(missing)
                                val selected = preset.exerciseNames.mapNotNull { n ->
                                    existing[n.trim().lowercase(Locale.GERMAN)]?.id
                                        ?: missing.firstOrNull { it.name.equals(n, ignoreCase = true) }?.id
                                }
                                selectedIds = selected.toSet()
                                presetNotice = "${selected.size}/${preset.exerciseNames.size} Übungen übernommen."
                                showPresetPicker = false
                            }) {
                            Column(Modifier.padding(13.dp)) {
                                Text(preset.title, color = White, fontSize = 15.sp)
                                Text("${preset.kind.name} · ${preset.exerciseNames.joinToString(", ")}", color = Muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresetPicker = false }) { Text("Schließen") } },
            containerColor = Panel
        )
    }
    if (showExercisePicker) {
        AlertDialog(
            onDismissRequest = { showExercisePicker = false },
            title = { Text("ÜBUNGEN AUSWÄHLEN", color = White) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allExercises.forEach { ex ->
                        val included = ex.id in selectedIds
                        Row(Modifier.fillMaxWidth().clickable {
                            selectedIds = if (included) selectedIds - ex.id else selectedIds + ex.id
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(if (included) "✓" else "○", color = if (included) Red else Muted, fontSize = 18.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(ex.name, color = White, fontSize = 14.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExercisePicker = false }) { Text("Übernehmen") } },
            containerColor = Panel
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
    onRemoveFromWorkout: (() -> Unit)?,
    onDeleteExercise: () -> Unit,
    onShareExercise: () -> Unit,
    onExportExercise: () -> Unit,
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
    var distanceText by remember(exercise.id) { mutableStateOf((lastLog?.distanceKm ?: 0f).takeIf { it > 0f }?.toString() ?: "") }
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
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.weight(1f)
                    )
                    if (onRemoveFromWorkout != null) {
                        TextButton(onClick = onRemoveFromWorkout) {
                            Text("Entfernen", color = Muted, fontSize = 11.sp)
                        }
                    }
                    TextButton(onClick = onDeleteExercise) {
                        Text("Löschen", color = Red, fontSize = 11.sp)
                    }
                }

                DisplayTitle(exercise.name, ndot, fontSize = 26.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onShareExercise) { Text("Teilen ↗", color = White) }
                    TextButton(onClick = onExportExercise) { Text("Als Datei exportieren", color = Muted) }
                }

                if (exercise.description.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(exercise.description, color = Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }

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

                SectionLabel("SATZ EINTRAGEN", topPadding = 18.dp, bottomPadding = 10.dp)
                when (exercise.kind) {
                    ExerciseKind.CARDIO, ExerciseKind.AMRAP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(secondsText, { secondsText = it.filter(Char::isDigit).take(5) },
                                label = { Text("Dauer (Sek.)") }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            if (exercise.kind == ExerciseKind.CARDIO) {
                                OutlinedTextField(distanceText, { distanceText = it.take(8) },
                                    label = { Text("Distanz (km)") }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                            if (exercise.kind == ExerciseKind.AMRAP) {
                                OutlinedTextField(repsText, { repsText = it.filter(Char::isDigit).take(4) },
                                    label = { Text("Runden") }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }
                    }

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
                                    reps = r,
                                    seconds = if (exercise.kind in listOf(ExerciseKind.TIMED, ExerciseKind.CARDIO, ExerciseKind.AMRAP)) s else 0,
                                    distanceKm = if (exercise.kind == ExerciseKind.CARDIO) distanceText.replace(',', '.').toFloatOrNull()?.coerceIn(0f, 1000f) ?: 0f else 0f
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

private fun validatedMediaUrl(raw: String): Uri? = runCatching {
    val uri = Uri.parse(raw.trim())
    if (uri.scheme?.lowercase(Locale.ROOT) != "https" || uri.host.isNullOrBlank()) null else uri
}.getOrNull()

private fun youtubeVideoId(uri: Uri): String? {
    val host = uri.host?.lowercase(Locale.ROOT)?.removePrefix("www.") ?: return null
    val candidate = when (host) {
        "youtu.be" -> uri.pathSegments.firstOrNull()
        "youtube.com", "m.youtube.com", "music.youtube.com" -> when {
            uri.path == "/watch" -> uri.getQueryParameter("v")
            uri.pathSegments.firstOrNull() in listOf("shorts", "embed", "live") -> uri.pathSegments.getOrNull(1)
            else -> null
        }
        else -> null
    }
    return candidate?.takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
}

@Composable
private fun ExternalMediaCard(uri: Uri, isVideo: Boolean) {
    val context = LocalContext.current
    val youtubeId = remember(uri) { youtubeVideoId(uri) }
    val image = remember(uri) { mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null) }
    LaunchedEffect(uri, isVideo) {
        image.value = null
        if (!isVideo && uri.toString().length <= 2048 &&
            uri.path.orEmpty().lowercase(Locale.ROOT).endsWithAny(listOf(".jpg", ".jpeg", ".png", ".webp"))) {
            image.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val conn = (java.net.URL(uri.toString()).openConnection() as java.net.HttpURLConnection).apply {
                        connectTimeout = 5000
                        readTimeout = 5000
                        instanceFollowRedirects = false
                    }
                    try {
                        if (conn.responseCode !in 200..299 ||
                            !conn.contentType.orEmpty().startsWith("image/") ||
                            conn.contentLengthLong > 4_000_000L) null
                        else {
                            val bytes = conn.inputStream.use { input ->
                                val output = java.io.ByteArrayOutputStream()
                                val buffer = ByteArray(8192)
                                var count = 0
                                while (true) {
                                    val read = input.read(buffer)
                                    if (read < 0) break
                                    count += read
                                    if (count > 4_000_000) break
                                    output.write(buffer, 0, read)
                                }
                                if (count > 4_000_000) null else output.toByteArray()
                            }
                            bytes?.let { android.graphics.BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap() }
                        }
                    } finally { conn.disconnect() }
                }.getOrNull()
            }
        }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Panel2).padding(12.dp)) {
        val bitmap = image.value
        if (bitmap != null) {
            androidx.compose.foundation.Image(bitmap, "Externes Übungsbild", Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
        } else {
            Text(if (youtubeId != null) "▶  YOUTUBE-VIDEO" else if (isVideo) "▶  VIDEO-LINK" else "▧  BILD-LINK",
                color = White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(uri.host.orEmpty(), color = Muted, fontSize = 11.sp)
        TextButton(onClick = {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
        }) { Text(if (youtubeId != null) "In YouTube öffnen ↗" else "Extern öffnen ↗") }
    }
}

private fun String.endsWithAny(suffixes: List<String>): Boolean = suffixes.any { endsWith(it) }

@Composable
private fun ExerciseMediaViewer(mediaPath: String, isVideo: Boolean) {
    val external = remember(mediaPath) { validatedMediaUrl(mediaPath) }
    if (isVideo) {
        val context = LocalContext.current
        val target = external
        Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("▶  VIDEOANLEITUNG", color = White, fontSize = 14.sp)
                Text(if (external == null) "Für lokale Videos bitte einen HTTPS-Link hinterlegen." else "Video wird in einer externen App geöffnet.", color = Muted, fontSize = 11.sp)
                TextButton(enabled = target != null, onClick = {
                    target?.let { uri ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            })
                        }.onFailure { Toast.makeText(context, "Keine passende Video-App gefunden.", Toast.LENGTH_SHORT).show() }
                    }
                }) { Text("Video öffnen ↗", color = White) }
            }
        }
        return
    }
    if (external != null) {
        ExternalMediaCard(external, false)
        return
    }
    val file = remember(mediaPath) { File(mediaPath) }
    if (!file.exists()) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Bg)
    ) {
        run {
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
private fun MusclePickerDialog(
    primary: String,
    secondary: Set<String>,
    onDismiss: () -> Unit,
    onApply: (String, List<String>) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var selectedPrimary by remember { mutableStateOf(primary) }
    val selectedSecondary = remember { mutableStateListOf<String>().apply { addAll(secondary) } }
    var selectingSecondary by remember { mutableStateOf(false) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            color = Panel,
            shape = RoundedCornerShape(26.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Text("MUSKELGRUPPEN", color = White, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(50) },
                    label = { Text("Muskelgruppe suchen") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Hauptmuskel", !selectingSecondary, Modifier.weight(1f)) { selectingSecondary = false }
                    ChoiceChip("Weitere Muskeln", selectingSecondary, Modifier.weight(1f)) { selectingSecondary = true }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState())) {
                    val filtered = muscleCatalogue.filter {
                        it.label.contains(search, ignoreCase = true) || it.category.contains(search, ignoreCase = true)
                    }
                    filtered.groupBy { it.category }.forEach { (category, entries) ->
                        Text(category.uppercase(Locale.GERMAN), color = Muted, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp))
                        entries.forEach { option ->
                            val selected = if (selectingSecondary) option.id in selectedSecondary else option.id == selectedPrimary
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    if (selectingSecondary) {
                                        if (option.id != selectedPrimary) {
                                            if (selected) selectedSecondary.remove(option.id) else selectedSecondary.add(option.id)
                                        }
                                    } else {
                                        selectedPrimary = option.id
                                        selectedSecondary.remove(option.id)
                                    }
                                }.padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(if (selected) "●" else "○", color = if (selected) Red else Muted, fontSize = 18.sp)
                                Spacer(Modifier.width(12.dp))
                                Text(option.label, color = White, fontSize = 14.sp)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(onClick = { onApply(selectedPrimary, selectedSecondary.toList()) }) { Text("Übernehmen") }
                }
            }
        }
    }
}

@Composable
private fun NewExerciseDialog(
    ndot: FontFamily,
    targetWorkoutName: String?,
    onDismiss: () -> Unit,
    onCreate: (Exercise) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf("") }
    var primaryMuscleId by remember { mutableStateOf("chest") }
    val secondaryMuscles = remember { mutableStateListOf<String>() }
    var musclePickerOpen by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(ExerciseKind.WEIGHT) }
    var description by remember { mutableStateOf("") }
    var targetWeight by remember { mutableStateOf("40") }
    var targetReps by remember { mutableStateOf("10") }
    var targetSeconds by remember { mutableStateOf("45") }
    var copiedMediaPath by remember { mutableStateOf("") }
    var mediaUrl by remember { mutableStateOf("") }
    var mediaUrlIsVideo by remember { mutableStateOf(true) }
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
                if (!targetWorkoutName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Wird direkt in „$targetWorkoutName“ eingetragen.",
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text("Name der Übung") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { musclePickerOpen = true },
                    color = Panel2,
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text("MUSKELGRUPPEN AUSWÄHLEN  ›", color = White, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text("Hauptmuskel: ${muscleName(primaryMuscleId)}", color = White, fontSize = 14.sp)
                        if (secondaryMuscles.isNotEmpty()) {
                            Text("Weitere: ${secondaryMuscles.joinToString(", ") { muscleName(it) }}", color = Muted, fontSize = 11.sp)
                        }
                    }
                }

                SectionLabel("ÜBUNGSTYP", topPadding = 16.dp, bottomPadding = 8.dp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ExerciseKind.WEIGHT to "Gewicht",
                        ExerciseKind.BODYWEIGHT to "Körpergewicht",
                        ExerciseKind.TIMED to "Zeit",
                        ExerciseKind.CARDIO to "Cardio",
                        ExerciseKind.AMRAP to "AMRAP"
                    ).forEach { (k, label) ->
                        ChoiceChip(label, kind == k, Modifier.width(115.dp)) {
                            kind = k
                            triggerHaptic(context, haptic, HapticStyle.TICK)
                        }
                    }
                }

                if (kind == ExerciseKind.CARDIO) {
                    SectionLabel("CARDIO-ART", topPadding = 12.dp, bottomPadding = 6.dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Laufen", "Radfahren", "Rudern", "Schwimmen", "Gehen", "Crosstrainer", "Seilspringen").forEach { suggestion ->
                            ChoiceChip(suggestion, name == suggestion, Modifier.width(112.dp)) { name = suggestion }
                        }
                    }
                }
                if (kind == ExerciseKind.AMRAP) {
                    TextButton(onClick = {
                        name = "Cindy"
                        description = "20 Minuten AMRAP: 5 Klimmzüge, 10 Liegestütze, 15 Kniebeugen pro Runde."
                        targetSeconds = "1200"
                        targetReps = "1"
                    }) { Text("CINDY-VORLAGE (20 MIN.)") }
                }
                Spacer(Modifier.height(12.dp))
                when (kind) {
                    ExerciseKind.CARDIO, ExerciseKind.AMRAP -> {
                        OutlinedTextField(targetSeconds, { targetSeconds = it.filter(Char::isDigit).take(5) },
                            label = { Text("Dauer in Sekunden") }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (kind == ExerciseKind.AMRAP) {
                            OutlinedTextField(targetReps, { targetReps = it.filter(Char::isDigit).take(4) },
                                label = { Text("Zielrunden") }, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }

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

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = mediaUrl,
                    onValueChange = { mediaUrl = it.take(2048) },
                    label = { Text("Externer Medienlink (HTTPS)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip("Video / YouTube", mediaUrlIsVideo, Modifier.weight(1f)) { mediaUrlIsVideo = true }
                    ChoiceChip("Bild", !mediaUrlIsVideo, Modifier.weight(1f)) { mediaUrlIsVideo = false }
                }
                if (mediaUrl.isNotBlank() && validatedMediaUrl(mediaUrl) == null) {
                    Text("Bitte einen gültigen HTTPS-Link eingeben.", color = Red, fontSize = 11.sp)
                }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Abbrechen") }
                    TextButton(
                        enabled = name.isNotBlank() && (mediaUrl.isBlank() || validatedMediaUrl(mediaUrl) != null),
                        onClick = {
                            onCreate(
                                Exercise(
                                    id = UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    muscle = muscleCatalogue.firstOrNull { it.id == primaryMuscleId }?.category ?: "Ganzkörper",
                                    primaryMuscleId = primaryMuscleId,
                                    secondaryMuscleIds = secondaryMuscles.toList(),
                                    kind = kind,
                                    description = description.trim(),
                                    mediaPath = mediaUrl.takeIf { it.isNotBlank() }?.trim() ?: copiedMediaPath,
                                    mediaIsVideo = if (mediaUrl.isNotBlank()) mediaUrlIsVideo else copiedMediaIsVideo,
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
    if (musclePickerOpen) {
        MusclePickerDialog(
            primary = primaryMuscleId,
            secondary = secondaryMuscles.toSet(),
            onDismiss = { musclePickerOpen = false },
            onApply = { primary, secondary ->
                primaryMuscleId = primary
                secondaryMuscles.clear()
                secondaryMuscles.addAll(secondary.filter { it != primary })
                musclePickerOpen = false
            }
        )
    }

}

@Composable
private fun AiExerciseImportDialog(
    ndot: FontFamily,
    targetWorkoutName: String?,
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
                    buildString {
                        append("Format: $NPEF_FORMAT_ID · Sende die Vorlage an Gemini, ChatGPT oder Claude und füge die JSON-Antwort hier ein.")
                        if (!targetWorkoutName.isNullOrBlank()) {
                            append(" Importierte Übungen landen direkt in „$targetWorkoutName“.")
                        }
                    },
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
            "NPLANNER · VERSION 0.31",
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
