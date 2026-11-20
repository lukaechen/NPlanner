package at.lukas.daily

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

// First native milestone: intentionally small, not yet feature-complete.
data class Rule(val id: String, val name: String)
class MainActivity : ComponentActivity() {
    private val prefs by lazy { getSharedPreferences("daily-v1", MODE_PRIVATE) }
    private fun loadRules(): List<Rule> = try {
        val raw = prefs.getString("rules", null)
        if (raw == null) listOf("Morgenroutine", "30 Minuten lernen", "Vitamine").map { Rule(UUID.randomUUID().toString(), it) }
        else JSONArray(raw).let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { Rule(it.getString("id"), it.getString("name")) } } }
    } catch (_: Exception) { emptyList() }
    private fun saveRules(rules: List<Rule>) {
        val a = JSONArray()
        rules.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name)) }
        prefs.edit().putString("rules", a.toString()).apply()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFFE8E7E2), background = Color(0xFF080808), surface = Color(0xFF191919))) {
                var rules by remember { mutableStateOf(loadRules()) }
                var date by remember { mutableStateOf(LocalDate.now()) }
                var tick by remember { mutableIntStateOf(0) }
                var page by remember { mutableIntStateOf(0) }
                var name by remember { mutableStateOf("") }
                var dialog by remember { mutableStateOf(false) }
                // Recheck local date; persistent completion keys include the date.
                LaunchedEffect(Unit) { while (true) { date = LocalDate.now(); delay(1000) } }
                LaunchedEffect(Unit) { saveRules(rules) }
                fun key(r: Rule) = "done:$date:${r.id}"
                val done = remember(rules, date, tick) { rules.count { prefs.getBoolean(key(it), false) } }
                Scaffold(
                    containerColor = Color(0xFF080808),
                    bottomBar = {
                        NavigationBar(modifier = Modifier.navigationBarsPadding().padding(18.dp), containerColor = Color(0xFF252525), tonalElevation = 0.dp) {
                            listOf("Heute", "Setup").forEachIndexed { i, label ->
                                NavigationBarItem(selected = page == i, onClick = { page = i }, icon = { Text(if (i == 0) "◉" else "⚙") }, label = { Text(label) })
                            }
                        }
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(22.dp)) {
                        Text("●  DAILY", fontSize = 12.sp, color = Color.LightGray)
                        Spacer(Modifier.height(28.dp))
                        Text(if (page == 0) "Heute" else "Einstellungen", fontSize = 34.sp)
                        Text(if (page == 0) date.format(DateTimeFormatter.ofPattern("EEEE, d. MMMM", Locale.GERMAN)) else "Deine Regeln. Dein Rhythmus.", color = Color.Gray)
                        Spacer(Modifier.height(28.dp))
                        if (page == 0) {
                            Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(progress = { if (rules.isEmpty()) 1f else done.toFloat() / rules.size }, modifier = Modifier.size(170.dp), strokeWidth = 5.dp, trackColor = Color(0xFF292929))
                                Text("$done / ${rules.size}", fontSize = 38.sp)
                            }
                            Spacer(Modifier.height(28.dp))
                            if (done == rules.size) {
                                Text("Für heute fertig.", fontSize = 26.sp)
                                Text("Alles erledigt. Der Rest des Tages gehört dir.", color = Color.Gray)
                            }
                            rules.forEach { rule ->
                                val checked = remember(tick, date, rule.id) { prefs.getBoolean(key(rule), false) }
                                Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(22.dp), color = Color(0xFF191919)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked, onCheckedChange = { value -> prefs.edit().putBoolean(key(rule), value).apply(); tick++ })
                                        Text(rule.name, modifier = Modifier.padding(start = 8.dp), color = if (checked) Color.Gray else Color.White)
                                    }
                                }
                            }
                        } else {
                            Text("REGELMÄSSIGE AUFGABEN", fontSize = 11.sp, color = Color.Gray)
                            rules.forEachIndexed { index, rule ->
                                Surface(Modifier.fillMaxWidth().padding(vertical = 5.dp), shape = RoundedCornerShape(22.dp), color = Color(0xFF191919)) {
                                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Text(rule.name, Modifier.weight(1f))
                                        TextButton(enabled = index > 0, onClick = {
                                            val next = rules.toMutableList(); val item = next.removeAt(index); next.add(index - 1, item); rules = next; saveRules(next)
                                        }) { Text("↑") }
                                        TextButton(enabled = index < rules.lastIndex, onClick = {
                                            val next = rules.toMutableList(); val item = next.removeAt(index); next.add(index + 1, item); rules = next; saveRules(next)
                                        }) { Text("↓") }
                                    }
                                }
                            }
                            Button(onClick = { dialog = true }, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) { Text("Neue Aufgabe") }
                            Text("Erster nativer Teststand: tägliche Aufgaben und Sortierung. Wochenziele, Verlauf, Bearbeiten und Drag-and-drop folgen separat.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 22.dp))
                        }
                    }
                }
                if (dialog) AlertDialog(onDismissRequest = { dialog = false }, title = { Text("Neue tägliche Aufgabe") }, text = {
                    OutlinedTextField(name, onValueChange = { name = it.take(80) }, label = { Text("Bezeichnung") }, singleLine = true)
                }, confirmButton = {
                    TextButton(enabled = name.isNotBlank(), onClick = { rules = rules + Rule(UUID.randomUUID().toString(), name.trim()); saveRules(rules); name = ""; dialog = false }) { Text("Anlegen") }
                }, dismissButton = { TextButton(onClick = { dialog = false }) { Text("Abbrechen") } })
            }
        }
    }
}
