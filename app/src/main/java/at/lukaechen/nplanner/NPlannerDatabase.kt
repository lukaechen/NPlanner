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
import androidx.room.Transaction
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.CopyOnWriteArraySet

@Entity(tableName = "tasks")
data class DbTask(@PrimaryKey val id: String, val name: String, val kind: String, val timeMin: Int, val everyMin: Int, val fromMin: Int, val toMin: Int, val weeklyTarget: Int, val createdDate: String, val oneTimeDate: String, val archived: Boolean, val optional: Boolean, val sortOrder: Int)
@Entity(tableName = "task_days", primaryKeys = ["taskId", "day"])
data class DbTaskDay(val taskId: String, val day: Int)
@Entity(tableName = "exercises")
data class DbExercise(@PrimaryKey val id: String, val name: String, val muscle: String, val primaryMuscleId: String, val secondaryJson: String, val kind: String, val description: String, val stepsJson: String, val mediaPath: String, val mediaIsVideo: Boolean, val targetWeightKg: Double, val targetReps: Int, val targetSeconds: Int)
@Entity(tableName = "workouts")
data class DbWorkout(@PrimaryKey val id: String, val name: String, val note: String, val kind: String, val durationSeconds: Int, val amrapTargetsJson: String, val targetWeightsJson: String, val sortOrder: Int)
@Entity(tableName = "workout_exercises", primaryKeys = ["workoutId", "exerciseId"])
data class DbWorkoutExercise(val workoutId: String, val exerciseId: String, val position: Int)
@Entity(tableName = "exercise_sets")
data class DbExerciseSet(@PrimaryKey val id: String, val exerciseId: String, val date: String, val weightKg: Double, val reps: Int, val seconds: Int, val workoutId: String, val distanceKm: Double, val sessionId: String)
@Entity(tableName = "task_values", primaryKeys = ["key"])
data class DbTaskValue(val key: String, val intValue: Int, val boolValue: Boolean, val stringValue: String)
@Entity(tableName = "records")
data class DbRecord(@PrimaryKey val key: String, val payload: String)
@Entity(tableName = "settings")
data class DbSetting(@PrimaryKey val key: String, val type: String, val value: String)

@Dao
interface NPlannerDao {
    @Query("SELECT * FROM tasks ORDER BY sortOrder") fun tasks(): List<DbTask>
    @Query("SELECT * FROM task_days") fun taskDays(): List<DbTaskDay>
    @Query("DELETE FROM task_days") fun clearTaskDays()
    @Query("DELETE FROM tasks") fun clearTasks()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putTasks(v: List<DbTask>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putTaskDays(v: List<DbTaskDay>)
    @Transaction fun replaceTasks(t: List<DbTask>, d: List<DbTaskDay>) { clearTaskDays(); clearTasks(); if (t.isNotEmpty()) putTasks(t); if (d.isNotEmpty()) putTaskDays(d) }

    @Query("SELECT * FROM exercises ORDER BY name COLLATE NOCASE") fun exercises(): List<DbExercise>
    @Query("DELETE FROM exercises") fun clearExercises()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putExercises(v: List<DbExercise>)
    @Transaction fun replaceExercises(v: List<DbExercise>) { clearExercises(); if (v.isNotEmpty()) putExercises(v) }

    @Query("SELECT * FROM workouts ORDER BY sortOrder") fun workouts(): List<DbWorkout>
    @Query("SELECT * FROM workout_exercises ORDER BY position") fun workoutExercises(): List<DbWorkoutExercise>
    @Query("DELETE FROM workout_exercises") fun clearWorkoutExercises()
    @Query("DELETE FROM workouts") fun clearWorkouts()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putWorkouts(v: List<DbWorkout>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putWorkoutExercises(v: List<DbWorkoutExercise>)
    @Transaction fun replaceWorkouts(w: List<DbWorkout>, x: List<DbWorkoutExercise>) { clearWorkoutExercises(); clearWorkouts(); if (w.isNotEmpty()) putWorkouts(w); if (x.isNotEmpty()) putWorkoutExercises(x) }

    @Query("SELECT * FROM exercise_sets ORDER BY date, id") fun sets(): List<DbExerciseSet>
    @Query("DELETE FROM exercise_sets") fun clearSets()
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putSets(v: List<DbExerciseSet>)
    @Transaction fun replaceSets(v: List<DbExerciseSet>) { clearSets(); if (v.isNotEmpty()) putSets(v) }

    @Query("SELECT * FROM task_values WHERE `key`=:key LIMIT 1") fun taskValue(key: String): DbTaskValue?
    @Query("SELECT * FROM task_values") fun taskValues(): List<DbTaskValue>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putTaskValue(v: DbTaskValue)
    @Query("DELETE FROM task_values WHERE `key`=:key") fun removeTaskValue(key: String)

    @Query("SELECT * FROM records WHERE `key`=:key LIMIT 1") fun record(key: String): DbRecord?
    @Query("SELECT * FROM records") fun records(): List<DbRecord>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putRecord(v: DbRecord)
    @Query("DELETE FROM records WHERE `key`=:key") fun removeRecord(key: String)

    @Query("SELECT * FROM settings WHERE `key`=:key LIMIT 1") fun setting(key: String): DbSetting?
    @Query("SELECT * FROM settings") fun settings(): List<DbSetting>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putSetting(v: DbSetting)
    @Query("DELETE FROM settings WHERE `key`=:key") fun removeSetting(key: String)

    @Query("DELETE FROM tasks") fun wipeTasks(); @Query("DELETE FROM task_days") fun wipeTaskDays()
    @Query("DELETE FROM exercises") fun wipeExercises(); @Query("DELETE FROM workouts") fun wipeWorkouts()
    @Query("DELETE FROM workout_exercises") fun wipeWorkoutExercises(); @Query("DELETE FROM exercise_sets") fun wipeSets()
    @Query("DELETE FROM task_values") fun wipeTaskValues(); @Query("DELETE FROM records") fun wipeRecords(); @Query("DELETE FROM settings") fun wipeSettings()
    @Transaction fun wipeAll() { wipeTaskDays(); wipeTasks(); wipeExercises(); wipeWorkoutExercises(); wipeWorkouts(); wipeSets(); wipeTaskValues(); wipeRecords(); wipeSettings() }
}

@Database(entities=[DbTask::class,DbTaskDay::class,DbExercise::class,DbWorkout::class,DbWorkoutExercise::class,DbExerciseSet::class,DbTaskValue::class,DbRecord::class,DbSetting::class], version=1, exportSchema=false)
abstract class NPlannerDatabase: RoomDatabase() {
    abstract fun dao(): NPlannerDao
    companion object {
        @Volatile private var instance: NPlannerDatabase?=null
        fun get(context: Context)=instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext,NPlannerDatabase::class.java,"nplanner_room_v1.db")
                .allowMainThreadQueries().build().also{instance=it}
        }
        fun closeInstance() = synchronized(this) { instance?.close(); instance = null }
    }
}

/** Room is the only persistent store for NPlanner user data. JSON is generated only in memory
 * to keep the established UI code compatible; large arrays are never stored as JSON blobs. */
class NPlannerPreferences private constructor(private val dao: NPlannerDao): SharedPreferences {
    private val listeners=CopyOnWriteArraySet<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String,Any>().apply {
        dao.settings().forEach { put(it.key, decode(it)) }; dao.taskValues().forEach { put(it.key, when { it.stringValue.isNotEmpty()->it.stringValue; it.boolValue->true; else->it.intValue }) }; dao.records().forEach{put(it.key,it.payload)}
        put("rules",rulesJson());put("exercises_v1",exercisesJson());put("workouts_v1",workoutsJson());put("exercise_logs_v1",setsJson())
    }
    private fun decode(s:DbSetting):Any=when(s.type){"b"->s.value.toBoolean();"i"->s.value.toInt();"l"->s.value.toLong();"f"->s.value.toFloat();"ss"->if(s.value.isBlank()) emptySet<String>() else s.value.split('\u001f').toSet();else->s.value}
    private fun special(key:String)=key in setOf("rules","exercises_v1","workouts_v1","exercise_logs_v1")
    override fun getString(key:String,def:String?):String?=when(key){
        "rules" -> if (dao.tasks().isEmpty()) def else rulesJson()
        "exercises_v1" -> if (dao.exercises().isEmpty()) def else exercisesJson()
        "workouts_v1" -> if (dao.workouts().isNotEmpty() || dao.setting("workouts_initialized")?.value == "true") workoutsJson() else def
        "exercise_logs_v1" -> if (dao.sets().isEmpty()) def else setsJson()
        else -> dao.record(key)?.payload ?: dao.setting(key)?.value ?: dao.taskValue(key)?.stringValue?.takeIf{it.isNotEmpty()} ?: def
    }
    override fun getStringSet(key:String,def:Set<String>?):Set<String>?=(dao.setting(key)?.takeIf{it.type=="ss"}?.let{ if(it.value.isBlank()) mutableSetOf() else it.value.split('\u001f').toMutableSet() }?:def)
    override fun getInt(key:String,def:Int)=dao.taskValue(key)?.intValue?:dao.setting(key)?.value?.toIntOrNull()?:def
    override fun getLong(key:String,def:Long)=dao.setting(key)?.value?.toLongOrNull()?:def
    override fun getFloat(key:String,def:Float)=dao.setting(key)?.value?.toFloatOrNull()?:def
    override fun getBoolean(key:String,def:Boolean)=dao.taskValue(key)?.let{it.boolValue||it.intValue>0}?:dao.setting(key)?.value?.toBooleanStrictOrNull()?:def
    override fun contains(key:String)=when(key){
        "rules" -> dao.tasks().isNotEmpty()
        "exercises_v1" -> dao.exercises().isNotEmpty()
        "workouts_v1" -> dao.workouts().isNotEmpty() || dao.setting("workouts_initialized")?.value == "true"
        "exercise_logs_v1" -> dao.sets().isNotEmpty()
        else -> dao.setting(key)!=null || dao.taskValue(key)!=null || dao.record(key)!=null
    }
    override fun edit():SharedPreferences.Editor=EditorImpl()
    override fun registerOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners+=l}
    override fun unregisterOnSharedPreferenceChangeListener(l:SharedPreferences.OnSharedPreferenceChangeListener){listeners-=l}
    private fun notifyKey(k:String)=listeners.forEach{it.onSharedPreferenceChanged(this,k)}

    private inner class EditorImpl:SharedPreferences.Editor{
        private val values=linkedMapOf<String,Any?>();private var clear=false
        override fun putString(k:String,v:String?)=apply{values[k]=v}; override fun putStringSet(k:String,v:Set<String>?)=apply{values[k]=v}
        override fun putInt(k:String,v:Int)=apply{values[k]=v};override fun putLong(k:String,v:Long)=apply{values[k]=v};override fun putFloat(k:String,v:Float)=apply{values[k]=v};override fun putBoolean(k:String,v:Boolean)=apply{values[k]=v};override fun remove(k:String)=apply{values[k]=null};override fun clear()=apply{clear=true}
        override fun commit():Boolean=runCatching{applyNow();true}.getOrDefault(false);override fun apply(){applyNow()}
        private fun applyNow(){if(clear)dao.wipeAll();values.forEach{(k,v)->if(v==null){dao.removeSetting(k);dao.removeTaskValue(k);dao.removeRecord(k)}else when(k){"rules"->writeRules(v.toString());"exercises_v1"->writeExercises(v.toString());"workouts_v1"->writeWorkouts(v.toString());"exercise_logs_v1"->writeSets(v.toString());else->writeValue(k,v)}};values.keys.forEach(::notifyKey)}
    }
    private fun writeValue(k:String,v:Any){when{ k.startsWith("done:")||k.startsWith("cnt:")||k.startsWith("wk:")||k.startsWith("wk_cnt:")||k.startsWith("one_time_done_date:")->dao.putTaskValue(DbTaskValue(k,(v as? Number)?.toInt()?:0,v as? Boolean?:false,v as? String?:"")); k.startsWith("snap:")||k.startsWith("workout_session:")||k.startsWith("amrap_session:")||k.startsWith("workout_template:")->dao.putRecord(DbRecord(k,v.toString()));else->{val (t,x)=when(v){is Boolean->"b" to v.toString();is Int->"i" to v.toString();is Long->"l" to v.toString();is Float->"f" to v.toString();is Set<*>->"ss" to v.joinToString("\u001f");else->"s" to v.toString()};dao.putSetting(DbSetting(k,t,x))}}}

    private fun writeRules(raw:String){val a=runCatching{JSONArray(raw)}.getOrElse{JSONArray()};val t=mutableListOf<DbTask>();val d=mutableListOf<DbTaskDay>();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.optString("id");if(id.isBlank())continue;t+=DbTask(id,o.optString("name"),o.optString("kind","ALLDAY"),o.optInt("time",480),o.optInt("every",120),o.optInt("from",480),o.optInt("to",1200),o.optInt("weeklyTarget",1),o.optString("createdDate"),o.optString("oneTimeDate"),o.optBoolean("archived"),o.optBoolean("optional"),i);val x=o.optJSONArray("days")?:JSONArray();for(j in 0 until x.length())x.optInt(j).takeIf{it in 1..7}?.let{d+=DbTaskDay(id,it)}};dao.replaceTasks(t,d)}
    private fun rulesJson():String{val days=dao.taskDays().groupBy{it.taskId};return JSONArray().also{a->dao.tasks().forEach{r->a.put(JSONObject().put("id",r.id).put("name",r.name).put("days",JSONArray(days[r.id].orEmpty().map{it.day})).put("kind",r.kind).put("time",r.timeMin).put("every",r.everyMin).put("from",r.fromMin).put("to",r.toMin).put("weeklyTarget",r.weeklyTarget).put("createdDate",r.createdDate).put("oneTimeDate",r.oneTimeDate).put("archived",r.archived).put("optional",r.optional))}}.toString()}
    private fun writeExercises(raw:String){val a=runCatching{JSONArray(raw)}.getOrElse{JSONArray()};dao.replaceExercises((0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val id=o.optString("id");if(id.isBlank())null else DbExercise(id,o.optString("name"),o.optString("muscle"),o.optString("primaryMuscleId"),(o.optJSONArray("secondaryMuscleIds")?:JSONArray()).toString(),o.optString("kind","WEIGHT"),o.optString("description"),(o.optJSONArray("steps")?:JSONArray()).toString(),o.optString("mediaPath"),o.optBoolean("mediaIsVideo"),o.optDouble("targetWeightKg"),o.optInt("targetReps"),o.optInt("targetSeconds"))})}
    private fun exercisesJson()=JSONArray().also{a->dao.exercises().forEach{e->a.put(JSONObject().put("id",e.id).put("name",e.name).put("muscle",e.muscle).put("primaryMuscleId",e.primaryMuscleId).put("secondaryMuscleIds",JSONArray(e.secondaryJson)).put("kind",e.kind).put("description",e.description).put("steps",JSONArray(e.stepsJson)).put("mediaPath",e.mediaPath).put("mediaIsVideo",e.mediaIsVideo).put("targetWeightKg",e.targetWeightKg).put("targetReps",e.targetReps).put("targetSeconds",e.targetSeconds))}}.toString()
    private fun writeWorkouts(raw:String){val a=runCatching{JSONArray(raw)}.getOrElse{JSONArray()};val w=mutableListOf<DbWorkout>();val x=mutableListOf<DbWorkoutExercise>();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.optString("id");if(id.isBlank())continue;w+=DbWorkout(id,o.optString("name"),o.optString("note"),o.optString("kind","CLASSIC"),o.optInt("durationSeconds"),(o.optJSONObject("amrapTargets")?:JSONObject()).toString(),(o.optJSONObject("targetWeights")?:JSONObject()).toString(),i);val ids=o.optJSONArray("exerciseIds")?:JSONArray();for(j in 0 until ids.length())x+=DbWorkoutExercise(id,ids.optString(j),j)};dao.replaceWorkouts(w,x);dao.putSetting(DbSetting("workouts_initialized","b","true"))}
    private fun workoutsJson():String{val links=dao.workoutExercises().groupBy{it.workoutId};return JSONArray().also{a->dao.workouts().forEach{w->a.put(JSONObject().put("id",w.id).put("name",w.name).put("note",w.note).put("exerciseIds",JSONArray(links[w.id].orEmpty().sortedBy{it.position}.map{it.exerciseId})).put("kind",w.kind).put("durationSeconds",w.durationSeconds).put("amrapTargets",JSONObject(w.amrapTargetsJson)).put("targetWeights",JSONObject(w.targetWeightsJson)))}}.toString()}
    private fun writeSets(raw:String){val a=runCatching{JSONArray(raw)}.getOrElse{JSONArray()};dao.replaceSets((0 until a.length()).mapNotNull{i->val o=a.optJSONObject(i)?:return@mapNotNull null;val id=o.optString("id");if(id.isBlank())null else DbExerciseSet(id,o.optString("exerciseId"),o.optString("date"),o.optDouble("weightKg"),o.optInt("reps"),o.optInt("seconds"),o.optString("workoutId"),o.optDouble("distanceKm"),o.optString("sessionId"))})}
    private fun setsJson()=JSONArray().also{a->dao.sets().forEach{s->a.put(JSONObject().put("id",s.id).put("exerciseId",s.exerciseId).put("date",s.date).put("weightKg",s.weightKg).put("reps",s.reps).put("seconds",s.seconds).put("workoutId",s.workoutId).put("distanceKm",s.distanceKm).put("sessionId",s.sessionId))}}.toString()

    companion object{
        @Volatile private var instance:NPlannerPreferences?=null
        fun get(context:Context)=instance?:synchronized(this){instance?:NPlannerPreferences(NPlannerDatabase.get(context).dao()).also{instance=it}}
        fun reset() = synchronized(this) { instance = null }
    }
}

object NPlannerStore {
    fun get(context:Context):SharedPreferences=NPlannerPreferences.get(context)
    fun reset()=NPlannerPreferences.reset()
}
