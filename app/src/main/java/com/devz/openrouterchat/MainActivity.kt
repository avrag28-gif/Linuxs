package com.devz.openrouterchat

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.view.Gravity
import android.widget.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var apiKey: EditText
    private lateinit var prompt: EditText
    private lateinit var output: TextView
    private lateinit var send: Button
    private val model = "nvidia/nemotron-3.5-super-vl:free"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(20, 24, 35)
        window.navigationBarColor = Color.rgb(20, 24, 35)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 24, 20, 16)
            setBackgroundColor(Color.rgb(20, 24, 35))
        }
        root.addView(TextView(this).apply {
            text = "OpenRouter Chat"; textSize = 26f
            setTextColor(Color.WHITE); gravity = Gravity.CENTER
        }, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply {
            text = "Model: $model"; textSize = 12f; setTextColor(Color.LTGRAY)
            setPadding(0, 8, 0, 12)
        })
        apiKey = EditText(this).apply {
            hint = "Masukkan OpenRouter API key"; setSingleLine(true)
            inputType = 129; setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        root.addView(apiKey, LinearLayout.LayoutParams(-1, -2))
        prompt = EditText(this).apply {
            hint = "Tulis pesan untuk AI..."; minLines = 2; maxLines = 5
            setTextColor(Color.WHITE); setHintTextColor(Color.GRAY)
        }
        root.addView(prompt, LinearLayout.LayoutParams(-1, -2))
        send = Button(this).apply { text = "Kirim ke AI" }
        root.addView(send, LinearLayout.LayoutParams(-1, -2))
        val scroll = ScrollView(this)
        output = TextView(this).apply {
            text = "Jawaban AI akan muncul di sini."; textSize = 16f
            setTextColor(Color.WHITE); setPadding(4, 16, 4, 16)
        }
        scroll.addView(output)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        send.setOnClickListener { submit() }
    }

    private fun submit() {
        val key = apiKey.text.toString().trim()
        val message = prompt.text.toString().trim()
        if (key.isEmpty()) { apiKey.error = "Masukkan API key"; return }
        if (message.isEmpty()) { prompt.error = "Tulis pesan"; return }
        send.isEnabled = false
        output.text = "Menghubungi OpenRouter..."
        thread {
            var connection: HttpURLConnection? = null
            val answer = try {
                connection = (URL("https://openrouter.ai/api/v1/chat/completions").openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"; connectTimeout = 20000; readTimeout = 60000; doOutput = true
                    setRequestProperty("Authorization", "Bearer $key")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("HTTP-Referer", "https://github.com/avrag28-gif/Linuxs")
                    setRequestProperty("X-Title", "OpenRouter Chat Android")
                }
                val body = JSONObject().put("model", model).put("messages",
                    org.json.JSONArray().put(JSONObject().put("role", "user").put("content", message)))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream.bufferedReader().use { it.readText() }
                if (code !in 200..299) "HTTP $code\n$raw" else {
                    JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.optString("content")
                        ?.takeIf { it.isNotBlank() } ?: "Respons kosong: $raw"
                }
            } catch (e: Exception) {
                "Gagal: ${e.message ?: "kesalahan jaringan"}"
            } finally { connection?.disconnect() }
            runOnUiThread { output.text = answer; send.isEnabled = true }
        }
    }
}
