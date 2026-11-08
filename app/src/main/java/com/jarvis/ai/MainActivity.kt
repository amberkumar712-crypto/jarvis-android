package com.jarvis.ai

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.webkit.WebView
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
    private var pendingPerm: (() -> Unit)? = null
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
        add("Online. Try: “WhatsApp Rahul ko bolo ki main late hoon”, “play Kesariya”, “let’s play 20 questions”, “make a snake game”, “make a multiplayer tic tac toe game”, “write python code for a calculator”, “torch on”, “alarm 6:30 am”, “accessibility”.")
    }

    private fun btn(t: String, f: () -> Unit) = Button(this).apply {
        text = t; setTextColor(Color.parseColor("#22d3ee"))
        setBackgroundColor(Color.parseColor("#0f1e3c")); setOnClickListener { f() }
    }

    private fun add(t: String, user: Boolean = false): TextView {
        val v = TextView(this).apply {
            text = t; textSize = 15f; setTextColor(Color.parseColor("#e0f2fe")); setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.parseColor(if (user) "#1e3a8a" else "#0f1e3c"))
            setTextIsSelectable(true)
        }
        log.addView(v, LinearLayout.LayoutParams(-2, -2).apply { topMargin = 12; gravity = if (user) Gravity.END else Gravity.START })
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
        return v
    }

    private fun speak(t: String) {
        tts?.speak(t.replace(Regex("[*#`]"), ""), TextToSpeech.QUEUE_FLUSH, null, "j")
    }
    private fun say(t: String) { add(t); speak(t) }

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

    override fun onRequestPermissionsResult(rc: Int, p: Array<out String>, g: IntArray) {
        super.onRequestPermissionsResult(rc, p, g)
        if (rc == 2) {
            if (g.isNotEmpty() && g[0] == PackageManager.PERMISSION_GRANTED) pendingPerm?.invoke()
            else add("Contacts permission denied, so I can't look up names. Say a number instead: “WhatsApp 9876543210 hello”.")
            pendingPerm = null
        }
    }

    private fun handle(t: String) {
        if (t.isBlank()) return
        tts?.stop(); add(t, true); input.setText("")
        val l = t.lowercase().trim()
        val wa = Regex("whatsapp|व्हाट्सएप|व्हाट्सऐप|वॉट्सऐप").containsMatchIn(l) &&
            Regex("message|msg|send|text|bhej|bolo|likh|कहो|भेज|बोल|लिख").containsMatchIn(l)
        val game = Regex("game|गेम").containsMatchIn(l) && Regex("make|create|build|bana|बना").containsMatchIn(l)
        if (wa) { whatsapp(t); return }
        if (game) { makeGame(t); return }
        val yt = Regex("\\b(play|bajao|chalao)\\b|बजाओ|चलाओ").containsMatchIn(l) &&
            !Regex("game|गेम|with me|mere saath|मेरे साथ").containsMatchIn(l)
        if (yt) { playYoutube(l); return }
        val r = local(l)
        if (r != null) { say(r); return }
        val w = add("…")
        gemini(t, hist) { rep, err ->
            if (rep == null) { w.text = "Error: $err\nIs the server running? Check ⚙ for the URL."; return@gemini }
            hist.put(JSONObject().put("role", "user").put("text", t))
            hist.put(JSONObject().put("role", "model").put("text", rep))
            w.text = rep
            speak(if (rep.contains("```")) "Code is ready on screen. Long-press to copy." else rep)
        }
    }

    private fun gemini(msg: String, history: JSONArray = JSONArray(), cb: (String?, String?) -> Unit) {
        Thread {
            try {
                val r = post("/api/chat", JSONObject().put("history", history).put("message", msg)).getString("reply")
                runOnUiThread { cb(r, null) }
            } catch (e: Exception) { runOnUiThread { cb(null, e.message) } }
        }.start()
    }

    private fun post(path: String, body: JSONObject): JSONObject {
        val c = URL(base + path).openConnection() as HttpURLConnection
        c.requestMethod = "POST"; c.doOutput = true; c.readTimeout = 120000
        c.setRequestProperty("Content-Type", "application/json")
        c.outputStream.write(body.toString().toByteArray())
        val code = c.responseCode
        val s = (if (code < 400) c.inputStream else c.errorStream).bufferedReader().readText()
        if (code >= 400) throw Exception(s.take(200))
        return JSONObject(s)
    }


    // ---------- YouTube: finds the first matching video and opens it ----------
    private fun playYoutube(l: String) {
        val q = l.replace(Regex("\\b(open|launch|youtube|and|on|play|bajao|chalao|video|song)\\b|बजाओ|चलाओ|गाना|वीडियो"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (q.isEmpty()) { say("Tell me what to play, e.g. “play Kesariya”."); return }
        val w = add("Finding “$q” on YouTube…")
        Thread {
            var id: String? = null
            try {
                val c = URL("https://www.youtube.com/results?search_query=" + Uri.encode(q) + "&hl=en").openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                c.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                c.setRequestProperty("Cookie", "CONSENT=YES+1; SOCS=CAI")
                c.connectTimeout = 10000; c.readTimeout = 15000
                val html = c.inputStream.bufferedReader().readText()
                id = Regex("\"videoId\":\"([A-Za-z0-9_-]{11})\"").find(html)?.groupValues?.get(1)
            } catch (e: Exception) {}
            runOnUiThread {
                try {
                    if (id != null) {
                        w.text = "Playing “$q” on YouTube."
                        try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$id"))) }
                        catch (e: Exception) { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))) }
                    } else {
                        w.text = "Couldn't pick a video automatically. Opening search results."
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))))
                    }
                } catch (e: Exception) { w.text = "Couldn't open YouTube: ${e.message}" }
            }
        }.start()
    }

    // ---------- WhatsApp (always asks you to confirm) ----------
    private fun whatsapp(t: String) {
        val w = add("Understanding…")
        val p = "Extract the WhatsApp recipient and message from this request. Reply ONLY with JSON like " +
            "{\"name\":\"...\",\"message\":\"...\"}. Write the name in English letters. Write the message in first person, " +
            "in the same language the user spoke. Request: $t"
        gemini(p) { r, err ->
            if (r == null) { w.text = "Error: $err"; return@gemini }
            try {
                val j = JSONObject(r.substring(r.indexOf('{'), r.lastIndexOf('}') + 1))
                val name = j.getString("name"); val msg = j.getString("message")
                w.text = "To: $name\nMessage: $msg"
                withContacts { sendWa(name, msg) }
            } catch (ex: Exception) {
                w.text = "I couldn't understand that. Try: “WhatsApp Rahul ko bolo ki main late hoon”."
            }
        }
    }

    private fun withContacts(f: () -> Unit) {
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) f()
        else { pendingPerm = f; requestPermissions(arrayOf(Manifest.permission.READ_CONTACTS), 2) }
    }

    private fun findContact(name: String): String? {
        contentResolver.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " LIKE ?", arrayOf("%$name%"), null)?.use {
            if (it.moveToFirst()) return it.getString(0).filter { c -> c.isDigit() }
        }
        return null
    }

    private fun sendWa(name: String, msg: String) {
        val digits = name.filter { it.isDigit() }
        val found = if (digits.length >= 8) digits else findContact(name)
        if (found == null) { say("I couldn't find “$name” in your contacts."); return }
        val n = if (found.length == 10) "91$found" else found
        AlertDialog.Builder(this).setTitle("Send WhatsApp?").setMessage("To: $name (+$n)\n\n$msg")
            .setPositiveButton("Send") { _, _ ->
                JarvisService.arm()
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$n?text=" + Uri.encode(msg))))
                if (!JarvisService.running) add("WhatsApp opened with your message. Tap send. (Say “accessibility” to enable auto-send.)")
            }.setNegativeButton("Cancel", null).show()
    }

    // ---------- Games (Gemini writes an HTML game, runs it in a WebView) ----------
    private fun makeGame(t: String) {
        val multi = Regex("multiplayer|multi player|dost|friend|दोस्त|online").containsMatchIn(t.lowercase())
        val ws = base.replaceFirst("http", "ws") + "/ws/"
        var p = "Create this as ONE complete self-contained HTML file (inline CSS and JS, no external files or libraries): $t. " +
            "Mobile-first with touch controls, dark neon style, responsive full-screen, with score and a restart button. " +
            "Reply with only the code in a single ```html block."
        if (multi) p += " It is an online multiplayer game. The page first asks for a room code, then connects with " +
            "new WebSocket(\"$ws\" + roomCode). The server relays every message you send to the other players in the room. " +
            "On connect it sends you {\"_role\":N,\"_count\":N} (N is your player number) and sends others {\"_join\":N} when someone joins. " +
            "Send small JSON messages (moves/state) and keep both screens in sync. Player 1 is the host."
        val w = add("Building your game… (up to a minute)")
        gemini(p) { r, err ->
            if (r == null) { w.text = "Error: $err"; return@gemini }
            val m = Regex("```(?:html)?\\s*([\\s\\S]*?)```").find(r)
            val html = m?.groupValues?.get(1) ?: (if (r.contains("<html", true)) r else null)
            if (html == null) { w.text = "I couldn't build it. Try again with a simpler description."; return@gemini }
            w.text = if (multi) "Game ready. Both players need the same room code, and the same server (see ⚙)." else "Game ready!"
            showGame(html)
        }
    }

    private fun showGame(html: String) {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Color.BLACK); setPadding(0, 48, 0, 0) }
        val wv = WebView(this)
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        col.addView(btn("✕ Close") { d.dismiss(); wv.destroy() })
        col.addView(wv, LinearLayout.LayoutParams(-1, 0, 1f))
        d.setContentView(col); d.show()
        wv.loadDataWithBaseURL("http://localhost/", html, "text/html", "utf-8", null)
    }

    // ---------- Device actions (only what Android allows) ----------
    private fun local(t: String): String? = try {
        when {
            t.contains("accessibility") || t.contains("एक्सेसिबिलिटी") -> {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                "Find JARVIS in the list and turn it on. If it's greyed out: Settings > Apps > JARVIS > ⋮ > Allow restricted settings."
            }
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
            t.contains("timer") -> {
                val m = Regex("(\\d+)").find(t)
                if (m == null) "Tell me how long, e.g. “timer 10 minutes”." else {
                    val mins = m.value.toInt()
                    startActivity(Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, mins * 60)
                        .putExtra(AlarmClock.EXTRA_SKIP_UI, true))
                    "Timer set for $mins minutes."
                }
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
