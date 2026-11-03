package com.devz.openrouterchat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * SharedPreferences-backed registry for OpenAI-compatible cloud and local providers.
 * API keys remain on-device. Never log request headers or key values.
 */
data class AiProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val defaultModel: String,
    val requiresKey: Boolean = true
)

object ProviderRegistry {
    val providers = listOf(
        AiProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free"),
        AiProvider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
        AiProvider("gemini", "Google Gemini (OpenAI-compatible)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.0-flash"),
        AiProvider("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
        AiProvider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
        AiProvider("together", "Together AI", "https://api.together.xyz/v1", "meta-llama/Llama-3.3-70B-Instruct-Turbo"),
        AiProvider("ollama", "Ollama (local/network)", "http://localhost:11434/v1", "llama3.2", false),
        AiProvider("lmstudio", "LM Studio (local/network)", "http://localhost:1234/v1", "local-model", false),
        AiProvider("custom", "Custom OpenAI-compatible", "", "", false)
    )

    fun selected(context: Context): AiProvider {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        val id = prefs.getString("provider_id", "openrouter")
        return providers.firstOrNull { it.id == id } ?: providers.first()
    }

    fun apiKey(context: Context, provider: AiProvider = selected(context)): String {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getString("api_key_${provider.id}", null)
            ?: if (provider.id == "openrouter") prefs.getString("api_key", "") else ""
            ?: ""
    }

    fun baseUrl(context: Context, provider: AiProvider = selected(context)): String {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getString("base_url_${provider.id}", null)
            ?: if (provider.id == "custom") prefs.getString("custom_base_url", "") else provider.baseUrl
            ?: provider.baseUrl
    }

    fun model(context: Context, provider: AiProvider = selected(context)): String {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getString("model_${provider.id}", null)
            ?: if (provider.id == "openrouter") prefs.getString("model", provider.defaultModel) else provider.defaultModel
            ?: provider.defaultModel
    }

    fun save(context: Context, providerId: String, key: String, baseUrl: String, model: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: providers.last()
        context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE).edit()
            .putString("provider_id", provider.id)
            .putString("api_key_${provider.id}", key.trim())
            .putString("base_url_${provider.id}", baseUrl.trim().trimEnd('/'))
            .putString("model_${provider.id}", model.trim())
            .apply()
    }
}

object OpenAiCompatibleClient {
    fun chat(
        context: Context,
        messages: JSONArray,
        providerSnapshot: AiProvider? = null,
        apiKeySnapshot: String? = null,
        baseUrlSnapshot: String? = null,
        modelSnapshot: String? = null,
        connectTimeoutMs: Int = 20000,
        readTimeoutMs: Int = 90000
    ): String {
        // Optional snapshots keep an in-flight request independent from later settings edits.
        val provider = providerSnapshot ?: ProviderRegistry.selected(context)
        val endpoint = (baseUrlSnapshot ?: ProviderRegistry.baseUrl(context, provider)).trimEnd('/') + "/chat/completions"
        val key = apiKeySnapshot ?: ProviderRegistry.apiKey(context, provider)
        val model = modelSnapshot ?: ProviderRegistry.model(context, provider)
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://")) { "Base URL provider belum valid." }
        require(!provider.requiresKey || key.isNotBlank()) { "API key untuk ${provider.label} belum diisi." }
        require(model.isNotBlank()) { "Nama model belum diisi." }

        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            if (key.isNotBlank()) setRequestProperty("Authorization", "Bearer $key")
            if (provider.id == "openrouter") {
                setRequestProperty("HTTP-Referer", "https://github.com/avrag28-gif/Linuxs")
                setRequestProperty("X-Title", "AgentNova")
            }
        }
        try {
            val body = JSONObject().put("model", model).put("messages", messages).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: raw
                } catch (_: Exception) { raw }
                throw IllegalStateException("HTTP $code: ${detail.take(700)}")
            }
            val content = JSONObject(raw).optJSONArray("choices")
                ?.optJSONObject(0)?.optJSONObject("message")?.opt("content")
            return when (content) {
                is String -> content.ifBlank { "Respons kosong dari model." }
                is JSONArray -> (0 until content.length()).mapNotNull { i ->
                    content.optJSONObject(i)?.optString("text")?.takeIf { it.isNotBlank() }
                }.joinToString("\n").ifBlank { "Format respons belum didukung." }
                else -> "Respons kosong atau format respons belum didukung."
            }
        } finally {
            connection.disconnect()
        }
    }
}
