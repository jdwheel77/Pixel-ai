package com.hybrid.agent

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.security.crypto.MasterKey
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.*
import kotlin.concurrent.thread
import android.database.sqlite.SQLiteDatabase
import android.content.ContentValues
import android.content.Intent
import android.util.Log

class MainActivity : AppCompatActivity() {

    private var tfliteInterpreter: Interpreter? = null
    private lateinit var internetToggle: Switch
    private lateinit var statusConsole: TextView
    private lateinit var askButton: Button

    private var speechRecognizer: SpeechRecognizer? = null
    private var recognizing = false

    private lateinit var db: SQLiteDatabase

    companion object {
        const val TAG = "MainActivity"
        const val WAKE_PHRASE = "hey kin"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        internetToggle = findViewById(R.id.switch_internet)
        statusConsole = findViewById(R.id.text_console)
        askButton = findViewById(R.id.button_ask)

        internetToggle.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                enableNetwork()
                appendConsole("Internet: ON")
            } else {
                disableNetwork()
                appendConsole("Internet: OFF")
            }
        }

        askButton.setOnClickListener {
            appendConsole("[Ask Jesy] Please approve action: Send email to test@example.com")
        }

        db = openOrCreateDatabase("kin_memory.db", MODE_PRIVATE, null)
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS memory (
                id TEXT PRIMARY KEY,
                text TEXT,
                tags TEXT,
                ts INTEGER
            );
        """.trimIndent())
        appendConsole("Local memory DB initialized")


        thread {
            try {
                val model = loadModelFile("dummy_model.tflite")
                tfliteInterpreter = Interpreter(model)
                runOnUiThread { appendConsole("TFLite model loaded (stub)") }
            } catch (e: Exception) {
                runOnUiThread { appendConsole("TFLite load error: ${'$'}{e.message}") }
            }
        }

        try {
            val masterKey = MasterKey.Builder(this)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val file = File(filesDir, "secure_notes.txt")
            appendConsole("Encrypted storage initialized (stub)")
        } catch (e: Exception) {
            appendConsole("Encrypted storage error: ${'$'}{e.message}")
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            startWakeListener()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startWakeListener()
            } else {
                appendConsole("Audio permission denied — wake-word disabled.")
            }
        }
    }

    private fun startWakeListener() {
        try {
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                appendConsole("Speech recognition not available on device")
                return
            }
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this)
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { appendConsole("Wake-listener ready") }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    appendConsole("SpeechRecognizer error: $error")
                    restartListening()
                }
                override fun onResults(results: Bundle?) {
                    handleResults(results, true)
                    restartListening()
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    handleResults(partialResults, false)
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            }
            speechRecognizer?.setRecognitionListener(listener)
            recognizing = true
            speechRecognizer?.startListening(intent)
            appendConsole("Wake-word listener started (listening for \"${WAKE_PHRASE}\")")
        } catch (e: Exception) {
            appendConsole("Failed to start wake listener: ${'$'}{e.message}")
        }
    }

    private fun restartListening() {
        thread {
            try { Thread.sleep(300) } catch (e: Exception) {}
            try {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                appendConsole("Failed to restart recognizer: ${'$'}{e.message}")
            }
        }
    }

    private fun handleResults(results: Bundle?, isFinal: Boolean) {
        val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
        if (matches.size > 0) {
            val text = matches[0].toLowerCase(Locale.getDefault())
            appendConsole("[ASR] $text")
            if (text.contains(WAKE_PHRASE)) {
                appendConsole("Wake phrase detected: $text")
                promptForCommand()
            }
        }
    }

    private fun promptForCommand() {
        appendConsole("Listening for command...")
        try {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            speechRecognizer?.startListening(intent)
        } catch (e: Exception) {
            appendConsole("Error prompting for command: ${'$'}{e.message}")
        }
    }

    private fun appendConsole(s: String) {
        runOnUiThread {
            statusConsole.append("\n" + s)
        }
        Log.d(TAG, s)
    }

    private fun loadModelFile(assetName: String): MappedByteBuffer {
        val fileDescriptor = assets.openFd(assetName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun disableNetwork() {
        appendConsole("Network disabled by user toggle (app-level)")
    }

    private fun enableNetwork() {
        appendConsole("Network enabled by user toggle (app-level)")
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        tfliteInterpreter?.close()
        db.close()
        super.onDestroy()
    }

    private fun storeMemory(id: String, text: String, tags: String) {
        val cv = ContentValues()
        cv.put("id", id); cv.put("text", text); cv.put("tags", tags); cv.put("ts", System.currentTimeMillis()/1000)
        db.insertWithOnConflict("memory", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
        appendConsole("Stored memory: $id")
    }

    private fun queryRecent(limit: Int = 5) : List<String> {
        val cur = db.rawQuery("SELECT text, ts FROM memory ORDER BY ts DESC LIMIT ${limit}", null)
        val out = mutableListOf<String>()
        while (cur.moveToNext()) {
            out.add(cur.getString(0) + " (" + cur.getLong(1).toString() + ")")
        }
        cur.close()
        return out
    }
}
