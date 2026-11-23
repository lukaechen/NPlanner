package at.lukaechen.nplanner

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/** Transitional, non-destructive mirror. SharedPreferences remains authoritative in v0.26. */
@Entity(tableName = "legacy_values")
data class LegacyValue(
    @PrimaryKey val key: String,
    val kind: String,
    val value: String
)

@Entity(tableName = "exercise_logs")
data class StoredExerciseLog(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val date: String,
    val workoutId: String,
    val weightKg: Float,
    val reps: Int,
    val seconds: Int,
    val distanceKm: Float
)

@Dao
interface NPlannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLegacy(value: LegacyValue)

    @Query("DELETE FROM legacy_values WHERE `key` = :key")
    suspend fun deleteLegacy(key: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLogs(logs: List<StoredExerciseLog>)

    @Query("DELETE FROM exercise_logs")
    suspend fun clearLogs()

    @androidx.room.Transaction
    suspend fun replaceLogs(logs: List<StoredExerciseLog>) {
        clearLogs()
        upsertLogs(logs)
    }
}

@Database(entities = [LegacyValue::class, StoredExerciseLog::class], version = 1, exportSchema = false)
abstract class NPlannerDatabase : RoomDatabase() {
    abstract fun dao(): NPlannerDao
}

/**
 * First step toward Room: migrate and mirror every preference, plus structured training logs.
 * No legacy keys are deleted. On errors the existing app data remains untouched.
 * A later version can atomically switch reads/writes to Room after restore testing.
 */
object NPlannerRoomBridge {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private val changes = Channel<String>(Channel.UNLIMITED)

    fun start(context: Context, prefs: SharedPreferences) {
        val db = Room.databaseBuilder(context.applicationContext, NPlannerDatabase::class.java, "nplanner.db").build()
        // The listener is held strongly; SharedPreferences otherwise keeps only weak references.
        listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null) changes.trySend(key)
        }
        prefs.registerOnSharedPreferenceChangeListener(listener!!)
        scope.launch {
            val dao = db.dao()
            // Initial import, then ordered changes. Never clear the original preferences.
            for (key in prefs.all.keys) syncKey(dao, prefs, key)
            for (key in changes) syncKey(dao, prefs, key)
        }
    }

    private suspend fun syncKey(dao: NPlannerDao, prefs: SharedPreferences, key: String) {
        try {
            val value = prefs.all[key]
            if (value == null) dao.deleteLegacy(key)
            else {
                val encoded = when (value) {
                    is Boolean -> LegacyValue(key, "bool", value.toString())
                    is Int -> LegacyValue(key, "int", value.toString())
                    is Long -> LegacyValue(key, "long", value.toString())
                    is Float -> LegacyValue(key, "float", value.toString())
                    is String -> LegacyValue(key, "string", value)
                    is Set<*> -> LegacyValue(key, "strings", JSONArray(value.toList()).toString())
                    else -> return
                }
                dao.upsertLegacy(encoded)
            }
            if (key == "exercise_logs_v1") {
                val raw = prefs.getString(key, "[]") ?: "[]"
                val array = JSONArray(raw)
                val logs = (0 until array.length()).map { i ->
                    val o = array.getJSONObject(i)
                    StoredExerciseLog(
                        id = o.getString("id"),
                        exerciseId = o.getString("exerciseId"),
                        date = o.getString("date"),
                        workoutId = o.optString("workoutId", ""),
                        weightKg = o.optDouble("weightKg", 0.0).toFloat(),
                        reps = o.optInt("reps", 0),
                        seconds = o.optInt("seconds", 0),
                        distanceKm = o.optDouble("distanceKm", 0.0).toFloat()
                    )
                }
                dao.replaceLogs(logs)
            }
        } catch (_: Exception) {
            // Keep legacy data intact; a later migration can retry safely.
        }
    }
}
