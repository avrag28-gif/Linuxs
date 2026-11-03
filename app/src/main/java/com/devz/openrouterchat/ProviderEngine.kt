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
        AiProvider("anthropic", "Anthropic Claude (native API)", "https://api.anthropic.com/v1", "claude-3-5-haiku-latest"),
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
            ?: if (provider.id == "openrouter") prefs.getString("api_key", "").orEmpty() else ""
    }

    fun baseUrl(context: Context, provider: AiProvider = selected(context)): String {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getString("base_url_${provider.id}", null)
            ?: if (provider.id == "custom") prefs.getString("custom_base_url", "").orEmpty() else provider.baseUrl
    }

    fun model(context: Context, provider: AiProvider = selected(context)): String {
        val prefs = context.getSharedPreferences("nova_settings", Context.MODE_PRIVATE)
        return prefs.getString("model_${provider.id}", null)
            ?: if (provider.id == "openrouter") prefs.getString("model", provider.defaultModel).orEmpty() else provider.defaultModel
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

/**
 * Native Anthropic Messages API client. Claude does not use the OpenAI
 * /chat/completions protocol; system messages and content blocks are mapped
 * explicitly here. API keys are never logged.
 */
object AnthropicClient {
    fun chat(
        messages: JSONArray,
        apiKey: String,
        baseUrl: String,
        model: String,
        connectTimeoutMs: Int = 20000,
        readTimeoutMs: Int = 90000
    ): String {
        require(apiKey.isNotBlank()) { "API key Anthropic belum diisi." }
        require(model.isNotBlank()) { "Nama model Claude belum diisi." }
        val endpoint = baseUrl.trimEnd('/') + "/messages"
        require(endpoint.startsWith("https://")) { "Anthropic API wajib menggunakan HTTPS." }
        val systemParts = mutableListOf<String>()
        val conversation = JSONArray()
        for (i in 0 until messages.length()) {
            val item = messages.optJSONObject(i) ?: continue
            val role = item.optString("role")
            val contentValue = item.opt("content")
            if (role == "system") {
                val systemText = if (contentValue is String) contentValue else contentValue?.toString().orEmpty()
                if (systemText.isNotBlank()) systemParts.add(systemText)
            } else if (role == "user" || role == "assistant") {
                conversation.put(JSONObject().put("role", role).put("content", contentValue ?: ""))
            }
        }
        require(conversation.length() > 0) { "Pesan percakapan kosong." }
        val body = JSONObject()
            .put("model", model)
            .put("max_tokens", 4096)
            .put("messages", conversation)
        if (systemParts.isNotEmpty()) body.put("system", systemParts.joinToString("\n\n"))
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val raw = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                val detail = try {
                    JSONObject(raw).optJSONObject("error")?.optString("message")?.takeIf { it.isNotBlank() } ?: raw
                } catch (_: Exception) { raw }
                throw IllegalStateException("Anthropic HTTP $code: ${detail.take(700)}")
            }
            val blocks = JSONObject(raw).optJSONArray("content") ?: JSONArray()
            val answer = (0 until blocks.length()).mapNotNull { index ->
                blocks.optJSONObject(index)?.takeIf { it.optString("type") == "text" }
                    ?.optString("text")?.takeIf { it.isNotBlank() }
            }.joinToString("\n")
            return answer.ifBlank { "Claude mengembalikan respons kosong atau format yang belum didukung." }
        } finally {
            connection.disconnect()
        }
    }
}
