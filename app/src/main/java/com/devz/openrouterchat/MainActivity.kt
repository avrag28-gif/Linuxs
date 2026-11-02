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
    private val model = "nvidia/nemotron-3.5-super-vl:free"
    private val prefs by lazy { getSharedPreferences("nova_settings", Context.MODE_PRIVATE) }
    private lateinit var root: FrameLayout
    private lateinit var contentColumn: LinearLayout
    private lateinit var page: LinearLayout
    private lateinit var drawer: LinearLayout
    private lateinit var scrim: View
    private lateinit var messages: LinearLayout
    private lateinit var prompt: EditText
    private lateinit var keyField: EditText
    private lateinit var status: TextView
    private lateinit var sendButton: TextView
    private var drawerOpen = false
    private var showKey = false
    private var accent = Color.rgb(133, 115, 255)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 12, 25)
        window.navigationBarColor = Color.rgb(10, 12, 25)
        window.decorView.systemUiVisibility = 0
        buildShell()
        showChat()
    }

    private fun buildShell() {
        root = FrameLayout(this).apply { setBackgroundColor(Color.rgb(10, 12, 25)) }
        contentColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(13, 17, 39), Color.rgb(22, 18, 48), Color.rgb(9, 20, 34)))
        }
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(12))
        }
        val menu = label("☰", 25f, Color.WHITE).apply {
            gravity = Gravity.CENTER
            background = glass(18)
            setPadding(dp(13), dp(8), dp(13), dp(8))
            setOnClickListener { toggleDrawer() }
        }
        top.addView(menu, LinearLayout.LayoutParams(dp(48), dp(44)))
        val brand = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), 0, 0, 0)
        }
        brand.addView(label("NOVA AI", 19f, Color.WHITE, true))
        brand.addView(label("YOUR SPACE TO THINK", 9f, Color.rgb(164, 169, 204), true))
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        val online = label("●", 13f, Color.rgb(102, 255, 197)).apply {
            background = glass(30)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        top.addView(online)
        contentColumn.addView(top)
        val divider = View(this).apply { setBackgroundColor(0x22FFFFFF) }
        contentColumn.addView(divider, LinearLayout.LayoutParams(-1, dp(1)))
        page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(14))
        }
        contentColumn.addView(page, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(contentColumn, FrameLayout.LayoutParams(-1, -1))

        scrim = View(this).apply {
            setBackgroundColor(0x99050A18.toInt())
            visibility = View.GONE
            setOnClickListener { closeDrawer() }
        }
        root.addView(scrim, FrameLayout.LayoutParams(-1, -1))
        drawer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(38), dp(16), dp(20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.rgb(28, 30, 62), Color.rgb(14, 23, 43))).apply {
                setCornerRadii(floatArrayOf(0f,0f,dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),dp(26).toFloat(),0f,0f))
                setStroke(dp(1), 0x55FFFFFF)
            }
            elevation = dp(24).toFloat()
            visibility = View.GONE
        }
        root.addView(drawer, FrameLayout.LayoutParams(dp(292), -1, Gravity.START))
        setContentView(root)
        buildDrawer()
    }

    private fun buildDrawer() {
        drawer.removeAllViews()
        drawer.addView(label("✦  NOVA", 27f, Color.WHITE, true))
        drawer.addView(label("LIQUID GLASS AI", 10f, Color.rgb(163, 154, 255), true).apply {
            setPadding(0, dp(4), 0, dp(32))
        })
        drawer.addView(label("WORKSPACE", 10f, Color.rgb(140, 149, 184), true).apply {
            setPadding(dp(8), 0, 0, dp(10))
        })
        drawerItem("✧", "Chat baru", "Percakapan AI") { showChat(); closeDrawer() }
        drawerItem("⚙", "Pengaturan", "API key & preferensi") { showSettings(); closeDrawer() }
        drawerItem("◈", "Model AI", "Nemotron · Free") { showModelInfo(); closeDrawer() }
        val spacer = View(this)
        drawer.addView(spacer, LinearLayout.LayoutParams(1, 0, 1f))
        val note = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(20)
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        note.addView(label("✦  NOVA AI", 15f, Color.WHITE, true))
        note.addView(label("Made for ideas that matter.", 11f, Color.rgb(180, 186, 216)).apply {
            setPadding(0, dp(5), 0, 0)
        })
        drawer.addView(note)
    }

    private fun drawerItem(icon: String, title: String, subtitle: String, action: () -> Unit) {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = glass(18)
            setPadding(dp(13), dp(13), dp(10), dp(13))
            setOnClickListener { action() }
        }
        item.addView(label(icon, 23f, Color.rgb(178, 164, 255), true), LinearLayout.LayoutParams(dp(38), -2))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(label(title, 15f, Color.WHITE, true))
        texts.addView(label(subtitle, 10f, Color.rgb(163, 171, 202)).apply { setPadding(0, dp(3), 0, 0) })
        item.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10)
        drawer.addView(item, lp)
    }

    private fun toggleDrawer() { if (drawerOpen) closeDrawer() else openDrawer() }
    private fun openDrawer() {
        if (drawerOpen) return
        drawerOpen = true
        scrim.visibility = View.VISIBLE
        drawer.visibility = View.VISIBLE
        val anim = TranslateAnimation(-dp(300).toFloat(), 0f, 0f, 0f).apply {
            duration = 230; interpolator = DecelerateInterpolator()
        }
        drawer.startAnimation(anim)
    }
    private fun closeDrawer() {
        if (!drawerOpen) return
        drawerOpen = false
        val anim = TranslateAnimation(0f, -dp(300).toFloat(), 0f, 0f).apply {
            duration = 180; interpolator = DecelerateInterpolator()
        }
        drawer.startAnimation(anim)
        drawer.visibility = View.GONE
        scrim.visibility = View.GONE
    }

    private fun showChat() {
        page.removeAllViews()
        val heading = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        heading.addView(label("Halo, explorer. ✨", 25f, Color.WHITE, true))
        heading.addView(label("Apa yang ingin kamu ciptakan hari ini?", 13f, Color.rgb(174, 181, 213)).apply {
            setPadding(0, dp(7), 0, dp(16))
        })
        page.addView(heading)
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x443C4BAA, 0x332C8BCA, 0x222D205F)).apply {
                cornerRadius = dp(24).toFloat()
                setStroke(dp(1), 0x55DAD6FF)
            }
            setPadding(dp(17), dp(16), dp(17), dp(16))
        }
        hero.addView(label("◈  NOVA INTELLIGENCE", 10f, Color.rgb(177, 166, 255), true))
        hero.addView(label("Ide besar dimulai dari satu pesan.", 18f, Color.WHITE, true).apply {
            setPadding(0, dp(8), 0, dp(5))
        })
        hero.addView(label("Powered by Nemotron · Free model", 11f, Color.rgb(183, 195, 223)))
        val heroLp = LinearLayout.LayoutParams(-1, -2)
        heroLp.bottomMargin = dp(16)
        page.addView(hero, heroLp)

        val historyScroll = ScrollView(this).apply { clipToPadding = false }
        messages = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        messages.addView(messageCard("NOVA", "Aku NOVA, partner AI kamu. Tanya apa saja, brainstorm ide, atau mulai proyek baru. 🌌", false))
        historyScroll.addView(messages)
        page.addView(historyScroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = glass(24)
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        prompt = EditText(this).apply {
            hint = "Tulis sesuatu untuk NOVA..."
            setTextColor(Color.WHITE); setHintTextColor(Color.rgb(132, 143, 177))
            textSize = 14f; minLines = 1; maxLines = 4
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            setPadding(dp(6), dp(5), dp(6), dp(7))
            background = null
        }
        composer.addView(prompt, LinearLayout.LayoutParams(-1, -2))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        status = label("●  Siap membantu", 10f, Color.rgb(151, 163, 199))
        bottom.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        sendButton = label("➤  Kirim", 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(123, 98, 255), Color.rgb(72, 145, 255))).apply { cornerRadius = dp(15).toFloat() }
            setPadding(dp(17), dp(11), dp(17), dp(11))
            setOnClickListener { submit() }
        }
        bottom.addView(sendButton)
        composer.addView(bottom)
        val composerLp = LinearLayout.LayoutParams(-1, -2)
        composerLp.topMargin = dp(12)
        page.addView(composer, composerLp)
        if (prefs.getString("api_key", "").isNullOrBlank()) {
            status.text = "●  Tambahkan API key di Pengaturan"
            status.setTextColor(Color.rgb(255, 202, 120))
        }
    }

    private fun showSettings() {
        page.removeAllViews()
        page.addView(label("Pengaturan", 27f, Color.WHITE, true))
        page.addView(label("Atur NOVA sesuai gaya kamu.", 13f, Color.rgb(174, 181, 213)).apply {
            setPadding(0, dp(6), 0, dp(20))
        })
        val scroll = ScrollView(this)
        val settings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        settings.addView(sectionTitle("KONEKSI AI"))
        val keyCard = cardColumn()
        keyCard.addView(label("OpenRouter API Key", 14f, Color.WHITE, true))
        keyCard.addView(label("Key disimpan lokal di perangkat ini.", 11f, Color.rgb(163, 171, 202)).apply {
            setPadding(0, dp(5), 0, dp(10))
        })
        keyField = EditText(this).apply {
            hint = "sk-or-v1-..."
            setText(prefs.getString("api_key", ""))
            setTextColor(Color.WHITE); setHintTextColor(Color.rgb(132, 143, 177))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            textSize = 13f; singleLine = true
            setPadding(dp(12), dp(11), dp(12), dp(11))
            background = glass(13)
        }
        keyCard.addView(keyField, LinearLayout.LayoutParams(-1, -2))
        val keyActions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(10), 0, 0)
        }
        val show = label("Lihat / Sembunyikan", 11f, Color.rgb(195, 190, 255), true).apply {
            setPadding(dp(3), dp(9), dp(12), dp(9))
            setOnClickListener {
                showKey = !showKey
                keyField.inputType = InputType.TYPE_CLASS_TEXT or if (showKey) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD else InputType.TYPE_TEXT_VARIATION_PASSWORD
                keyField.setSelection(keyField.text.length)
            }
        }
        keyActions.addView(show, LinearLayout.LayoutParams(0, -2, 1f))
        keyActions.addView(actionButton("Simpan key") {
            prefs.edit().putString("api_key", keyField.text.toString().trim()).apply()
            Toast.makeText(this, "API key tersimpan di perangkat.", Toast.LENGTH_SHORT).show()
        })
        keyCard.addView(keyActions)
        settings.addView(keyCard, sectionLp())

        settings.addView(sectionTitle("MODEL"))
        val modelCard = cardColumn()
        modelCard.addView(label("Nemotron 3.5 Super VL", 16f, Color.WHITE, true))
        modelCard.addView(label(model, 10f, Color.rgb(174, 181, 213)).apply { setPadding(0, dp(6), 0, dp(9)) })
        modelCard.addView(label("Provider: OpenRouter  ·  Tier: Free", 11f, Color.rgb(102, 255, 197)))
        settings.addView(modelCard, sectionLp())

        settings.addView(sectionTitle("TAMPILAN"))
        val lookCard = cardColumn()
        lookCard.addView(label("Liquid Glass", 15f, Color.WHITE, true))
        lookCard.addView(label("Dark space • glass panels • violet neon", 11f, Color.rgb(174, 181, 213)).apply {
            setPadding(0, dp(5), 0, dp(12))
        })
        val accentRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        accentRow.addView(label("Aksen neon", 12f, Color.rgb(195, 201, 228)), LinearLayout.LayoutParams(0, -2, 1f))
        val colors = listOf(Color.rgb(133,115,255), Color.rgb(65,210,255), Color.rgb(255,100,190), Color.rgb(102,255,197))
        colors.forEach { c ->
            val dot = View(this).apply {
                background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(c); setStroke(dp(2), if (accent == c) Color.WHITE else 0x55FFFFFF) }
                setOnClickListener { accent = c; Toast.makeText(this@MainActivity, "Aksen diperbarui untuk sesi ini.", Toast.LENGTH_SHORT).show() }
            }
            val lp = LinearLayout.LayoutParams(dp(27), dp(27)); lp.leftMargin = dp(8)
            accentRow.addView(dot, lp)
        }
        lookCard.addView(accentRow)
        settings.addView(lookCard, sectionLp())

        settings.addView(sectionTitle("DATA & PRIVASI"))
        val privacy = cardColumn()
        privacy.addView(label("API key hanya disimpan pada preferensi lokal aplikasi; jangan bagikan key ke orang lain.", 12f, Color.rgb(174, 181, 213)))
        val clear = actionButton("Hapus API key") {
            prefs.edit().remove("api_key").apply()
            keyField.setText("")
            Toast.makeText(this, "API key dihapus.", Toast.LENGTH_SHORT).show()
        }
        val clearLp = LinearLayout.LayoutParams(-2, -2); clearLp.topMargin = dp(12)
        privacy.addView(clear, clearLp)
        settings.addView(privacy, sectionLp())
        settings.addView(label("NOVA AI  •  Liquid Glass Edition", 10f, Color.rgb(117, 129, 166)).apply {
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(16))
        })
        scroll.addView(settings)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
    }

    private fun showModelInfo() {
        page.removeAllViews()
        page.addView(label("Model AI", 27f, Color.WHITE, true))
        page.addView(label("Model yang digunakan NOVA.", 13f, Color.rgb(174,181,213)).apply { setPadding(0, dp(8), 0, dp(20)) })
        val c = cardColumn()
        c.addView(label("◈  Nemotron 3.5 Super VL", 18f, Color.WHITE, true))
        c.addView(label(model, 11f, Color.rgb(195,190,255)).apply { setPadding(0, dp(9), 0, dp(12)) })
        c.addView(label("Model ini dipanggil melalui OpenRouter. Ketersediaan model gratis, kuota, dan respons dapat berubah mengikuti kebijakan provider.", 13f, Color.rgb(174,181,213)))
        page.addView(c)
        page.addView(actionButton("Kembali ke chat") { showChat() }.apply { setPadding(dp(18), dp(13), dp(18), dp(13)) },
            LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(16) })
    }

    private fun submit() {
        val key = prefs.getString("api_key", "").orEmpty().trim()
        val message = prompt.text.toString().trim()
        if (key.isEmpty()) {
            Toast.makeText(this, "Tambahkan API key lewat ☰ → Pengaturan.", Toast.LENGTH_LONG).show()
            showSettings()
            return
        }
        if (message.isEmpty()) { prompt.error = "Tulis pesan terlebih dahulu"; return }
        addMessage("KAMU", message, true)
        prompt.setText("")
        sendButton.isEnabled = false
        sendButton.alpha = 0.55f
        status.text = "◌  NOVA sedang berpikir..."
        val loading = messageCard("NOVA", "Sedang menyusun jawaban…", false)
        messages.addView(loading)
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
                val body = JSONObject().put("model", model).put("messages",
                    JSONArray().put(JSONObject().put("role", "user").put("content", message)))
                connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val raw = stream?.bufferedReader()?.use { it.readText() } ?: ""
                if (code !in 200..299) {
                    val detail = try { JSONObject(raw).optJSONObject("error")?.optString("message") ?: raw } catch (_: Exception) { raw }
                    "Permintaan gagal (HTTP $code).\n$detail"
                } else {
                    val content = JSONObject(raw).optJSONArray("choices")?.optJSONObject(0)
                        ?.optJSONObject("message")?.opt("content")
                    when (content) {
                        is String -> content.ifBlank { "Respons kosong dari model." }
                        is JSONArray -> (0 until content.length()).mapNotNull { i ->
                            val part = content.optJSONObject(i)
                            part?.optString("text")?.takeIf { it.isNotBlank() }
                        }.joinToString("\n").ifBlank { "Model mengirim format respons yang belum didukung." }
                        else -> "Respons kosong dari model."
                    }
                }
            } catch (e: Exception) {
                "Koneksi gagal: ${e.message ?: "periksa internet dan API key kamu."}"
            } finally { connection?.disconnect() }
            runOnUiThread {
                messages.removeView(loading)
                addMessage("NOVA", answer, false)
                sendButton.isEnabled = true
                sendButton.alpha = 1f
                status.text = "●  Selesai"
            }
        }
    }

    private fun addMessage(who: String, text: String, mine: Boolean) {
        messages.addView(messageCard(who, text, mine))
    }

    private fun messageCard(who: String, text: String, mine: Boolean): LinearLayout {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = if (mine) GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0x553F51A6, 0x443E3687)).apply { cornerRadius = dp(19).toFloat(); setStroke(dp(1), 0x44A7A0FF) } else glass(19)
            setPadding(dp(14), dp(12), dp(14), dp(13))
        }
        box.addView(label(if (mine) "◉  $who" else "✦  $who", 10f,
            if (mine) Color.rgb(190,181,255) else Color.rgb(103,238,205), true))
        box.addView(label(text, 14f, Color.rgb(239,242,255)).apply {
            setPadding(0, dp(7), 0, 0)
            setTextIsSelectable(true)
        })
        val lp = LinearLayout.LayoutParams(-1, -2)
        lp.bottomMargin = dp(10)
        if (mine) lp.leftMargin = dp(22) else lp.rightMargin = dp(22)
        box.layoutParams = lp
        return box
    }

    private fun sectionTitle(s: String) = label(s, 10f, Color.rgb(150, 158, 194), true).apply {
        setPadding(dp(4), dp(8), 0, dp(10))
    }
    private fun sectionLp() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
    private fun cardColumn() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = glass(20)
        setPadding(dp(15), dp(15), dp(15), dp(15))
    }
    private fun actionButton(text: String, action: () -> Unit): TextView =
        label(text, 12f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(112, 87, 242), Color.rgb(61, 131, 237))).apply { cornerRadius = dp(13).toFloat() }
            setPadding(dp(13), dp(10), dp(13), dp(10))
            setOnClickListener { action() }
        }

    private fun label(text: String, size: Float, color: Int, bold: Boolean = false): TextView =
        TextView(this).apply {
            this.text = text; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

    private fun glass(radius: Int) = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = dp(radius).toFloat()
        setColor(0x1FFFFFFF)
        setStroke(dp(1), 0x35FFFFFF)
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
}
