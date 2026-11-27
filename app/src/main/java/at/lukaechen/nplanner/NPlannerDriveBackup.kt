package at.lukaechen.nplanner

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Process
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.Scopes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

private const val DRIVE_SCOPE="https://www.googleapis.com/auth/drive.appdata"
private const val BACKUP_NAME="nplanner-backup-v1.zip"
private const val PREFS="nplanner-drive-backup"
private const val KEY_ENABLED="enabled"
private const val KEY_LAST_SYNC="last_sync"
private const val KEY_LAST_ERROR="last_error"
private const val PERIODIC_WORK="nplanner-drive-periodic-backup"
private const val DB_NAME="nplanner_room_v1.db"

private val BackupPanel=Color(0xFF191919)
private val BackupPanel2=Color(0xFF252525)
private val BackupWhite=Color(0xFFE8E7E2)
private val BackupMuted=Color(0xFF929292)
private val BackupRed=Color(0xFFE03930)
private val BackupBg=Color(0xFF080808)

data class DriveBackupState(val account:String="",val enabled:Boolean=false,val lastSync:Long=0,val error:String="")

object NPlannerDriveBackup {
    fun state(context:Context):DriveBackupState {
        val p=context.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
        return DriveBackupState(GoogleSignIn.getLastSignedInAccount(context)?.email.orEmpty(),p.getBoolean(KEY_ENABLED,false),p.getLong(KEY_LAST_SYNC,0),p.getString(KEY_LAST_ERROR,"").orEmpty())
    }
    fun signInIntent(context:Context):Intent {
        val options=GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail().requestScopes(Scope(DRIVE_SCOPE)).build()
        return GoogleSignIn.getClient(context,options).signInIntent
    }
    fun setEnabled(context:Context,enabled:Boolean){
        context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putBoolean(KEY_ENABLED,enabled).apply()
        if(enabled) schedule(context) else WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
    }
    fun schedule(context:Context){
        val constraints=Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request=PeriodicWorkRequestBuilder<NPlannerDriveBackupWorker>(24,TimeUnit.HOURS)
            .setConstraints(constraints).setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.MINUTES).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(PERIODIC_WORK,ExistingPeriodicWorkPolicy.UPDATE,request)
    }
    fun enqueueNow(context:Context){
        val request=OneTimeWorkRequestBuilder<NPlannerDriveBackupWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
        WorkManager.getInstance(context).enqueueUniqueWork("nplanner-drive-manual",ExistingWorkPolicy.REPLACE,request)
    }
    suspend fun backup(context:Context,account:GoogleSignInAccount?=GoogleSignIn.getLastSignedInAccount(context)):Result<String> = withContext(Dispatchers.IO){
        runCatching{
            val a=account?.account?:error("Kein Google-Konto verbunden")
            val token=token(context,a)
            val zip=createBackup(context)
            val existing=findBackup(token)
            if(existing==null) createRemote(token,zip) else updateRemote(token,existing,zip)
            zip.delete()
            context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putLong(KEY_LAST_SYNC,System.currentTimeMillis()).remove(KEY_LAST_ERROR).apply()
            "OK"
        }.onFailure{context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().putString(KEY_LAST_ERROR,it.message?:it.javaClass.simpleName).apply()}
    }
    suspend fun restore(context:Context):Result<Unit> = withContext(Dispatchers.IO){runCatching{
        val account=GoogleSignIn.getLastSignedInAccount(context)?.account?:error("Kein Google-Konto verbunden")
        val token=token(context,account);val id=findBackup(token)?:error("Kein Backup in Google Drive gefunden")
        val response=request("https://www.googleapis.com/drive/v3/files/$id?alt=media","GET",token)
        val zip=File(context.cacheDir,"restore-download.zip").apply{writeBytes(response)}
        val target=File(context.filesDir,"nplanner-restore").apply{mkdirs()};target.listFiles()?.forEach{it.delete()}
        ZipInputStream(zip.inputStream().buffered()).use{zin->var entry=zin.nextEntry;while(entry!=null){
            val out=File(target,entry.name);require(out.canonicalPath.startsWith(target.canonicalPath));if(!entry.isDirectory){out.parentFile?.mkdirs();out.outputStream().use{zin.copyTo(it)}};entry=zin.nextEntry}}
        val db=File(target,DB_NAME);require(db.isFile&&db.length()>0){"Backup enthält keine gültige Datenbank"}
        File(context.filesDir,"pending-nplanner-restore.db").also{db.copyTo(it,true)}
        val prefsDir=File(target,"shared_prefs");if(prefsDir.isDirectory) prefsDir.copyRecursively(File(context.filesDir,"pending-shared-prefs"),true)
        zip.delete()
    }}
    fun applyPendingRestore(context:Context):Boolean{
        val pending=File(context.filesDir,"pending-nplanner-restore.db");if(!pending.exists())return false
        NPlannerDatabase.closeInstance();NPlannerStore.reset()
        val target=context.getDatabasePath(DB_NAME);target.parentFile?.mkdirs();listOf(target,File(target.path+"-wal"),File(target.path+"-shm")).forEach{it.delete()};pending.copyTo(target,true);pending.delete()
        val pendingPrefs=File(context.filesDir,"pending-shared-prefs");if(pendingPrefs.isDirectory){val targetPrefs=File(context.applicationInfo.dataDir,"shared_prefs");targetPrefs.mkdirs();pendingPrefs.listFiles()?.forEach{it.copyTo(File(targetPrefs,it.name),true)};pendingPrefs.deleteRecursively()}
        return true
    }
    fun restart(context:Context){val i=context.packageManager.getLaunchIntentForPackage(context.packageName)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK);if(i!=null)context.startActivity(i);Process.killProcess(Process.myPid())}
    fun disconnect(context:Context,onDone:()->Unit){GoogleSignIn.getClient(context,GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestEmail().requestScopes(Scope(DRIVE_SCOPE)).build()).signOut().addOnCompleteListener{setEnabled(context,false);context.getSharedPreferences(PREFS,Context.MODE_PRIVATE).edit().clear().apply();onDone()}}
    private fun token(context:Context,account:Account)=GoogleAuthUtil.getToken(context,account,"oauth2:$DRIVE_SCOPE")
    private fun createBackup(context:Context):File{
        NPlannerDatabase.get(context).openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use{}
        val out=File(context.cacheDir,BACKUP_NAME)
        ZipOutputStream(out.outputStream().buffered()).use{zip->
            zip.putNextEntry(ZipEntry("metadata.json"));zip.write(JSONObject().put("format","nplanner-backup").put("version",1).put("createdAt",System.currentTimeMillis()).put("package",context.packageName).toString().toByteArray());zip.closeEntry()
            val db=context.getDatabasePath(DB_NAME);require(db.isFile){"NPlanner-Datenbank nicht gefunden"};zip.putNextEntry(ZipEntry(DB_NAME));db.inputStream().use{it.copyTo(zip)};zip.closeEntry()
            val prefsDir=File(context.applicationInfo.dataDir,"shared_prefs");prefsDir.listFiles()?.filter{it.extension=="xml"&&it.nameWithoutExtension!=PREFS}?.forEach{f->zip.putNextEntry(ZipEntry("shared_prefs/${f.name}"));f.inputStream().use{it.copyTo(zip)};zip.closeEntry()}
        };return out
    }
    private fun findBackup(token:String):String?{val q=java.net.URLEncoder.encode("name='$BACKUP_NAME' and trashed=false","UTF-8");val json=JSONObject(String(request("https://www.googleapis.com/drive/v3/files?spaces=appDataFolder&q=$q&fields=files(id,name)&pageSize=1","GET",token)));return json.optJSONArray("files")?.optJSONObject(0)?.optString("id")?.takeIf{it.isNotBlank()}}
    private fun createRemote(token:String,file:File){val boundary="nplanner-${System.currentTimeMillis()}";val meta=JSONObject().put("name",BACKUP_NAME).put("parents",org.json.JSONArray().put("appDataFolder")).toString();val body=ByteArrayOutputStream();body.write("--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$meta\r\n--$boundary\r\nContent-Type: application/zip\r\n\r\n".toByteArray());body.write(file.readBytes());body.write("\r\n--$boundary--".toByteArray());request("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart","POST",token,body.toByteArray(),"multipart/related; boundary=$boundary")}
    private fun updateRemote(token:String,id:String,file:File){request("https://www.googleapis.com/upload/drive/v3/files/$id?uploadType=media","PATCH",token,file.readBytes(),"application/zip")}
    private fun request(url:String,method:String,token:String,body:ByteArray?=null,contentType:String?=null):ByteArray{val c=URL(url).openConnection() as HttpURLConnection;c.requestMethod=method;c.setRequestProperty("Authorization","Bearer $token");c.connectTimeout=20000;c.readTimeout=30000;if(body!=null){c.doOutput=true;c.setRequestProperty("Content-Type",contentType);c.outputStream.use{it.write(body)}};val code=c.responseCode;val bytes=(if(code in 200..299)c.inputStream else c.errorStream).use{it?.readBytes()?:byteArrayOf()};if(code !in 200..299)error("Google Drive HTTP $code: ${String(bytes).take(300)}");return bytes}
}

class NPlannerDriveBackupWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params){override suspend fun doWork():Result{val s=NPlannerDriveBackup.state(applicationContext);if(!s.enabled)return Result.success();return NPlannerDriveBackup.backup(applicationContext).fold({Result.success()},{if(runAttemptCount<4)Result.retry()else Result.failure()})}}

@Composable
fun NPlannerDriveBackupSettings(){
    val context=LocalContext.current;val scope=rememberCoroutineScope();var state by remember{mutableStateOf(NPlannerDriveBackup.state(context))};var busy by remember{mutableStateOf(false)};var message by remember{mutableStateOf("")};var confirmRestore by remember{mutableStateOf(false)}
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){result->
        if(result.resultCode==Activity.RESULT_OK)runCatching{GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(ApiException::class.java)}.onSuccess{state=NPlannerDriveBackup.state(context);message="Google-Konto verbunden"}.onFailure{message="Verbindung fehlgeschlagen: ${it.message}"}
    }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)){
        Text("GOOGLE DRIVE",color=BackupMuted,fontSize=10.sp,letterSpacing=1.6.sp)
        Surface(color=BackupPanel,shape=RoundedCornerShape(22.dp),modifier=Modifier.fillMaxWidth()){
            Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
                Row(verticalAlignment=Alignment.CenterVertically){Box(Modifier.size(9.dp).background(if(state.account.isNotBlank())Color(0xFF79C66A)else BackupMuted,CircleShape));Spacer(Modifier.width(10.dp));Column(Modifier.weight(1f)){Text(if(state.account.isBlank())"Kein Google-Konto verbunden" else state.account,color=BackupWhite,fontSize=14.sp);Text("Sicherung im privaten App-Datenordner",color=BackupMuted,fontSize=10.sp)}}
                Button(onClick={if(state.account.isBlank())launcher.launch(NPlannerDriveBackup.signInIntent(context))else NPlannerDriveBackup.disconnect(context){state=NPlannerDriveBackup.state(context)}},colors=ButtonDefaults.buttonColors(containerColor=BackupPanel2,contentColor=BackupWhite),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text(if(state.account.isBlank())"GOOGLE-KONTO VERBINDEN" else "KONTO TRENNEN",fontSize=11.sp)}
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("Automatische Sicherung",color=BackupWhite,fontSize=14.sp);Text("Einmal täglich bei Internetverbindung",color=BackupMuted,fontSize=10.sp)};Switch(checked=state.enabled,onCheckedChange={enabled->if(state.account.isBlank())launcher.launch(NPlannerDriveBackup.signInIntent(context))else{NPlannerDriveBackup.setEnabled(context,enabled);state=NPlannerDriveBackup.state(context)}})}
                val last=if(state.lastSync==0L)"Noch keine Sicherung"else DateFormat.getDateTimeInstance(DateFormat.MEDIUM,DateFormat.SHORT).format(Date(state.lastSync));Text("Letzter Sync: $last",color=BackupMuted,fontSize=11.sp)
                Button(enabled=!busy&&state.account.isNotBlank(),onClick={busy=true;scope.launch{val r=NPlannerDriveBackup.backup(context);message=if(r.isSuccess)"Backup erfolgreich gespeichert"else"Backup fehlgeschlagen: ${r.exceptionOrNull()?.message}";state=NPlannerDriveBackup.state(context);busy=false}},colors=ButtonDefaults.buttonColors(containerColor=BackupWhite,contentColor=BackupBg),shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth()){Text(if(busy)"SICHERUNG LÄUFT …"else"JETZT SICHERN",fontSize=11.sp)}
                TextButton(enabled=!busy&&state.account.isNotBlank(),onClick={confirmRestore=true},modifier=Modifier.fillMaxWidth()){Text("BACKUP WIEDERHERSTELLEN",color=BackupWhite,fontSize=11.sp)}
                if(message.isNotBlank())Text(message,color=if(message.contains("fehl",true))BackupRed else BackupMuted,fontSize=11.sp)
                if(state.error.isNotBlank()&&message.isBlank())Text("Letzter Fehler: ${state.error}",color=BackupRed,fontSize=10.sp)
            }
        }
    }
    if(confirmRestore)AlertDialog(onDismissRequest={confirmRestore=false},containerColor=BackupPanel,title={Text("Backup wiederherstellen",color=BackupWhite)},text={Text("Die lokalen NPlanner-Daten werden durch das letzte Drive-Backup ersetzt. Die App startet danach neu.",color=BackupMuted)},confirmButton={TextButton(onClick={confirmRestore=false;busy=true;scope.launch{val r=NPlannerDriveBackup.restore(context);if(r.isSuccess)NPlannerDriveBackup.restart(context)else{message="Wiederherstellung fehlgeschlagen: ${r.exceptionOrNull()?.message}";busy=false}}}){Text("WIEDERHERSTELLEN",color=BackupRed)}},dismissButton={TextButton(onClick={confirmRestore=false}){Text("ABBRECHEN",color=BackupWhite)}})
}
