package at.lukaechen.nplanner

import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class NPlannerPalette(
    val dark: Boolean, val background: Color, val panel: Color, val panel2: Color,
    val foreground: Color, val muted: Color, val accent: Color
)

internal object NPlannerAppearance {
    private lateinit var context: Context
    private val resourceCache = ConcurrentHashMap<String, Resources>()
    private val darkPalette = NPlannerPalette(true, Color(0xFF080808), Color(0xFF191919), Color(0xFF252525),
        Color(0xFFE8E7E2), Color(0xFF929292), Color(0xFFE03930))
    private val lightPalette = NPlannerPalette(false, Color(0xFFF8F8F5), Color(0xFFFFFFFF), Color(0xFFECECE8),
        Color(0xFF171717), Color(0xFF626262), Color(0xFFC42C24))
    var palette by mutableStateOf(darkPalette)
        private set
    var mode by mutableStateOf("auto")
        private set
    var language by mutableStateOf("de")
        private set

    fun initialize(base: Context) {
        context = base.applicationContext
        resourceCache.clear()
        val prefs = context.getSharedPreferences("nplanner-v2", Context.MODE_PRIVATE)
        val dark = (base.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        // First launch snapshots the current system appearance. Auto is an explicit live-follow option.
        if (!prefs.contains("appearance_mode_v43")) prefs.edit().putString("appearance_mode_v43", if (dark) "dark" else "light").apply()
        mode = prefs.getString("appearance_mode_v43", "auto").takeIf { it in listOf("auto", "light", "dark") } ?: "auto"
        language = prefs.getString("app_language_v43", null).takeIf { it in listOf("de", "en") }
            ?: if (base.resources.configuration.locales[0].language == "de") "de" else "en"
        applyPalette(if (mode == "auto") dark else mode == "dark")
    }
    fun applyPalette(dark: Boolean) {
        val next = if (dark) darkPalette else lightPalette
        if (palette != next) palette = next
    }
    fun updateMode(value: String) {
        if (value !in listOf("auto", "light", "dark")) return
        context.getSharedPreferences("nplanner-v2", Context.MODE_PRIVATE).edit().putString("appearance_mode_v43", value).apply()
        mode = value
    }
    fun updateLanguage(value: String) {
        if (value !in listOf("de", "en")) return
        context.getSharedPreferences("nplanner-v2", Context.MODE_PRIVATE).edit().putString("app_language_v43", value).apply()
        language = value
    }
    fun resources(): Resources {
        val tag = language
        return resourceCache.getOrPut(tag) {
            context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            }).resources
        }
    }
}

// Snapshot-backed language is intentionally readable outside composables (Toast, exports and summaries).
internal fun npString(@StringRes id: Int, vararg args: Any?): String =
    if (args.isEmpty()) NPlannerAppearance.resources().getString(id)
    else NPlannerAppearance.resources().getString(id, *args)

internal fun npLocale(): Locale = Locale.forLanguageTag(NPlannerAppearance.language)

internal fun npNeutral(argb: Long): Color {
    if (NPlannerAppearance.palette.dark) return Color(argb)
    val a = argb and 0xFF000000L
    val shade = (argb and 0xFFL).toInt()
    val inverted = (255 - shade).coerceIn(20, 244).toLong()
    return Color(a or (inverted shl 16) or (inverted shl 8) or inverted)
}

/** Same neutral, rounded selection surfaces as the existing heading/progress settings. */
@Composable
private fun AppearanceChoice(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val palette = NPlannerAppearance.palette
    Surface(
        modifier = modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        color = if (selected) palette.foreground else palette.panel2,
        contentColor = if (selected) palette.background else palette.foreground,
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Box(
            Modifier.heightIn(min = 48.dp).padding(horizontal = 8.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) palette.background else palette.foreground,
                fontFamily = FontFamily.Default,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
internal fun NPlannerAppearanceSettings() {
    val palette = NPlannerAppearance.palette
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(
            color = palette.panel,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(npString(R.string.np_bb7394e2373e), color = palette.foreground,
                    fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(npString(R.string.np_dfd8aad6150a), color = palette.muted,
                    fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "light" to npString(R.string.np_ed10fe113de1),
                        "dark" to npString(R.string.np_ec4616f89869),
                        "auto" to npString(R.string.np_c614ba7c453c)
                    ).forEach { (value, label) ->
                        AppearanceChoice(label, NPlannerAppearance.mode == value, Modifier.weight(1f)) {
                            NPlannerAppearance.updateMode(value)
                        }
                    }
                }
            }
        }
        Surface(
            color = palette.panel,
            shape = RoundedCornerShape(22.dp),
            tonalElevation = 0.dp,
            shadowElevation = 0.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(16.dp)) {
                Text(npString(R.string.np_b28f6f150a0f), color = palette.foreground,
                    fontFamily = FontFamily.Default, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                Spacer(Modifier.height(4.dp))
                Text(npString(R.string.np_834bc9df07a7), color = palette.muted,
                    fontFamily = FontFamily.Default, fontSize = 11.sp, lineHeight = 16.sp)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "de" to npString(R.string.np_a6a7709266d3),
                        "en" to npString(R.string.np_649df08a448e)
                    ).forEach { (value, label) ->
                        AppearanceChoice(label, NPlannerAppearance.language == value, Modifier.weight(1f)) {
                            NPlannerAppearance.updateLanguage(value)
                        }
                    }
                }
            }
        }
    }
}
