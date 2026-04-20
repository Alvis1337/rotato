package com.chrisalvis.rotato.data

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.chrisalvis.rotato.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom

class MalRepository(private val context: Context) {

    private val prefs = MalPreferences(context)
    private val http = OkHttpClient()

    companion object {
        const val REDIRECT_URI = "rotato://callback"
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(64)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    suspend fun buildAuthUrl(): String {
        val verifier = generateCodeVerifier()
        prefs.setCodeVerifier(verifier)
        return Uri.Builder()
            .scheme("https")
            .authority("myanimelist.net")
            .path("/v1/oauth2/authorize")
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", BuildConfig.MAL_CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", verifier)
            .appendQueryParameter("code_challenge_method", "plain")
            .build()
            .toString()
    }

    suspend fun exchangeCode(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val verifier = prefs.codeVerifier.first()
            check(verifier.isNotBlank()) { "No code verifier stored" }

            val bodyBuilder = FormBody.Builder()
                .add("client_id", BuildConfig.MAL_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", verifier)
            if (BuildConfig.MAL_CLIENT_SECRET.isNotBlank()) {
                bodyBuilder.add("client_secret", BuildConfig.MAL_CLIENT_SECRET)
            }

            val req = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(bodyBuilder.build())
                .build()

            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Token exchange failed: ${resp.code} ${resp.body?.string()}" }
                val json = JSONObject(resp.body!!.string())
                prefs.setTokens(
                    json.getString("access_token"),
                    json.getString("refresh_token")
                )
            }

            val username = fetchUsername(prefs.accessToken.first())
            prefs.setUsername(username)
            prefs.clearCodeVerifier()
        }
    }

    private fun fetchUsername(token: String): String {
        val req = Request.Builder()
            .url("https://api.myanimelist.net/v2/users/@me")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Failed to fetch user: ${resp.code}" }
            return JSONObject(resp.body!!.string()).getString("name")
        }
    }

    suspend fun refreshAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val refreshToken = prefs.refreshToken.first()
            check(refreshToken.isNotBlank()) { "No refresh token stored" }

            val bodyBuilder = FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refreshToken)
                .add("client_id", BuildConfig.MAL_CLIENT_ID)
            if (BuildConfig.MAL_CLIENT_SECRET.isNotBlank()) {
                bodyBuilder.add("client_secret", BuildConfig.MAL_CLIENT_SECRET)
            }

            val req = Request.Builder()
                .url("https://myanimelist.net/v1/oauth2/token")
                .post(bodyBuilder.build())
                .build()

            http.newCall(req).execute().use { resp ->
                check(resp.isSuccessful) { "Refresh failed: ${resp.code}" }
                val json = JSONObject(resp.body!!.string())
                val newAccess = json.getString("access_token")
                val newRefresh = json.getString("refresh_token")
                prefs.setTokens(newAccess, newRefresh)
                newAccess
            }
        }
    }

    suspend fun fetchAnimeList(): Result<List<MalAnimeEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            var token = prefs.accessToken.first()
            check(token.isNotBlank()) { "Not authenticated" }
            val statuses = prefs.filterStatuses.first()
            check(statuses.isNotEmpty()) { "No statuses selected" }

            val result = fetchAnimeListWithToken(token, statuses)
            if (result.isFailure && result.exceptionOrNull()?.message == "TOKEN_EXPIRED") {
                token = refreshAccessToken().getOrThrow()
                fetchAnimeListWithToken(token, statuses).getOrThrow()
            } else {
                result.getOrThrow()
            }
        }
    }

    private fun fetchAnimeListWithToken(token: String, statuses: Set<String>): Result<List<MalAnimeEntry>> = runCatching {
        val entries = mutableListOf<MalAnimeEntry>()
        for (status in statuses) {
            var url: String? = "https://api.myanimelist.net/v2/users/@me/animelist" +
                "?status=$status&fields=list_status&limit=1000"
            while (url != null) {
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.code == 401) throw Exception("TOKEN_EXPIRED")
                    check(resp.isSuccessful) { "Anime list fetch failed: ${resp.code}" }
                    val json = JSONObject(resp.body!!.string())
                    val data = json.getJSONArray("data")
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val title = item.getJSONObject("node").getString("title")
                        val score = item.optJSONObject("list_status")?.optInt("score", 0) ?: 0
                        entries.add(MalAnimeEntry(title, score))
                    }
                    url = json.optJSONObject("paging")?.optString("next")?.takeIf { it.isNotBlank() }
                }
            }
        }
        entries.distinctBy { it.title }
    }
}
