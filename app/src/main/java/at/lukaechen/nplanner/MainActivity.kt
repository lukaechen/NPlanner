package at.lukaechen.nplanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.toArgb
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.widget.MediaController
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.foundation.ScrollState
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
import androidx.compose.ui.text.style.TextOverflow
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

private val Bg get() = NPlannerAppearance.palette.background
private val Panel get() = NPlannerAppearance.palette.panel
private val Panel2 get() = NPlannerAppearance.palette.panel2
private val White get() = NPlannerAppearance.palette.foreground
private val Muted get() = NPlannerAppearance.palette.muted
private val Red get() = NPlannerAppearance.palette.accent

private const val NPLANNER_VERSION = "0.54"

/** FIXED = feste Uhrzeit, INTERVAL = Zeitabstand mit Von/Bis, ALLDAY = ohne Zeit, WEEKLY = Flexibles Wochenziel (1..14x pro Woche). */
enum class RuleKind { FIXED, INTERVAL, ALLDAY, ONE_TIME, WEEKLY }

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
    val oneTimeDate: String = "",
    val archived: Boolean = false,
    val optional: Boolean = false
)

private data class MuscleOption(val id: String, private val rawLabel: String, val rawCategory: String) {
    val label: String get() = npMuscleLabel(rawLabel)
    val category: String get() = npMuscleLabel(rawCategory)
}

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
    val distanceKm: Float = 0f,
    val sessionId: String = ""
)

enum class WorkoutKind { CLASSIC, AMRAP, FOR_TIME, EMOM }

data class Workout(
    val id: String,
    val name: String,
    val exerciseIds: List<String> = emptyList(),
    val note: String = "",
    val kind: WorkoutKind = WorkoutKind.CLASSIC,
    val durationSeconds: Int = 0,
    val amrapTargets: Map<String, Int> = emptyMap(),
    val targetWeights: Map<String, Float> = emptyMap()
)

private fun systemTypography(): Typography {
    val base = Typography()
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = FontFamily.Default),
        displayMedium = base.displayMedium.copy(fontFamily = FontFamily.Default),
        displaySmall = base.displaySmall.copy(fontFamily = FontFamily.Default),
        headlineLarge = base.headlineLarge.copy(fontFamily = FontFamily.Default),
        headlineMedium = base.headlineMedium.copy(fontFamily = FontFamily.Default),
        headlineSmall = base.headlineSmall.copy(fontFamily = FontFamily.Default),
        titleLarge = base.titleLarge.copy(fontFamily = FontFamily.Default),
        titleMedium = base.titleMedium.copy(fontFamily = FontFamily.Default),
        titleSmall = base.titleSmall.copy(fontFamily = FontFamily.Default),
        bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.Default),
        bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.Default),
        bodySmall = base.bodySmall.copy(fontFamily = FontFamily.Default),
        labelLarge = base.labelLarge.copy(fontFamily = FontFamily.Default),
        labelMedium = base.labelMedium.copy(fontFamily = FontFamily.Default),
        labelSmall = base.labelSmall.copy(fontFamily = FontFamily.Default)
    )
}

private val LocalDotHeadings = compositionLocalOf { false }
private val LocalClassicHeadings = compositionLocalOf<FontFamily> { FontFamily.Default }
private val LocalPageScroll = compositionLocalOf<ScrollState?> { null }

// ---------- Zeit- und Regel-Helfer ----------

private fun fmt(min: Int): String = String.format(npLocale(), "%02d:%02d", min / 60, min % 60)

private fun fmtKg(kg: Float): String =
    if (kg % 1f == 0f) npString(R.string.np_370a3b22c813 , kg.toInt()) else String.format(npLocale(), npString(R.string.np_907d342d4ac0), kg)

private fun minuteOfDay(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

private fun everyLabel(min: Int): String = when {
    min < 60 -> npString(R.string.np_0e961ac28402 , min)
    min % 60 == 0 -> npString(R.string.np_e36d2e63597b , min / 60)
    else -> npString(R.string.np_96c878b2e330 , min / 60, min % 60)
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
        RuleKind.INTERVAL -> npString(R.string.np_446996825c07 , everyLabel(r.everyMin), fmt(r.fromMin), fmt(r.toMin), dayLabel(r.days))
        RuleKind.WEEKLY -> npString(R.string.np_45a8510351ce , r.weeklyTarget.coerceIn(1, 14))
        RuleKind.ONE_TIME -> {
            val due = runCatching { LocalDate.parse(r.oneTimeDate) }.getOrNull()
            if (due != null) {
                val prefix = if (due.isBefore(LocalDate.now())) { if (npLocale().language == "de") "Überfällig · " else "Overdue · " } else ""
                prefix + due.format(DateTimeFormatter.ofPattern("EEE, d. MMM", npLocale()))
            } else if (npLocale().language == "de") "Einmalig" else "One-time"
        }
        RuleKind.ALLDAY -> dayLabel(r.days)
    }
    return if (r.optional) npString(R.string.np_2eb32096eb64 , base) else base
}

private fun exerciseKindLabel(kind: ExerciseKind): String = when (kind) {
    ExerciseKind.WEIGHT -> npString(R.string.np_b80121b7ef4f)
    ExerciseKind.BODYWEIGHT -> npString(R.string.np_f75cff63870c)
    ExerciseKind.TIMED -> npString(R.string.np_8ab99e35cd2b)
    ExerciseKind.CARDIO -> npString(R.string.np_eb013efc32e2)
    ExerciseKind.AMRAP -> npString(R.string.np_60adbb887847)
}

private fun exerciseSummary(ex: Exercise): String = when (ex.kind) {
    ExerciseKind.CARDIO -> npString(R.string.np_afb51020f59b , npMuscleLabel(ex.muscle), ex.targetSeconds / 60)
    ExerciseKind.AMRAP -> npString(R.string.np_7a82603f955e , npMuscleLabel(ex.muscle), ex.targetSeconds / 60, ex.targetReps)
    ExerciseKind.WEIGHT -> npString(R.string.np_73e9acc95f81 , npMuscleLabel(ex.muscle), fmtKg(ex.targetWeightKg), ex.targetReps)
    ExerciseKind.BODYWEIGHT -> npString(R.string.np_c4d6f0b64fd9 , npMuscleLabel(ex.muscle), ex.targetReps)
    ExerciseKind.TIMED -> if (ex.targetReps > 1) {
        npString(R.string.np_d1493a657eaf , npMuscleLabel(ex.muscle), ex.targetReps, ex.targetSeconds)
    } else {
        npString(R.string.np_cfe7d95c9056 , npMuscleLabel(ex.muscle), ex.targetSeconds)
    }
}

private fun logSummary(ex: Exercise, log: ExerciseLog): String = when (ex.kind) {
    ExerciseKind.CARDIO -> npString(R.string.np_25c1be96314d , log.seconds / 60, log.seconds % 60) +
        if (log.distanceKm > 0f) " · ${fmtKg(log.distanceKm).replace(npString(R.string.np_77c51fce099a), npString(R.string.np_f50a8a12533c))}" else ""
    ExerciseKind.AMRAP -> npString(R.string.np_34d251f16f5b , log.reps, log.seconds / 60)
    ExerciseKind.WEIGHT -> npString(R.string.np_7e719873d064 , fmtKg(log.weightKg), log.reps)
    ExerciseKind.BODYWEIGHT -> npString(R.string.np_6a8982ba88d0 , log.reps)
    ExerciseKind.TIMED -> if (log.reps > 1) npString(R.string.np_81648b7f9994 , log.reps, log.seconds) else npString(R.string.np_7f854c57c3cb , log.seconds)
}

private fun weekStartKey(d: LocalDate): String =
    d.minusDays((d.dayOfWeek.value - 1).toLong()).toString()

private fun triggerHaptic(context: Context, haptic: HapticFeedback, style: HapticStyle) {
    // Common actions use Android's action-based feedback so every tap is consistent and respects system settings.
    if (style != HapticStyle.DAY_COMPLETE) {
        runCatching {
            haptic.performHapticFeedback(
                when (style) {
                    HapticStyle.TASK_DONE -> HapticFeedbackType.LongPress
                    HapticStyle.UNDO -> HapticFeedbackType.TextHandleMove
                    HapticStyle.TICK -> HapticFeedbackType.TextHandleMove
                    else -> HapticFeedbackType.TextHandleMove
                }
            )
        }
        return
    }
    // Day complete deliberately gets a richer, unmistakable finish pattern.
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION") context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
    val played = runCatching {
        if (vibrator == null || !vibrator.hasVibrator()) false else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val effect = VibrationEffect.startComposition()
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, .65f)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, .90f, 55)
                    .addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1.0f, 85)
                    .compose()
                vibrator.vibrate(effect)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createWaveform(
                    longArrayOf(0L, 24L, 42L, 38L, 54L, 75L),
                    intArrayOf(0, 105, 0, 175, 0, 255), -1))
            } else {
                @Suppress("DEPRECATION") vibrator.vibrate(longArrayOf(0L, 28L, 45L, 42L, 60L, 80L), -1)
            }
            true
        }
    }.getOrDefault(false)
    if (!played) runCatching { haptic.performHapticFeedback(HapticFeedbackType.LongPress) }
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
        "Unbekanntes Dateiformat „${format}“. Erwartet wird „${NPEF_FORMAT_ID}“."
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
    private val prefs by lazy { NPlannerStore.get(this) }

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
                    oneTimeDate = o.optString("oneTimeDate", ""),
                    archived = o.optBoolean("archived", false),
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
                val oldCnt = prefs.getInt("goal_cnt:${wk}", 0).coerceIn(0, legacyTarget)
                prefs.edit()
                    .putInt("wk_cnt:${wk}:${sportRule.id}", oldCnt)
                    .putBoolean("wk:${wk}:${sportRule.id}", oldCnt >= legacyTarget)
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
                    .put("oneTimeDate", r.oneTimeDate)
                    .put("archived", r.archived)
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
                distanceKm = o.optDouble("distanceKm", 0.0).toFloat(),
                sessionId = o.optString("sessionId", "")
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
                    .put("sessionId", log.sessionId)
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
                    durationSeconds = o.optInt("durationSeconds", 0),
                    amrapTargets = o.optJSONObject("amrapTargets")?.let { targets ->
                        targets.keys().asSequence().associateWith { targets.optInt(it, 1).coerceIn(1, 500) }
                    } ?: emptyMap(),
                    targetWeights = o.optJSONObject("targetWeights")?.let { values ->
                        values.keys().asSequence().associateWith { values.optDouble(it, 0.0).toFloat() }
                    } ?: emptyMap()
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
                    .put("amrapTargets", JSONObject().apply { w.amrapTargets.forEach { (id, reps) -> put(id, reps) } })
                    .put("targetWeights", JSONObject().apply { w.targetWeights.forEach { (id, kg) -> put(id, kg.toDouble()) } })
            )
        }
        prefs.edit().putString("workouts_v1", arr.toString()).apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        NPlannerDriveBackup.applyPendingRestore(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.isNavigationBarContrastEnforced = false
        NPlannerAppearance.initialize(this)
        seedEditableWorkoutTemplates(this)
        // Room is now the primary and only user-data store.
        setContent {
            val ndot = FontFamily(androidx.compose.ui.text.font.Font(resId = R.font.ndot_47))
            // NType 82 is supplied separately as app/src/main/res/font/ntype_82.ttf.
            // Resolve dynamically so an absent file does not break compilation.
            val ntypeId = resources.getIdentifier("ntype_82", "font", packageName)
            val classicHeadings = if (ntypeId != 0) {
                FontFamily(androidx.compose.ui.text.font.Font(resId = ntypeId))
            } else FontFamily.Default
            val systemDark = androidx.compose.foundation.isSystemInDarkTheme()
            val effectiveDark = when (NPlannerAppearance.mode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            // Single-activity app: snapshot-backed palette also works inside Canvas callbacks.
            NPlannerAppearance.applyPalette(effectiveDark)
            androidx.compose.runtime.SideEffect {
                @Suppress("DEPRECATION")
                window.statusBarColor = NPlannerAppearance.palette.background.toArgb()
                @Suppress("DEPRECATION")
                window.navigationBarColor = Color.Transparent.toArgb()
                androidx.core.view.WindowCompat.getInsetsController(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !effectiveDark
                    isAppearanceLightNavigationBars = !effectiveDark
                }
            }
            // Read language here so all visible screens update without recreating the Activity.
            val language = NPlannerAppearance.language
            val localizedConfiguration = remember(language, androidx.compose.ui.platform.LocalConfiguration.current) {
                android.content.res.Configuration(resources.configuration).apply {
                    setLocale(java.util.Locale.forLanguageTag(language))
                }
            }
            CompositionLocalProvider(androidx.compose.ui.platform.LocalConfiguration provides localizedConfiguration) {
            MaterialTheme(
                colorScheme = if (effectiveDark) darkColorScheme(
                    primary = Red, secondary = White, background = Bg, surface = Panel,
                    onPrimary = Color.White, onSecondary = Bg, onBackground = White, onSurface = White,
                    surfaceVariant = Panel2, onSurfaceVariant = Muted, outline = Muted
                ) else lightColorScheme(
                    primary = Red, secondary = White, background = Bg, surface = Panel,
                    onPrimary = Color.White, onSecondary = Bg, onBackground = White, onSurface = White,
                    surfaceVariant = Panel2, onSurfaceVariant = Muted, outline = Muted
                ),
                typography = systemTypography()
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
        var editingRule by remember { mutableStateOf<Rule?>(null) }
        var dotHeadings by remember { mutableStateOf(prefs.getBoolean("use_ndot_headings", false)) }
        var orbit by remember { mutableStateOf(prefs.getString("progress_style", "bars") == "orbit") }
        var historyDots by remember { mutableStateOf(prefs.getString("history_style", "dots") == "dots") }
        var testMode by remember { mutableStateOf(prefs.getBoolean("test_mode_multi_weekly", false)) }

        LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); nowMin = minuteOfDay(); delay(30_000) } }

        fun weekKey(rule: Rule, d: LocalDate = date) = "wk:${weekStartKey(d)}:${rule.id}"
        fun weekCountKey(rule: Rule, d: LocalDate = date) = "wk_cnt:${weekStartKey(d)}:${rule.id}"
        fun weeklyDayKey(rule: Rule, d: LocalDate = date) = "wk_day:${d}:${rule.id}"

        fun doneCount(rule: Rule, d: LocalDate = date): Int = when (rule.kind) {
            RuleKind.INTERVAL -> prefs.getInt("cnt:${d}:${rule.id}", 0).coerceIn(0, totalOf(rule))
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
            else -> if (prefs.getBoolean("done:${d}:${rule.id}", false)) 1 else 0
        }

        fun doneTodayForWeekly(rule: Rule, d: LocalDate = date): Boolean =
            prefs.getBoolean(weeklyDayKey(rule, d), false)

        fun oneTimeDue(rule: Rule): LocalDate? = rule.oneTimeDate.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        LaunchedEffect(date, rules) {
            val updated = rules.map { rule ->
                if (rule.kind != RuleKind.ONE_TIME || rule.archived) rule else {
                    val due = oneTimeDue(rule)
                    val completedOn = prefs.getString("one_time_done_date:${rule.id}", "").orEmpty().takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                    if (due != null && due.isBefore(date) && completedOn != null && completedOn.isBefore(date)) rule.copy(archived = true) else rule
                }
            }
            if (updated != rules) { rules = updated; saveRules(updated); tick++ }
        }
        val dailyRules = remember(rules, date, tick) {
            rules.filter { rule ->
                if (rule.archived || rule.kind == RuleKind.WEEKLY) false
                else if (rule.kind == RuleKind.ONE_TIME) oneTimeDue(rule)?.let { !date.isBefore(it) } == true
                else date.dayOfWeek.value in rule.days
            }.sortedWith(compareBy<Rule> { if (it.kind == RuleKind.ONE_TIME && oneTimeDue(it)?.isBefore(date) == true) 0 else 1 }.thenBy { sortKey(it) })
        }
        val weeklyRules = remember(rules) { rules.filter { it.kind == RuleKind.WEEKLY && !it.archived } }

        fun weeklyProgressThroughDay(rule: Rule, d: LocalDate): Int {
            val weekStart = d.minusDays((d.dayOfWeek.value - 1).toLong())
            return (0L..(d.dayOfWeek.value - 1).toLong())
                .count { offset -> prefs.getBoolean(weeklyDayKey(rule, weekStart.plusDays(offset)), false) }
                .coerceIn(0, totalOf(rule))
        }

        fun daySnapshot(d: LocalDate): DaySnapshot {
            val allDailyForDay = rules.filter { r ->
                r.kind != RuleKind.WEEKLY &&
                    (if (r.kind == RuleKind.ONE_TIME) {
                        val due = oneTimeDue(r)
                        val completedOn = prefs.getString("one_time_done_date:${r.id}", "").orEmpty()
                            .takeIf(String::isNotBlank)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                        due != null && !d.isBefore(due) && (completedOn == null || !d.isAfter(completedOn))
                    } else d.dayOfWeek.value in r.days) &&
                    (r.createdDate.isBlank() || r.createdDate <= d.toString())
            }
            val mandatoryDailyForDay = allDailyForDay.filterNot { it.optional }
            val weeklyDoneOnDay = weeklyRules.filter { doneTodayForWeekly(it, d) }
            // Flexible weekly goals are shown as x/target in the day details, but they do
            // not decide whether the day itself is complete.
            val doneDaily = mandatoryDailyForDay.count { doneCount(it, d) >= totalOf(it) }
            return DaySnapshot(
                date = d,
                done = doneDaily,
                total = mandatoryDailyForDay.size,
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
                items.put(
                    JSONArray()
                        .put("${r.name} (Wochenziel)")
                        .put(weeklyProgressThroughDay(r, d))
                        .put(totalOf(r))
                        .put(1)
                )
            }
            val obj = JSONObject().put("done", snap.done).put("total", snap.total).put("items", items)
            prefs.edit().putString("snap:${d}", obj.toString()).apply()
        }

        fun freezeHistoryBeforeRuleChange() {
            val yesterday = date.minusDays(1)
            val dates = rules.mapNotNull { runCatching { LocalDate.parse(it.createdDate) }.getOrNull() }
            val earliest = dates.minOrNull() ?: return
            var d = maxOf(earliest, yesterday.minusYears(10))
            while (!d.isAfter(yesterday)) {
                val old = prefs.getString("snap:${d}", null)
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
                            items.put(
                    JSONArray()
                        .put("${r.name} (Wochenziel)")
                        .put(weeklyProgressThroughDay(r, d))
                        .put(totalOf(r))
                        .put(1)
                )
                        }
                        prefs.edit().putString("snap:${d}", obj.put("items", items).toString()).apply()
                    }
                }
                d = d.plusDays(1)
            }
        }

        fun readDaySnapshot(d: LocalDate): DaySnapshot {
            val live = daySnapshot(d)
            if (d == date || d.isAfter(date)) return live
            val raw = prefs.getString("snap:${d}", null)
            if (raw != null) {
                val parsed = runCatching {
                    val o = JSONObject(raw)
                    val sDone = o.optInt("done", live.done)
                    val sTotal = o.optInt("total", live.total)
                    val arr = o.optJSONArray("items")
                    val items = if (arr == null) emptyList() else (0 until arr.length()).mapNotNull { i ->
                        runCatching {
                            val a = arr.getJSONArray(i)
                            val storedName = a.getString(0)
                            val weeklyRule = if (storedName.endsWith(" (Wochenziel)")) {
                                weeklyRules.firstOrNull { it.name == storedName.removeSuffix(" (Wochenziel)") }
                            } else null
                            HistoricalTask(
                                name = storedName,
                                done = weeklyRule?.let { weeklyProgressThroughDay(it, d) } ?: a.getInt(1),
                                target = weeklyRule?.let(::totalOf) ?: a.getInt(2),
                                optional = weeklyRule != null || a.optInt(3) == 1
                            )
                        }.getOrNull()
                    }
                    val requiredItems = items.filterNot { it.optional || it.name.endsWith(" (Wochenziel)") }
                    val repairedDone = if (items.isNotEmpty()) requiredItems.sumOf { it.done.coerceAtMost(it.target) } else sDone
                    val repairedTotal = if (items.isNotEmpty()) requiredItems.sumOf { it.target } else sTotal
                    live.copy(done = repairedDone, total = repairedTotal, historicalTasks = items)
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
                    e.putInt("cnt:${date}:${rule.id}", next)
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
                    e.putBoolean("done:${date}:${rule.id}", !wasDone)
                    if (rule.kind == RuleKind.ONE_TIME) e.putString("one_time_done_date:${rule.id}", if (!wasDone) date.toString() else "")
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
        val pageScrollStates = remember { List(4) { ScrollState(0) } }
        val scope = rememberCoroutineScope()
        var pageOrder by remember { mutableStateOf(listOf(0, 1, 2, 3)) }
        var directTarget by remember { mutableStateOf<Int?>(null) }
        var navigationJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

        fun selectPage(target: Int) {
            if (target !in 0..3) return
            if (navigationJob?.isActive != true && pageOrder[pagerState.currentPage] == target) return
            val previous = navigationJob
            navigationJob = scope.launch {
                previous?.cancel()
                previous?.join()
                val source = pagerState.currentPage
                if (source == target) return@launch
                directTarget = target
                val adjacent = source + if (target > source) 1 else -1
                // Only source and target can become visible, even for Today -> Settings.
                pageOrder = listOf(0, 1, 2, 3).toMutableList().apply {
                    this[adjacent] = target
                    this[target] = adjacent
                }
                try {
                    pagerState.animateScrollToPage(adjacent,
                        animationSpec = tween(220, easing = FastOutSlowInEasing))
                } finally {
                    val logicalPage = pageOrder[pagerState.currentPage]
                    androidx.compose.runtime.snapshots.Snapshot.withMutableSnapshot {
                        pageOrder = listOf(0, 1, 2, 3)
                        pagerState.requestScrollToPage(logicalPage)
                        directTarget = null
                    }
                }
            }
        }

        @Composable
        fun PageContent(p: Int) {
            CompositionLocalProvider(LocalPageScroll provides pageScrollStates[p]) {
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
                            onUpdateExercise = { updated ->
                                exercises = exercises.map { existing -> if (existing.id == updated.id) updated else existing }
                                saveExercises(exercises)
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
                                val session = NPlannerStore.get(context)
                                val activeId = session.getString("active_workout", null)
                                if (newLog.workoutId.isNotBlank() && newLog.workoutId == activeId) {
                                    val runId = session.getString("classic_run_id", null)
                                        ?: UUID.randomUUID().toString().also {
                                            session.edit().putString("classic_run_id", it).apply()
                                        }
                                    val saved = newLog.copy(sessionId = runId)
                                    exerciseLogs = exerciseLogs + saved
                                    saveExerciseLogs(exerciseLogs)
                                    exercises = exercises.map { ex ->
                                        if (ex.id != saved.exerciseId) ex else ex.copy(
                                            targetWeightKg = if (ex.kind == ExerciseKind.WEIGHT) saved.weightKg else ex.targetWeightKg,
                                            targetReps = if (saved.reps > 0) saved.reps else ex.targetReps,
                                            targetSeconds = if (saved.seconds > 0) saved.seconds else ex.targetSeconds
                                        )
                                    }
                                    saveExercises(exercises)
                                    workouts = workouts.map { w ->
                                        if (w.id != saved.workoutId) w else w.copy(
                                            targetWeights = w.targetWeights + (saved.exerciseId to saved.weightKg),
                                            amrapTargets = if (saved.reps > 0) w.amrapTargets + (saved.exerciseId to saved.reps) else w.amrapTargets
                                        )
                                    }
                                    saveWorkouts(workouts)
                                    triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                                }
                            },
                            onDeleteSessionLogs = { ids ->
                                val affected = exerciseLogs.filter { it.id in ids }.map { it.exerciseId }.toSet()
                                exerciseLogs = exerciseLogs.filterNot { it.id in ids }
                                saveExerciseLogs(exerciseLogs)
                                exercises = exercises.map { ex ->
                                    val latest = exerciseLogs.lastOrNull { it.exerciseId == ex.id }
                                    if (ex.id !in affected || latest == null) ex else ex.copy(
                                        targetWeightKg = if (ex.kind == ExerciseKind.WEIGHT) latest.weightKg else ex.targetWeightKg,
                                        targetReps = latest.reps.takeIf { it > 0 } ?: ex.targetReps,
                                        targetSeconds = latest.seconds.takeIf { it > 0 } ?: ex.targetSeconds
                                    )
                                }
                                saveExercises(exercises)
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
                            rules = rules.filterNot { it.archived },
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
                            onEdit = { rule ->
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                                editingRule = rule
                            },
                            onDelete = ::deleteRule,
                            onAdd = {
                                triggerHaptic(context, haptic, HapticStyle.TICK)
                                addDialog = true
                            }
                        )
                    }
            }
        }

        CompositionLocalProvider(LocalDotHeadings provides dotHeadings) {
            Box(Modifier.fillMaxSize().background(Bg)) {
                HorizontalPager(
                    state = pagerState,
                    key = { slot -> pageOrder[slot] },
                    beyondViewportPageCount = 1,
                    userScrollEnabled = directTarget == null,
                    modifier = Modifier.fillMaxSize()
                ) { slot ->
                    PageContent(pageOrder[slot])
                }
                GlyphNav(
                    pagerState = pagerState,
                    directTarget = directTarget,
                    select = ::selectPage,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }

            if (addDialog || editingRule != null) NewTaskDialog(
                ndot = ndot,
                initialRule = editingRule,
                onDismiss = {
                    addDialog = false
                    editingRule = null
                },
                onSave = { rule ->
                    freezeHistoryBeforeRuleChange()
                    rules = if (editingRule == null) {
                        rules + rule.copy(createdDate = date.toString())
                    } else {
                        rules.map { existing -> if (existing.id == rule.id) rule else existing }
                    }
                    saveRules(rules)
                    persistTodaySnapshot(date)
                    triggerHaptic(context, haptic, HapticStyle.TASK_DONE)
                    addDialog = false
                    editingRule = null
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
    val family = if (useDot) ndot else LocalClassicHeadings.current
    val weight = if (useDot) FontWeight.Normal else FontWeight.Light
    val spacing = if (useDot) 1.sp else (-0.8).sp
    val digitSlots = maxOf(1, total.toString().length, done.toString().length)
    val numberWidth = (fontSize.value * 0.64f * digitSlots).dp
    val slashWidth = (fontSize.value * 0.42f).dp
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Box(Modifier.width(numberWidth), contentAlignment = Alignment.CenterEnd) {
            Text(done.toString(), color = White, fontFamily = family, fontWeight = weight,
                fontSize = fontSize, lineHeight = fontSize, letterSpacing = spacing,
                maxLines = 1, softWrap = false, style = TextStyle(fontFeatureSettings = "tnum",
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        Box(Modifier.width(slashWidth), contentAlignment = Alignment.Center) {
            Text("/", color = npNeutral(0xFF5A5A5A), fontFamily = family, fontWeight = weight,
                fontSize = fontSize, lineHeight = fontSize, maxLines = 1,
                style = TextStyle(fontFeatureSettings = "tnum",
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        Box(Modifier.width(numberWidth), contentAlignment = Alignment.CenterStart) {
            Text(total.toString(), color = npNeutral(0xFF5A5A5A), fontFamily = family,
                fontWeight = weight, fontSize = fontSize, lineHeight = fontSize,
                letterSpacing = spacing, maxLines = 1, softWrap = false,
                style = TextStyle(fontFeatureSettings = "tnum",
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
    }
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
    Column(if (scrollable) base.imePadding().verticalScroll(LocalPageScroll.current ?: rememberScrollState()) else base) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(Red, CircleShape))
            Spacer(Modifier.width(9.dp))
            Text(eyebrow, color = Muted, fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 1.8.sp)
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    val open = totalToday - doneToday
    var completionAnimationKey by remember { mutableIntStateOf(0) }
    var wasComplete by remember { mutableStateOf(false) }
    LaunchedEffect(doneToday, totalToday) {
        val complete = totalToday > 0 && doneToday >= totalToday
        if (complete && !wasComplete) completionAnimationKey++
        wasComplete = complete
    }
    val next = remember(mandatoryDaily, date, tick, nowMin) {
        mandatoryDaily
            .filter { count(it) < totalOf(it) }
            .mapNotNull { r -> nextTime(r, count(r))?.let { t -> r to t } }
            .filter { it.second >= nowMin }
            .minByOrNull { it.second }
    }
    val status = when {
        open == 0 -> npString(R.string.np_d0e1ef5286dc)
        next != null -> npString(R.string.np_9886f3aa23b9 , fmt(next.second), next.first.name)
        else -> npString(R.string.np_8ad305b002c2 , open, if (open == 1) npString(R.string.np_bc96c191b7dd) else npString(R.string.np_72062a54a368))
    }

    Screen(
        ndot = ndot,
        eyebrow = npString(R.string.np_6354d998d12d),
        title = npString(R.string.np_2ae2ac311151),
        subtitle = date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", npLocale())),
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
                AnimatedContent(targetState = completionAnimationKey, label = "day_complete_text") {
                    Column(Modifier.fillMaxWidth().padding(top = if (orbit) 10.dp else 18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        DisplayTitle(npString(R.string.np_5fdacf19cdc6), ndot, fontSize = 28.sp)
                        Text(npString(R.string.np_eef51c581083), color = Muted, fontSize = 13.sp,
                            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
                    }
                }
            } else if (mandatoryDaily.isNotEmpty()) {
                SectionLabel(
                    npString(R.string.np_c710983cc05f),
                    topPadding = if (orbit) 10.dp else 26.dp,
                    bottomPadding = if (orbit) 8.dp else 12.dp
                )
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    mandatoryDaily.forEachIndexed { index, rule ->
                        val c = count(rule)
                        val total = totalOf(rule)
                        val subtitle = when (rule.kind) {
                            RuleKind.INTERVAL -> "Alle ${everyLabel(rule.everyMin)} · ${c}/${total}"
                            RuleKind.ONE_TIME -> scheduleLabel(rule)
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
                            HorizontalDivider(color = npNeutral(0xFF282828), modifier = Modifier.padding(start = 64.dp))
                        }
                    }
                }
            }

            if (optionalDaily.isNotEmpty()) {
                SectionLabel(
                    npString(R.string.np_f16dba575b22),
                    topPadding = if (orbit) 12.dp else 20.dp,
                    bottomPadding = if (orbit) 8.dp else 10.dp
                )
                Column(Modifier.clip(RoundedCornerShape(24.dp)).background(Panel)) {
                    optionalDaily.forEachIndexed { index, rule ->
                        val c = count(rule)
                        val total = totalOf(rule)
                        val subtitle = when (rule.kind) {
                            RuleKind.INTERVAL -> "Optional · Alle ${everyLabel(rule.everyMin)} · ${c}/${total}"
                            RuleKind.ONE_TIME -> "Optional · ${scheduleLabel(rule)}"
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
                            HorizontalDivider(color = npNeutral(0xFF282828), modifier = Modifier.padding(start = 64.dp))
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
    val animatedDone by animateFloatAsState(done.toFloat(),
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow), label = "bar_progress")
    val completed = total > 0 && done >= total
    val finish = remember { Animatable(1f) }
    LaunchedEffect(completed) {
        if (completed) {
            finish.snapTo(0f)
            finish.animateTo(1f, tween(1450, easing = FastOutSlowInEasing))
        }
    }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
            DisplayCount(done, total, ndot, 84.sp)
            Text(if (completed) npString(R.string.np_595b69683dcf) else npString(R.string.np_71f43d0f0475),
                color = Muted, fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 6.dp))
        }
        Spacer(Modifier.height(16.dp))
        Box(Modifier.fillMaxWidth().height(42.dp), contentAlignment = Alignment.Center) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (total > 10) 3.dp else 6.dp)) {
                repeat(total.coerceAtLeast(1)) { i ->
                    val fill = if (total == 0) 1f else (animatedDone - i).coerceIn(0f,1f)
                    val waveHead = if (completed) finish.value * total.coerceAtLeast(1) else -1f
                    val wave = (1f - kotlin.math.abs(waveHead - (i + .5f)) * 1.35f).coerceIn(0f,1f)
                    Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(8.dp))
                        .background(npNeutral(0xFF2A2A2A))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(
                            if (wave > .12f) androidx.compose.ui.graphics.lerp(White, Red, wave) else White, RoundedCornerShape(8.dp)))
                    }
                }
            }
            if (completed && finish.value < .92f) Canvas(Modifier.fillMaxSize()) {
                val p=finish.value; val head=size.width*p
                drawLine(White.copy(alpha=(1f-p)*.65f),Offset(head-42.dp.toPx(),size.height/2),Offset(head,size.height/2),
                    2.dp.toPx(),StrokeCap.Round)
                repeat(18) { i ->
                    val birth=i/18f; val q=((p-birth)*2.1f).coerceIn(0f,1f)
                    if(q>0f && q<1f){
                        val x=size.width*birth + kotlin.math.sin(i*2.7).toFloat()*q*16.dp.toPx()
                        val direction=if(i%2==0)-1f else 1f
                        val y=size.height/2 + direction*(5.dp.toPx()+q*17.dp.toPx())
                        if(i%3==0) drawRoundRect(Red.copy(alpha=1f-q),Offset(x,y),Size(5.dp.toPx(),2.dp.toPx()),CornerRadius(1.dp.toPx()))
                        else drawCircle(White.copy(alpha=1f-q),if(i%4==0)2.dp.toPx() else 1.2.dp.toPx(),Offset(x,y))
                    }
                }
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
    val complete = total > 0 && done >= total
    val celebration = remember { Animatable(1f) }
    LaunchedEffect(complete) {
        if (complete) {
            celebration.snapTo(0f)
            celebration.animateTo(1f, tween(1850, easing = FastOutSlowInEasing))
        }
    }
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
                val animatedSegments = progress.coerceIn(0f, 1f) * total
                repeat(total) { i ->
                    val segStart = startBase + i * stepDeg
                    drawArc(npNeutral(0xFF2A2A2A), segStart, sweepDeg, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    val fill = (animatedSegments - i).coerceIn(0f, 1f)
                    if (fill > 0f) {
                        val celebrationColor = if (complete && celebration.value < .88f) {
                            val phase = ((celebration.value * 8f - i * .45f) % 2f + 2f) % 2f
                            androidx.compose.ui.graphics.lerp(White, Red, if (phase < 1f) phase else 2f - phase)
                        } else White
                        drawArc(celebrationColor, segStart, sweepDeg * fill, false,
                        Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                    }
                }
            } else {
                drawArc(
                    color = npNeutral(0xFF242424),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
                val completionRingColor = if (complete && celebration.value < .88f) {
                    val phase = (celebration.value * 6f) % 2f
                    androidx.compose.ui.graphics.lerp(White, Red, if (phase < 1f) phase else 2f-phase)
                } else White
                drawArc(
                    color = completionRingColor,
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        if (complete && celebration.value < .88f) {
            val pop = (celebration.value / .38f).coerceIn(0f,1f)
            Canvas(Modifier.size(ringSize)) {
                val center = Offset(size.width/2f,size.height/2f)
                val dot = 3.2.dp.toPx()
                val pattern = listOf(
                    -4 to 0, -3 to 1, -2 to 2, -1 to 3,
                    0 to 2, 1 to 1, 2 to 0, 3 to -1, 4 to -2, 5 to -3
                )
                val visible = (pattern.size * pop).toInt().coerceIn(0, pattern.size)
                val scale = .72f + .36f * kotlin.math.sin(pop * Math.PI.toFloat()/2f)
                pattern.take(visible).forEachIndexed { index, pair ->
                    val x=center.x + pair.first*dot*1.55f*scale
                    val y=center.y + pair.second*dot*1.55f*scale
                    drawCircle(if(index==visible-1) Red else White,dot*.48f,Offset(x,y))
                }
                val burst=((celebration.value-.20f)/.58f).coerceIn(0f,1f)
                repeat(20) { i ->
                    val a=(i*18.0-90.0)*Math.PI/180.0
                    val stagger=(i%5)*.045f
                    val q=((burst-stagger)/(1f-stagger)).coerceIn(0f,1f)
                    val r=size.minDimension*(.20f+.34f*q)
                    val x=center.x+kotlin.math.cos(a).toFloat()*r
                    val y=center.y+kotlin.math.sin(a).toFloat()*r
                    val alpha=(1f-q).coerceIn(0f,1f)
                    if(i%4==0) drawRoundRect(Red.copy(alpha=alpha),Offset(x-3.dp.toPx(),y-1.dp.toPx()),Size(6.dp.toPx(),2.dp.toPx()),CornerRadius(1.dp.toPx()))
                    else drawCircle(White.copy(alpha=alpha),if(i%3==0)2.dp.toPx() else 1.25.dp.toPx(),Offset(x,y))
                }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                DisplayCount(done, total, ndot, countFontSize)
                Text(if (complete) npString(R.string.np_595b69683dcf) else npString(R.string.np_71f43d0f0475),
                    color = Muted, fontFamily = FontFamily.Default, fontSize = 9.sp, letterSpacing = 2.sp)
            }
        }
    }
}

@Composable
private fun DayCompleteCelebration(ndot: FontFamily, compact: Boolean, title: String, subtitle: String) {
    val ring = remember { Animatable(0f) }
    val check = remember { Animatable(0f) }
    val content = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        ring.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
        check.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
        content.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
    }
    Column(Modifier.fillMaxWidth().padding(top = if (compact) 8.dp else 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(if (compact) 74.dp else 86.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                val radius = size.minDimension * 0.48f
                drawCircle(Red.copy(alpha = (1f - ring.value) * .22f), radius * ring.value,
                    center, style = Stroke(2.dp.toPx()))
                drawCircle(White, radius * .58f * check.value.coerceAtLeast(.01f), center,
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
                if (check.value > .25f) {
                    val local = ((check.value - .25f) / .75f).coerceIn(0f, 1f)
                    val p1 = Offset(size.width*.34f,size.height*.51f)
                    val p2 = Offset(size.width*.46f,size.height*.63f)
                    val p3 = Offset(size.width*.69f,size.height*.37f)
                    if (local <= .38f) drawLine(White,p1,Offset(p1.x+(p2.x-p1.x)*(local/.38f),p1.y+(p2.y-p1.y)*(local/.38f)),3.dp.toPx(),StrokeCap.Round)
                    else { drawLine(White,p1,p2,3.dp.toPx(),StrokeCap.Round); val second=(local-.38f)/.62f
                        drawLine(White,p2,Offset(p2.x+(p3.x-p2.x)*second,p2.y+(p3.y-p2.y)*second),3.dp.toPx(),StrokeCap.Round) }
                }
            }
        }
        Column(Modifier.graphicsLayer { alpha=content.value; translationY=(1f-content.value)*14.dp.toPx() },
            horizontalAlignment = Alignment.CenterHorizontally) {
            DisplayTitle(title, ndot, fontSize = 28.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp, textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
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
                color = if (optional) npNeutral(0xFF454545) else npNeutral(0xFF5A5A5A),
                radius = radius,
                center = center,
                style = Stroke(width = strokePx)
            )
            if (optional) {
                drawCircle(
                    color = npNeutral(0xFF5A5A5A),
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
                fontFamily = FontFamily.Default,
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
                color = if (canDec) White else npNeutral(0xFF4C4C4C),
                fontSize = 18.sp,
                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))
            )
        }
        Text(
            "${clamped} / ${safeTarget}",
            color = if (clamped >= safeTarget) Red else Color.LightGray,
            fontFamily = FontFamily.Default,
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
                color = if (canInc) Bg else npNeutral(0xFF4C4C4C),
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
                        HorizontalDivider(color = npNeutral(0xFF282828), modifier = Modifier.padding(start = 19.dp))
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
    val optSuffix = if (rule.optional) npString(R.string.np_f7fb4627b990) else ""
    val statusText = when {
        weekDone -> npString(R.string.np_d9da98f8078e , target, optSuffix)
        checkedToday && !testMode -> npString(R.string.np_0acd32441928 , target, optSuffix)
        testMode -> npString(R.string.np_e4e95c845760 , target, optSuffix)
        else -> npString(R.string.np_21199640e8ea , target, optSuffix)
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
                            .background(if (idx < count) Red else npNeutral(0xFF333333), CircleShape)
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
        npString(R.string.np_606a26e6f91d , currentYear)
    } else {
        currentMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", npLocale()))
    }

    Screen(
        ndot = ndot,
        eyebrow = npString(R.string.np_47ed0cee9663),
        title = npString(R.string.np_cd3f33f4efd7),
        subtitle = subtitle
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ChoiceChip(npString(R.string.np_da13625eeb37), !yearScope, Modifier.width(82.dp)) {
                    if (yearScope) {
                        yearScope = false
                        onHapticTick()
                    }
                }
                ChoiceChip(npString(R.string.np_956a6e5ab6c7), yearScope, Modifier.width(82.dp)) {
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
                        .background(if (canGoNext) Panel else npNeutral(0xFF101010))
                        .clickable(enabled = canGoNext) {
                            if (yearScope) currentYear += 1 else currentMonth = currentMonth.plusMonths(1)
                            onHapticTick()
                        },
                    contentAlignment = Alignment.Center
                ) { Text("›", color = if (canGoNext) White else npNeutral(0xFF3A3A3A), fontSize = 18.sp) }
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
                            currentMonth.atDay(1).format(DateTimeFormatter.ofPattern("MMMM yyyy", npLocale())).uppercase(npLocale()),
                            color = White,
                            fontFamily = FontFamily.Default,
                            fontSize = 11.sp,
                            letterSpacing = 1.6.sp
                        )
                        Text(
                            npString(R.string.np_c14c5be3b07e , perfectDays, activeDays),
                            color = Muted,
                            fontFamily = FontFamily.Default,
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
                                    fontFamily = FontFamily.Default,
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
                MiniYearStat(npString(R.string.np_dba8c7f0f351), "${counts.first}", Red, Modifier.weight(1f), ndot)
                MiniYearStat("TEILWEISE", "${counts.second}", White, Modifier.weight(1f), ndot)
                MiniYearStat(npString(R.string.np_8f075c7e5cba), "${counts.third}", Muted, Modifier.weight(1f), ndot)
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
                                                isFuture -> npNeutral(0xFF141414)
                                                snap.state == DayCompletionState.ALL -> Red
                                                snap.state == DayCompletionState.PARTIAL -> White
                                                else -> npNeutral(0xFF2B2B2B)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .aspectRatio(1f)
                                                    .clip(CircleShape)
                                                    .then(
                                                        when {
                                                            isSelected -> Modifier.border(1.dp, White, CircleShape)
                                                            isToday -> Modifier.border(1.dp, npNeutral(0xFF5A5A5A), CircleShape)
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
            DayCompletionState.NONE -> npNeutral(0xFF2D2D2D)
            null -> npNeutral(0xFF141414)
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
                    if (isToday) Modifier.border(1.dp, npNeutral(0xFF525252), CircleShape)
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
            DayCompletionState.NONE -> npNeutral(0xFF121212)
            null -> Color.Transparent
        }
        val textColor = when (state) {
            DayCompletionState.ALL -> White
            DayCompletionState.PARTIAL -> White
            DayCompletionState.NONE -> Muted
            null -> npNeutral(0xFF333333)
        }
        Box(
            modifier = modifier
                .height(38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(bgColor)
                .then(
                    when {
                        isSelected -> Modifier.border(1.5.dp, White, RoundedCornerShape(11.dp))
                        isToday -> Modifier.border(1.dp, npNeutral(0xFF555555), RoundedCornerShape(11.dp))
                        else -> Modifier
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${dayNum}",
                    color = textColor,
                    fontFamily = FontFamily.Default,
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
    val monthName = ym.atDay(1).format(DateTimeFormatter.ofPattern("MMM", npLocale())).uppercase(npLocale())
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
                    fontFamily = FontFamily.Default,
                    fontSize = 11.sp,
                    letterSpacing = 1.4.sp
                )
                Text(
                    "${perfect}/${ym.lengthOfMonth()}",
                    color = Muted,
                    fontFamily = FontFamily.Default,
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
                                    isFuture -> npNeutral(0xFF141414)
                                    s.state == DayCompletionState.ALL -> Red
                                    s.state == DayCompletionState.PARTIAL -> White
                                    else -> npNeutral(0xFF2B2B2B)
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
                Text(label, color = Muted, fontFamily = FontFamily.Default, fontSize = 9.sp, letterSpacing = 1.2.sp)
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
        LegendItem(Red, npString(R.string.np_f77edaf1cc27))
        LegendItem(White, "Teilweise")
        LegendItem(npNeutral(0xFF2D2D2D), npString(R.string.np_b730f7bffbc0))
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
        npString(R.string.np_97043bacc3aa , snap.date.format(DateTimeFormatter.ofPattern("d. MMMM", npLocale())).uppercase(npLocale()))
    } else {
        snap.date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", npLocale())).uppercase(npLocale())
    }
    val statusBadge = when (snap.state) {
        DayCompletionState.ALL -> npString(R.string.np_aa4654cc6edd)
        DayCompletionState.PARTIAL -> npString(R.string.np_fae16c3927d1)
        DayCompletionState.NONE -> npString(R.string.np_15815f5956d2)
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
                    Text(label, color = Muted, fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 1.4.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(statusBadge, color = badgeColor, fontFamily = FontFamily.Default, fontSize = 11.sp, letterSpacing = 1.4.sp)
                }
                DisplayCount(snap.done, snap.total, ndot, 28.sp)
            }

            if (snap.historicalTasks.isNotEmpty()) {
                HorizontalDivider(color = npNeutral(0xFF282828), modifier = Modifier.padding(vertical = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snap.historicalTasks.forEach { item ->
                        val isDone = item.done >= item.target
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(if (isDone) Red else npNeutral(0xFF333333), CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(item.name + if (item.optional) npString(R.string.np_f1a3cf2f741b) else "", color = if (isDone) White else Muted,
                                fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Text(if (isDone) "✓" else "${item.done}/${item.target}",
                                color = if (isDone) Red else Muted, fontSize = 12.sp)
                        }
                    }
                }
            } else if (snap.dailyRules.isNotEmpty() || snap.weeklyDoneRules.isNotEmpty()) {
                HorizontalDivider(color = npNeutral(0xFF282828), modifier = Modifier.padding(vertical = 12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    snap.dailyRules.forEach { r ->
                        val isDone = doneCountFor(r, snap.date) >= totalOf(r)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(7.dp)
                                    .background(if (isDone) Red else npNeutral(0xFF333333), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (r.optional) npString(R.string.np_4c8fecf40cb0 , r.name) else r.name,
                                color = if (isDone) White else Muted,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                if (isDone) "✓" else "–",
                                color = if (isDone) Red else Muted,
                                fontFamily = FontFamily.Default,
                                fontSize = 12.sp
                            )
                        }
                    }
                    snap.weeklyDoneRules.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(7.dp).background(Red, CircleShape))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (w.optional) npString(R.string.np_1ecdf65a05c3 , w.name) else npString(R.string.np_0164e250ee12 , w.name),
                                color = White,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Text("✓", color = Red, fontFamily = FontFamily.Default, fontSize = 12.sp)
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
    val day = listOf(npString(R.string.np_8bb0f19f592e), npString(R.string.np_b2ce6b5d7cb1), npString(R.string.np_ea3552526134), npString(R.string.np_7c3df2c5fe25), npString(R.string.np_0ca5853904f5), npString(R.string.np_85ad5644425c), npString(R.string.np_f8e9c756eaa2))[now.dayOfWeek.value - 1]
    val time = when (now.hour) {
        in 5..9 -> "Morgens"
        in 10..11 -> "Vormittags"
        12 -> "Mittags"
        in 13..16 -> "Nachmittags"
        else -> "Abends"
    }
    val base = npString(R.string.np_204a119419ae , day, time)
    val used = existing.map { it.name.trim() }.toSet()
    if (base !in used) return base
    var number = 2
    while ("${base} (${number})" in used) number++
    return "${base} (${number})"
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
            npString(R.string.np_6a8982ba88d0 , best.reps) to best.date
        }
        ExerciseKind.CARDIO -> {
            val best = logs.maxByOrNull { it.seconds } ?: return null
            npString(R.string.np_0e961ac28402 , best.seconds / 60) to best.date
        }
        ExerciseKind.AMRAP -> {
            val best = logs.maxByOrNull { it.reps } ?: return null
            npString(R.string.np_7704a2ed7811 , best.reps) to best.date
        }
        ExerciseKind.TIMED -> {
            val best = if (ex.targetReps > 1) {
                logs.maxWithOrNull(compareBy<ExerciseLog> { it.reps }.thenBy { -it.seconds })
            } else {
                logs.maxByOrNull { it.seconds }
            } ?: return null
            val txt = if (best.reps > 1) npString(R.string.np_dd9d4ca852da , best.reps, best.seconds) else npString(R.string.np_7f854c57c3cb , best.seconds)
            txt to best.date
        }
    }
}

@Composable
private fun ExerciseKindIcon(kind: ExerciseKind) {
    Canvas(Modifier.size(28.dp)) {
        val stroke = 1.9.dp.toPx()
        val w = size.width; val h = size.height
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(White, Offset(w*x, h*y), Offset(w*xx, h*yy), stroke, StrokeCap.Round)
        fun ring(x: Float, y: Float, r: Float) =
            drawCircle(White, w*r, Offset(w*x,h*y), style = Stroke(stroke))
        when (kind) {
            ExerciseKind.WEIGHT -> {
                // Solid plates with rounded corners and a single connecting bar.
                line(.20f,.50f,.80f,.50f)
                listOf(.20f,.68f).forEach { x ->
                    drawRoundRect(White, Offset(w*x,h*.25f), Size(w*.12f,h*.50f), CornerRadius(2.dp.toPx()))
                }
                listOf(.08f,.82f).forEach { x ->
                    drawRoundRect(White, Offset(w*x,h*.35f), Size(w*.10f,h*.30f), CornerRadius(1.5.dp.toPx()))
                }
            }
            ExerciseKind.BODYWEIGHT -> {
                drawCircle(White,w*.085f,Offset(w*.50f,h*.18f))
                line(.50f,.34f,.50f,.57f)
                line(.25f,.37f,.75f,.37f)
                line(.50f,.57f,.31f,.85f); line(.50f,.57f,.69f,.85f)
            }
            ExerciseKind.TIMED -> {
                // Minimal clock, no crown or decorative tick marks.
                ring(.50f,.50f,.34f)
                line(.50f,.50f,.50f,.27f)
                line(.50f,.50f,.68f,.61f)
                drawCircle(White,w*.035f,Offset(w*.50f,h*.50f))
            }
            ExerciseKind.CARDIO -> {
                // Rounded heart outline with a pulse inside.
                val heart = Path().apply {
                    moveTo(w*.50f,h*.83f)
                    cubicTo(w*.35f,h*.70f,w*.12f,h*.52f,w*.16f,h*.34f)
                    cubicTo(w*.20f,h*.12f,w*.42f,h*.14f,w*.50f,h*.30f)
                    cubicTo(w*.61f,h*.11f,w*.81f,h*.15f,w*.85f,h*.34f)
                    cubicTo(w*.89f,h*.52f,w*.65f,h*.73f,w*.50f,h*.83f)
                }
                drawPath(heart, White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                val pulse = Path().apply {
                    moveTo(w*.24f,h*.49f); lineTo(w*.39f,h*.49f)
                    lineTo(w*.45f,h*.39f); lineTo(w*.53f,h*.62f)
                    lineTo(w*.61f,h*.49f); lineTo(w*.76f,h*.49f)
                }
                drawPath(pulse, White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            ExerciseKind.AMRAP -> {
                // Legacy exercises retain a recognizable circular timer icon.
                ring(.50f,.53f,.31f)
                line(.50f,.53f,.50f,.34f)
                line(.50f,.53f,.65f,.60f)
                line(.50f,.10f,.50f,.18f)
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
    onUpdateExercise: (Exercise) -> Unit,
    onImportExercises: (List<Exercise>, String?) -> Unit,
    onDeleteExercise: (String) -> Unit,
    onLogSet: (ExerciseLog) -> Unit,
    onDeleteLog: (String) -> Unit,
    onDeleteSessionLogs: (Set<String>) -> Unit,
    onHapticTick: () -> Unit
) {
    val context = LocalContext.current
    // 0 = Workouts (Hauptfokus), 1 = Uebungen, 2 = Rekorde
    var subPage by remember { mutableIntStateOf(0) }
    val resultPrefs = remember { NPlannerStore.get(context) }
    var resultRevision by remember { mutableIntStateOf(0) }
    var selectedAmrapResult by remember { mutableStateOf<JSONObject?>(null) }
    var exerciseQuery by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf<ExerciseKind?>(null) }
    var exerciseDescending by remember { mutableStateOf(false) }
    var groupExercises by remember { mutableStateOf(true) }
    var showExerciseFilters by remember { mutableStateOf(false) }
    var showAiExerciseMenu by remember { mutableStateOf(false) }
    val amrapResults = remember(resultRevision, subPage) {
        resultPrefs.all.entries.filter { it.key.startsWith("amrap_session:") }
            .mapNotNull { runCatching { JSONObject(it.value as String) }.getOrNull() }
            .sortedByDescending { it.optLong("endedAt") }
    }
    val pastWorkouts = remember(resultRevision, logs, workouts) {
        val stored = resultPrefs.all.filterKeys { it.startsWith("workout_session:") }.values
            .mapNotNull { raw -> runCatching { JSONObject(raw as String) }.getOrNull() }
        val legacy = logs.filter { it.sessionId.isBlank() && it.workoutId.isNotBlank() }
            .groupBy { it.workoutId to it.date }.mapNotNull { (key, sets) ->
                val w = workouts.find { it.id == key.first } ?: return@mapNotNull null
                if (w.kind == WorkoutKind.AMRAP) return@mapNotNull null
                workoutSessionSnapshot(w, exercises, sets, 0L, 0L, "legacy:${key.first}:${key.second}")
                    .put("legacyDate", key.second).put("legacy", true)
            }
        (stored + legacy).sortedByDescending { obj ->
            if (obj.optBoolean("legacy")) obj.optString("legacyDate") + " 00:00"
            else java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(java.util.Date(obj.optLong("endedAt")))
        }
    }
    val exerciseComparator = remember(NPlannerAppearance.language) { java.text.Collator.getInstance(npLocale()) }
    val visibleExercises = exercises.filter {
        (kindFilter == null || it.kind == kindFilter) && it.name.contains(exerciseQuery, ignoreCase = true)
    }.sortedWith(Comparator { a, b ->
        val group = if (groupExercises) a.kind.ordinal.compareTo(b.kind.ordinal) else 0
        if (group != 0) group else exerciseComparator.compare(a.name, b.name) * if (exerciseDescending) -1 else 1
    })

    val sessionPrefs = remember { NPlannerStore.get(context) }
    var activeWorkoutSessionId by remember { mutableStateOf(sessionPrefs.getString("active_workout", null)) }
    var sessionStartedAt by remember { mutableStateOf(sessionPrefs.getLong("started_at", 0L)) }
    var classicRunId by remember {
        mutableStateOf(sessionPrefs.getString("classic_run_id", null) ?: UUID.randomUUID().toString().also {
            sessionPrefs.edit().putString("classic_run_id", it).apply()
        })
    }
    var selectedWorkoutResult by remember { mutableStateOf<JSONObject?>(null) }
    var pendingHistoryDelete by remember { mutableStateOf<Pair<Boolean, JSONObject>?>(null) }
    var showAddExistingExercise by remember { mutableStateOf(false) }
    var openWorkoutId by remember { mutableStateOf<String?>(null) }
    var amrapWorkoutId by remember { mutableStateOf<String?>(sessionPrefs.getString("amrap_workout", null)) }
    fun beginWorkout(id: String) {
        if (activeWorkoutSessionId == id) { openWorkoutId = id; return }
        if (activeWorkoutSessionId != null) {
            Toast.makeText(context, npString(R.string.np_bbfb23b841a2), Toast.LENGTH_SHORT).show()
            return
        }
        classicRunId = UUID.randomUUID().toString()
        sessionPrefs.edit().putString("classic_run_id", classicRunId).apply()
        activeWorkoutSessionId = id
        sessionStartedAt = System.currentTimeMillis()
        sessionPrefs.edit().putString("active_workout", id).putLong("started_at", sessionStartedAt).apply()
        openWorkoutId = id
        if (workouts.firstOrNull { it.id == id }?.kind == WorkoutKind.AMRAP) {
            val prefix = "amrap:${id}:"
            if (sessionPrefs.getBoolean(prefix + "finished", false)) {
                val rounds = sessionPrefs.getInt(prefix + "rounds", 0)
                val completed = sessionPrefs.getInt(prefix + "index", 0)
                val archiveKey = "amrap_result:${System.currentTimeMillis()}:${id}"
                sessionPrefs.edit().putString(archiveKey, "${rounds}:${completed}").apply()
                sessionPrefs.edit().remove(prefix + "finished").remove(prefix + "running")
                    .remove(prefix + "index").remove(prefix + "rounds")
                    .remove(prefix + "remaining").remove(prefix + "endAt").apply()
            }
            amrapWorkoutId = id
            sessionPrefs.edit().putString("amrap_workout", id).apply()
        }
    }
    fun finishWorkout() {
        val finishedWorkout = workouts.firstOrNull { it.id == activeWorkoutSessionId }
        if (finishedWorkout != null && finishedWorkout.kind != WorkoutKind.AMRAP && sessionStartedAt > 0L) {
            val persistedLogs = runCatching {
                val raw = resultPrefs.getString("exercise_logs_v1", "[]") ?: "[]"
                val arr = JSONArray(raw)
                (0 until arr.length()).mapNotNull { i ->
                    val obj = arr.optJSONObject(i) ?: return@mapNotNull null
                    if (obj.optString("workoutId") != finishedWorkout.id || obj.optString("sessionId") != classicRunId) return@mapNotNull null
                    ExerciseLog(id = obj.getString("id"), exerciseId = obj.getString("exerciseId"), date = obj.getString("date"),
                        weightKg = obj.optDouble("weightKg", 0.0).toFloat(), reps = obj.optInt("reps"),
                        seconds = obj.optInt("seconds"), workoutId = finishedWorkout.id,
                        distanceKm = obj.optDouble("distanceKm", 0.0).toFloat(), sessionId = classicRunId)
                }
            }.getOrDefault(emptyList())
            val sets = (logs.filter { it.workoutId == finishedWorkout.id && it.sessionId == classicRunId } + persistedLogs).distinctBy { it.id }
            val result = workoutSessionSnapshot(finishedWorkout, exercises, sets, sessionStartedAt,
                System.currentTimeMillis(), classicRunId)
            resultPrefs.edit().putString("workout_session:${classicRunId}", result.toString()).apply()
            resultRevision++
            selectedWorkoutResult = result
            onDeleteWorkout(finishedWorkout.id)
        }
        amrapWorkoutId = null
        sessionPrefs.edit().remove("amrap_workout").apply()
        activeWorkoutSessionId = null
        sessionStartedAt = 0L
        sessionPrefs.edit().remove("active_workout").remove("started_at").remove("classic_run_id").apply()
        openWorkoutId = null
    }
    var activeExercise by remember { mutableStateOf<Exercise?>(null) }
    var editingExercise by remember { mutableStateOf<Exercise?>(null) }
    var showNewWorkoutDialog by remember { mutableStateOf(false) }
    var showTemplateManager by remember { mutableStateOf(false) }
    var requestedPersonalTemplate by remember { mutableStateOf<String?>(null) }
    var editingTemplateKey by remember { mutableStateOf<String?>(null) }
    var detailWorkoutId by remember { mutableStateOf<String?>(null) }
    var templateRevision by remember { mutableIntStateOf(0) }
    val templates = remember(templateRevision, showTemplateManager) {
        resultPrefs.all.filterKeys { it.startsWith("workout_template:") }
            .mapNotNull { (key, value) -> runCatching { key to JSONObject(value as String) }.getOrNull() }
            .sortedBy { it.second.optString("name").lowercase(Locale.GERMAN) }
    }
    var editingWorkout by remember { mutableStateOf<Workout?>(null) }
    var showNewExerciseDialog by remember { mutableStateOf(false) }
    var showAiImportDialog by remember { mutableStateOf(false) }
    var pendingExerciseExport by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null && pendingExerciseExport.isNotBlank()) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(pendingExerciseExport) }
            }.onFailure { Toast.makeText(context, npString(R.string.np_a9695d3efead), Toast.LENGTH_SHORT).show() }
        }
    }
    fun shareExercise(ex: Exercise) {
        val payload = exportNpefJson(listOf(ex))
        runCatching {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, payload)
            }, npString(R.string.np_3e30e6838966)))
        }
    }
    var targetWorkoutForNewEx by remember { mutableStateOf<String?>(null) }

    val todayStr = today.toString()

    fun shareAiPromptToExternalApp() {
        onHapticTick()
        val prompt = buildAiExercisePrompt()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("NPlanner KI-Vorlage", prompt))
        Toast.makeText(context, npString(R.string.np_1166fc3ed310), Toast.LENGTH_SHORT).show()

        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "NPlanner Übung erstellen (${NPEF_FORMAT_ID})")
            putExtra(Intent.EXTRA_TEXT, prompt)
        }
        val chooser = Intent.createChooser(sendIntent, npString(R.string.np_96fc72d1a322)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    Screen(
        ndot = ndot,
        eyebrow = npString(R.string.np_d30ed0e05d1b),
        title = npString(R.string.np_78db94f73df9),
        subtitle = npString(R.string.np_40641d40924f , if (activeWorkoutSessionId != null) npString(R.string.np_3f85c2f7419d) else npString(R.string.np_57fce7be21d3), pastWorkouts.size + amrapResults.size)
    ) {
        // Subtile Unterseiten-Navigation: Workouts | Übungen | Rekorde
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ChoiceChip(npString(R.string.np_ccb58b229959), subPage == 0, Modifier.weight(1f)) {
                if (subPage != 0) {
                    subPage = 0
                    onHapticTick()
                }
            }
            ChoiceChip(npString(R.string.np_bd0ffd87acdf), subPage == 1, Modifier.weight(1f)) {
                if (subPage != 1) {
                    subPage = 1
                    onHapticTick()
                }
            }
            ChoiceChip(npString(R.string.np_6a1cae58339e), subPage == 2, Modifier.weight(1f)) {
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
                            Text(npString(R.string.np_3d6849f8dec5), color = Red, fontSize = 10.sp, fontFamily = FontFamily.Default)
                            Text(running.name, color = White, fontSize = 14.sp)
                        }
                        Text(npString(R.string.np_d3a0c92f97e5), color = White, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        when (subPage) {
            // ================= UNTERSEITE 0: WORKOUTS (HAUPTFOKUS) =================
            0 -> {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(color = White, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).clickable {

                        requestedPersonalTemplate = null
                        showNewWorkoutDialog = true
                    }) {
                        Column(Modifier.padding(15.dp)) {
                            Text(npString(R.string.np_08a05cd11652), color = Bg, fontSize = 12.sp)
                            Text(npString(R.string.np_7a61498caf47), color = npNeutral(0xFF555555), fontSize = 10.sp)
                        }
                    }
                    Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.weight(1f).clickable {
                        showTemplateManager = true
                    }) {
                        Column(Modifier.padding(15.dp)) {
                            Text(npString(R.string.np_7e022f02cec6), color = White, fontSize = 12.sp)
                            Text(npString(R.string.np_4c5118cd65ed), color = Muted, fontSize = 10.sp)
                        }
                    }
                }
                SectionLabel(npString(R.string.np_7238de321955), topPadding = 22.dp, bottomPadding = 10.dp)
                if (pastWorkouts.isEmpty()) Text(npString(R.string.np_761fc34088c6), color = Muted, fontSize = 12.sp)
                pastWorkouts.forEach { result ->
                    Surface(color = Panel, shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { selectedWorkoutResult = result }) {
                        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(result.optString("name"), color = White, fontSize = 15.sp)
                                Text(if (result.optBoolean("legacy")) result.optString("legacyDate") + npString(R.string.np_82d36c5ed290)
                                    else java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", npLocale()).format(java.util.Date(result.optLong("endedAt"))),
                                    color = Muted, fontSize = 11.sp)
                            }
                            Text(npString(R.string.np_85b7821c3553 , result.optInt("setCount")), color = White, fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(18.dp))
                SectionLabel(npString(R.string.np_9bf3182032ce), topPadding = 6.dp, bottomPadding = 10.dp)
                if (workouts.none { it.kind != WorkoutKind.AMRAP && it.id != activeWorkoutSessionId }) {
                    Surface(
                        color = Panel,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onHapticTick()

                        requestedPersonalTemplate = null
                        showNewWorkoutDialog = true
                            }
                    ) {
                        Column(Modifier.padding(22.dp)) {
                            Text(npString(R.string.np_1c8383eed46b), color = White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                npString(R.string.np_ee3a19e84698),
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
                                    npString(R.string.np_9f521292df82),
                                    color = Bg,
                                    fontFamily = FontFamily.Default,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 1.2.sp
                                )
                            }
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        workouts.filter { it.kind != WorkoutKind.AMRAP && it.id != activeWorkoutSessionId }.forEach { workout ->
                            val wExercises = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } }
                            val isThisWorkoutActive = activeWorkoutSessionId == workout.id

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
                                            .background(if (isThisWorkoutActive) Red else npNeutral(0xFF3A3A3A), CircleShape)
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
                                            
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        val previewNames = if (wExercises.isEmpty()) {
                                            npString(R.string.np_6f7a59eef1d7)
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
                                        fontFamily = FontFamily.Default,
                                        fontSize = 12.sp
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text("›", color = Muted, fontSize = 22.sp)
                                }
                            }
                        }
                    }
                }

                if (amrapResults.isNotEmpty()) {
                    SectionLabel(npString(R.string.np_15baed2cabe0), topPadding = 20.dp, bottomPadding = 10.dp)
                    amrapResults.forEach { result ->
                        AmrapResultCard(result, ndot) { selectedAmrapResult = result }
                    }
                }
            }

            // ================= UNTERSEITE 1: ALLE ÜBUNGEN =================
            1 -> {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        exerciseQuery, { exerciseQuery = it },
                        label = { Text(npString(R.string.np_d8650daa10f4)) }, singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Surface(color = Panel2, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(52.dp).clickable { showExerciseFilters = true }) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("☷", color = White, fontSize = 23.sp)
                        }
                    }
                }
                Text(npString(R.string.np_5600339b66ff , visibleExercises.size) +
                    (if (groupExercises) npString(R.string.np_370d44de0e4f) else npString(R.string.np_8917bbe624ec)) + " · " +
                    (if (exerciseDescending) npString(R.string.np_3d0a383079cf) else npString(R.string.np_20ec7e3ddab7)),
                    color = Muted, fontFamily = FontFamily.Default, fontSize = 10.sp,
                    modifier = Modifier.padding(top = 8.dp, bottom = 10.dp))
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
                                npString(R.string.np_9efdcfb98021),
                                color = Bg,
                                fontFamily = FontFamily.Default,
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
                            .clickable { showAiExerciseMenu = true },
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
                                npString(R.string.np_8d255d3f1bd7),
                                color = White,
                                fontFamily = FontFamily.Default,
                                fontSize = 10.sp,
                                letterSpacing = 1.sp,
                                maxLines = 1
                            )
                        }
                    }

                }

                SectionLabel(npString(R.string.np_af9f8f9cf25b), topPadding = 14.dp, bottomPadding = 10.dp)

                if (visibleExercises.isEmpty()) {
                    Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(
                            npString(R.string.np_17cbec673e17),
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        visibleExercises.forEachIndexed { index, ex ->
                            if (groupExercises && (index == 0 || visibleExercises[index - 1].kind != ex.kind)) {
                                SectionLabel(exerciseKindLabel(ex.kind).uppercase(npLocale()), topPadding = 10.dp, bottomPadding = 6.dp)
                            }
                            val exLogs = logs.filter { it.exerciseId == ex.id }
                            val lastLog = exLogs.lastOrNull()
                            Surface(
                                color = Panel,
                                shape = RoundedCornerShape(22.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onHapticTick()
                                        detailWorkoutId = null
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
                                                npString(R.string.np_c60937ec6f6d , logSummary(ex, lastLog)),
                                                color = Color(0xFFB4B4B0),
                                                fontFamily = FontFamily.Default,
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
                    exercises.filter { it.kind != ExerciseKind.AMRAP }.mapNotNull { ex ->
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
                                Text(npString(R.string.np_eb3f070bc197), color = Muted, fontFamily = FontFamily.Default, fontSize = 9.sp, letterSpacing = 1.4.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            DisplayTitle("${records.size + amrapResults.map { it.optString("signature") }.distinct().size}", ndot, fontSize = 32.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = Panel, shape = RoundedCornerShape(22.dp)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(6.dp).background(White, CircleShape))
                                Spacer(Modifier.width(7.dp))
                                Text(npString(R.string.np_dadafe9ea717), color = Muted, fontFamily = FontFamily.Default, fontSize = 9.sp, letterSpacing = 1.4.sp)
                            }
                            Spacer(Modifier.height(8.dp))
                            DisplayTitle("${logs.size}", ndot, fontSize = 32.sp)
                        }
                    }
                }

                val amrapBests = amrapResults.groupBy { it.optString("signature") }
                    .values.mapNotNull { group -> group.maxByOrNull { it.optInt("score") } }
                val strength = records.filter { it.first.kind in listOf(ExerciseKind.WEIGHT, ExerciseKind.BODYWEIGHT, ExerciseKind.TIMED) }
                val cardio = records.filter { it.first.kind == ExerciseKind.CARDIO }
                @Composable
                fun RecordGroup(title: String, entries: List<Triple<Exercise, String, String>>) {
                    SectionLabel(title, topPadding = 18.dp, bottomPadding = 10.dp)
                    if (entries.isEmpty()) Text(npString(R.string.np_17e45c81cc42), color = Muted, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        entries.sortedBy { it.first.name.lowercase(Locale.GERMAN) }.forEach { (ex, value, day) ->
                            Surface(color = Panel, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth().clickable {
                                detailWorkoutId = null
                                activeExercise = ex
                            }) {
                                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ExerciseKindIcon(ex.kind)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(ex.name, color = White, fontSize = 15.sp)
                                        Text(day, color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Default)
                                    }
                                    Text(value, color = Red, fontSize = 14.sp, fontFamily = FontFamily.Default)
                                }
                            }
                        }
                    }
                }
                RecordGroup(npString(R.string.np_84415f0defd3), strength)
                RecordGroup(npString(R.string.np_3e0f7ed18a84), cardio)
                SectionLabel(npString(R.string.np_fca4530c0eb9), topPadding = 18.dp, bottomPadding = 10.dp)
                if (amrapBests.isEmpty()) Text(npString(R.string.np_73a8664f0b43), color = Muted, fontSize = 12.sp)
                amrapBests.forEach { result -> AmrapResultCard(result, ndot) { selectedAmrapResult = result } }
                if (amrapBests.isNotEmpty()) Text(npString(R.string.np_4d62cf662110),
                    color = Muted, fontSize = 10.sp)
            }
        }
    }

    // Subtiles Overlay-Dialog-Fenster fuer das geoeffnete Workout (hier erscheinen die Uebungen)
    pendingHistoryDelete?.let { (isAmrap, result) ->
        AlertDialog(
            onDismissRequest = { pendingHistoryDelete = null }, containerColor = Panel,
            title = { DisplayTitle(npString(R.string.np_e00b9ebf7918), ndot, fontSize = 24.sp) },
            text = { Text(if (isAmrap)
                npString(R.string.np_06bbdee649e4)
                else if (result.optBoolean("legacy"))
                npString(R.string.np_850b0e521c71)
                else npString(R.string.np_c235598fe990), color = Muted) },
            confirmButton = { TextButton(onClick = {
                val runId = result.optString("runId")
                if (isAmrap) {
                    resultPrefs.edit().remove("amrap_session:${runId}").apply()
                    selectedAmrapResult = null
                } else {
                    val storedIds = result.optJSONArray("logIds")
                    val ids = if (storedIds != null) (0 until storedIds.length()).map { storedIds.optString(it) }.toSet()
                    else logs.filter { log ->
                        if (result.optBoolean("legacy")) log.sessionId.isBlank() &&
                            log.workoutId == result.optString("workoutId") && log.date == result.optString("legacyDate")
                        else log.sessionId == runId && log.workoutId == result.optString("workoutId")
                    }.map { it.id }.toSet()
                    onDeleteSessionLogs(ids)
                    resultPrefs.edit().remove("workout_session:${runId}").apply()
                    selectedWorkoutResult = null
                }
                resultRevision++
                pendingHistoryDelete = null
            }) { Text(npString(R.string.np_9df6718de96c), color = Red) } },
            dismissButton = { TextButton(onClick = { pendingHistoryDelete = null }) { Text(npString(R.string.np_07af7cb30fca)) } }
        )
    }

    selectedWorkoutResult?.let { result ->
        WorkoutSessionSummary(result, ndot,
            onDelete = { pendingHistoryDelete = false to result },
            onClose = { selectedWorkoutResult = null })
    }

    selectedAmrapResult?.let { result ->
        AmrapSummaryDialog(result, ndot,
            onDelete = { pendingHistoryDelete = true to result },
            onClose = { selectedAmrapResult = null })
    }

    openWorkoutId?.let { wId ->
        val workout = workouts.find { it.id == wId }
        if (workout == null) {
            openWorkoutId = null
        } else {
            if (workout.kind == WorkoutKind.AMRAP && activeWorkoutSessionId == workout.id) {
                AmrapLiveDialog(
                    ndot = ndot,
                    workout = workout,
                    exercises = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id }?.let { ex ->
                        ex.copy(targetWeightKg = workout.targetWeights[id] ?: ex.targetWeightKg)
                    } },
                    prefs = sessionPrefs,
                    onDismiss = { openWorkoutId = null },
                    onFinish = { resultRevision++; finishWorkout() },
                    onHapticTick = onHapticTick
                )
            } else WorkoutSheetDialog(
                ndot = ndot,
                todayStr = todayStr,
                workout = workout,
                exercises = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id } },
                logs = logs,
                onDismiss = { openWorkoutId = null },
                isActive = activeWorkoutSessionId == workout.id,
                startedAt = sessionStartedAt,
                sessionId = classicRunId,
                onStartWorkout = { beginWorkout(workout.id) },
                onFinishWorkout = { finishWorkout() },
                onAddExistingExercise = { showAddExistingExercise = true },
                onSelectExercise = { ex ->
                    onHapticTick()
                    detailWorkoutId = workout.id
                    activeExercise = ex
                },
                onAddNewExercise = {
                    onHapticTick()
                    targetWorkoutForNewEx = workout.id
                    showNewExerciseDialog = true
                },
                onReorderExercises = { orderedIds ->
                    if (orderedIds.size == workout.exerciseIds.size && orderedIds.toSet() == workout.exerciseIds.toSet()) {
                        onUpdateWorkout(workout.copy(exerciseIds = orderedIds))
                    }
                },
                onSaveTemplate = {
                    val latest = workout.exerciseIds.mapNotNull { id ->
                        logs.lastOrNull { it.exerciseId == id && it.workoutId == workout.id }?.let { id to it }
                    }.toMap()
                    val snapshot = workout.copy(
                        amrapTargets = workout.amrapTargets + latest.mapValues { it.value.reps.coerceAtLeast(1) },
                        targetWeights = workout.targetWeights + latest.mapValues { it.value.weightKg }
                    )
                    savePersonalWorkoutTemplate(context, snapshot, exercises)
                    Toast.makeText(context, npString(R.string.np_eafdd7e6d735), Toast.LENGTH_SHORT).show()
                },
                onQuickLog = { log -> onLogSet(log.copy(workoutId = workout.id)) },
                onEditWorkout = {
                    onHapticTick()
                    editingWorkout = workout
                },
                onDeleteWorkout = {
                    if (activeWorkoutSessionId == workout.id) finishWorkout()
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
                title = { Text(npString(R.string.np_98ed891e604b), color = White) },
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
                confirmButton = { TextButton(onClick = { showAddExistingExercise = false }) { Text(npString(R.string.np_44424b18700e)) } },
                containerColor = Panel
            )
        }
    }

    if (showTemplateManager) {
        WorkoutTemplateManager(
            ndot = ndot, templates = templates,
            onDismiss = { showTemplateManager = false },
            onPersonal = { key ->

                requestedPersonalTemplate = key
                showTemplateManager = false
                showNewWorkoutDialog = true
            },
            onEdit = { key ->
                showTemplateManager = false
                editingTemplateKey = key
            },
            onDelete = { key -> resultPrefs.edit().remove(key).apply(); templateRevision++ }
        )
    }

    editingTemplateKey?.let { templateKey ->
        key(templateKey) {
            WorkoutEditorDialog(
                ndot = ndot, title = "VORLAGE BEARBEITEN",
                initialName = "", initialNote = "", initialPersonalKey = templateKey,
                editingTemplate = true,
                initialSelectedIds = emptySet(), initialKind = WorkoutKind.CLASSIC,
                initialDurationSeconds = 0, existingWorkouts = workouts,
                allExercises = exercises, isNew = false,
                onAddPresetExercises = { missing -> onImportExercises(missing, null) },
                onDismiss = { editingTemplateKey = null; showTemplateManager = true },
                onSave = { name, note, ids, kind, seconds, targets, weights ->
                    val w = Workout("template-edit", name, ids, note, kind, seconds, targets, weights)
                    savePersonalWorkoutTemplate(context, w, exercises, templateKey)
                    templateRevision++
                    editingTemplateKey = null
                    showTemplateManager = true
                }
            )
        }
    }

    if (showNewWorkoutDialog) {
        WorkoutEditorDialog(
            ndot = ndot,
            title = "NEUES WORKOUT",
            initialName = suggestWorkoutName(workouts, emptyList()),
            initialNote = "",
            initialPersonalKey = requestedPersonalTemplate,
            initialSelectedIds = emptySet(),
            initialKind = WorkoutKind.CLASSIC,
            initialDurationSeconds = 0,
            existingWorkouts = workouts,
            allExercises = exercises,
            isNew = true,
            onAddPresetExercises = { missing -> onImportExercises(missing, null) },
            onDismiss = { showNewWorkoutDialog = false },
            onSave = createWorkout@ { finalName, note, selectedIds, kind, durationSeconds, targets, weights ->
                if (kind == WorkoutKind.AMRAP && activeWorkoutSessionId != null) {
                    Toast.makeText(context, npString(R.string.np_bbfb23b841a2), Toast.LENGTH_SHORT).show()
                    return@createWorkout
                }
                val created = Workout(
                    id = UUID.randomUUID().toString(),
                    name = finalName,
                    exerciseIds = selectedIds,
                    note = note,
                    kind = kind,
                    durationSeconds = durationSeconds,
                    amrapTargets = targets,
                    targetWeights = weights
                )
                onCreateWorkout(created)
                showNewWorkoutDialog = false
                openWorkoutId = created.id
                if (created.kind == WorkoutKind.AMRAP) {
                    beginWorkout(created.id)
                    val prefix = "amrap:${created.id}:"
                    sessionPrefs.edit().putInt(prefix + "index", 0).putInt(prefix + "rounds", 0)
                        .putBoolean(prefix + "finished", false).putBoolean(prefix + "running", false)
                        .putLong(prefix + "remaining", created.durationSeconds.coerceAtLeast(1) * 1000L).apply()
                    amrapWorkoutId = created.id
                    sessionPrefs.edit().putString("amrap_workout", created.id).apply()
                }
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
            initialTargets = wToEdit.amrapTargets,
            initialWeights = wToEdit.targetWeights,
            existingWorkouts = workouts,
            allExercises = exercises,
            isNew = false,
            onAddPresetExercises = { missing -> onImportExercises(missing, null) },
            onDismiss = { editingWorkout = null },
            onSave = { finalName, note, selectedIds, kind, durationSeconds, targets, weights ->
                onUpdateWorkout(
                    wToEdit.copy(
                        name = finalName,
                        note = note,
                        exerciseIds = selectedIds,
                        kind = kind,
                        durationSeconds = durationSeconds,
                        amrapTargets = targets,
                        targetWeights = weights
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
            val activeWorkoutObj = detailWorkoutId?.let { wid -> workouts.find { it.id == wid } }
            ExerciseDetailDialog(
                ndot = ndot,
                today = today,
                exercise = freshEx.copy(
                    targetWeightKg = logs.lastOrNull { it.exerciseId == freshEx.id }?.weightKg ?: freshEx.targetWeightKg,
                    targetReps = logs.lastOrNull { it.exerciseId == freshEx.id && it.reps > 0 }?.reps ?: freshEx.targetReps
                ),
                logs = logs.filter { it.exerciseId == freshEx.id },
                onDismiss = { activeExercise = null },
                canLog = detailWorkoutId != null && detailWorkoutId == activeWorkoutSessionId &&
                    workouts.any { it.id == detailWorkoutId && it.kind != WorkoutKind.AMRAP },
                onLogSet = { log ->
                    val id = detailWorkoutId
                    if (id != null && id == activeWorkoutSessionId && workouts.any { it.id == id && it.kind != WorkoutKind.AMRAP }) {
                        onLogSet(log.copy(workoutId = id))
                    }
                },
                onRemoveFromWorkout = activeWorkoutObj?.let { w ->
                    {
                        onUpdateWorkout(w.copy(exerciseIds = w.exerciseIds.filterNot { it == freshEx.id }))
                        activeExercise = null
                    }
                },
                onEditExercise = { editingExercise = freshEx; activeExercise = null },
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

    editingExercise?.let { original ->
        key(original.id) {
            NewExerciseDialog(
                ndot = ndot,
                targetWorkoutName = null,
                initialExercise = original,
                onDismiss = { editingExercise = null; activeExercise = original },
                onCreate = { updated ->
                    onUpdateExercise(updated)
                    editingExercise = null
                    activeExercise = updated
                }
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

    if (showExerciseFilters) {
        AlertDialog(
            onDismissRequest = { showExerciseFilters = false },
            containerColor = Panel, shape = RoundedCornerShape(26.dp),
            title = { DisplayTitle(npString(R.string.np_edc5934c34a6), ndot, fontSize = 25.sp) },
            text = {
                Column {
                    Text(npString(R.string.np_4a79bcf07989), color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Default)
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 235.dp).verticalScroll(rememberScrollState())) {
                        (listOf<ExerciseKind?>(null) + ExerciseKind.values().toList()).forEach { kind ->
                            Row(Modifier.fillMaxWidth().clickable { kindFilter = kind }.padding(vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(if (kindFilter == kind) "●" else "○", color = if (kindFilter == kind) Red else Muted)
                                Spacer(Modifier.width(12.dp))
                                Text(kind?.let { exerciseKindLabel(it) } ?: npString(R.string.np_445434ebfa2e), color = White)
                            }
                        }
                    }
                    HorizontalDivider(color = npNeutral(0xFF303030))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(npString(R.string.np_8e6c596cbe42), color = White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = exerciseDescending, onCheckedChange = { exerciseDescending = it })
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(npString(R.string.np_3c1c7a067895), color = White, modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Switch(checked = groupExercises, onCheckedChange = { groupExercises = it })
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExerciseFilters = false }) { Text(npString(R.string.np_594308426372)) } },
            dismissButton = { TextButton(onClick = {
                kindFilter = null; exerciseDescending = false; groupExercises = true; showExerciseFilters = false
            }) { Text(npString(R.string.np_a4565af537e2)) } }
        )
    }
    if (showAiExerciseMenu) {
        AlertDialog(
            onDismissRequest = { showAiExerciseMenu = false },
            containerColor = Panel, shape = RoundedCornerShape(26.dp),
            title = { DisplayTitle(npString(R.string.np_8d255d3f1bd7), ndot, fontSize = 24.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Panel2, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showAiExerciseMenu = false
                            shareAiPromptToExternalApp()
                        }) { Text(npString(R.string.np_7a9f1f7e8ce8), color = White, modifier = Modifier.padding(16.dp)) }
                    Surface(color = Panel2, shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            showAiExerciseMenu = false
                            targetWorkoutForNewEx = null
                            showAiImportDialog = true
                        }) { Text(npString(R.string.np_0e20248ad581), color = White, modifier = Modifier.padding(16.dp)) }
                }
            },
            confirmButton = { TextButton(onClick = { showAiExerciseMenu = false }) { Text(npString(R.string.np_44424b18700e)) } }
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

private fun amrapScoreText(r: JSONObject): String =
    npString(R.string.np_10f171af156d , r.optInt("rounds"), r.optInt("extraExercises"))

@Composable
private fun AmrapResultCard(result: JSONObject, ndot: FontFamily, onClick: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(onClick = onClick),
        color = Panel2, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(15.dp)) {
            Text(result.optString("name"), color = White, fontSize = 16.sp)
            Text(amrapScoreText(result), color = Red, fontSize = 13.sp)
            Text(java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", npLocale()).format(java.util.Date(result.optLong("endedAt"))) +
                if (result.optBoolean("completed")) npString(R.string.np_2453a761b048) else npString(R.string.np_3448fa26997a), color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun AmrapSummaryDialog(result: JSONObject, ndot: FontFamily, onDelete: (() -> Unit)? = null, onClose: () -> Unit) {
    val items = result.optJSONArray("items") ?: JSONArray()
    val totalReps = (0 until items.length()).sumOf { items.optJSONObject(it)?.optInt("totalReps") ?: 0 }
    val completed = result.optBoolean("completed")
    val elapsedSeconds = result.optLong("elapsedMs") / 1000
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp), color = Panel,
            shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(18.dp).heightIn(max = 610.dp).imePadding().verticalScroll(rememberScrollState())) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(Red, CircleShape))
                    Spacer(Modifier.width(9.dp))
                    Text(npString(R.string.np_34a7f3d134eb), color = Muted, fontFamily = FontFamily.Default,
                        fontSize = 10.sp, letterSpacing = 1.4.sp, modifier = Modifier.weight(1f))
                    if (onDelete != null) IconButton(onClick = onDelete,
                        modifier = Modifier.semantics { contentDescription = npString(R.string.np_c80854a748cc) }) {
                        WorkoutActionIcon(WorkoutAction.DELETE, Red)
                    }
                }
                DisplayTitle(result.optString("name").uppercase(npLocale()), ndot, fontSize = 26.sp)
                Text(if (completed) npString(R.string.np_28439032958e) else npString(R.string.np_60fe1ae1f6a6),
                    color = if (completed) Red else Muted, fontSize = 10.sp,
                    fontFamily = FontFamily.Default)
                Spacer(Modifier.height(18.dp))
                Surface(color = Panel2, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        DisplayTitle("${result.optInt("rounds")}", ndot, fontSize = 54.sp)
                        Text(npString(R.string.np_69830ab7e0a9), color = Muted, fontSize = 10.sp,
                            fontFamily = FontFamily.Default)
                        if (result.optInt("extraExercises") > 0) {
                            Text(npString(R.string.np_aa7a64a2aec3 , result.optInt("extraExercises")), color = White,
                                fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Surface(Modifier.weight(1f), color = Panel2, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            DisplayTitle("${totalReps}", ndot, fontSize = 28.sp)
                            Text(npString(R.string.np_acece0d2784d), color = Muted, fontSize = 9.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = Panel2, shape = RoundedCornerShape(18.dp)) {
                        Column(Modifier.padding(13.dp)) {
                            DisplayTitle("%02d:%02d".format(npLocale(), elapsedSeconds / 60, elapsedSeconds % 60), ndot, fontSize = 28.sp)
                            Text(npString(R.string.np_2b0153ecca3c), color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                SectionLabel(npString(R.string.np_a001ef56f69c), topPadding = 18.dp, bottomPadding = 9.dp)
                for (i in 0 until items.length()) {
                    val item = items.optJSONObject(i) ?: continue
                    Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(28.dp).background(Panel2, CircleShape), contentAlignment = Alignment.Center) {
                            Text("${i + 1}", color = Muted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.optString("name"), color = White, fontSize = 14.sp)
                            Text(npString(R.string.np_bd5f97b68e6d , item.optInt("target")), color = Muted, fontSize = 10.sp)
                        }
                        Text("${item.optInt("totalReps")}", color = White, fontSize = 17.sp,
                            fontFamily = FontFamily.Default)
                    }
                    if (i < items.length() - 1) HorizontalDivider(color = npNeutral(0xFF303030))
                }
                Spacer(Modifier.height(14.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg),
                    shape = RoundedCornerShape(16.dp)) { Text(npString(R.string.np_5b769ca72f3c)) }
            }
        }
    }
}

@Composable
private fun StableWorkoutTimer(milliseconds: Long, ndot: FontFamily) {
    val value = milliseconds.coerceAtLeast(0L)
    val timer = "%02d:%02d.%03d".format(Locale.ROOT, value / 60000, value / 1000 % 60, value % 1000)
    val family = if (LocalDotHeadings.current) ndot else LocalClassicHeadings.current
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val cell = minOf(29.dp, maxWidth / timer.length)
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            timer.forEach { glyph ->
                Box(Modifier.width(cell).height(52.dp), contentAlignment = Alignment.Center) {
                    Text(glyph.toString(), color = White, fontFamily = family, fontSize = 34.sp,
                        textAlign = TextAlign.Center, maxLines = 1, softWrap = false,
                        style = TextStyle(fontFeatureSettings = "tnum", platformStyle = PlatformTextStyle(includeFontPadding = false)))
                }
            }
        }
    }
}

private enum class WorkoutAction { EDIT, DELETE }
@Composable
private fun WorkoutActionIcon(action: WorkoutAction, tint: Color = White) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width; val h = size.height; val stroke = 1.7.dp.toPx()
        fun line(x: Float, y: Float, xx: Float, yy: Float) =
            drawLine(tint, Offset(w*x,h*y), Offset(w*xx,h*yy), stroke, StrokeCap.Round)
        if (action == WorkoutAction.DELETE) {
            line(.20f,.28f,.80f,.28f); line(.38f,.14f,.62f,.14f)
            line(.38f,.14f,.38f,.26f); line(.62f,.14f,.62f,.26f)
            val bin = Path().apply { moveTo(w*.27f,h*.34f); lineTo(w*.32f,h*.86f)
                lineTo(w*.68f,h*.86f); lineTo(w*.73f,h*.34f) }
            drawPath(bin,tint,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
            line(.44f,.43f,.44f,.72f); line(.56f,.43f,.56f,.72f)
        } else {
            val pencil = Path().apply { moveTo(w*.20f,h*.79f); lineTo(w*.26f,h*.59f)
                lineTo(w*.70f,h*.15f); lineTo(w*.85f,h*.30f); lineTo(w*.41f,h*.74f); close() }
            drawPath(pencil,tint,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round))
            line(.63f,.22f,.78f,.37f)
        }
    }
}

@Composable
private fun AmrapLiveDialog(
    ndot: FontFamily,
    workout: Workout,
    exercises: List<Exercise>,
    prefs: android.content.SharedPreferences,
    onDismiss: () -> Unit,
    onFinish: () -> Unit,
    onHapticTick: () -> Unit
) {
    val context = LocalContext.current
    val resultPrefs = remember { NPlannerStore.get(context) }
    val prefix = "amrap:${workout.id}:"
    val durationMs = workout.durationSeconds.coerceAtLeast(1) * 1000L
    var index by remember(workout.id) { mutableIntStateOf(prefs.getInt(prefix + "index", 0)) }
    var rounds by remember(workout.id) { mutableIntStateOf(prefs.getInt(prefix + "rounds", 0)) }
    var finished by remember(workout.id) { mutableStateOf(prefs.getBoolean(prefix + "finished", false)) }
    var running by remember(workout.id) { mutableStateOf(prefs.getBoolean(prefix + "running", false)) }
    var remaining by remember(workout.id) { mutableLongStateOf(prefs.getLong(prefix + "remaining", durationMs).coerceIn(0L, durationMs)) }
    var endAt by remember(workout.id) { mutableLongStateOf(prefs.getLong(prefix + "endAt", 0L)) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val runId = remember(workout.id) {
        if (prefs.getBoolean(prefix + "finished", false)) prefs.getString(prefix + "runId", null) ?: UUID.randomUUID().toString()
        else if (prefs.getBoolean(prefix + "running", false) || prefs.getLong(prefix + "remaining", durationMs) < durationMs)
            prefs.getString(prefix + "runId", null) ?: UUID.randomUUID().toString()
        else UUID.randomUUID().toString()
    }
    var result by remember { mutableStateOf<JSONObject?>(resultPrefs.getString("amrap_session:${runId}", null)?.let { runCatching { JSONObject(it) }.getOrNull() }) }
    val left = if (running) (endAt - now).coerceAtLeast(0L) else remaining
    fun save() {
        prefs.edit().putString(prefix + "runId", runId).putInt(prefix + "index", index).putInt(prefix + "rounds", rounds)
            .putBoolean(prefix + "finished", finished).putBoolean(prefix + "running", running)
            .putLong(prefix + "remaining", remaining).putLong(prefix + "endAt", endAt).apply()
    }
    fun finish(completed: Boolean) {
        if (result != null) return
        val rest = if (running) (endAt - System.currentTimeMillis()).coerceIn(0L, durationMs) else remaining
        remaining = if (completed) 0L else rest
        running = false
        finished = true
        val items = JSONArray()
        exercises.forEachIndexed { i, ex ->
            val target = workout.amrapTargets[ex.id] ?: ex.targetReps.coerceAtLeast(1)
            val sets = rounds + if (i < index) 1 else 0
            items.put(JSONObject().put("id", ex.id).put("name", ex.name).put("target", target)
                .put("sets", sets).put("totalReps", sets * target).put("weightKg", ex.targetWeightKg.toDouble()))
        }
        val signature = workout.durationSeconds.toString() + ":" + exercises.joinToString("|") {
            it.id + ":" + it.kind.name + ":" + it.targetWeightKg + ":" + (workout.amrapTargets[it.id] ?: it.targetReps.coerceAtLeast(1))
        }
        val obj = JSONObject().put("name", workout.name).put("workoutId", workout.id).put("runId", runId)
            .put("endedAt", if (completed) endAt else System.currentTimeMillis()).put("durationSeconds", workout.durationSeconds)
            .put("elapsedMs", durationMs - remaining).put("rounds", rounds).put("extraExercises", index)
            .put("score", rounds * exercises.size + index).put("completed", completed)
            .put("signature", signature).put("items", items)
        resultPrefs.edit().putString("amrap_session:${runId}", obj.toString()).apply()
        result = obj
        save()
    }
    LaunchedEffect(workout.id, running, endAt) {
        while (running) {
            now = System.currentTimeMillis()
            if (now >= endAt) { finish(true); break }
            delay(25)
        }
    }
    fun togglePause() {
        if (finished) return
        now = System.currentTimeMillis()
        if (running) {
            if (now >= endAt) { finish(true); return }
            remaining = (endAt - now).coerceAtLeast(0L)
            running = false
        } else {
            endAt = now + remaining
            running = true
        }
        save()
    }
    if (result != null) {
        AmrapSummaryDialog(result!!, ndot) { onFinish() }
        return
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp), color = Panel, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(npString(R.string.np_90d2782a0b9e), color = Red, fontSize = 11.sp)
                DisplayTitle(workout.name, ndot, fontSize = 25.sp)
                Spacer(Modifier.height(14.dp))
                StableWorkoutTimer(left, ndot)
                Text(if (running) npString(R.string.np_f7a984b986e2) else npString(R.string.np_99946c19db88), color = Muted, fontSize = 11.sp)
                Text(npString(R.string.np_aac71de4cd34 , rounds), color = White, fontSize = 16.sp)
                Spacer(Modifier.height(16.dp))
                val ex = exercises.getOrNull(index)
                if (ex != null) {
                    DisplayTitle(ex.name.uppercase(npLocale()), ndot, fontSize = 25.sp)
                    Text(npString(R.string.np_a31fbb1a8dfd , workout.amrapTargets[ex.id] ?: ex.targetReps.coerceAtLeast(1)), color = White)
                }
                Button(onClick = {
                    if (!running) togglePause()
                    else if (System.currentTimeMillis() >= endAt) finish(true)
                    else {
                        if (index >= exercises.lastIndex) { rounds++; index = 0 } else index++
                        save(); onHapticTick()
                    }
                }, enabled = exercises.isNotEmpty() && !finished,
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg)) {
                    Text(if (running) npString(R.string.np_1e12875abff8) else if (remaining == durationMs) npString(R.string.np_e7e229c1b0d1) else npString(R.string.np_d2ae184f0aa5))
                }
                Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
                    Surface(
                        modifier = Modifier.size(56.dp).clip(CircleShape).clickable(
                            enabled = !finished && (running || remaining < durationMs),
                            onClickLabel = if (running) npString(R.string.np_e33efa2b3499) else npString(R.string.np_dc1478111cc6)
                        ) { togglePause() },
                        color = Panel2, shape = CircleShape
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Canvas(Modifier.size(24.dp)) {
                                if (running) {
                                    drawRoundRect(White, Offset(size.width*.19f,size.height*.15f),
                                        Size(size.width*.22f,size.height*.70f),
                                        androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                    drawRoundRect(White, Offset(size.width*.59f,size.height*.15f),
                                        Size(size.width*.22f,size.height*.70f),
                                        androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()))
                                } else {
                                    val triangle = Path().apply {
                                        moveTo(size.width*.28f,size.height*.12f)
                                        lineTo(size.width*.85f,size.height*.5f)
                                        lineTo(size.width*.28f,size.height*.88f)
                                        close()
                                    }
                                    drawPath(triangle, White)
                                }
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss) { Text(npString(R.string.np_209bcbdc731e), color = Muted) }
                    TextButton(onClick = { finish(running && System.currentTimeMillis() >= endAt) }) { Text(npString(R.string.np_ce9e650ed816), color = Red) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutInfoIcon() {
    Canvas(Modifier.size(19.dp)) {
        val stroke = 1.6.dp.toPx()
        drawCircle(Muted, size.width*.40f, style = Stroke(stroke))
        drawCircle(Muted, size.width*.045f, Offset(size.width*.50f,size.height*.31f))
        drawLine(Muted, Offset(size.width*.50f,size.height*.47f), Offset(size.width*.50f,size.height*.70f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun WorkoutTemplateIcon() {
    Canvas(Modifier.size(20.dp)) {
        val stroke = 1.7.dp.toPx()
        val w = size.width; val h = size.height
        val bookmark = Path().apply {
            moveTo(w*.25f,h*.14f); lineTo(w*.75f,h*.14f)
            lineTo(w*.75f,h*.88f); lineTo(w*.50f,h*.70f)
            lineTo(w*.25f,h*.88f); close()
        }
        drawPath(bookmark, White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

private fun workoutSessionSnapshot(
    workout: Workout, exercises: List<Exercise>, logs: List<ExerciseLog>,
    startedAt: Long, endedAt: Long, runId: String
): JSONObject {
    val items = JSONArray()
    val ids = (workout.exerciseIds + logs.map { it.exerciseId }).distinct()
    ids.forEach { id ->
        val ex = exercises.find { it.id == id }
        val sets = logs.filter { it.exerciseId == id }
        if (sets.isNotEmpty()) {
            val entries = JSONArray()
            sets.forEach { log -> entries.put(JSONObject().put("weightKg", log.weightKg.toDouble())
                .put("reps", log.reps).put("seconds", log.seconds).put("distanceKm", log.distanceKm.toDouble())) }
            items.put(JSONObject().put("name", ex?.name ?: "Entfernte Übung")
                .put("kind", ex?.kind?.name ?: "WEIGHT").put("sets", entries))
        }
    }
    return JSONObject().put("runId", runId).put("workoutId", workout.id).put("name", workout.name)
        .put("logIds", JSONArray(logs.map { it.id }))
        .put("startedAt", startedAt).put("endedAt", endedAt).put("setCount", logs.size)
        .put("items", items).put("elapsedMs", if (startedAt > 0L && endedAt >= startedAt) endedAt - startedAt else 0L)
}

private data class WorkoutSetGroup(val label: String, val count: Int)

private fun workoutSetLabel(set: JSONObject, kind: ExerciseKind): String = when (kind) {
    ExerciseKind.WEIGHT -> npString(R.string.np_e88f262429f9 , java.math.BigDecimal.valueOf(set.optDouble("weightKg")).stripTrailingZeros().toPlainString().replace('.', ','), set.optInt("reps"))
    ExerciseKind.BODYWEIGHT -> npString(R.string.np_6a8982ba88d0 , set.optInt("reps"))
    ExerciseKind.CARDIO -> {
        val duration = set.optInt("seconds"); val km = set.optDouble("distanceKm")
        val base = "%02d:%02d".format(npLocale(), duration / 60, duration % 60) + if (km > 0.0) " · %.2f km".format(npLocale(), km) else ""
        if (km > 0.0 && duration > 0) { val pace = (duration / km).roundToInt(); val speed = km / (duration / 3600.0); base + " · %d:%02d min/km · Ø %.2f km/h".format(npLocale(), pace / 60, pace % 60, speed) } else base
    }
    ExerciseKind.TIMED, ExerciseKind.AMRAP -> "%02d:%02d".format(npLocale(), set.optInt("seconds") / 60, set.optInt("seconds") % 60)
}

private fun groupedWorkoutSets(sets: JSONArray, kind: ExerciseKind): List<WorkoutSetGroup> {
    val counts = linkedMapOf<String, Int>()
    for (i in 0 until sets.length()) {
        val set = sets.optJSONObject(i) ?: continue
        val label = workoutSetLabel(set, kind)
        counts[label] = (counts[label] ?: 0) + 1
    }
    return counts.map { (label, count) -> WorkoutSetGroup(label, count) }
}

@Composable
private fun WorkoutSessionSummary(
    result: JSONObject, ndot: FontFamily,
    onDelete: (() -> Unit)? = null,
    onClose: () -> Unit
) {
    val items = result.optJSONArray("items") ?: JSONArray()
    val elapsed = result.optLong("elapsedMs") / 1000
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .85f
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).height(height), color = Panel, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(18.dp).imePadding()) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(npString(R.string.np_47dfd446bfe8), color = Red, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    if (onDelete != null) IconButton(onClick = onDelete,
                        modifier = Modifier.semantics { contentDescription = npString(R.string.np_e0ad4c95450e) }) {
                        WorkoutActionIcon(WorkoutAction.DELETE, Red)
                    }
                }
                DisplayTitle(result.optString("name"), ndot, fontSize = 26.sp)
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(Modifier.weight(1f), color = Panel2, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            DisplayTitle("${result.optInt("setCount")}", ndot, fontSize = 28.sp)
                            Text(npString(R.string.np_dadafe9ea717), color = Muted, fontSize = 9.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = Panel2, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            DisplayTitle("${items.length()}", ndot, fontSize = 28.sp)
                            Text(npString(R.string.np_a001ef56f69c), color = Muted, fontSize = 9.sp)
                        }
                    }
                    Surface(Modifier.weight(1f), color = Panel2, shape = RoundedCornerShape(16.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text(if (result.optBoolean("legacy")) "–" else "%02d:%02d".format(npLocale(), elapsed / 60, elapsed % 60),
                                color = White, fontSize = 21.sp)
                            Text(npString(R.string.np_d70301ab9f7e), color = Muted, fontSize = 9.sp)
                        }
                    }
                }
                if (result.optBoolean("legacy")) Text(npString(R.string.np_5b96f6c45c8b , result.optString("legacyDate")),
                    color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.height(12.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (items.length() == 0) Text(npString(R.string.np_afbb7532e92a), color = Muted, fontSize = 13.sp)
                    for (i in 0 until items.length()) {
                        val item = items.getJSONObject(i)
                        val sets = item.optJSONArray("sets") ?: JSONArray()
                        val kind = runCatching { ExerciseKind.valueOf(item.optString("kind")) }.getOrDefault(ExerciseKind.WEIGHT)
                        val groups = groupedWorkoutSets(sets, kind)
                        val reps = (0 until sets.length()).sumOf { sets.optJSONObject(it)?.optInt("reps") ?: 0 }
                        key(result.optString("runId"), i) {
                            var showIndividual by remember { mutableStateOf(false) }
                            Surface(color = Panel2, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(14.dp)) {
                                    Text(item.optString("name"), color = White, fontSize = 16.sp)
                                    Text("${sets.length()} ${if (sets.length() == 1) npString(R.string.np_2998fd7d2d10) else npString(R.string.np_60d312b24709)}" +
                                        if (kind in listOf(ExerciseKind.WEIGHT, ExerciseKind.BODYWEIGHT)) npString(R.string.np_2ee87a811816 , reps) else "",
                                        color = Muted, fontSize = 11.sp)
                                    Spacer(Modifier.height(8.dp))
                                    groups.forEach { group ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("${group.count} ${if (group.count == 1) npString(R.string.np_2998fd7d2d10) else npString(R.string.np_60d312b24709)}", color = Red,
                                                fontSize = 12.sp, modifier = Modifier.width(64.dp))
                                            Text(group.label, color = White, fontSize = 14.sp, modifier = Modifier.weight(1f))
                                        }
                                    }
                                    TextButton(onClick = { showIndividual = !showIndividual }, contentPadding = PaddingValues(0.dp)) {
                                        Text(if (showIndividual) npString(R.string.np_f8777d668985) else npString(R.string.np_ffb4fb4a1c5c), color = Muted, fontSize = 11.sp)
                                    }
                                    if (showIndividual) {
                                        HorizontalDivider(color = npNeutral(0xFF353535))
                                        for (j in 0 until sets.length()) {
                                            val set = sets.optJSONObject(j) ?: continue
                                            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                                Text("${j + 1}.", color = Muted, fontSize = 11.sp, modifier = Modifier.width(30.dp))
                                                Text(workoutSetLabel(set, kind), color = White, fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg)) {
                    Text(npString(R.string.np_5b769ca72f3c))
                }
            }
        }
    }
}

@Composable
private fun QuickWorkoutSet(exercise: Exercise, workout: Workout, today: String, lastLog: ExerciseLog?, onSave: (ExerciseLog) -> Unit) {
    var weight by remember(exercise.id, workout.id) { mutableStateOf(
        (lastLog?.weightKg ?: workout.targetWeights[exercise.id] ?: exercise.targetWeightKg).toString()) }
    var reps by remember(exercise.id, workout.id) { mutableStateOf((lastLog?.reps?.takeIf { it > 0 } ?: workout.amrapTargets[exercise.id] ?: exercise.targetReps).toString()) }
    val initialSeconds = lastLog?.seconds?.takeIf { it > 0 } ?: exercise.targetSeconds.coerceAtLeast(0)
    var minutes by remember(exercise.id, workout.id) { mutableStateOf((initialSeconds / 60).toString()) }
    var seconds by remember(exercise.id, workout.id) { mutableStateOf((initialSeconds % 60).toString().padStart(2, '0')) }
    var distance by remember(exercise.id, workout.id) { mutableStateOf(lastLog?.distanceKm?.takeIf { it > 0f }?.toString() ?: "") }
    val secondsPart = seconds.toIntOrNull() ?: -1
    val duration = (minutes.toIntOrNull() ?: 0) * 60 + secondsPart.coerceAtLeast(0)
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        if (exercise.kind in listOf(ExerciseKind.WEIGHT, ExerciseKind.BODYWEIGHT)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (exercise.kind == ExerciseKind.WEIGHT) OutlinedTextField(weight, { weight = it.take(6) }, label = { Text(npString(R.string.np_1389845b02c0)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(reps, { reps = it.filter(Char::isDigit).take(3) }, label = { Text(npString(R.string.np_8752f1c63e6e)) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(3) }, label = { Text("Minuten") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(seconds, { seconds = it.filter(Char::isDigit).take(2) }, label = { Text("Sekunden") }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
            if (exercise.kind == ExerciseKind.CARDIO) {
                OutlinedTextField(distance, { distance = it.take(8) }, label = { Text("Distanz (km, optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(top = 8.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                val kmPreview = distance.replace(',', '.').toFloatOrNull() ?: 0f
                if (kmPreview > 0f && duration > 0) {
                    val pace = (duration / kmPreview).roundToInt(); val speed = kmPreview / (duration / 3600f)
                    Text("%d:%02d min/km · Ø %.2f km/h".format(npLocale(), pace / 60, pace % 60, speed), color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }
        val kg = weight.replace(',', '.').toFloatOrNull(); val repetitions = reps.toIntOrNull(); val km = distance.replace(',', '.').toFloatOrNull() ?: 0f
        val valid = when (exercise.kind) {
            ExerciseKind.WEIGHT -> repetitions in 1..500 && kg != null && kg.isFinite() && kg in 0f..500f
            ExerciseKind.BODYWEIGHT -> repetitions in 1..500
            ExerciseKind.TIMED -> duration in 1..86400 && secondsPart in 0..59
            ExerciseKind.CARDIO -> duration in 1..86400 && secondsPart in 0..59 && km in 0f..1000f
            ExerciseKind.AMRAP -> false
        }
        Button(onClick = { onSave(ExerciseLog(UUID.randomUUID().toString(), exercise.id, today, weightKg = if (exercise.kind == ExerciseKind.WEIGHT) kg ?: 0f else 0f, reps = if (exercise.kind in listOf(ExerciseKind.WEIGHT, ExerciseKind.BODYWEIGHT)) repetitions ?: 0 else 0, seconds = if (exercise.kind in listOf(ExerciseKind.TIMED, ExerciseKind.CARDIO)) duration else 0, distanceKm = if (exercise.kind == ExerciseKind.CARDIO) km else 0f, workoutId = workout.id)) }, enabled = valid, shape = RoundedCornerShape(14.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg), modifier = Modifier.fillMaxWidth().height(46.dp).padding(top = 8.dp)) { Text(if (exercise.kind == ExerciseKind.CARDIO) "+ EINTRAG" else "+ SATZ", fontSize = 11.sp) }
    }
}

@Composable
private fun WorkoutExerciseStack(
    workout: Workout,
    exercises: List<Exercise>,
    logs: List<ExerciseLog>,
    today: String,
    isActive: Boolean,
    sessionId: String,
    scrollState: ScrollState,
    viewportTop: Float,
    viewportBottom: Float,
    onReorder: (List<String>) -> Unit,
    onSelect: (Exercise) -> Unit,
    onQuickLog: (ExerciseLog) -> Unit
) {
    var order by remember(workout.id) { mutableStateOf(exercises.map { it.id }) }
    var draggedId by remember(workout.id) { mutableStateOf<String?>(null) }
    var pointerY by remember { mutableFloatStateOf(0f) }
    var grabOffset by remember { mutableFloatStateOf(0f) }
    var listTop by remember { mutableFloatStateOf(0f) }
    var expandedId by remember(workout.id) { mutableStateOf<String?>(null) }
    val heights = remember(workout.id) { mutableStateMapOf<String, Int>() }
    val latestExercises by rememberUpdatedState(exercises)
    val latestReorder by rememberUpdatedState(onReorder)
    val latestSelect by rememberUpdatedState(onSelect)
    val density = LocalDensity.current
    val gap = with(density) { 10.dp.toPx() }
    val defaultHeight = with(density) { 84.dp.toPx() }
    val edge = with(density) { 48.dp.toPx() }
    val latestViewportTop by rememberUpdatedState(viewportTop)
    val latestViewportBottom by rememberUpdatedState(viewportBottom)
    val haptic = LocalHapticFeedback.current

    LaunchedEffect(exercises.map { it.id }, draggedId) {
        if (draggedId == null) order = exercises.map { it.id }
    }
    fun topOf(id: String): Float {
        var y = 0f
        for (item in order) {
            if (item == id) return y
            y += (heights[item]?.toFloat() ?: defaultHeight) + gap
        }
        return y
    }
    fun moveAtPointer() {
        val id = draggedId ?: return
        val from = order.indexOf(id)
        if (from < 0) return
        val center = pointerY - listTop - grabOffset + (heights[id]?.toFloat() ?: defaultHeight) / 2f
        var to = from
        if (from < order.lastIndex) {
            val next = order[from + 1]
            if (center > topOf(next) + (heights[next]?.toFloat() ?: defaultHeight) / 2f) to = from + 1
        }
        if (to == from && from > 0) {
            val previous = order[from - 1]
            if (center < topOf(previous) + (heights[previous]?.toFloat() ?: defaultHeight) / 2f) to = from - 1
        }
        if (to != from) order = order.toMutableList().apply { add(to, removeAt(from)) }
    }
    LaunchedEffect(draggedId) {
        if (draggedId != null) {
            while (true) {
                val top = latestViewportTop
                val bottom = latestViewportBottom
                val speed = when {
                    bottom <= top -> 0f
                    pointerY < top + edge -> -((top + edge - pointerY) / edge).coerceIn(0f, 1f) * 16f
                    pointerY > bottom - edge -> ((pointerY - bottom + edge) / edge).coerceIn(0f, 1f) * 16f
                    else -> 0f
                }
                if (speed != 0f) {
                    val moved = scrollState.scrollBy(speed)
                    // The next layout updates listTop; pointer stays in screen coordinates.
                    if (moved != 0f) moveAtPointer()
                }
                delay(16)
            }
        }
    }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { listTop = it.positionInRoot().y },
        verticalArrangement = Arrangement.spacedBy(10.dp)) {
        order.forEachIndexed { index, id ->
            val ex = exercises.firstOrNull { it.id == id } ?: return@forEachIndexed
            key(id) {
                val dragging = draggedId == id
                val translation = if (dragging) pointerY - listTop - grabOffset - topOf(id) else 0f
                val ownLogs = logs.filter { it.workoutId == workout.id && it.exerciseId == id && it.sessionId == sessionId }
                val lastLog = logs.lastOrNull { it.exerciseId == id }
                val expanded = expandedId == id
                Surface(
                    modifier = Modifier.fillMaxWidth().onSizeChanged { heights[id] = it.height }
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = translation; scaleX = if (dragging) 1.015f else 1f; scaleY = if (dragging) 1.015f else 1f },
                    color = if (dragging || expanded) Panel2 else Panel,
                    shape = RoundedCornerShape(20.dp),
                    shadowElevation = if (dragging) 8.dp else 0.dp
                ) {
                    Column {
                        Row(
                            Modifier.fillMaxWidth().pointerInput(id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { local ->
                                        if (draggedId == null) {
                                            grabOffset = local.y
                                            pointerY = listTop + topOf(id) + local.y
                                            draggedId = id
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                        }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        if (draggedId == id) { pointerY += amount.y; moveAtPointer() }
                                    },
                                    onDragEnd = {
                                        if (draggedId == id) {
                                            val valid = latestExercises.map { it.id }.toSet()
                                            val finalOrder = order.filter { it in valid }
                                            if (finalOrder.size == valid.size) latestReorder(finalOrder)
                                            draggedId = null
                                        }
                                    },
                                    onDragCancel = { order = latestExercises.map { it.id }; draggedId = null }
                                )
                            }.clickable {
                                if (draggedId == null) expandedId = if (expanded) null else id
                            }.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) { ExerciseKindIcon(ex.kind) }
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(ex.name, color = White, fontSize = 15.sp, lineHeight = 19.sp)
                                Text(if (isActive) npString(R.string.np_1221f3fe1563 , ownLogs.size) else npString(R.string.np_eb94cc90f3be , index + 1, exerciseKindLabel(ex.kind)),
                                    color = if (ownLogs.isNotEmpty()) Red else Muted, fontSize = 10.sp)
                            }
                            IconButton(onClick = { latestSelect(ex) }, modifier = Modifier.semantics { contentDescription = npString(R.string.np_e643fc8f64d5) }) { WorkoutInfoIcon() }
                            val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f,
                                animationSpec = tween(160), label = "exerciseChevron")
                            Canvas(Modifier.size(20.dp).graphicsLayer { rotationZ = arrowRotation }) {
                                val path = Path().apply {
                                    moveTo(size.width*.22f,size.height*.38f)
                                    lineTo(size.width*.50f,size.height*.66f)
                                    lineTo(size.width*.78f,size.height*.38f)
                                }
                                drawPath(path, White, style = Stroke(1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                            }
                        }
                        if (expanded) {
                            Column(Modifier.padding(start = 14.dp, end = 14.dp, bottom = 12.dp)) {
                                if (isActive && ex.kind != ExerciseKind.AMRAP) {
                                    QuickWorkoutSet(ex, workout, today, lastLog, onQuickLog)
                                    ownLogs.forEachIndexed { setIndex, log ->
                                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                                            Text(npString(R.string.np_67c4677dcedc , setIndex + 1), color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                                            Text(logSummary(ex, log), color = White, fontSize = 12.sp)
                                        }
                                    }
                                } else Text(exerciseSummary(ex), color = Muted, fontSize = 12.sp)

                            }
                        }
                    }
                }
            }
        }
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
    sessionId: String,
    onStartWorkout: () -> Unit,
    onFinishWorkout: () -> Unit,
    onAddExistingExercise: () -> Unit,
    onAddNewExercise: () -> Unit,
    onEditWorkout: () -> Unit,
    onReorderExercises: (List<String>) -> Unit,
    onSaveTemplate: () -> Unit,
    onQuickLog: (ExerciseLog) -> Unit,
    onDeleteWorkout: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }
    val scroll = rememberScrollState()
    var viewportTop by remember { mutableFloatStateOf(0f) }
    var viewportBottom by remember { mutableFloatStateOf(0f) }
    val maxHeight = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .88f
    val currentSets = logs.filter { it.workoutId == workout.id && it.sessionId == sessionId }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(maxHeight),
            color = Bg, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(7.dp).background(if (isActive) Red else Muted, CircleShape))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isActive) npString(R.string.np_3d6849f8dec5) else npString(R.string.np_115e71776c64), color = if (isActive) Red else Muted,
                        fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 1.3.sp,
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = onSaveTemplate, modifier = Modifier.semantics { contentDescription = npString(R.string.np_7295825fe629) }) { WorkoutTemplateIcon() }
                    IconButton(onClick = onEditWorkout, modifier = Modifier.semantics { contentDescription = npString(R.string.np_6c4a58f36192) }) { WorkoutActionIcon(WorkoutAction.EDIT) }
                    IconButton(onClick = { confirmDelete = true }, modifier = Modifier.semantics { contentDescription = npString(R.string.np_081c6a21268a) }) { WorkoutActionIcon(WorkoutAction.DELETE, Red) }
                }
                DisplayTitle(workout.name, ndot, fontSize = 25.sp)
                Text(if (isActive) npString(R.string.np_912b94c66246 , exercises.size, currentSets.size) else npString(R.string.np_40119a2c9c19 , exercises.size),
                    color = Muted, fontSize = 12.sp)
                if (isActive && startedAt > 0L) {
                    Text(npString(R.string.np_bc6f103c2f20) + java.text.SimpleDateFormat("HH:mm", npLocale()).format(java.util.Date(startedAt)),
                        color = Muted, fontSize = 10.sp)
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(color = Panel2, shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).clickable(onClick = onAddExistingExercise)) {
                        Text(npString(R.string.np_afafcf8171c4), color = White, fontSize = 12.sp, modifier = Modifier.padding(13.dp))
                    }
                    Surface(color = Panel2, shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable(onClick = onAddNewExercise)) {
                        Text(npString(R.string.np_694aa76eb977), color = White, fontSize = 12.sp, modifier = Modifier.padding(13.dp))
                    }
                }
                Text(npString(R.string.np_339a27327c34),
                    color = Muted, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp, bottom = 10.dp))
                Column(
                    Modifier.weight(1f).fillMaxWidth()
                        .imePadding().onGloballyPositioned { coords ->
                            viewportTop = coords.positionInRoot().y
                            viewportBottom = viewportTop + coords.size.height
                        }.verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (workout.note.isNotBlank()) Text(workout.note, color = Muted, fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp))
                    if (exercises.isEmpty()) {
                        Surface(color = Panel, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                            Text(npString(R.string.np_71482b1a8014),
                                color = Muted, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
                        }
                    } else WorkoutExerciseStack(workout, exercises, logs, todayStr, isActive, sessionId, scroll,
                        viewportTop, viewportBottom, onReorderExercises, onSelectExercise, onQuickLog)
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = { if (isActive) onFinishWorkout() else onStartWorkout() },
                    enabled = isActive || exercises.isNotEmpty(), modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = if (isActive) Panel2 else White,
                        contentColor = if (isActive) White else Bg)) {
                    Text(if (isActive) npString(R.string.np_c843c5c939d0) else npString(R.string.np_bca3386909f2))
                }
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (isActive) npString(R.string.np_209bcbdc731e) else npString(R.string.np_44424b18700e), color = Muted)
                }
            }
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false }, containerColor = Panel,
        title = { DisplayTitle(npString(R.string.np_eec4b9363f9f), ndot, fontSize = 24.sp) },
        text = { Text(npString(R.string.np_f6fd5dfdcbd9), color = Muted) },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onDeleteWorkout() }) { Text(npString(R.string.np_ffa5a8a7e21d), color = Red) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(npString(R.string.np_07af7cb30fca)) } }
    )
}



private fun savePersonalWorkoutTemplate(context: Context, workout: Workout, exercises: List<Exercise>, templateKey: String? = null) {
    val chosen = workout.exerciseIds.mapNotNull { id -> exercises.find { it.id == id }?.let { ex ->
        ex.copy(targetReps = workout.amrapTargets[id] ?: ex.targetReps,
            targetWeightKg = workout.targetWeights[id] ?: ex.targetWeightKg)
    } }
    val obj = JSONObject().put("name", workout.name).put("note", workout.note)
        .put("kind", workout.kind.name).put("duration", workout.durationSeconds)
        .put("sourceIds", JSONArray(chosen.map { it.id }))
        .put("catalog", JSONObject(exportNpefJson(chosen)))
    NPlannerStore.get(context).edit()
        .putString(templateKey ?: ("workout_template:" + UUID.randomUUID().toString()), obj.toString()).apply()
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
    WorkoutPreset("Dev-AMRAP", WorkoutKind.AMRAP, 2,
        listOf("Liegestütze", "Kniebeugen", "Klimmzüge"),
        "2-Minuten-Test: 3 Liegestütze, 5 Kniebeugen, 2 Klimmzüge pro Runde."),
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

private fun seedEditableWorkoutTemplates(context: Context) {
    val prefs = NPlannerStore.get(context)
    if (prefs.getBoolean("editable_template_catalog_v43", false)) return
    workoutPresets.forEachIndexed { index, preset ->
        val existing = prefs.all.filterKeys { it.startsWith("workout_template:") }.values.any { raw ->
            runCatching { JSONObject(raw as String).optString("name").equals(preset.title, true) }.getOrDefault(false)
        }
        if (!existing) {
            val reps = when (preset.title) {
                "Cindy" -> mapOf("Klimmzüge" to 5, "Liegestütze" to 10, "Kniebeugen" to 15)
                "Dev-AMRAP" -> mapOf("Liegestütze" to 3, "Kniebeugen" to 5, "Klimmzüge" to 2)
                else -> emptyMap()
            }
            val selected = preset.exerciseNames.mapNotNull { name -> presetExercise(name)?.let { ex ->
                ex.copy(targetReps = reps[name] ?: ex.targetReps)
            } }
            val workout = Workout("template-seed-${index}", preset.title, selected.map { it.id },
                preset.description, preset.kind, preset.minutes * 60,
                selected.associate { it.id to it.targetReps }, selected.associate { it.id to it.targetWeightKg })
            savePersonalWorkoutTemplate(context, workout, selected, "workout_template:seed-v43-${index}")
        }
    }
    prefs.edit().putBoolean("editable_template_catalog_v43", true).apply()
}

@Composable
private fun WorkoutTemplateManager(
    ndot: FontFamily,
    templates: List<Pair<String, JSONObject>>,
    onDismiss: () -> Unit,
    onPersonal: (String) -> Unit,
    onEdit: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    var deleteKey by remember { mutableStateOf<String?>(null) }
    val height = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp * .85f
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(16.dp).height(height), color = Panel, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.padding(18.dp)) {
                DisplayTitle(npString(R.string.np_7e022f02cec6), ndot, fontSize = 28.sp)
                Text(npString(R.string.np_78c2350a7dac), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(14.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (templates.isEmpty()) Text(npString(R.string.np_2a64c542f06d), color = Muted, fontSize = 13.sp)
                    templates.forEach { (key, obj) ->
                        Surface(color = Panel2, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp)) {
                                Text(obj.optString("name"), color = White, fontSize = 17.sp)
                                val kind = runCatching { WorkoutKind.valueOf(obj.optString("kind")) }.getOrDefault(WorkoutKind.CLASSIC)
                                Text(npString(R.string.np_57cd0aa977a8 , obj.optJSONArray("sourceIds")?.length() ?: 0, workoutKindLabel(kind)),
                                    color = Muted, fontSize = 11.sp)
                                Spacer(Modifier.height(10.dp))
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Button(onClick = { onPersonal(key) }, modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg)) {
                                        Text(npString(R.string.np_8590499d2618), fontSize = 12.sp)
                                    }
                                    IconButton(onClick = { onEdit(key) },
                                        modifier = Modifier.semantics { contentDescription = npString(R.string.np_bd648e366f1c) }) {
                                        WorkoutActionIcon(WorkoutAction.EDIT)
                                    }
                                    IconButton(onClick = { deleteKey = key },
                                        modifier = Modifier.semantics { contentDescription = npString(R.string.np_3541f89c389b) }) {
                                        WorkoutActionIcon(WorkoutAction.DELETE, Red)
                                    }
                                }
                            }
                        }
                    }
                }
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = White, contentColor = Bg)) {
                    Text(npString(R.string.np_5b769ca72f3c))
                }
            }
        }
    }
    deleteKey?.let { key ->
        AlertDialog(onDismissRequest = { deleteKey = null }, containerColor = Panel,
            title = { DisplayTitle(npString(R.string.np_0d704e60aba9), ndot, fontSize = 24.sp) },
            text = { Text(npString(R.string.np_5d9a1728ae58), color = Muted) },
            confirmButton = { TextButton(onClick = { onDelete(key); deleteKey = null }) { Text(npString(R.string.np_3541f89c389b), color = Red) } },
            dismissButton = { TextButton(onClick = { deleteKey = null }) { Text(npString(R.string.np_07af7cb30fca)) } })
    }
}

private fun workoutKindLabel(kind: WorkoutKind): String = when (kind) {
    WorkoutKind.CLASSIC -> npString(R.string.np_2cc58a112d0a)
    WorkoutKind.AMRAP -> npString(R.string.np_3c22ccd4029f)
    WorkoutKind.FOR_TIME -> npString(R.string.np_a8ed8ebb903d)
    WorkoutKind.EMOM -> npString(R.string.np_05def8f48ffa)
}

@Composable
private fun ExerciseSelectionMark(selected: Boolean) {
    Canvas(Modifier.size(24.dp)) {
        val stroke = 1.8.dp.toPx()
        val center = Offset(size.width / 2f, size.height / 2f)
        drawCircle(if (selected) White else Muted, size.width * .37f, center, style = Stroke(stroke))
        if (selected) {
            drawLine(White, Offset(size.width*.29f,size.height*.50f), Offset(size.width*.44f,size.height*.64f), stroke, StrokeCap.Round)
            drawLine(White, Offset(size.width*.44f,size.height*.64f), Offset(size.width*.73f,size.height*.35f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun WorkoutEditorDialog(
    ndot: FontFamily,
    title: String,
    initialName: String,
    initialNote: String,
    initialPersonalKey: String? = null,
    editingTemplate: Boolean = false,
    initialSelectedIds: Set<String>,
    initialKind: WorkoutKind,
    initialDurationSeconds: Int,
    initialTargets: Map<String, Int> = emptyMap(),
    initialWeights: Map<String, Float> = emptyMap(),
    existingWorkouts: List<Workout>,
    allExercises: List<Exercise>,
    isNew: Boolean,
    onAddPresetExercises: (List<Exercise>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (String, String, List<String>, WorkoutKind, Int, Map<String, Int>, Map<String, Float>) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf(initialName) }
    var nameManuallyEdited by remember { mutableStateOf(!isNew) }
    var showNote by remember { mutableStateOf(initialNote.isNotBlank()) }
    var note by remember { mutableStateOf(initialNote) }
    var workoutKind by remember { mutableStateOf(initialKind) }
    var durationText by remember { mutableStateOf((initialDurationSeconds / 60).takeIf { it > 0 }?.toString() ?: "20") }
    var selectedIds by remember { mutableStateOf(initialSelectedIds) }
    var selectedOrder by remember { mutableStateOf(initialSelectedIds.toList()) }
    var saveAsTemplate by remember { mutableStateOf(false) }
    var selectedWeights by remember { mutableStateOf(initialWeights) }
    var pendingPresetExercises by remember { mutableStateOf<List<Exercise>>(emptyList()) }
    val personalPrefs = remember { NPlannerStore.get(context) }
    var personalRevision by remember { mutableIntStateOf(0) }
    val personalTemplates = remember(personalRevision) {
        personalPrefs.all.filterKeys { it.startsWith("workout_template:") }.mapNotNull { (key, value) ->
            runCatching { key to JSONObject(value as String) }.getOrNull()
        }.sortedBy { it.second.optString("name").lowercase(Locale.GERMAN) }
    }
    var showPresetPicker by remember { mutableStateOf(false) }
    var showExercisePicker by remember { mutableStateOf(false) }
    var presetNotice by remember { mutableStateOf("") }
    var selectedPresetName by remember { mutableStateOf(if (initialPersonalKey != null) "Vorlage" else "") }
    var selectedPresetTargets by remember { mutableStateOf(initialTargets) }

    fun choosePersonalTemplate(obj: JSONObject) {
                                        val imported = parseNpefExercises(obj.getJSONObject("catalog").toString()).getOrDefault(emptyList())
                                        val sourceIds = obj.optJSONArray("sourceIds") ?: JSONArray()
                                        val missing = mutableListOf<Exercise>()
                                        val resolved = imported.mapIndexed { i, ex ->
                                            val found = allExercises.find { it.id == sourceIds.optString(i) }
                                                ?: allExercises.find { it.name.equals(ex.name, true) && it.kind == ex.kind }
                                            if (found != null) ex.copy(id = found.id) else ex.also { missing.add(it) }
                                        }
                                        if (missing.isNotEmpty()) onAddPresetExercises(missing)
                                        pendingPresetExercises = missing
                                        selectedOrder = resolved.map { it.id }
                                        selectedIds = selectedOrder.toSet()
                                        selectedPresetTargets = resolved.associate { it.id to it.targetReps }
                                        selectedWeights = resolved.associate { it.id to it.targetWeightKg }
                                        workoutKind = runCatching { WorkoutKind.valueOf(obj.optString("kind")) }.getOrDefault(WorkoutKind.CLASSIC)
                                        durationText = (obj.optInt("duration") / 60).coerceAtLeast(1).toString()
                                        name = obj.optString("name"); note = obj.optString("note")
                                        showNote = note.isNotBlank()
                                        selectedPresetName = name
                                        nameManuallyEdited = true
                                        presetNotice = npString(R.string.np_10d6959edc6c , resolved.size)
                                        showPresetPicker = false
    }
    LaunchedEffect(initialPersonalKey) {
        if (initialPersonalKey != null) {
            personalPrefs.getString(initialPersonalKey, null)?.let { raw ->
                runCatching { JSONObject(raw) }.getOrNull()?.let { choosePersonalTemplate(it) }
                if (editingTemplate) selectedPresetName = ""
            }
        }
    }

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
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(22.dp)) {
                Text(npString(R.string.np_365b02b7539f), color = Red, fontSize = 10.sp,
                    fontFamily = FontFamily.Default, letterSpacing = 1.3.sp)
                Spacer(Modifier.height(6.dp))
                DisplayTitle(if (editingTemplate) npString(R.string.np_133ef6da2558) else if (isNew) npString(R.string.np_6d4ea26db3e6) else npString(R.string.np_b15d10e09c4f), ndot, fontSize = 26.sp)
                Text(npString(R.string.np_1becf5c9569f), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(16.dp))

                if (!editingTemplate) {
                Text(npString(R.string.np_7ddced18c41c), color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Default)
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = Panel2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showPresetPicker = true }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(npString(R.string.np_191afa7267a0), color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("›", color = Muted)
                    }
                }
                Spacer(Modifier.height(8.dp))
                }
                if (selectedPresetName.isNotBlank()) {
                    Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text(npString(R.string.np_5963b0f293cf), color = Red, fontSize = 10.sp, fontFamily = FontFamily.Default)
                            Spacer(Modifier.height(6.dp))
                            DisplayTitle(name.uppercase(npLocale()), ndot, fontSize = 24.sp)
                            Text(npString(R.string.np_57cd0aa977a8 , selectedIds.size, when (workoutKind) {
                                WorkoutKind.CLASSIC -> npString(R.string.np_2cc58a112d0a)
                                WorkoutKind.AMRAP -> npString(R.string.np_3c22ccd4029f)
                                WorkoutKind.FOR_TIME -> npString(R.string.np_a8ed8ebb903d)
                                WorkoutKind.EMOM -> npString(R.string.np_05def8f48ffa)
                            }), color = Muted, fontSize = 12.sp)
                            if (workoutKind != WorkoutKind.CLASSIC) {
                                Spacer(Modifier.height(10.dp))
                                OutlinedTextField(durationText, { durationText = it.filter(Char::isDigit).take(3) },
                                    label = { Text(npString(R.string.np_3e2b606422c8)) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.fillMaxWidth())
                            }
                            TextButton(onClick = { selectedPresetName = ""; saveAsTemplate = false }) {
                                Text(npString(R.string.np_49892ed3adbb), color = White, fontSize = 12.sp)
                            }
                        }
                    }
                } else {
                Text(npString(R.string.np_17246eaa3c0c), color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Default,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(48)
                        nameManuallyEdited = true
                    },
                    label = { Text(npString(R.string.np_05bcabc4a9ba)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (!showNote) {
                    TextButton(onClick = { showNote = true }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                        Text(npString(R.string.np_de51b771f43d), color = Muted, fontSize = 12.sp)
                    }
                } else {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it.take(80) },
                        label = { Text(npString(R.string.np_af51affdaed8)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SectionLabel(npString(R.string.np_893106ea23f8), topPadding = 14.dp, bottomPadding = 8.dp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    listOf(
                        WorkoutKind.CLASSIC to npString(R.string.np_2cc58a112d0a),
                        WorkoutKind.AMRAP to npString(R.string.np_3c22ccd4029f),
                        WorkoutKind.FOR_TIME to npString(R.string.np_a8ed8ebb903d),
                        WorkoutKind.EMOM to npString(R.string.np_05def8f48ffa)
                    ).forEach { (kind, label) ->
                        ChoiceChip(label, workoutKind == kind, Modifier.width(108.dp)) { workoutKind = kind }
                    }
                }
                if (workoutKind != WorkoutKind.CLASSIC) {
                    OutlinedTextField(durationText, { durationText = it.filter(Char::isDigit).take(3) },
                        label = { Text(if (workoutKind == WorkoutKind.FOR_TIME) npString(R.string.np_6aed3e27e961) else npString(R.string.np_3e2b606422c8)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text(npString(R.string.np_bf7fe08cc83d), color = Muted, fontSize = 10.sp, fontFamily = FontFamily.Default,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                Surface(
                    color = Panel2,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showExercisePicker = true }
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(npString(R.string.np_5fed967a4959), color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text("${selectedIds.size}  ›", color = Muted)
                    }
                }
                }
                if (editingTemplate && selectedIds.isNotEmpty()) {
                    Text(npString(R.string.np_31b7c5eb20fc), color = Muted, fontSize = 10.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp))
                    selectedOrder.filter { it in selectedIds }.forEach { id ->
                        val ex = (allExercises + pendingPresetExercises).firstOrNull { it.id == id }
                        if (ex != null) key(id) {
                            Surface(color = Panel2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                                Column(Modifier.padding(12.dp)) {
                                    Text(ex.name, color = White, fontSize = 14.sp)
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (ex.kind == ExerciseKind.WEIGHT) {
                                            var weightText by remember { mutableStateOf((selectedWeights[id] ?: ex.targetWeightKg).toString()) }
                                            OutlinedTextField(weightText, { value ->
                                                weightText = value.take(6)
                                                value.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() && it in 0f..500f }?.let {
                                                    selectedWeights = selectedWeights + (id to it)
                                                }
                                            }, label = { Text(npString(R.string.np_237d426ca60f)) }, singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                                        }
                                        if (ex.kind in listOf(ExerciseKind.WEIGHT, ExerciseKind.BODYWEIGHT) || workoutKind == WorkoutKind.AMRAP) {
                                            var repsText by remember { mutableStateOf((selectedPresetTargets[id] ?: ex.targetReps).toString()) }
                                            OutlinedTextField(repsText, { value ->
                                                repsText = value.filter(Char::isDigit).take(3)
                                                repsText.toIntOrNull()?.takeIf { it in 1..500 }?.let {
                                                    selectedPresetTargets = selectedPresetTargets + (id to it)
                                                }
                                            }, label = { Text(npString(R.string.np_153aa01039aa)) }, singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (presetNotice.isNotBlank() && selectedPresetName.isBlank()) Text(presetNotice, color = Muted, fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp))

                Spacer(Modifier.height(12.dp))
                if (!editingTemplate && selectedPresetName.isBlank()) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(npString(R.string.np_f711c4662ae4), color = White, fontSize = 13.sp)
                        Text(npString(R.string.np_fba7bf471cf9), color = Muted, fontSize = 10.sp)
                    }
                    Switch(checked = saveAsTemplate, enabled = selectedIds.isNotEmpty(), onCheckedChange = { saveAsTemplate = it })
                }
                Text(if (selectedPresetName.isNotBlank())
                    npString(R.string.np_7396ed26d384)
                    else if (isNew && workoutKind == WorkoutKind.AMRAP)
                    npString(R.string.np_2430d35abb61)
                    else if (isNew) npString(R.string.np_df8af55eabbf)
                    else npString(R.string.np_bf810ccd740c),
                    color = Muted, fontSize = 11.sp, lineHeight = 15.sp)
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(npString(R.string.np_07af7cb30fca)) }
                    TextButton(
                        onClick = {
                            val fallback = suggestWorkoutName(
                                existingWorkouts,
                                allExercises.filter { it.id in selectedIds }
                            )
                            val finalName = name.trim().ifBlank { fallback }
                            val orderedIds = (selectedOrder.filter { it in selectedIds } + selectedIds.filterNot { it in selectedOrder }).distinct()
                            val duration = if (workoutKind == WorkoutKind.CLASSIC) 0 else (durationText.toIntOrNull() ?: 20).coerceIn(1, 480) * 60
                            if (saveAsTemplate && orderedIds.isNotEmpty()) {
                                savePersonalWorkoutTemplate(context,
                                    Workout(UUID.randomUUID().toString(), finalName, orderedIds, note.trim(), workoutKind,
                                        duration, selectedPresetTargets.filterKeys { it in selectedIds }, selectedWeights.filterKeys { it in selectedIds }),
                                    (allExercises + pendingPresetExercises).distinctBy { it.id })
                            }
                            onSave(finalName, note.trim(), orderedIds, workoutKind,
                                if (workoutKind == WorkoutKind.CLASSIC) 0 else (durationText.toIntOrNull() ?: 20).coerceIn(1, 480) * 60,
                                selectedPresetTargets.filterKeys { it in selectedIds }, selectedWeights.filterKeys { it in selectedIds })
                        }
                    ) { Text(if (!isNew) npString(R.string.np_c05fabc0e5af) else npString(R.string.np_2484cd764a80)) }
                }
            }
        }
    }
    if (showPresetPicker) {
        AlertDialog(
            onDismissRequest = { showPresetPicker = false },
            title = { DisplayTitle(npString(R.string.np_7e022f02cec6), ndot, fontSize = 25.sp) },
            text = {
                Column(Modifier.heightIn(max = 410.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (personalTemplates.isEmpty()) Text(npString(R.string.np_2a64c542f06d), color = Muted, fontSize = 12.sp)
                    if (personalTemplates.isNotEmpty()) {
                        Text(npString(R.string.np_7e022f02cec6), color = Red, fontSize = 10.sp, fontFamily = FontFamily.Default)
                        personalTemplates.forEach { (templateKey, obj) ->
                            Surface(color = Panel2, shape = RoundedCornerShape(15.dp), modifier = Modifier.fillMaxWidth()) {
                                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f).clickable {
                                        choosePersonalTemplate(obj)
                                    }) {
                                        Text(obj.optString("name"), color = White, fontSize = 15.sp)
                                        Text(npString(R.string.np_831b6a6e215c), color = Muted, fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPresetPicker = false }) { Text(npString(R.string.np_44424b18700e)) } },
            containerColor = Panel
        )
    }
    if (showExercisePicker) {
        AlertDialog(
            onDismissRequest = { showExercisePicker = false },
            title = { DisplayTitle(npString(R.string.np_5fed967a4959), ndot, fontSize = 24.sp) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    allExercises.forEach { ex ->
                        val included = ex.id in selectedIds
                        Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                            selectedIds = if (included) selectedIds - ex.id else selectedIds + ex.id
                            selectedOrder = if (included) selectedOrder - ex.id else selectedOrder + ex.id
                        }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            ExerciseSelectionMark(included)
                            Spacer(Modifier.width(12.dp))
                            Text(ex.name, color = White, fontSize = 14.sp, lineHeight = 20.sp,
                                modifier = Modifier.weight(1f),
                                style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showExercisePicker = false }) { Text(npString(R.string.np_594308426372)) } },
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
    canLog: Boolean,
    onRemoveFromWorkout: (() -> Unit)?,
    onDeleteExercise: () -> Unit,
    onEditExercise: () -> Unit,
    onShareExercise: () -> Unit,
    onExportExercise: () -> Unit,
    onHapticTick: () -> Unit
) {
    var confirmExerciseDelete by remember { mutableStateOf(false) }
    var showExerciseActions by remember { mutableStateOf(false) }
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
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(22.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(exerciseKindLabel(exercise.kind).uppercase(npLocale()), color = Red,
                        fontFamily = FontFamily.Default, fontSize = 10.sp, modifier = Modifier.weight(1f))
                    Box {
                        IconButton(onClick = { showExerciseActions = true }) { Text("⋮", color = White, fontSize = 24.sp) }
                        DropdownMenu(expanded = showExerciseActions, onDismissRequest = { showExerciseActions = false }) {
                            DropdownMenuItem(text = { Text(npString(R.string.np_3e30e6838966)) }, onClick = { showExerciseActions = false; onShareExercise() })
                            DropdownMenuItem(text = { Text(npString(R.string.np_f3434075835f)) }, onClick = { showExerciseActions = false; onExportExercise() })
                            if (onRemoveFromWorkout != null) DropdownMenuItem(text = { Text(npString(R.string.np_4dfb6705cdd7)) },
                                onClick = { showExerciseActions = false; onRemoveFromWorkout() })
                        }
                    }
                    IconButton(onClick = onEditExercise) { WorkoutActionIcon(WorkoutAction.EDIT) }
                    IconButton(onClick = { confirmExerciseDelete = true }) { WorkoutActionIcon(WorkoutAction.DELETE, Red) }
                }
                DisplayTitle(exercise.name.uppercase(npLocale()), ndot, fontSize = 26.sp)
                Text(npMuscleLabel(exercise.muscle) + " · " + exerciseKindLabel(exercise.kind), color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
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
                    SectionLabel(npString(R.string.np_9e8be764359c), topPadding = 16.dp, bottomPadding = 8.dp)
                    Surface(color = Panel2, shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            exercise.steps.forEachIndexed { i, step ->
                                Row(verticalAlignment = Alignment.Top) {
                                    Text(
                                        "${i + 1}.",
                                        color = Red,
                                        fontFamily = FontFamily.Default,
                                        fontSize = 11.sp,
                                        modifier = Modifier.width(22.dp)
                                    )
                                    Text(step, color = White, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                if (canLog) {
                SectionLabel(npString(R.string.np_6e1b76036d3e), topPadding = 18.dp, bottomPadding = 10.dp)
                when (exercise.kind) {
                    ExerciseKind.CARDIO, ExerciseKind.AMRAP -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(secondsText, { secondsText = it.filter(Char::isDigit).take(5) },
                                label = { Text(npString(R.string.np_5b0e65c12ab8)) }, modifier = Modifier.weight(1f),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            if (exercise.kind == ExerciseKind.CARDIO) {
                                OutlinedTextField(distanceText, { distanceText = it.take(8) },
                                    label = { Text(npString(R.string.np_8f9f75816344)) }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                            }
                            if (exercise.kind == ExerciseKind.AMRAP) {
                                OutlinedTextField(repsText, { repsText = it.filter(Char::isDigit).take(4) },
                                    label = { Text(npString(R.string.np_c042750d784a)) }, modifier = Modifier.weight(1f),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                            }
                        }
                    }

                    ExerciseKind.WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = weightText,
                                onValueChange = { weightText = it.take(6) },
                                label = { Text(npString(R.string.np_237d426ca60f)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = repsText,
                                onValueChange = { repsText = it.filter(Char::isDigit).take(4) },
                                label = { Text(npString(R.string.np_8752f1c63e6e)) },
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
                            label = { Text(npString(R.string.np_c45203d53584)) },
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
                                label = { Text(npString(R.string.np_733e0e2f101e)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = secondsText,
                                onValueChange = { secondsText = it.filter(Char::isDigit).take(5) },
                                label = { Text(npString(R.string.np_b7a874a0db72)) },
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
                            if (canLog) onLogSet(
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
                            npString(R.string.np_40def82f84df),
                            color = Bg,
                            fontFamily = FontFamily.Default,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.4.sp
                        )
                    }
                }

                } else {
                    Surface(color = Panel2, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        Text(npString(R.string.np_34921ec5cba3), color = Muted,
                            fontSize = 12.sp, modifier = Modifier.padding(14.dp))
                    }
                }
                if (logs.isNotEmpty()) {
                    SectionLabel(npString(R.string.np_51c7037a75ce , logs.size), topPadding = 18.dp, bottomPadding = 8.dp)
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
                                Text(log.date, color = Muted, fontFamily = FontFamily.Default, fontSize = 11.sp)
                                Text(logSummary(exercise, log), color = White, fontFamily = FontFamily.Default, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        onHapticTick()
                        onDismiss()
                    }) { Text(npString(R.string.np_44424b18700e)) }
                }
            }
        }
    }
    if (confirmExerciseDelete) AlertDialog(
        onDismissRequest = { confirmExerciseDelete = false }, containerColor = Panel,
        title = { DisplayTitle(npString(R.string.np_d54e3800f52b), ndot, fontSize = 24.sp) },
        text = { Text(npString(R.string.np_f6de9f58f3d0), color = Muted) },
        confirmButton = { TextButton(onClick = { confirmExerciseDelete = false; onDeleteExercise() }) { Text(npString(R.string.np_ffa5a8a7e21d), color = Red) } },
        dismissButton = { TextButton(onClick = { confirmExerciseDelete = false }) { Text(npString(R.string.np_07af7cb30fca)) } }
    )

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
            androidx.compose.foundation.Image(bitmap, npString(R.string.np_a5f223348642), Modifier.fillMaxWidth().height(180.dp), contentScale = ContentScale.Fit)
        } else {
            Text(if (youtubeId != null) npString(R.string.np_6b75f65ab453) else if (isVideo) npString(R.string.np_d2c05164a3b3) else npString(R.string.np_4bd2f3421aa0),
                color = White, fontSize = 14.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(uri.host.orEmpty(), color = Muted, fontSize = 11.sp)
        TextButton(onClick = {
            runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri)) }
        }) { Text(if (youtubeId != null) npString(R.string.np_5253512f28de) else npString(R.string.np_111250542e0b)) }
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
                Text(npString(R.string.np_5af65216e8d2), color = White, fontSize = 14.sp)
                Text(if (external == null) npString(R.string.np_992b25cdc80c) else npString(R.string.np_6b3a650f4661), color = Muted, fontSize = 11.sp)
                TextButton(enabled = target != null, onClick = {
                    target?.let { uri ->
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri).apply {
                            })
                        }.onFailure { Toast.makeText(context, npString(R.string.np_6a2fce0e54c0), Toast.LENGTH_SHORT).show() }
                    }
                }) { Text(npString(R.string.np_b13ccfe84c88), color = White) }
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
                    contentDescription = npString(R.string.np_6ecaaff87dea),
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
                Text(npString(R.string.np_51c52096e45b), color = White, fontSize = 20.sp)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it.take(50) },
                    label = { Text(npString(R.string.np_c0ddbd78adad)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(npString(R.string.np_75993db09725), !selectingSecondary, Modifier.weight(1f)) { selectingSecondary = false }
                    ChoiceChip(npString(R.string.np_9d1181d822cc), selectingSecondary, Modifier.weight(1f)) { selectingSecondary = true }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 370.dp).verticalScroll(rememberScrollState())) {
                    val filtered = muscleCatalogue.filter {
                        it.label.contains(search, ignoreCase = true) || it.category.contains(search, ignoreCase = true)
                    }
                    filtered.groupBy { it.category }.forEach { (category, entries) ->
                        Text(category.uppercase(npLocale()), color = Muted, fontSize = 11.sp,
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
                    TextButton(onClick = onDismiss) { Text(npString(R.string.np_07af7cb30fca)) }
                    TextButton(onClick = { onApply(selectedPrimary, selectedSecondary.toList()) }) { Text(npString(R.string.np_594308426372)) }
                }
            }
        }
    }
}

@Composable
private fun NewExerciseDialog(
    ndot: FontFamily,
    targetWorkoutName: String?,
    initialExercise: Exercise? = null,
    onDismiss: () -> Unit,
    onCreate: (Exercise) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember { mutableStateOf(initialExercise?.name ?: "") }
    var primaryMuscleId by remember { mutableStateOf(initialExercise?.primaryMuscleId?.takeIf { it.isNotBlank() }
        ?: muscleCatalogue.firstOrNull { it.rawCategory.equals(initialExercise?.muscle, true) }?.id ?: "chest") }
    var muscleSelectionChanged by remember { mutableStateOf(false) }
    val secondaryMuscles = remember { mutableStateListOf<String>().apply { addAll(initialExercise?.secondaryMuscleIds.orEmpty()) } }
    var musclePickerOpen by remember { mutableStateOf(false) }
    var kind by remember { mutableStateOf(initialExercise?.kind ?: ExerciseKind.WEIGHT) }
    var description by remember { mutableStateOf(initialExercise?.description ?: "") }
    var stepsText by remember { mutableStateOf(initialExercise?.steps?.joinToString("\n") ?: "") }
    var targetWeight by remember { mutableStateOf((initialExercise?.targetWeightKg ?: 40f).toString()) }
    var targetReps by remember { mutableStateOf((initialExercise?.targetReps ?: 10).toString()) }
    var targetSeconds by remember { mutableStateOf((initialExercise?.targetSeconds ?: 45).toString()) }
    var copiedMediaPath by remember { mutableStateOf(initialExercise?.mediaPath?.takeUnless { validatedMediaUrl(it) != null } ?: "") }
    var mediaUrl by remember { mutableStateOf(initialExercise?.mediaPath?.takeIf { validatedMediaUrl(it) != null } ?: "") }
    var mediaUrlIsVideo by remember { mutableStateOf(initialExercise?.mediaIsVideo ?: true) }
    var showExerciseDetails by remember { mutableStateOf(false) }
    var showExerciseMedia by remember { mutableStateOf(false) }
    var copiedMediaIsVideo by remember { mutableStateOf(initialExercise?.mediaIsVideo ?: false) }

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            runCatching {
                val mime = context.contentResolver.getType(uri).orEmpty()
                val isVid = mime.startsWith("video/")
                val ext = if (isVid) "mp4" else "jpg"
                val dir = File(context.filesDir, "exercise_media").apply { mkdirs() }
                val dest = File(dir, "media_${UUID.randomUUID()}.${ext}")
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                copiedMediaPath = dest.absolutePath
                mediaUrl = ""
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
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle(if (initialExercise == null) npString(R.string.np_12b67bb6f916) else npString(R.string.np_e3417b4e7753), ndot, fontSize = 24.sp)
                if (!targetWorkoutName.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        npString(R.string.np_5b4eeaeb72a7 , targetWorkoutName),
                        color = Muted,
                        fontSize = 11.sp
                    )
                }
                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(npString(R.string.np_78b296080ae2)) },
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
                        Text(npString(R.string.np_32fcc564de8a), color = White, fontSize = 12.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(npString(R.string.np_40f41025e969 , muscleName(primaryMuscleId)), color = White, fontSize = 14.sp)
                        if (secondaryMuscles.isNotEmpty()) {
                            Text(npString(R.string.np_86d19c0a8ded , secondaryMuscles.joinToString(", ") { muscleName(it) }), color = Muted, fontSize = 11.sp)
                        }
                    }
                }

                SectionLabel(npString(R.string.np_82ef343eef67), topPadding = 16.dp, bottomPadding = 8.dp)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        ExerciseKind.WEIGHT to npString(R.string.np_b80121b7ef4f),
                        ExerciseKind.BODYWEIGHT to npString(R.string.np_f75cff63870c),
                        ExerciseKind.TIMED to npString(R.string.np_d34f8dd0e537),
                        ExerciseKind.CARDIO to npString(R.string.np_eb013efc32e2)
                    ).forEach { (k, label) ->
                        ChoiceChip(label, kind == k, Modifier.width(115.dp)) {
                            kind = k
                            triggerHaptic(context, haptic, HapticStyle.TICK)
                        }
                    }
                }

                if (kind == ExerciseKind.CARDIO) {
                    SectionLabel(npString(R.string.np_d8331d85aac4), topPadding = 12.dp, bottomPadding = 6.dp)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(npString(R.string.np_228d2ab8ff7e), npString(R.string.np_8f60a5524b26), npString(R.string.np_147fca586077), npString(R.string.np_de210adba382), npString(R.string.np_bcff9160c016), "Crosstrainer", "Seilspringen").forEach { suggestion ->
                            ChoiceChip(suggestion, name == suggestion, Modifier.width(112.dp)) { name = suggestion }
                        }
                    }
                }
                if (kind == ExerciseKind.AMRAP) {
                    TextButton(onClick = {
                        name = npString(R.string.np_6ac07aa166b6)
                        description = npString(R.string.np_4b84c437442d)
                        targetSeconds = "1200"
                        targetReps = "1"
                    }) { Text(npString(R.string.np_c1c8487f611d)) }
                }
                Spacer(Modifier.height(12.dp))
                when (kind) {
                    ExerciseKind.CARDIO, ExerciseKind.AMRAP -> {
                        OutlinedTextField(targetSeconds, { targetSeconds = it.filter(Char::isDigit).take(5) },
                            label = { Text(npString(R.string.np_ca4712174561)) }, modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        if (kind == ExerciseKind.AMRAP) {
                            OutlinedTextField(targetReps, { targetReps = it.filter(Char::isDigit).take(4) },
                                label = { Text(npString(R.string.np_d70a08bcf3ec)) }, modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                    }

                    ExerciseKind.WEIGHT -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = targetWeight,
                                onValueChange = { targetWeight = it.take(6) },
                                label = { Text(npString(R.string.np_7662a4352ac6)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetReps,
                                onValueChange = { targetReps = it.filter(Char::isDigit).take(4) },
                                label = { Text(npString(R.string.np_af011b0785ed)) },
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
                            label = { Text(npString(R.string.np_696c877954da)) },
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
                                label = { Text(npString(R.string.np_733e0e2f101e)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = targetSeconds,
                                onValueChange = { targetSeconds = it.filter(Char::isDigit).take(5) },
                                label = { Text(npString(R.string.np_6dc754e2bb60)) },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                  Spacer(Modifier.height(10.dp))
                  Surface(color = Panel2, shape = RoundedCornerShape(16.dp),
                      modifier = Modifier.fillMaxWidth().clickable { showExerciseDetails = !showExerciseDetails }) {
                      Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                          Text(npString(R.string.np_c5aa0094e224), color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                          Text(if (showExerciseDetails) "⌃" else "›", color = Muted)
                      }
                  }
                  if (showExerciseDetails) {
                      Spacer(Modifier.height(8.dp))
                      OutlinedTextField(value = description, onValueChange = { description = it.take(400) },
                          label = { Text(npString(R.string.np_01a5e0ece5e4)) }, minLines = 2, maxLines = 4,
                          modifier = Modifier.fillMaxWidth())
                      OutlinedTextField(value = stepsText, onValueChange = { stepsText = it.take(4000) },
                          label = { Text(npString(R.string.np_77400e4f0ec5)) }, minLines = 2, maxLines = 5,
                          modifier = Modifier.fillMaxWidth())
                  }
                  Spacer(Modifier.height(8.dp))
                  Surface(color = Panel2, shape = RoundedCornerShape(16.dp),
                      modifier = Modifier.fillMaxWidth().clickable { showExerciseMedia = !showExerciseMedia }) {
                      Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                          Text(npString(R.string.np_d5e5956e6aa4), color = White, fontSize = 12.sp, modifier = Modifier.weight(1f))
                          Text(if (showExerciseMedia) "⌃" else "›", color = Muted)
                      }
                  }
                  if (showExerciseMedia) {
                SectionLabel(npString(R.string.np_e1e41fb2b17a), topPadding = 16.dp, bottomPadding = 8.dp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(
                        label = if (copiedMediaPath.isNotBlank() && !copiedMediaIsVideo) npString(R.string.np_31e7a39d201a) else npString(R.string.np_42a94d15d8f2),
                        selected = copiedMediaPath.isNotBlank() && !copiedMediaIsVideo,
                        modifier = Modifier.weight(1f)
                    ) {
                        mediaPicker.launch("image/*")
                    }
                    ChoiceChip(
                        label = if (copiedMediaPath.isNotBlank() && copiedMediaIsVideo) npString(R.string.np_81a729ac41c5) else npString(R.string.np_85a30e7cb28e),
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
                    label = { Text(npString(R.string.np_a26b53ae58e2)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(npString(R.string.np_ae3c9b5e31db), mediaUrlIsVideo, Modifier.weight(1f)) { mediaUrlIsVideo = true }
                    ChoiceChip(npString(R.string.np_d42485349869), !mediaUrlIsVideo, Modifier.weight(1f)) { mediaUrlIsVideo = false }
                }
                if (mediaUrl.isNotBlank() && validatedMediaUrl(mediaUrl) == null) {
                    Text(npString(R.string.np_7fb362e8d0bf), color = Red, fontSize = 11.sp)
                }
                  }
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(npString(R.string.np_07af7cb30fca)) }
                    TextButton(
                        enabled = name.isNotBlank() && (mediaUrl.isBlank() || validatedMediaUrl(mediaUrl) != null),
                        onClick = {
                            onCreate(
                                Exercise(
                                    id = initialExercise?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    muscle = if (initialExercise != null && !muscleSelectionChanged) initialExercise.muscle else muscleCatalogue.firstOrNull { it.id == primaryMuscleId }?.rawCategory ?: "Ganzkörper",
                                    primaryMuscleId = if (initialExercise != null && !muscleSelectionChanged) initialExercise.primaryMuscleId else primaryMuscleId,
                                    secondaryMuscleIds = secondaryMuscles.toList(),
                                    kind = kind,
                                    description = description.trim(),
                                    steps = stepsText.lines().map { it.trim() }.filter { it.isNotBlank() },
                                    mediaPath = mediaUrl.takeIf { it.isNotBlank() }?.trim() ?: copiedMediaPath,
                                    mediaIsVideo = if (mediaUrl.isNotBlank()) mediaUrlIsVideo else copiedMediaIsVideo,
                                    targetWeightKg = targetWeight.replace(',', '.').toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 500f) ?: 20f,
                                    targetReps = targetReps.toIntOrNull()?.coerceIn(1, 500) ?: 10,
                                    targetSeconds = targetSeconds.toIntOrNull()?.coerceIn(1, 3600) ?: 45
                                )
                            )
                        }
                    ) { Text(if (initialExercise == null) npString(R.string.np_846460c3195a) else npString(R.string.np_70b73bbc118d)) }
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
                muscleSelectionChanged = true
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
                errorMsg = npString(R.string.np_92859f8d2db5)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            color = Panel,
            shape = RoundedCornerShape(28.dp)
        ) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle(npString(R.string.np_f330ec3da298), ndot, fontSize = 24.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    buildString {
                        append(npString(R.string.np_ee22333397e5, NPEF_FORMAT_ID))
                        if (!targetWorkoutName.isNullOrBlank()) {
                            append(npString(R.string.np_748297d0d36e, targetWorkoutName))
                        }
                    },
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(npString(R.string.np_6202f94efd35), true, Modifier.weight(1.2f)) {
                        onSharePrompt()
                    }
                    ChoiceChip(npString(R.string.np_b5e5727b7dfd), false, Modifier.weight(0.8f)) {
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
                    label = { Text(npString(R.string.np_d989fa52d71e)) },
                    minLines = 5,
                    maxLines = 9,
                    textStyle = TextStyle(fontFamily = FontFamily.Default, fontSize = 11.sp),
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
                        Toast.makeText(context, npString(R.string.np_c918198212cb), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(npString(R.string.np_508fde77ee49), color = Muted, fontSize = 11.sp)
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text(npString(R.string.np_07af7cb30fca)) }
                        TextButton(
                            enabled = jsonInput.isNotBlank(),
                            onClick = {
                                parseNpefExercises(jsonInput)
                                    .onSuccess { parsed -> onImport(parsed) }
                                    .onFailure { err -> errorMsg = err.message ?: npString(R.string.np_74ba7ad179b9) }
                            }
                        ) { Text(npString(R.string.np_7d4a3f2aaafe)) }
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
    val navHaptic = LocalHapticFeedback.current
    val navContext = LocalContext.current

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
            .padding(horizontal = 56.dp)
            .padding(bottom = 22.dp)
            .widthIn(max = 274.dp)
            .fillMaxWidth()
            .height(56.dp),
        color = Color(0xB8323232),
        shape = RoundedCornerShape(31.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, White.copy(alpha = .16f)),
        shadowElevation = 6.dp
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
                        npNeutral(0xFF969696),
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
                            ) {
                                    triggerHaptic(navContext, navHaptic, HapticStyle.TICK)
                                    select(i)
                                },
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
            preview(if (selected) Bg else White, if (selected) Color(0xFFB5B4AF) else npNeutral(0xFF4A4A4A))
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
    onEdit: (Rule) -> Unit,
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

    Screen(ndot, npString(R.string.np_b0bae95080a9), npString(R.string.np_5fbb9f0b064b), npString(R.string.np_b86312ffdb86)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 26.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(npString(R.string.np_2af464a2d2d7), color = Muted, fontFamily = FontFamily.Default, fontSize = 10.sp, letterSpacing = 1.7.sp)
            TextButton(onClick = onAdd) {
                Text(npString(R.string.np_06df2fea07b8), color = White, fontFamily = FontFamily.Default, fontSize = 11.sp, letterSpacing = 1.7.sp)
            }
        }

        if (rules.isEmpty()) {
            Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                Text(
                    npString(R.string.np_adf9b9b52535),
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
                                .clickable { onEdit(rule) },
                            contentAlignment = Alignment.Center
                        ) {
                            WorkoutActionIcon(WorkoutAction.EDIT, White)
                        }
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .clickable { onDelete(rule.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            WorkoutActionIcon(WorkoutAction.DELETE, Red)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(npString(R.string.np_b8331e795763))
        NPlannerAppearanceSettings()
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(npString(R.string.np_29db02be308f), fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text(npString(R.string.np_ed3994287ff4), fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(false to npString(R.string.np_2cc58a112d0a), true to npString(R.string.np_db4262bdee36)).forEach { (dot, label) ->
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
                DisplayTitle(npString(R.string.np_42c1112fd679), ndot, fontSize = 25.sp)
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(npString(R.string.np_27afe8e47ea3), fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text(npString(R.string.np_e35fa12ca640), fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressStyleTile(npString(R.string.np_e51bd6be1fe7), !orbit, Modifier.weight(1f), { onProgressStyleChange(false) }) { fg, track -> MiniBars(fg, track) }
                    ProgressStyleTile(npString(R.string.np_78ba515b2614), orbit, Modifier.weight(1f), { onProgressStyleChange(true) }) { fg, track -> MiniRing(fg, track) }
                }
            }
        }
        Surface(color = Panel, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) {
            Column(Modifier.padding(16.dp)) {
                Text(npString(R.string.np_418ebf21947a), fontSize = 16.sp, color = White, fontWeight = FontWeight.Medium)
                Text(npString(R.string.np_349e9979c0db), fontSize = 11.sp, color = Muted)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProgressStyleTile(npString(R.string.np_b6dafaa1c8b5), historyDots, Modifier.weight(1f), { onHistoryStyleChange(true) }) { fg, track -> MiniHistoryDots(fg, track) }
                    ProgressStyleTile(npString(R.string.np_33fe92f625e5), !historyDots, Modifier.weight(1f), { onHistoryStyleChange(false) }) { fg, track -> MiniHistoryCalendar(fg, track) }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionLabel(if (npLocale().language == "de") "DATENSICHERUNG" else "BACKUP")
        NPlannerDriveBackupSettings()
        Spacer(Modifier.height(10.dp))
        SectionLabel(npString(R.string.np_9028b65aa0a5), topPadding = 22.dp, bottomPadding = 10.dp)
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
                    Text(npString(R.string.np_4711c2206922), fontSize = 15.sp, color = White, fontWeight = FontWeight.Medium)
                    Text(
                        if (testMode) npString(R.string.np_709bae28f41d)
                        else npString(R.string.np_05942485fc9b),
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
            npString(R.string.np_3839958189dd),
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 18.dp)
        )
        HorizontalDivider(color = npNeutral(0xFF242424), modifier = Modifier.padding(top = 6.dp))
        Text(
            "${npString(R.string.np_69a89741235d)} $NPLANNER_VERSION",
            color = npNeutral(0xFFB6B6B6),
            fontFamily = FontFamily.Default,
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
private fun NewTaskDialog(
    ndot: FontFamily,
    initialRule: Rule? = null,
    onDismiss: () -> Unit,
    onSave: (Rule) -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var name by remember(initialRule?.id) { mutableStateOf(initialRule?.name.orEmpty()) }
    var kind by remember(initialRule?.id) { mutableStateOf(initialRule?.kind ?: RuleKind.ALLDAY) }
    var timeMin by remember(initialRule?.id) { mutableIntStateOf(initialRule?.timeMin ?: 8 * 60) }
    var every by remember(initialRule?.id) { mutableIntStateOf(initialRule?.everyMin ?: 120) }
    var startMin by remember(initialRule?.id) { mutableIntStateOf(initialRule?.fromMin ?: 8 * 60) }
    var endMin by remember(initialRule?.id) { mutableIntStateOf(initialRule?.toMin ?: 20 * 60) }
    var weeklyTarget by remember(initialRule?.id) { mutableIntStateOf(initialRule?.weeklyTarget ?: 3) }
    var oneTimeDate by remember(initialRule?.id) { mutableStateOf(initialRule?.oneTimeDate?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var days by remember(initialRule?.id) { mutableStateOf(initialRule?.days ?: (1..7).toSet()) }
    var optional by remember(initialRule?.id) { mutableStateOf(initialRule?.optional ?: false) }

    fun pick(initial: Int, set: (Int) -> Unit) {
        android.app.TimePickerDialog(
            context,
            android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, h, m -> set(h * 60 + m) },
            initial / 60, initial % 60, true
        ).show()
    }

    fun pickDate() {
        val initial = runCatching { LocalDate.parse(oneTimeDate) }.getOrDefault(LocalDate.now())
        android.app.DatePickerDialog(context, android.R.style.Theme_DeviceDefault_Dialog_Alert,
            { _, year, month, day -> oneTimeDate = LocalDate.of(year, month + 1, day).toString() },
            initial.year, initial.monthValue - 1, initial.dayOfMonth
        ).apply { datePicker.minDate = System.currentTimeMillis() - 86_400_000L }.show()
    }

    val valid = name.isNotBlank() &&
        (kind != RuleKind.INTERVAL || endMin >= startMin) &&
        (kind != RuleKind.ONE_TIME || runCatching { LocalDate.parse(oneTimeDate) }.isSuccess) &&
        (kind == RuleKind.WEEKLY || kind == RuleKind.ONE_TIME || days.isNotEmpty())
    val slotCount = Rule("", "", days, kind = RuleKind.INTERVAL, everyMin = every, fromMin = startMin, toMin = endMin).slots().size

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = Panel, shape = RoundedCornerShape(28.dp)) {
            Column(Modifier.imePadding().verticalScroll(rememberScrollState()).padding(22.dp)) {
                DisplayTitle(if (initialRule == null) npString(R.string.np_060813ffb8c9) else "AUFGABE BEARBEITEN", ndot, fontSize = 24.sp)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(60) },
                    label = { Text(npString(R.string.np_a0b60ec855e9)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                SectionLabel(npString(R.string.np_abfe09c3932d))
                val taskKindOptions = listOf(
                    RuleKind.FIXED to "Uhrzeit",
                    RuleKind.INTERVAL to npString(R.string.np_6c03c59685ba),
                    RuleKind.ALLDAY to npString(R.string.np_e5721c734beb),
                    RuleKind.ONE_TIME to if (npLocale().language == "de") "Einmalig" else "One-time",
                    RuleKind.WEEKLY to npString(R.string.np_d56d73957fc5)
                )
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    taskKindOptions.chunked(3).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            rowItems.forEach { (k, label) ->
                                ChoiceChip(label, kind == k, Modifier.weight(1f)) {
                                    kind = k
                                    triggerHaptic(context, haptic, HapticStyle.TICK)
                                }
                            }
                            repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                when (kind) {
                    RuleKind.FIXED -> {
                        SectionLabel(npString(R.string.np_b3acd2792395))
                        TimeBox("Uhrzeit", timeMin, ndot, Modifier.fillMaxWidth()) { pick(timeMin) { timeMin = it } }
                    }
                    RuleKind.INTERVAL -> {
                        SectionLabel(npString(R.string.np_b8f128a679b7))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(30 to npString(R.string.np_c7618bdb3fde), 60 to npString(R.string.np_d3a004e65a59), 120 to npString(R.string.np_c2c726a4f959), 240 to npString(R.string.np_0d949b4e9677)).forEach { (m, l) ->
                                ChoiceChip(l, every == m, Modifier.weight(1f)) {
                                    every = m
                                    triggerHaptic(context, haptic, HapticStyle.TICK)
                                }
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            TimeBox(npString(R.string.np_a4b078f9eb7b), startMin, ndot, Modifier.weight(1f)) { pick(startMin) { startMin = it } }
                            TimeBox(npString(R.string.np_0afaa0e566a1), endMin, ndot, Modifier.weight(1f)) { pick(endMin) { endMin = it } }
                        }
                        Text(
                            if (endMin >= startMin) npString(R.string.np_9587a83db38f , slotCount) else npString(R.string.np_8e4bdae0da46),
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                    RuleKind.ALLDAY -> {}
                    RuleKind.ONE_TIME -> {
                        SectionLabel(if (npLocale().language == "de") "DATUM" else "DATE")
                        val due = runCatching { LocalDate.parse(oneTimeDate) }.getOrDefault(LocalDate.now())
                        Surface(Modifier.fillMaxWidth().clickable { pickDate() }, color = Panel2, shape = RoundedCornerShape(22.dp)) {
                            Column(Modifier.padding(horizontal = 19.dp, vertical = 14.dp)) {
                                Text(if (npLocale().language == "de") "Ausführen am" else "Due on", color = Muted, fontSize = 10.sp)
                                DisplayTitle(due.format(DateTimeFormatter.ofPattern("EEE, d. MMM yyyy", npLocale())), ndot, Modifier.padding(top = 4.dp), 22.sp)
                            }
                        }
                        Text(if (npLocale().language == "de") "Bleibt bei Nichterledigung überfällig. Nach Erledigung wird sie am nächsten Tag automatisch archiviert." else "Stays overdue until completed. After completion it is archived automatically the next day.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                    RuleKind.WEEKLY -> {
                        SectionLabel(npString(R.string.np_5c4529318bde))
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
                            npString(R.string.np_289c8b1461e3),
                            color = Muted,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }
                }

                if (kind != RuleKind.WEEKLY && kind != RuleKind.ONE_TIME) {
                    SectionLabel(npString(R.string.np_950c2621787f))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        listOf(npString(R.string.np_91e885d8e5af), npString(R.string.np_fe564f789dc6), npString(R.string.np_6b7439b40105), npString(R.string.np_22bdf47be3fd), npString(R.string.np_aa77f314fab0), npString(R.string.np_50cf95cee822), npString(R.string.np_26c0ca793e8f)).forEachIndexed { i, d ->
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

                SectionLabel(npString(R.string.np_3d943e57f928))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ChoiceChip(npString(R.string.np_44e89254df74), !optional, Modifier.weight(1f)) {
                        optional = false
                        triggerHaptic(context, haptic, HapticStyle.TICK)
                    }
                    ChoiceChip(npString(R.string.np_9ff8f89a6660), optional, Modifier.weight(1f)) {
                        optional = true
                        triggerHaptic(context, haptic, HapticStyle.TICK)
                    }
                }
                Text(
                    if (optional) npString(R.string.np_61457fec99c1)
                    else npString(R.string.np_b3dc4bdd3e5b),
                    color = Muted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(npString(R.string.np_07af7cb30fca)) }
                    TextButton(
                        enabled = valid,
                        onClick = {
                            onSave(
                                Rule(
                                    id = initialRule?.id ?: UUID.randomUUID().toString(),
                                    name = name.trim(),
                                    days = if (kind == RuleKind.WEEKLY) (1..7).toSet() else if (kind == RuleKind.ONE_TIME) emptySet() else days,
                                    kind = kind,
                                    timeMin = timeMin,
                                    everyMin = every,
                                    fromMin = startMin,
                                    toMin = endMin,
                                    weeklyTarget = if (kind == RuleKind.WEEKLY) weeklyTarget else 1,
                                    oneTimeDate = if (kind == RuleKind.ONE_TIME) oneTimeDate else "",
                                    archived = initialRule?.archived ?: false,
                                    optional = optional,
                                    createdDate = initialRule?.createdDate.orEmpty()
                                )
                            )
                        }
                    ) { Text(npString(R.string.np_846460c3195a)) }
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
        fontFamily = FontFamily.Default,
        fontSize = 10.sp,
        letterSpacing = 1.7.sp,
        modifier = Modifier.padding(top = topPadding, bottom = bottomPadding)
    )
}

private fun dayLabel(days: Set<Int>): String =
    if (days.size == 7) npString(R.string.np_3e286c337675) else days.sorted().joinToString(" · ") { listOf(npString(R.string.np_91e885d8e5af), npString(R.string.np_fe564f789dc6), npString(R.string.np_6b7439b40105), npString(R.string.np_22bdf47be3fd), npString(R.string.np_aa77f314fab0), npString(R.string.np_50cf95cee822), npString(R.string.np_26c0ca793e8f))[it - 1] }
