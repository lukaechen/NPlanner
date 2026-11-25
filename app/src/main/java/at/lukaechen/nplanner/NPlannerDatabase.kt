package at.lukaechen.nplanner

import android.content.Context
import android.content.SharedPreferences
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

@Entity(tableName = "tasks", indices = [Index("archived"), Index("kind")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: String,
    val timeMin: Int,
    val everyMin: Int,
    val fromMin: Int,
    val toMin: Int,
    val weeklyTarget: Int,
    val createdDate: String,
    val oneTimeDate: String,
    val archived: Boolean,
    val optional: Boolean,
    val sortOrder: Int
)

@Entity(
    tableName = "task_days",
    primaryKeys = ["taskId", "dayOfWeek"],
    indices = [Index("taskId")]
)
data class TaskDayEntity(val taskId: String, val dayOfWeek: Int)

@Entity(
    tableName = "task_completions",
    primaryKeys = ["taskId", "date"],
    indices = [Index("date")]
)
data class TaskCompletionEntity(val taskId: String, val date: String, val count: Int)

@Entity(tableName = "exercises", indices = [Index("kind"), Index("name")])
data class ExerciseEntity(
    @PrimaryKey val id: String,
    val name: String,
    val muscle: String,
    val primaryMuscleId: String,
    val secondaryMuscleIdsJson: String,
    val kind: String,
    val description: String,
    val stepsJson: String,
    val mediaPath: String,
    val mediaIsVideo: Boolean,
    val targetWeightKg: Double,
    val targetReps: Int,
    val targetSeconds: Int
)

@Entity(tableName = "workouts", indices = [Index("kind")])
data class WorkoutEntity(
    @PrimaryKey val id: String,
    val name: String,
    val note: String,
    val kind: String,
    val durationSeconds: Int,
    val amrapTargetsJson: String,
    val targetWeightsJson: String
)

@Entity(
    tableName = "workout_exercises",
    primaryKeys = ["workoutId", "exerciseId"],
    indices = [Index("workoutId"), Index("exerciseId")]
)
data class WorkoutExerciseEntity(val workoutId: String, val exerciseId: String, val position: Int)

@Entity(
    tableName = "exercise_sets",
    indices = [Index("sessionId"), Index("exerciseId"), Index("date")]
)
data class ExerciseSetEntity(
    @PrimaryKey val id: String,
    val exerciseId: String,
    val date: String,
    val weightKg: Double,
    val reps: Int,
    val seconds: Int,
    val workoutId: String,
    val distanceKm: Double,
    val sessionId: String
)

@Entity(tableName = "legacy_documents")
data class LegacyDocumentEntity(
    @PrimaryKey val key: String,
    val json: String,
    val importedAt: Long
)

@Entity(tableName = "migration_state")
data class MigrationStateEntity(@PrimaryKey val key: String, val value: String)

@Dao
interface NPlannerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTasks(items: List<TaskEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertTaskDays(items: List<TaskDayEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertCompletions(items: List<TaskCompletionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertExercises(items: List<ExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkouts(items: List<WorkoutEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertWorkoutExercises(items: List<WorkoutExerciseEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSets(items: List<ExerciseSetEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertLegacyDocument(item: LegacyDocumentEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putState(item: MigrationStateEntity)

    @Query("DELETE FROM tasks") suspend fun clearTasks()
    @Query("DELETE FROM task_days") suspend fun clearTaskDays()
    @Query("DELETE FROM exercises") suspend fun clearExercises()
    @Query("DELETE FROM workouts") suspend fun clearWorkouts()
    @Query("DELETE FROM workout_exercises") suspend fun clearWorkoutExercises()
    @Query("SELECT value FROM migration_state WHERE `key` = :key LIMIT 1") suspend fun state(key: String): String?
    @Query("SELECT COUNT(*) FROM tasks") suspend fun taskCount(): Int
    @Query("SELECT COUNT(*) FROM exercises") suspend fun exerciseCount(): Int
    @Query("SELECT COUNT(*) FROM exercise_sets") suspend fun setCount(): Int

    @Transaction
    suspend fun replaceTasks(tasks: List<TaskEntity>, days: List<TaskDayEntity>) {
        clearTaskDays()
        clearTasks()
        if (tasks.isNotEmpty()) upsertTasks(tasks)
        if (days.isNotEmpty()) upsertTaskDays(days)
    }

    @Transaction
    suspend fun replaceExercises(items: List<ExerciseEntity>) {
        clearExercises()
        if (items.isNotEmpty()) upsertExercises(items)
    }

    @Transaction
    suspend fun replaceWorkouts(items: List<WorkoutEntity>, links: List<WorkoutExerciseEntity>) {
        clearWorkoutExercises()
        clearWorkouts()
        if (items.isNotEmpty()) upsertWorkouts(items)
        if (links.isNotEmpty()) upsertWorkoutExercises(links)
    }
}

@Database(
    entities = [
        TaskEntity::class, TaskDayEntity::class, TaskCompletionEntity::class,
        ExerciseEntity::class, WorkoutEntity::class, WorkoutExerciseEntity::class,
        ExerciseSetEntity::class, LegacyDocumentEntity::class, MigrationStateEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class NPlannerDatabase : RoomDatabase() {
    abstract fun dao(): NPlannerDao

    companion object {
        @Volatile private var instance: NPlannerDatabase? = null
        fun get(context: Context): NPlannerDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                NPlannerDatabase::class.java,
                "nplanner_normalized.db"
            ).build().also { instance = it }
        }
    }
}

/**
 * Safe transition bridge for v0.48.
 *
 * SharedPreferences remains readable by the current UI while every relevant change is
 * normalized into Room. After one successful field test the UI can switch table by table
 * to Room and the JSON keys can be removed in a controlled follow-up migration.
 */
object NPlannerRoomBridge {
    private const val MIGRATION_KEY = "normalized_v2"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var started = false
    private var preferenceListener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    fun start(context: Context, prefs: SharedPreferences) {
        if (started) return
        started = true
        val dao = NPlannerDatabase.get(context).dao()
        scope.launch { importAll(prefs, dao) }
        preferenceListener = SharedPreferences.OnSharedPreferenceChangeListener { shared, key ->
            if (key == null) return@OnSharedPreferenceChangeListener
            scope.launch {
                when {
                    key == "rules" -> importTasks(shared, dao)
                    key == "exercises_v1" -> importExercises(shared, dao)
                    key == "workouts_v1" -> importWorkouts(shared, dao)
                    key == "exercise_logs_v1" -> importSets(shared, dao)
                    key.startsWith("done:") || key.startsWith("cnt:") -> importCompletionKey(shared, dao, key)
                    key.startsWith("snap:") || key.startsWith("workout_session:") ||
                        key.startsWith("amrap_session:") || key.startsWith("workout_template:") -> {
                        val value = shared.all[key]?.toString() ?: return@launch
                        dao.upsertLegacyDocument(LegacyDocumentEntity(key, value, System.currentTimeMillis()))
                    }
                }
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(preferenceListener)
    }

    private suspend fun importAll(prefs: SharedPreferences, dao: NPlannerDao) {
        importTasks(prefs, dao)
        importExercises(prefs, dao)
        importWorkouts(prefs, dao)
        importSets(prefs, dao)
        prefs.all.keys.filter { it.startsWith("done:") || it.startsWith("cnt:") }
            .forEach { importCompletionKey(prefs, dao, it) }
        prefs.all.forEach { (key, value) ->
            if (key.startsWith("snap:") || key.startsWith("workout_session:") ||
                key.startsWith("amrap_session:") || key.startsWith("workout_template:")) {
                dao.upsertLegacyDocument(LegacyDocumentEntity(key, value.toString(), System.currentTimeMillis()))
            }
        }
        dao.putState(MigrationStateEntity(MIGRATION_KEY, System.currentTimeMillis().toString()))
    }

    private suspend fun importTasks(prefs: SharedPreferences, dao: NPlannerDao) {
        val arr = runCatching { JSONArray(prefs.getString("rules", "[]")) }.getOrElse { JSONArray() }
        val tasks = mutableListOf<TaskEntity>()
        val days = mutableListOf<TaskDayEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            tasks += TaskEntity(
                id, o.optString("name"), o.optString("kind", "ALLDAY"),
                o.optInt("time", 480), o.optInt("every", 120),
                o.optInt("from", 480), o.optInt("to", 1200),
                o.optInt("weeklyTarget", 1).coerceIn(1, 14),
                o.optString("createdDate"), o.optString("oneTimeDate"),
                o.optBoolean("archived"), o.optBoolean("optional"), i
            )
            val dayArray = o.optJSONArray("days") ?: JSONArray()
            for (d in 0 until dayArray.length()) {
                dayArray.optInt(d).takeIf { it in 1..7 }?.let { days += TaskDayEntity(id, it) }
            }
        }
        dao.replaceTasks(tasks, days)
    }

    private suspend fun importExercises(prefs: SharedPreferences, dao: NPlannerDao) {
        val arr = runCatching { JSONArray(prefs.getString("exercises_v1", "[]")) }.getOrElse { JSONArray() }
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            o.optString("id").takeIf(String::isNotBlank)?.let { id ->
                ExerciseEntity(
                    id, o.optString("name"), o.optString("muscle"), o.optString("primaryMuscleId"),
                    (o.optJSONArray("secondaryMuscleIds") ?: JSONArray()).toString(),
                    o.optString("kind", "WEIGHT"), o.optString("description"),
                    (o.optJSONArray("steps") ?: JSONArray()).toString(), o.optString("mediaPath"),
                    o.optBoolean("mediaIsVideo"), o.optDouble("targetWeightKg", 0.0),
                    o.optInt("targetReps"), o.optInt("targetSeconds")
                )
            }
        }
        dao.replaceExercises(items)
    }

    private suspend fun importWorkouts(prefs: SharedPreferences, dao: NPlannerDao) {
        val arr = runCatching { JSONArray(prefs.getString("workouts_v1", "[]")) }.getOrElse { JSONArray() }
        val workouts = mutableListOf<WorkoutEntity>()
        val links = mutableListOf<WorkoutExerciseEntity>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val id = o.optString("id")
            if (id.isBlank()) continue
            workouts += WorkoutEntity(
                id, o.optString("name"), o.optString("note"), o.optString("kind", "CLASSIC"),
                o.optInt("durationSeconds"), (o.optJSONObject("amrapTargets") ?: JSONObject()).toString(),
                (o.optJSONObject("targetWeights") ?: JSONObject()).toString()
            )
            val ids = o.optJSONArray("exerciseIds") ?: JSONArray()
            for (position in 0 until ids.length()) {
                ids.optString(position).takeIf(String::isNotBlank)?.let {
                    links += WorkoutExerciseEntity(id, it, position)
                }
            }
        }
        dao.replaceWorkouts(workouts, links)
    }

    private suspend fun importSets(prefs: SharedPreferences, dao: NPlannerDao) {
        val arr = runCatching { JSONArray(prefs.getString("exercise_logs_v1", "[]")) }.getOrElse { JSONArray() }
        val items = (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            o.optString("id").takeIf(String::isNotBlank)?.let { id ->
                ExerciseSetEntity(
                    id, o.optString("exerciseId"), o.optString("date"),
                    o.optDouble("weightKg", 0.0), o.optInt("reps"), o.optInt("seconds"),
                    o.optString("workoutId"), o.optDouble("distanceKm", 0.0), o.optString("sessionId")
                )
            }
        }
        if (items.isNotEmpty()) dao.upsertSets(items)
    }

    private suspend fun importCompletionKey(prefs: SharedPreferences, dao: NPlannerDao, key: String) {
        val parts = key.split(':')
        if (parts.size < 3) return
        val date = parts[1]
        val taskId = parts.drop(2).joinToString(":")
        val count = if (key.startsWith("done:")) {
            if (prefs.getBoolean(key, false)) 1 else 0
        } else prefs.getInt(key, 0)
        dao.upsertCompletions(listOf(TaskCompletionEntity(taskId, date, count)))
    }
}
