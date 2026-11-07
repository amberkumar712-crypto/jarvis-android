package com.jarvis.ai

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var log: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private var tts: TextToSpeech? = null
    private val hist = JSONArray()
    private var lang = Locale("en", "IN")
    private val prefs by lazy { getSharedPreferences("j", MODE_PRIVATE) }
    private val base get() = prefs.getString("url", "http://127.0.0.1:8000")!!

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        tts = TextToSpeech(this) { tts?.language = lang }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#020617")); setPadding(24, 64, 24, 24)
        }
        val head = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(TextView(this).apply {
            text = "J.A.R.V.I.S"; textSize = 18f; letterSpacing = 0.2f
            setTextColor(Color.parseColor("#22d3ee"))
        }, LinearLayout.LayoutParams(0, -2, 1f))
        val langBtn = btn("EN") {}
        langBtn.setOnClickListener {
            val hi = lang.language == "en"
            lang = if (hi) Locale("hi", "IN") else Locale("en", "IN")
            tts?.language = lang; langBtn.text = if (hi) "हि" else "EN"
        }
        head.addView(langBtn); head.addView(btn("⚙") { settings() })
        root.addView(head)
        log = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll = ScrollView(this).apply { addView(log) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        input = EditText(this).apply {
            hint = "Ask JARVIS…"; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
            setSingleLine(); imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, _, _ -> handle(text.toString()); true }
        }
        val row = LinearLayout(this)
        row.addView(btn("🎤") { listen() })
        row.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(btn("➤") { handle(input.text.toString()) })
        root.addView(row)
        setContentView(root)
        add("Online. Try: “open YouTube”, “alarm 6:30 am”, “torch on”, “volume up”, “call 9876543210”, or ask anything.")
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.parseColor("#22d3ee"))
        setBackgroundColor(Color.parseColor("#0f1e3c")); setOnClickListener { f() }
    }

    private fun add(t: String, user: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t; textSize = 15f; setTextColor(Color.parseColor("#e0f2fe")); setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor(if (user) "#1e3a8a" else "#0f1e3c"))
        }
        log.addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 12; gravity = if (user) Gravity.END else Gravity.START })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return v
    }

    private fun speak(t: String) {
        tts?.speak(t.replace(Regex("[*#`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "j")
    }

    private fun settings() {
        val e = EditText(this).apply { setText(base) }
        AlertDialog.Builder(this).setTitle("Backend URL").setView(e)
            .setPositiveButton("Save") { _, _ -> prefs.edit().putString("url", e.text.toString().trim().trimEnd('/')).apply() }
            .show()
    }

    private fun listen() {
        tts?.stop()
        try {
            startActivityForResult(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, lang.toLanguageTag()), 1)
        } catch (e: Exception) { add("Speech recognition isn't available on this device.") }
    }

    @Deprecated("simple")
    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        super.onActivityResult(rc, res, d)
        if (rc == 1 && res == RESULT_OK)
            d?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { handle(it) }
    }

    private fun handle(t: String) {
        if (t.isBlank()) return
        tts?.stop(); add(t, true); input.setText("")
        val r = local(t.lowercase().trim())
        if (r != null) { add(r); speak(r); return }
        val w = add("…")
        Thread {
            try {
                val rep = post("/api/chat", JSONObject().put("history", hist).put("message", t)).getString("reply")
                hist.put(JSONObject().put("role", "user").put("text", t))
                hist.put(JSONObject().put("role", "model").put("text", rep))
                runOnUiThread { w.text = rep; speak(rep) }
            } catch (e: Exception) {
                runOnUiThread { w.text = "Error: ${e.message}\nIs the server running? Check ⚙ for the URL." }
            }
        }.start()
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.readTimeout = 90000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.write(body.toString().toByteArray())
        val code = c.responseCode
        val s = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code >= 400) throw Exception(s.take(200))
        return JSONObject(s)
    }

    // Device actions: only what Android allows without special access. Returns null if not a device command.
    private fun local(t: String): String? = try {
        when {
            Regex("torch|flashlight|टॉर्च").containsMatchIn(t) -> {
                val on = !Regex("\\boff\\b|band|बंद").containsMatchIn(t)
                val cm = getSystemService(CAMERA_SERVICE) as CameraManager
                cm.setTorchMode(cm.cameraIdList[0], on); "Torch ${if (on) "on" else "off"}."
            }
            t.contains("volume") -> {
                val up = !Regex("down|low|kam|कम").containsMatchIn(t)
                (getSystemService(AUDIO_SERVICE) as AudioManager).adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                "Volume ${if (up) "up" else "down"}."
            }
            t.contains("alarm") -> {
                val m = Regex("(\\d{1,2})(?:[:.](\\d{2}))?\\s*(am|pm)?").find(t)
                if (m == null) "Tell me a time, e.g. “alarm 6:30 am”." else {
                    var h = m.groupValues[1].toInt(); val mi = m.groupValues[2].ifEmpty { "0" }.toInt()
                    if (m.groupValues[3] == "pm" && h < 12) h += 12
                    if (m.groupValues[3] == "am" && h == 12) h = 0
                    if (h > 23 || mi > 59) "That time isn't valid." else {
                        startActivity(Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, h)
                            .putExtra(AlarmClock.EXTRA_MINUTES, mi).putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS")
                            .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                        "Alarm set for %02d:%02d.".format(h, mi)
                    }
                }
            }
            t.contains("wifi") || t.contains("wi-fi") -> {
                startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                "Android doesn't let apps switch Wi-Fi directly, so I opened the panel for you."
            }
            t.contains("bluetooth") -> {
                startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
                "Android doesn't let apps switch Bluetooth directly, so I opened its settings."
            }
            t.contains("camera") -> { startActivity(Intent(MediaStore.ACTION_IMAGE_CAPTURE)); "Opening camera." }
            Regex("(call|dial)\\s*[+\\d ]{5,}").containsMatchIn(t) -> {
                val n = Regex("[+\\d ]{5,}").find(t)!!.value.replace(" ", "")
                startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$n")))
                "Dialer opened for $n. Press call to confirm."
            }
            Regex("^(open|launch)\\s+.+|.+\\s(kholo|खोलो)$").containsMatchIn(t) -> {
                val name = t.replace(Regex("^(open|launch)\\s+|\\s+(kholo|खोलो)$"), "").trim()
                val pm = packageManager
                val apps = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
                val hit = apps.firstOrNull { it.loadLabel(pm).toString().lowercase().contains(name) }
                if (hit == null) "I couldn't find an app called “$name”." else {
                    startActivity(pm.getLaunchIntentForPackage(hit.activityInfo.packageName))
                    "Opening ${hit.loadLabel(pm)}."
                }
            }
            else -> null
        }
    } catch (e: Exception) { "That action failed: ${e.message}" }
}
