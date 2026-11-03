package com.devz.openrouterchat

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Bundle
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.provider.DocumentsContract
import android.os.Build
import android.util.Rational
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.TranslateAnimation
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val prefs by lazy { getSharedPreferences("nova_settings", Context.MODE_PRIVATE) }
    private val bg = Color.rgb(12, 14, 18)
    private val panel = Color.rgb(25, 29, 36)
    private val accent = Color.rgb(63, 125, 245)
    private val primary = Color.rgb(245, 246, 248)
    private val muted = Color.rgb(163, 171, 184)
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
    private lateinit var mcpField: EditText
    private var attachedText = ""
    private var attachedName = ""
    private var attachedImageData = ""
    private var attachedImageMime = ""
    private var webContext = ""
    private val REQ_FILE = 101
    private val REQ_FOLDER = 102
    private var drawerOpen = false
    private var showKey = false
    private var sessions = mutableListOf<JSONObject>()
    private var currentSessionId = ""
    private val REQ_VOICE = 103
    private var speechOutput: TextToSpeech? = null

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
        brand.addView(label("AgentNova", 17f, primary, true))
        brand.addView(label("Chat", 11f, muted))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val new = label("+", 26f, primary, true).apply {
            gravity = Gravity.CENTER
            background = solid(panel, 8)
            setOnClickListener { newSession() }
        }
        top.addView(new, LinearLayout.LayoutParams(dp(42), dp(42)))
        main.addView(top)
        main.addView(View(this).apply { setBackgroundColor(Color.rgb(53, 59, 69)) },
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
            background = solid(panel, 0)
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
        val name = label("AgentNova", 18f, primary, true).apply { setPadding(dp(10), 0, 0, 0) }
        head.addView(name)
        drawer.addView(head)
        drawer.addView(label("AI AGENT", 10f, muted, true).apply {
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
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        actions.addView(toolChip("＋ File") { pickFile() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { rightMargin = dp(5) })
        actions.addView(toolChip("Web") { webSearch() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(3); rightMargin = dp(3) })
        actions.addView(toolChip("Tools") { showTools() }, LinearLayout.LayoutParams(0, dp(38), 1f).apply { leftMargin = dp(5) })
        page.addView(actions, LinearLayout.LayoutParams(-1, -2))
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
                addMessageView(if (m.optString("role") == "user") "Kamu" else "AgentNova",
                    m.optString("content"), m.optString("role") == "user")
            }
        }
        history.addView(messagesView)
        page.addView(history, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(10) })
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
        if (attachedName.isNotBlank()) composer.addView(label("📎 $attachedName", 11f, muted).apply { setPadding(0, 0, 0, dp(7)) })
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = label(if (ProviderRegistry.selected(this).requiresKey && ProviderRegistry.apiKey(this).isBlank()) "Tambahkan API key di Pengaturan" else "", 11f, muted)
        row.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = label("Kirim  ↑", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = solid(accent, 7)
            setPadding(dp(16), dp(10), dp(16), dp(10))
            setOnClickListener { submit() }
        }
        val voiceButton = label("🎙", 19f, primary, true).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = solid(bg, 7)
            setOnClickListener { startVoiceInput() }
        }
        row.addView(voiceButton)
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
        body.addView(section("PROVIDER AI"))
        val providerSpinner = Spinner(this)
        val providerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, ProviderRegistry.providers.map { it.label })
        providerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        val selectedProvider = ProviderRegistry.selected(this)
        providerSpinner.adapter = providerAdapter
        providerSpinner.setSelection(ProviderRegistry.providers.indexOf(selectedProvider).coerceAtLeast(0))
        providerSpinner.background = solid(panel, 7)
        body.addView(providerSpinner, fieldLp())
        body.addView(section("API KEY"))
        keyField = EditText(this).apply { hint = "API key"; setText(ProviderRegistry.apiKey(this@MainActivity, selectedProvider)); setTextColor(primary); setHintTextColor(muted); textSize = 13f; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD; setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7) }
        body.addView(keyField, fieldLp())
        body.addView(smallButton("Lihat / sembunyikan API key") { showKey = !showKey; keyField.inputType = InputType.TYPE_CLASS_TEXT or if (showKey) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD; keyField.setSelection(keyField.text.length) })
        body.addView(section("BASE URL"))
        val baseUrlField = EditText(this).apply { hint = "https://api.example.com/v1"; setText(ProviderRegistry.baseUrl(this@MainActivity, selectedProvider)); setTextColor(primary); setHintTextColor(muted); textSize = 13f; isSingleLine = true; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI; setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7) }
        body.addView(baseUrlField, fieldLp())
        body.addView(section("MODEL AI"))
        val modelField = EditText(this).apply { hint = "Nama model"; setText(ProviderRegistry.model(this@MainActivity, selectedProvider)); setTextColor(primary); setHintTextColor(muted); textSize = 13f; isSingleLine = true; setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7) }
        body.addView(modelField, fieldLp())
        providerSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) { val p = ProviderRegistry.providers[position]; keyField.setText(ProviderRegistry.apiKey(this@MainActivity, p)); baseUrlField.setText(ProviderRegistry.baseUrl(this@MainActivity, p)); modelField.setText(ProviderRegistry.model(this@MainActivity, p)); keyField.hint = if (p.requiresKey) "API key " + p.label else "API key opsional" }
        }
        body.addView(smallButton("Simpan konfigurasi provider") {
            val p = ProviderRegistry.providers[providerSpinner.selectedItemPosition]
            ProviderRegistry.save(this, p.id, keyField.text.toString(), baseUrlField.text.toString(), modelField.text.toString())
            if (p.id == "openrouter") prefs.edit().putString("api_key", keyField.text.toString().trim()).putString("model", modelField.text.toString().trim()).apply()
            Toast.makeText(this, "Provider " + p.label + " tersimpan.", Toast.LENGTH_SHORT).show()
        }.apply { setPadding(dp(14), dp(12), dp(14), dp(12)) })
        body.addView(label("Mendukung provider cloud, server lokal, dan endpoint OpenAI-compatible kustom.", 11f, muted).apply { setPadding(0, dp(7), 0, dp(8)) })
        body.addView(section("MCP SERVER"))
        mcpField = EditText(this).apply {
            hint = "URL endpoint MCP (HTTP)"
            setText(prefs.getString("mcp_url", ""))
            setTextColor(primary); setHintTextColor(muted); textSize = 13f; isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            setPadding(dp(12), dp(12), dp(12), dp(12)); background = solid(panel, 7)
        }
        body.addView(mcpField, fieldLp())
        val mcpRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        mcpRow.addView(smallButton("Simpan MCP") {
            prefs.edit().putString("mcp_url", mcpField.text.toString().trim()).apply()
            Toast.makeText(this, "Endpoint MCP disimpan.", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(5) })
        mcpRow.addView(smallButton("Tes / daftar tools") { discoverMcpTools(mcpField.text.toString().trim()) },
            LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = dp(5) })
        body.addView(mcpRow)
        body.addView(smallButton("Jalankan MCP tool (manual + konfirmasi)") { mcpToolDialog() })
        body.addView(label(prefs.getString("mcp_tools", "Belum ada daftar tools MCP.") ?: "", 11f, muted).apply {
            setPadding(0, dp(8), 0, dp(8))
        })
        body.addView(section("MEMORI AgentNova"))
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
        val provider = ProviderRegistry.selected(this)
        val key = ProviderRegistry.apiKey(this, provider).trim()
        val baseUrl = ProviderRegistry.baseUrl(this, provider)
        val model = ProviderRegistry.model(this, provider)
        val typedMessage = prompt.text.toString().trim()
        val message = buildString {
            append(typedMessage)
            if (attachedText.isNotBlank()) append("\n\n[File terlampir: $attachedName]\n$attachedText")
            if (webContext.isNotBlank()) append("\n\n$webContext")
        }
        if (provider.requiresKey && key.isEmpty()) {
            Toast.makeText(this, "Masukkan API key di Pengaturan.", Toast.LENGTH_SHORT).show()
            showSettings(); return
        }
        if (typedMessage.isEmpty() && attachedText.isBlank() && webContext.isBlank()) { prompt.error = "Tulis pesan"; return }
        val imageDataSnapshot = attachedImageData
        val imageMimeSnapshot = attachedImageMime
        val requestSessionId = currentSessionId
        val requestSession = currentSession()
        val requestHistory = sessionMessages()
        requestHistory.put(JSONObject().put("role", "user").put("content", message))
        requestSession.put("messages", requestHistory)
        if (requestHistory.length() == 1) requestSession.put("title", message.take(34))
        saveSessions()
        addMessageView("Kamu", message, true)
        prompt.setText("")
        attachedText = ""; attachedName = ""; attachedImageData = ""; attachedImageMime = ""; webContext = ""
        sendButton.isEnabled = false
        status.text = "Menunggu jawaban..."
        buildDrawer()
        val requestMessagesView = messagesView
        val loading = messageCard("AgentNova", "Sedang menjawab...", false)
        requestMessagesView.addView(loading)
        val memory = prefs.getString("memory", "").orEmpty().trim()
        // Snapshot the conversation before leaving the UI thread. A later chat switch
        // must not change the request payload or the destination of its response.
        val historySnapshot = JSONArray(requestHistory.toString())
        thread {
            val answer = try {
                val requestMessages = JSONArray()
                requestMessages.put(JSONObject().put(
                    "role", "system"
                ).put(
                    "content",
                    "Kamu adalah AgentNova, asisten AI di Android. Jawab sesuai kemampuan yang benar-benar tersedia. " +
                        "Jangan mengaku telah membuka aplikasi, mengubah pengaturan, mengakses kamera, menjalankan MCP, " +
                        "atau melakukan tindakan perangkat jika tindakan itu belum benar-benar dijalankan melalui UI aplikasi. " +
                        "Jika diminta melakukan tindakan yang belum didukung, jelaskan keterbatasannya dan berikan langkah manual. " +
                        "Jaga privasi pengguna dan jangan meminta API key atau kata sandi di dalam percakapan."
                ))
                if (memory.isNotBlank()) requestMessages.put(JSONObject().put(
                    "role", "system"
                ).put(
                    "content",
                    "Informasi memori pengguna yang boleh digunakan untuk personalisasi: " + memory.take(6000)
                ))
                // Bound request context so long-running chats cannot grow requests indefinitely.
                // Keep the latest 40 messages; older messages remain saved in local chat history.
                val firstContextMessage = (historySnapshot.length() - 40).coerceAtLeast(0)
                for (i in firstContextMessage until historySnapshot.length()) {
                    val item = JSONObject(historySnapshot.getJSONObject(i).toString())
                    val content = item.optString("content")
                    if (content.length > 20000) item.put("content", content.take(20000) + "\\n[Konten dipotong untuk batas konteks.]")
                    requestMessages.put(item)
                }
                // Format image payload for the selected provider vision API.
                if (imageDataSnapshot.isNotBlank()) {
                    val last = requestMessages.optJSONObject(requestMessages.length() - 1)
                    if (last != null && last.optString("role") == "user") {
                        val parts = JSONArray()
                        if (provider.id == "anthropic") {
                            parts.put(JSONObject().put("type", "text").put("text", last.optString("content")))
                            parts.put(JSONObject().put("type", "image").put("source", JSONObject()
                                .put("type", "base64").put("media_type", imageMimeSnapshot).put("data", imageDataSnapshot)))
                        } else {
                            parts.put(JSONObject().put("type", "text").put("text", last.optString("content")))
                            parts.put(JSONObject().put("type", "image_url").put("image_url",
                                JSONObject().put("url", "data:$imageMimeSnapshot;base64,$imageDataSnapshot")))
                        }
                        last.put("content", parts)
                    }
                }
                if (provider.id == "anthropic") {
                    AnthropicClient.chat(
                        messages = requestMessages,
                        apiKey = key,
                        baseUrl = baseUrl,
                        model = model
                    )
                } else {
                    OpenAiCompatibleClient.chat(
                        this, requestMessages,
                        providerSnapshot = provider,
                        apiKeySnapshot = key,
                        baseUrlSnapshot = baseUrl,
                        modelSnapshot = model
                    )
                }
            } catch (e: Exception) { "Permintaan gagal: " + (e.message ?: "periksa konfigurasi provider dan koneksi.") }
            runOnUiThread {
                requestMessagesView.removeView(loading)
                val destination = sessions.firstOrNull { it.optString("id") == requestSessionId }
                if (destination != null) {
                    val destinationMessages = destination.optJSONArray("messages") ?: JSONArray()
                    destinationMessages.put(JSONObject().put("role", "assistant").put("content", answer))
                    destination.put("messages", destinationMessages)
                    saveSessions()
                    if (currentSessionId == requestSessionId) {
                        addMessageView("AgentNova", answer, false)
                        if (::sendButton.isInitialized) sendButton.isEnabled = true
                        if (::status.isInitialized) status.text = ""
                        buildDrawer()
                    }
                }
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
        if (!mine && text.isNotBlank()) {
            box.addView(label("🔊 Dengarkan", 11f, muted, true).apply {
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { speakText(text) }
            })
        }
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
        // Subtle glass-like edge: solid palette, no gradient.
        setStroke(dp(1), Color.argb(38, 235, 241, 255))
    }


    private fun toolChip(text: String, action: () -> Unit) =
        label(text, 12f, primary, true).apply {
            gravity = Gravity.CENTER
            background = solid(panel, 8)
            setOnClickListener { action() }
        }

    private fun pickFile() {
        android.app.AlertDialog.Builder(this)
            .setTitle("Tambah sumber")
            .setItems(arrayOf("Pilih file", "Pilih folder")) { _, which ->
                if (which == 0) {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    startActivityForResult(intent, REQ_FILE)
                } else {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    startActivityForResult(intent, REQ_FOLDER)
                }
            }.show()
    }

    @Deprecated("Deprecated in Android API but supported for this activity")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_VOICE) {
            val spoken = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank() && ::prompt.isInitialized) {
                prompt.setText(spoken)
                prompt.setSelection(prompt.text.length)
            }
            return
        }
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        if (requestCode == REQ_FOLDER) {
            try {
                contentResolver.takePersistableUriPermission(uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                attachedName = "Folder dipilih"
                val childUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri,
                    DocumentsContract.getTreeDocumentId(uri))
                val names = mutableListOf<String>()
                contentResolver.query(childUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    while (cursor.moveToNext() && names.size < 100) {
                        if (nameIndex >= 0) names.add(cursor.getString(nameIndex))
                    }
                }
                attachedText = "Daftar isi folder yang dipilih (hingga 100 item):\\n" +
                    names.joinToString("\n").ifBlank { "(Folder kosong atau daftar isi tidak dapat dibaca)" } +
                    "\\n\\nCatatan: hanya nama item folder yang dilampirkan, bukan seluruh isi file."
                showChat()
                Toast.makeText(this, "Folder dipilih.", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Tidak bisa membaca folder: ${e.message}", Toast.LENGTH_LONG).show()
            }
            return
        }
        if (requestCode != REQ_FILE) return
        try {
            val name = queryDisplayName(uri) ?: (uri.lastPathSegment ?: "file")
            attachedName = name
            val mime = contentResolver.getType(uri).orEmpty()
            if (mime.startsWith("image/") && (mime == "image/jpeg" || mime == "image/png" || mime == "image/webp" || mime == "image/gif")) {
                val bytes = contentResolver.openInputStream(uri)?.use { input ->
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > 8 * 1024 * 1024) throw IllegalArgumentException("Ukuran gambar maksimal 8 MB.")
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                } ?: throw IllegalStateException("Gambar tidak dapat dibaca.")
                attachedImageData = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                attachedImageMime = mime
                attachedText = "[Gambar terlampir: $name. Minta AI menganalisis gambar ini.]"
            } else {
                attachedText = if (mime.startsWith("text/") || mime.contains("json") ||
                    mime.contains("xml") || mime.contains("csv") || name.endsWith(".md", true) ||
                    name.endsWith(".kt", true) || name.endsWith(".py", true) || name.endsWith(".log", true)) {
                    contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText().take(80000) }.orEmpty()
                } else {
                    "[File dipilih: $name, tipe: ${mime.ifBlank { "tidak diketahui" }}. Konten biner belum diekstrak.]"
                }
            }
            showChat()
            Toast.makeText(this, "File ditambahkan: $name", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Gagal membaca file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) return it.getString(0)
        }
        return null
    }

    private fun webSearch() {
        val input = EditText(this).apply {
            hint = "Apa yang ingin dicari di web?"
            setTextColor(primary); setHintTextColor(muted)
            setPadding(dp(12), dp(10), dp(12), dp(10))
        }
        val dialog = android.app.AlertDialog.Builder(this)
            .setTitle("Pencarian web")
            .setView(input)
            .setNegativeButton("Batal", null)
            .setPositiveButton("Cari", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val q = input.text.toString().trim()
                if (q.isBlank()) { input.error = "Masukkan kata kunci"; return@setOnClickListener }
                dialog.dismiss()
                statusText("Mencari di web…")
                thread {
                    val result = try {
                        val url = URL("https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(q, "UTF-8"))
                        val c = (url.openConnection() as HttpURLConnection).apply {
                            connectTimeout = 15000; readTimeout = 20000
                            setRequestProperty("User-Agent", "Mozilla/5.0 AgentNova Android")
                        }
                        try {
                            val html = c.inputStream.bufferedReader().use { it.readText() }
                            val anchors = Regex("(?is)<a[^>]*class=[\"'][^\"']*result__a[^\"']*[\"'][^>]*>(.*?)</a>")
                            val snippets = Regex("(?is)<a[^>]*class=[\"'][^\"']*result__snippet[^\"']*[\"'][^>]*>(.*?)</a>")
                            val titles = anchors.findAll(html).take(6).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").htmlUnescape().trim() }.toList()
                            val descs = snippets.findAll(html).take(6).map { it.groupValues[1].replace(Regex("<[^>]+>"), "").htmlUnescape().trim() }.toList()
                            if (titles.isEmpty()) "Tidak ada hasil yang bisa dibaca. Coba kata kunci lain." else
                                titles.mapIndexed { i, t -> "${i + 1}. $t" + (descs.getOrNull(i)?.takeIf { it.isNotBlank() }?.let { "\n$it" } ?: "") }.joinToString("\n\n")
                        } finally { c.disconnect() }
                    } catch (e: Exception) { "Pencarian web gagal: ${e.message}" }
                    runOnUiThread {
                        webContext = "Hasil pencarian web untuk: $q\n$result"
                        if (::prompt.isInitialized) prompt.setText("Rangkum dan jelaskan hasil pencarian web tentang: $q")
                        if (::status.isInitialized) status.text = "Hasil web siap dilampirkan"
                        Toast.makeText(this, "Hasil pencarian disiapkan untuk AgentNova.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        dialog.show()
    }

    private fun String.htmlUnescape(): String = replace("&amp;", "&").replace("&quot;", "\"")
        .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ")

    private fun statusText(text: String) {
        if (::status.isInitialized) status.text = text
        else Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
    }

    private fun showTools() {
        val options = arrayOf("Mode melayang (PiP)", "Buka kamera", "Volume media", "Pengaturan Android", "Pengaturan AgentNova")
        android.app.AlertDialog.Builder(this)
            .setTitle("Tools AgentNova")
            .setItems(options) { _, which ->
                val descriptions = arrayOf(
                    "AgentNova akan masuk ke jendela Picture-in-Picture.",
                    "AgentNova akan membuka aplikasi kamera. Pengambilan foto tetap dilakukan di aplikasi kamera.",
                    "AgentNova akan menampilkan kontrol volume media Android.",
                    "AgentNova akan membuka halaman Pengaturan Android.",
                    "AgentNova akan membuka halaman pengaturan internal."
                )
                android.app.AlertDialog.Builder(this)
                    .setTitle("Konfirmasi tindakan")
                    .setMessage(descriptions[which])
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Lanjutkan") { _, _ ->
                        when (which) {
                            0 -> enterFloatingMode()
                            1 -> try { startActivity(Intent("android.media.action.IMAGE_CAPTURE")) }
                                catch (_: Exception) { Toast.makeText(this, "Aplikasi kamera tidak tersedia.", Toast.LENGTH_SHORT).show() }
                            2 -> volumeDialog()
                            3 -> try { startActivity(Intent(android.provider.Settings.ACTION_SETTINGS)) }
                                catch (_: Exception) { Toast.makeText(this, "Pengaturan tidak tersedia.", Toast.LENGTH_SHORT).show() }
                            4 -> showSettings()
                        }
                    }.show()
            }.show()
    }

    private fun enterFloatingMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Mode melayang tidak didukung perangkat ini.", Toast.LENGTH_SHORT).show()
            }
        } else Toast.makeText(this, "Mode PiP memerlukan Android 8.0 atau lebih baru.", Toast.LENGTH_SHORT).show()
    }

    private fun volumeDialog() {
        val audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER; setPadding(dp(8), dp(8), dp(8), dp(8)) }
        row.addView(smallButton("−") { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI) })
        row.addView(label("  Volume media  ", 14f, primary, true))
        row.addView(smallButton("+") { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI) })
        android.app.AlertDialog.Builder(this).setTitle("Kontrol volume").setView(row).setPositiveButton("Selesai", null).show()
    }

    private fun discoverMcpTools(endpoint: String) {
        if (endpoint.isBlank()) {
            Toast.makeText(this, "Masukkan URL endpoint MCP terlebih dahulu.", Toast.LENGTH_SHORT).show()
            return
        }
        val parsed = try { URL(endpoint) } catch (_: Exception) { null }
        if (parsed == null || (parsed.protocol != "https" && parsed.protocol != "http")) {
            Toast.makeText(this, "Endpoint MCP harus berupa URL HTTP(S) yang valid.", Toast.LENGTH_LONG).show()
            return
        }
        prefs.edit().putString("mcp_url", endpoint).apply()
        Toast.makeText(this, "Memulai koneksi MCP…", Toast.LENGTH_SHORT).show()
        thread {
            val result = try {
                var sessionId: String? = null
                var negotiatedProtocol: String? = null
                var requestId = 1

                fun rpc(method: String, params: JSONObject, notification: Boolean = false): JSONObject? {
                    val c = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"
                        connectTimeout = 12000
                        readTimeout = 25000
                        doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json, text/event-stream")
                        sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
                        negotiatedProtocol?.let { setRequestProperty("MCP-Protocol-Version", it) }
                    }
                    try {
                        val payload = JSONObject()
                            .put("jsonrpc", "2.0")
                            .put("method", method)
                            .put("params", params)
                        if (!notification) payload.put("id", requestId++)
                        c.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        val code = c.responseCode
                        c.getHeaderField("Mcp-Session-Id")?.takeIf { it.isNotBlank() }?.let { sessionId = it }
                        val raw = (if (code in 200..299) c.inputStream else c.errorStream)
                            ?.bufferedReader()?.use { it.readText() }.orEmpty()
                        if (code !in 200..299) throw IllegalStateException("MCP HTTP $code: ${raw.take(300)}")
                        if (notification || raw.isBlank()) return null
                        val jsonLine = raw.lineSequence()
                            .map { it.trim().removePrefix("data:").trim() }
                            .firstOrNull { it.startsWith("{") }
                            ?: throw IllegalStateException("Respons MCP bukan JSON/SSE yang dikenali.")
                        val response = JSONObject(jsonLine)
                        if (response.has("error")) {
                            val error = response.optJSONObject("error")
                            throw IllegalStateException("MCP: ${error?.optString("message") ?: "RPC error"}")
                        }
                        return response.optJSONObject("result")
                            ?: throw IllegalStateException("Respons MCP tidak memiliki result.")
                    } finally {
                        c.disconnect()
                    }
                }

                val init = rpc(
                    "initialize",
                    JSONObject()
                        .put("protocolVersion", "2024-11-05")
                        .put("capabilities", JSONObject())
                        .put("clientInfo", JSONObject().put("name", "AgentNova").put("version", "1.0"))
                ) ?: throw IllegalStateException("Server tidak mengembalikan hasil initialize.")
                val protocol = init.optString("protocolVersion", "2024-11-05")
                negotiatedProtocol = protocol
                rpc("notifications/initialized", JSONObject(), notification = true)
                val discoveredTools = mutableListOf<JSONObject>()
                var cursor: String? = null
                var pages = 0
                do {
                    val params = JSONObject()
                    cursor?.let { params.put("cursor", it) }
                    val toolsResult = rpc("tools/list", params)
                        ?: throw IllegalStateException("Server tidak mengembalikan daftar tools.")
                    val tools = toolsResult.optJSONArray("tools") ?: JSONArray()
                    for (i in 0 until tools.length()) {
                        tools.optJSONObject(i)?.let { discoveredTools.add(it) }
                        if (discoveredTools.size >= 100) break
                    }
                    cursor = toolsResult.optString("nextCursor").takeIf { it.isNotBlank() && it != "null" }
                    pages++
                } while (cursor != null && pages < 10 && discoveredTools.size < 100)
                val names = discoveredTools.mapNotNull { tool ->
                    val name = tool.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val description = tool.optString("description").takeIf { it.isNotBlank() }
                    if (description == null) name else "$name — $description"
                }
                "MCP aktif (protocol $protocol). Ditemukan ${names.size} tools:\\n" +
                    names.joinToString("\\n").ifBlank { "(Server belum menyediakan tools.)" }
            } catch (e: Exception) {
                "MCP gagal: ${e.message ?: "kesalahan koneksi"}"
            }
            prefs.edit().putString("mcp_tools", result).apply()
            runOnUiThread {
                Toast.makeText(this, result.take(220), Toast.LENGTH_LONG).show()
                if (::page.isInitialized && page.childCount > 0) {
                    // Refresh settings so the latest discovery result is visible.
                    if (mcpField.isInitialized) showSettings()
                }
            }
        }
    }


    /** Explicit, user-approved MCP invocation; never auto-runs discovered tools. */
    private fun mcpToolDialog() {
        val nameField = EditText(this).apply { hint = "Nama tool persis seperti tools/list"; setTextColor(primary); setHintTextColor(muted); isSingleLine = true }
        val argsField = EditText(this).apply { hint = "Arguments JSON, contoh: {\"query\":\"hello\"}"; setTextColor(primary); setHintTextColor(muted); minLines = 3; gravity = Gravity.TOP }
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(4), dp(16), 0)
            addView(nameField); addView(argsField)
        }
        val dialog = android.app.AlertDialog.Builder(this).setTitle("Panggil MCP tool")
            .setMessage("Masukkan nama tool dari hasil discovery dan argumen JSON.")
            .setView(box).setNegativeButton("Batal", null).setPositiveButton("Tinjau", null).create()
        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val toolName = nameField.text.toString().trim()
                val rawArgs = argsField.text.toString().trim().ifBlank { "{}" }
                if (toolName.isBlank()) { nameField.error = "Nama tool wajib diisi"; return@setOnClickListener }
                val args = try { JSONObject(rawArgs) } catch (_: Exception) { argsField.error = "JSON tidak valid"; return@setOnClickListener }
                dialog.dismiss()
                android.app.AlertDialog.Builder(this).setTitle("Konfirmasi MCP")
                    .setMessage("Tool: $toolName\nArgumen:\n" + args.toString(2).take(1800) +
                        "\n\nTool eksternal dapat membaca atau mengubah data sesuai izin server.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Jalankan") { _, _ -> executeMcpTool(toolName, args) }.show()
            }
        }
        dialog.show()
    }

    private fun executeMcpTool(toolName: String, arguments: JSONObject) {
        val endpoint = prefs.getString("mcp_url", "").orEmpty().trim()
        val parsed = try { URL(endpoint) } catch (_: Exception) { null }
        if (parsed == null || (parsed.protocol != "https" && parsed.protocol != "http")) {
            Toast.makeText(this, "Simpan endpoint MCP HTTP(S) yang valid terlebih dahulu.", Toast.LENGTH_LONG).show(); return
        }
        statusText("Menjalankan MCP tool…")
        thread {
            val output = try {
                var sessionId: String? = null
                var protocol: String? = null
                var nextId = 1
                fun rpc(method: String, params: JSONObject, notification: Boolean = false): JSONObject? {
                    val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                        requestMethod = "POST"; connectTimeout = 12000; readTimeout = 30000; doOutput = true
                        setRequestProperty("Content-Type", "application/json")
                        setRequestProperty("Accept", "application/json, text/event-stream")
                        sessionId?.let { setRequestProperty("Mcp-Session-Id", it) }
                        protocol?.let { setRequestProperty("MCP-Protocol-Version", it) }
                    }
                    try {
                        val payload = JSONObject().put("jsonrpc", "2.0").put("method", method).put("params", params)
                        if (!notification) payload.put("id", nextId++)
                        conn.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode
                        conn.getHeaderField("Mcp-Session-Id")?.takeIf { it.isNotBlank() }?.let { sessionId = it }
                        val raw = (if (code in 200..299) conn.inputStream else conn.errorStream)
                            ?.bufferedReader()?.use { it.readText().take(250000) }.orEmpty()
                        if (code !in 200..299) throw IllegalStateException("MCP HTTP $code: " + raw.take(500))
                        if (notification || raw.isBlank()) return null
                        val data = raw.lineSequence().filter { it.trim().startsWith("data:") }
                            .map { it.trim().removePrefix("data:").trim() }.firstOrNull { it.startsWith("{") }
                            ?: raw.trim().takeIf { it.startsWith("{") }
                            ?: throw IllegalStateException("Respons MCP bukan JSON/SSE yang dikenali.")
                        val response = JSONObject(data)
                        if (response.has("error")) throw IllegalStateException(response.optJSONObject("error")?.optString("message") ?: "RPC error")
                        return response.optJSONObject("result") ?: throw IllegalStateException("Result MCP kosong.")
                    } finally { conn.disconnect() }
                }
                val init = rpc("initialize", JSONObject().put("protocolVersion", "2024-11-05")
                    .put("capabilities", JSONObject())
                    .put("clientInfo", JSONObject().put("name", "AgentNova").put("version", "1.0")))
                    ?: throw IllegalStateException("Initialize tidak mengembalikan result.")
                protocol = init.optString("protocolVersion", "2024-11-05")
                rpc("notifications/initialized", JSONObject(), true)
                val result = rpc("tools/call", JSONObject().put("name", toolName).put("arguments", arguments))
                    ?: throw IllegalStateException("Tool tidak mengembalikan result.")
                val content = result.optJSONArray("content") ?: JSONArray()
                val textOutput = (0 until content.length()).mapNotNull { i ->
                    val item = content.optJSONObject(i) ?: return@mapNotNull null
                    if (item.optString("type") == "text") item.optString("text")
                    else if (item.optString("type") == "image") "[Image output belum dapat ditampilkan]"
                    else null
                }.joinToString("\n")
                if (result.optBoolean("isError")) "Tool melaporkan error:\n$textOutput"
                else textOutput.ifBlank { result.toString(2).take(6000) }
            } catch (e: Exception) { "MCP tool gagal: " + (e.message ?: "kesalahan tidak diketahui") }
            runOnUiThread {
                if (::status.isInitialized) status.text = "MCP selesai"
                android.app.AlertDialog.Builder(this).setTitle("Hasil MCP tool")
                    .setMessage(output.take(10000)).setPositiveButton("Tutup", null).show()
            }
        }
    }

    private fun startVoiceInput() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, java.util.Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara dengan AgentNova")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }
        try { startActivityForResult(intent, REQ_VOICE) }
        catch (_: Exception) { Toast.makeText(this, "Layanan pengenalan suara tidak tersedia.", Toast.LENGTH_LONG).show() }
    }

    private fun speakText(text: String) {
        if (speechOutput == null) {
            speechOutput = TextToSpeech(this) { state ->
                if (state == TextToSpeech.SUCCESS) {
                    speechOutput?.language = Locale.getDefault()
                    speechOutput?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "agentnova_reply")
                } else Toast.makeText(this, "Text-to-speech tidak tersedia.", Toast.LENGTH_SHORT).show()
            }
        } else {
            speechOutput?.stop()
            speechOutput?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "agentnova_reply")
        }
    }

    override fun onDestroy() {
        speechOutput?.stop()
        speechOutput?.shutdown()
        speechOutput = null
        super.onDestroy()
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
