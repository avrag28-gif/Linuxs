package com.devz.openrouterchat

import android.app.Activity
import android.os.Bundle
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("nova_settings", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(16, 18, 22)
    private val panel = Color.rgb(29, 32, 38)
    private val accent = Color.rgb(55, 116, 232)
    private val primary = Color.rgb(245, 246, 248)
    private val muted = Color.rgb(164, 170, 180)
    private val models = listOf(
        "nvidia/nemotron-3.5-super-vl:free",
        "meta-llama/llama-3.3-70b-instruct:free",
        "google/gemma-3-27b-it:free",
        "qwen/qwen3-4b:free"
    )
    private lateinit var root: FrameLayout
    private lateinit var page: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var messagesView: LinearLayout
    private lateinit var prompt: EditText
    private lateinit var status: TextView
    private lateinit var sendButton: TextView
    private lateinit var keyField: EditText
    private lateinit var memoryField: EditText
    private lateinit var modelSpinner: Spinner
    private var drawerOpen = false
    private var showKey = false
    private var sessions = mutableListOf<JSONObject>()
    private var currentSessionId = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0
        loadSessions()
        if (sessions.isEmpty()) newSession(false)
        buildShell()
        showChat()
    }

    private fun loadSessions() {
        sessions.clear()
        try {
            val arr = JSONArray(prefs.getString("sessions_json", "[]"))
            for (i in 0 until arr.length()) sessions.add(arr.getJSONObject(i))
        } catch (_: Exception) { sessions.clear() }
        currentSessionId = prefs.getString("current_session", "").orEmpty()
        if (sessions.none { it.optString("id") == currentSessionId }) {
            currentSessionId = sessions.firstOrNull()?.optString("id").orEmpty()
        }
    }

    private fun saveSessions() {
        val arr = JSONArray()
        sessions.forEach { arr.put(it) }
        prefs.edit().putString("sessions_json", arr.toString())
            .putString("current_session", currentSessionId).apply()
    }

    private fun newSession(refresh: Boolean = true) {
        val s = JSONObject()
            .put("id", System.currentTimeMillis().toString())
            .put("title", "Chat baru")
            .put("messages", JSONArray())
        sessions.add(0, s)
        currentSessionId = s.optString("id")
        saveSessions()
        if (refresh && ::page.isInitialized) {
            showChat()
            buildDrawer()
            closeDrawer()
        }
    }

    private fun currentSession(): JSONObject {
        return sessions.firstOrNull { it.optString("id") == currentSessionId }
            ?: sessions.first().also { currentSessionId = it.optString("id") }
    }

    private fun sessionMessages(): JSONArray = currentSession().optJSONArray("messages") ?: JSONArray()

    private fun buildShell() {
        root = FrameLayout(this).apply { setBackgroundColor(bg) }
        val main = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
            setBackgroundColor(bg)
        }
        val menu = label("☰", 22f, primary, true).apply {
            gravity = Gravity.CENTER
            background = solid(panel, 8)
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(44), dp(42)))
        menu.setOnClickListener { toggleDrawer() }
        val logo = label("N", 19f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = solid(accent, 8)
        }
        val logoLp = LinearLayout.LayoutParams(dp(34), dp(34)); logoLp.leftMargin = dp(12)
        top.addView(logo, logoLp)
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(9), 0, 0, 0) }
        brand.addView(label("NOVA AI", 17f, primary, true))
        brand.addView(label("Chat", 11f, muted))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val new = label("+", 26f, primary, true).apply {
            gravity = Gravity.CENTER
            background = solid(panel, 8)
            setOnClickListener { newSession() }
        }
        top.addView(new, LinearLayout.LayoutParams(dp(42), dp(42)))
        main.addView(top)
        main.addView(View(this).apply { setBackgroundColor(Color.rgb(42, 45, 52)) },
            LinearLayout.LayoutParams(-1, dp(1)))
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(12))
        }
        main.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(main, FrameLayout.LayoutParams(-1, -1))
        scrim = View(this).apply {
            setBackgroundColor(0x99000000.toInt())
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(20), dp(12), dp(16))
            setBackgroundColor(panel)
            elevation = dp(18).toFloat()
            visibility = View.GONE
        }
        root.addView(drawer, FrameLayout.LayoutParams(dp(292), -1, Gravity.START))
        setContentView(root)
        buildDrawer()
    }

    private fun buildDrawer() {
        drawer.removeAllViews()
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = label("N", 18f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER; background = solid(accent, 7)
        }
        head.addView(logo, LinearLayout.LayoutParams(dp(36), dp(36)))
        val name = label("NOVA AI", 18f, primary, true).apply { setPadding(dp(10), 0, 0, 0) }
        head.addView(name)
        drawer.addView(head)
        drawer.addView(label("SIMPLE AI CHAT", 10f, muted, true).apply {
            setPadding(0, dp(8), 0, dp(22))
        })
        drawer.addView(drawerButton("+   Chat baru") { newSession() })
        drawer.addView(drawerButton("Pengaturan") { showSettings(); closeDrawer() })
        drawer.addView(label("RIWAYAT CHAT", 10f, muted, true).apply {
            setPadding(dp(3), dp(24), 0, dp(10))
        })
        val scroll = ScrollView(this)
        val history = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sessions.forEach { s ->
            val title = s.optString("title", "Chat baru").ifBlank { "Chat baru" }
            val item = label(title, 13f, if (s.optString("id") == currentSessionId) Color.WHITE else muted)
            item.maxLines = 2
            item.setPadding(dp(10), dp(12), dp(8), dp(12))
            item.background = solid(if (s.optString("id") == currentSessionId) Color.rgb(45, 51, 62) else panel, 6)
            item.setOnClickListener {
                currentSessionId = s.optString("id")
                saveSessions()
                showChat()
                buildDrawer()
                closeDrawer()
            }
            val lp = LinearLayout.LayoutParams(-1, -2); lp.bottomMargin = dp(4)
            history.addView(item, lp)
        }
        scroll.addView(history)
        drawer.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun drawerButton(text: String, action: () -> Unit) =
        label(text, 14f, primary, true).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(13), dp(12), dp(13))
            background = solid(bg, 7)
            setOnClickListener { action() }
        }

    private fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer() }
    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        scrim.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        drawer.startAnimation(TranslateAnimation(-dp(300).toFloat(), 0f, 0f, 0f).apply {
            duration = 180; interpolator = DecelerateInterpolator()
        })
    }
    private fun closeDrawer() {
        drawerOpen = false
        drawer.visibility = View.GONE
        scrim.visibility = View.GONE
    }

    private fun showChat() {
        page.removeAllViews()
        val history = ScrollView(this).apply { clipToPadding = false }
        messagesView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(10))
        }
        val msgs = sessionMessages()
        if (msgs.length() == 0) {
            messagesView.addView(label("Apa yang bisa kubantu?", 25f, primary, true).apply {
                setPadding(0, dp(28), 0, dp(8))
            })
            messagesView.addView(label("Tulis pertanyaan atau ide kamu.", 14f, muted).apply {
                setPadding(0, 0, 0, dp(20))
            })
        } else {
            for (i in 0 until msgs.length()) {
                val m = msgs.optJSONObject(i) ?: continue
                addMessageView(if (m.optString("role") == "user") "Kamu" else "NOVA",
                    m.optString("content"), m.optString("role") == "user")
            }
        }
        history.addView(messagesView)
        page.addView(history, LinearLayout.LayoutParams(-1, 0, 1f))
        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = solid(panel, 9)
            setPadding(dp(12), dp(8), dp(10), dp(8))
        }
        prompt = EditText(this).apply {
            hint = "Tulis pesan..."
            setTextColor(primary); setHintTextColor(muted)
            textSize = 15f; minLines = 1; maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(4), dp(4), dp(4), dp(8))
            background = null
        }
        composer.addView(prompt, LinearLayout.LayoutParams(-1, -2))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = label(if (prefs.getString("api_key", "").isNullOrBlank()) "Tambahkan API key di Pengaturan" else "", 11f, muted)
        row.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = label("Kirim  ↑", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = solid(accent, 7)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { submit() }
        }
        row.addView(sendButton)
        composer.addView(row)
        val lp = LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(10)
        page.addView(composer, lp)
    }

    private fun showSettings() {
        page.removeAllViews()
        page.addView(label("Pengaturan", 25f, primary, true))
        page.addView(label("Koneksi, model, dan memori", 13f, muted).apply {
            setPadding(0, dp(5), 0, dp(18))
        })
        val scroll = ScrollView(this)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(section("OPENROUTER API KEY"))
        keyField = EditText(this).apply {
            hint = "Tempel API key di sini"
            setText(prefs.getString("api_key", ""))
            setTextColor(primary); setHintTextColor(muted); textSize = 13f; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7)
        }
        body.addView(keyField, fieldLp())
        val keyRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        keyRow.addView(smallButton("Lihat / sembunyikan") {
            showKey = !showKey
            keyField.inputType = InputType.TYPE_CLASS_TEXT or if (showKey) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
            keyField.setSelection(keyField.text.length)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        keyRow.addView(smallButton("Simpan key") {
            prefs.edit().putString("api_key", keyField.text.toString().trim()).apply()
            Toast.makeText(this, "API key tersimpan.", Toast.LENGTH_SHORT).show()
        })
        body.addView(keyRow)
        body.addView(section("MODEL AI"))
        val spinner = Spinner(this)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, models.map { it.substringBefore(":free") + if (it.endsWith(":free")) " · Free" else "" })
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        val selected = models.indexOf(prefs.getString("model", models[0])).let { if (it < 0) 0 else it }
        spinner.setSelection(selected)
        spinner.setPadding(dp(8), dp(5), dp(8), dp(5))
        spinner.background = solid(panel, 7)
        spinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                prefs.edit().putString("model", models[position]).apply()
            }
        }
        body.addView(spinner, fieldLp())
        body.addView(label("Model yang dipilih dipakai untuk chat baru dan berikutnya.", 11f, muted).apply {
            setPadding(0, dp(5), 0, dp(8))
        })
        body.addView(section("MEMORI NOVA"))
        body.addView(label("Simpan hal penting agar bisa dipakai di percakapan berikutnya.", 12f, muted).apply {
            setPadding(0, 0, 0, dp(8))
        })
        memoryField = EditText(this).apply {
            hint = "Contoh: saya suka jawaban singkat..."
            setText(prefs.getString("memory", ""))
            setTextColor(primary); setHintTextColor(muted); textSize = 13f
            minLines = 3; maxLines = 7
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            gravity = Gravity.TOP
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7)
        }
        body.addView(memoryField, fieldLp())
        body.addView(smallButton("Simpan memori") {
            prefs.edit().putString("memory", memoryField.text.toString().trim()).apply()
            Toast.makeText(this, "Memori disimpan di perangkat.", Toast.LENGTH_SHORT).show()
        }.apply { setPadding(dp(14), dp(12), dp(14), dp(12)) })
        body.addView(smallButton("Hapus semua riwayat chat") {
            sessions.clear()
            newSession(false)
            saveSessions()
            buildDrawer()
            showChat()
            Toast.makeText(this, "Riwayat chat dihapus.", Toast.LENGTH_SHORT).show()
        }.apply { setPadding(dp(14), dp(12), dp(14), dp(12)) })
        body.addView(label("API key, memori, dan riwayat tersimpan lokal di perangkat.", 11f, muted).apply {
            setPadding(0, dp(18), 0, dp(20))
        })
        scroll.addView(body)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun section(text: String) = label(text, 10f, muted, true).apply {
        setPadding(dp(2), dp(18), 0, dp(8))
    }
    private fun fieldLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun smallButton(text: String, action: () -> Unit) =
        label(text, 12f, primary, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = solid(if (text.contains("Simpan")) accent else panel, 7)
            setOnClickListener { action() }
        }

    private fun submit() {
        val key = prefs.getString("api_key", "").orEmpty().trim()
        val message = prompt.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "Masukkan API key di Pengaturan.", Toast.LENGTH_SHORT).show()
            showSettings(); return
        }
        if (message.isEmpty()) { prompt.error = "Tulis pesan"; return }
        val session = currentSession()
        val arr = sessionMessages()
        arr.put(JSONObject().put("role", "user").put("content", message))
        session.put("messages", arr)
        if (arr.length() == 1) session.put("title", message.take(34))
        saveSessions()
        addMessageView("Kamu", message, true)
        prompt.setText("")
        sendButton.isEnabled = false
        status.text = "Menunggu jawaban..."
        buildDrawer()
        val loading = messageCard("NOVA", "Sedang menjawab...", false)
        messagesView.addView(loading)
        val model = prefs.getString("model", models[0]) ?: models[0]
        val memory = prefs.getString("memory", "").orEmpty().trim()
        thread {
            var connection: HttpURLConnection? = null
            val answer = try {
                connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", "https://github.com/avrag28-gif/Linuxs")
                    setRequestProperty("X-Title", "NOVA AI")
                }
                val requestMessages = JSONArray()
                if (memory.isNotBlank()) requestMessages.put(JSONObject().put("role", "system")
                    .put("content", "Informasi memori pengguna yang boleh digunakan untuk personalisasi: $memory"))
                val history = sessionMessages()
                for (i in 0 until history.length()) requestMessages.put(history.getJSONObject(i))
                val body = JSONObject().put("model", model).put("messages", requestMessages)
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val detail = try { JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw } catch (_: Exception) { raw }
                    "Permintaan gagal (HTTP $code).\n$detail"
                } else {
                    val content = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
                    when (content) {
                        is String -> content.ifBlank { "Respons kosong dari model." }
                        is JSONArray -> (0 until content.length()).mapNotNull { i ->
                            content.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }
                        }.joinToString("\n").ifBlank { "Format respons belum didukung." }
                        else -> "Respons kosong dari model."
                    }
                }
            } catch (e: Exception) {
                "Koneksi gagal: ${e.message ?: "periksa internet dan API key."}"
            } finally { connection?.disconnect() }
            runOnUiThread {
                messagesView.removeView(loading)
                val latest = currentSession()
                val latestArr = sessionMessages()
                latestArr.put(JSONObject().put("role", "assistant").put("content", answer))
                latest.put("messages", latestArr)
                saveSessions()
                addMessageView("NOVA", answer, false)
                sendButton.isEnabled = true
                status.text = ""
                buildDrawer()
            }
        }
    }

    private fun addMessageView(who: String, text: String, mine: Boolean) {
        messagesView.addView(messageCard(who, text, mine))
    }

    private fun messageCard(who: String, text: String, mine: Boolean): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = solid(if (mine) Color.rgb(37, 54, 83) else panel, 8)
            setPadding(dp(13), dp(11), dp(13), dp(12))
        }
        box.addView(label(who, 10f, if (mine) Color.rgb(173, 202, 255) else Color.rgb(190, 195, 204), true))
        box.addView(label(text, 14f, primary).apply {
            setPadding(0, dp(6), 0, 0)
            setTextIsSelectable(true)
        })
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(9)
        if (mine) lp.leftMargin = dp(22) else lp.rightMargin = dp(22)
        box.layoutParams = lp
        return box
    }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun solid(color: Int, radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(color)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
