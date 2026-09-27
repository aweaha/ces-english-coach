package com.ces.englishcoach

import android.app.*
import android.content.*
import android.net.Uri
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.nl.translate.*
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

class TranscriptionService : Service() {
    companion object {
        const val EXTRA_NAMES="names"
        const val EXTRA_URIS="uris"
        const val EXTRA_KEYS="keys"
        private const val CHANNEL="ces_ai"
        private const val NOTI=77
        private const val MODEL_URL="https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.en.bin"
    }
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onBind(intent: Intent?): IBinder?=null

    override fun onCreate(){
        super.onCreate()
        if(android.os.Build.VERSION.SDK_INT>=26){
            val ch=NotificationChannel(CHANNEL,"MP3 AI 대본 분석",NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
    private fun notification(text:String)=NotificationCompat.Builder(this,CHANNEL)
        .setSmallIcon(android.R.drawable.ic_media_play)
        .setContentTitle("CES English Coach")
        .setContentText(text).setOngoing(true).build()

    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int{
        startForeground(NOTI,notification("AI 대본 분석 준비 중"))
        val names=intent?.getStringArrayListExtra(EXTRA_NAMES)?: arrayListOf()
        val uris=intent?.getStringArrayListExtra(EXTRA_URIS)?: arrayListOf()
        val keys=intent?.getStringArrayListExtra(EXTRA_KEYS)?: arrayListOf()
        scope.launch {
            try { analyze(names,uris,keys) }
            catch(e:Exception){ prefs().edit().putString("analysis_message","오류: "+(e.message?:"알 수 없음")).apply() }
            finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(startId) }
        }
        return START_NOT_STICKY
    }

    private suspend fun analyze(names:ArrayList<String>,uris:ArrayList<String>,keys:ArrayList<String>){
        if(names.isEmpty())return
        val model=ensureModel()
        val whisper=Whisper.loadModel(this,model.absolutePath)
        val translator=Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.KOREAN).build())
        try{
            Tasks.await(translator.downloadModelIfNeeded())
            for(i in names.indices){
                val key=keys[i]
                if(ScriptStore.has(this,key))continue
                val name=names[i]
                update("분석 중 \${i+1}/\${names.size} · \$name")
                val audio=copyAudio(Uri.parse(uris[i]),key)
                val result=Whisper.transcribe(whisper,audio.absolutePath,WhisperConfig(language="en"))
                val arr=JSONArray()
                var speaker="A"
                var prevEnd=-1L
                for(seg in result.segments){
                    val en=seg.text.trim()
                    if(en.isBlank())continue
                    if(prevEnd>=0 && seg.startMs-prevEnd>=450)speaker=if(speaker=="A")"B" else "A"
                    val cleaned=en.replace(Regex("^[ABab]\\\\s*[:\\\\-]\\\\s*"),"").trim()
                    if(en.matches(Regex("^\\\\s*[Aa]\\\\s*[:\\\\-].*")))speaker="A"
                    if(en.matches(Regex("^\\\\s*[Bb]\\\\s*[:\\\\-].*")))speaker="B"
                    val ko=try{Tasks.await(translator.translate(cleaned))}catch(_:Exception){""}
                    arr.put(JSONObject().put("speaker",speaker).put("en",cleaned).put("ko",ko)
                        .put("startMs",seg.startMs).put("endMs",seg.endMs))
                    prevEnd=seg.endMs
                }
                val root=JSONObject().put("name",name).put("sourceUri",uris[i]).put("lines",arr)
                ScriptStore.fileFor(this,key).writeText(root.toString(2),Charsets.UTF_8)
                audio.delete()
                prefs().edit().putString("analysis_message","\${i+1}/\${names.size} 완료").apply()
            }
            update("AI 대본 분석 완료")
            prefs().edit().putString("analysis_message","분석 완료").apply()
        }finally{
            translator.close()
            Whisper.releaseModel(whisper)
        }
    }

    private fun prefs()=getSharedPreferences("ces_prefs",MODE_PRIVATE)
    private fun update(msg:String){
        prefs().edit().putString("analysis_message",msg).apply()
        getSystemService(NotificationManager::class.java).notify(NOTI,notification(msg))
    }
    private fun copyAudio(uri:Uri,key:String):File{
        val f=File(cacheDir,"\$key.mp3")
        contentResolver.openInputStream(uri).use{input->
            requireNotNull(input){"MP3를 열 수 없습니다."}
            f.outputStream().use{out->input.copyTo(out)}
        }
        return f
    }
    private fun ensureModel():File{
        val dir=File(filesDir,"models").apply{mkdirs()}
        val f=File(dir,"ggml-base.en.bin")
        if(f.exists()&&f.length()>100_000_000)return f
        update("Whisper 모델 다운로드 중")
        URL(MODEL_URL).openConnection().apply{
            connectTimeout=30_000;readTimeout=120_000
        }.getInputStream().use{input->
            f.outputStream().use{out->input.copyTo(out)}
        }
        return f
    }
    override fun onDestroy(){scope.cancel();super.onDestroy()}
}
