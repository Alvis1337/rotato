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
import java.io.IOException
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
        // Reuse a pending verifier so tapping Connect twice (or an older auth tab finishing
        // last) still exchanges against the verifier MAL saw; it is cleared on success.
        val verifier = prefs.codeVerifier.first().ifBlank {
            generateCodeVerifier().also { prefs.setCodeVerifier(it) }
        }
        // MAL only implements the "plain" PKCE method: with S256 the token exchange always
        // fails with invalid_grant "Failed to verify code_verifier".
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
                val body = resp.body?.string()
                check(resp.isSuccessful) { "Token exchange failed: ${resp.code} $body" }
                val json = JSONObject(body ?: throw IOException("Empty token response"))
                val accessToken = json.getString("access_token")
                val refreshToken = json.getString("refresh_token")

                // Fetch username before persisting. On failure use empty string so tokens are
                // still saved — the user is logged in and username can be refreshed later.
                val username = runCatching { fetchUsername(accessToken) }.getOrDefault("")
                // Write all auth fields + clear verifier atomically in one DataStore transaction
                prefs.setAuthAndClearVerifier(accessToken, refreshToken, username)
            }
        }
    }

    private fun fetchUsername(token: String): String {
        val req = Request.Builder()
            .url("https://api.myanimelist.net/v2/users/@me")
            .header("Authorization", "Bearer $token")
            .build()
        http.newCall(req).execute().use { resp ->
            check(resp.isSuccessful) { "Failed to fetch user: ${resp.code}" }
            val body = resp.body?.string() ?: throw IOException("Empty user response")
            return JSONObject(body).getString("name")
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
                val json = JSONObject(resp.body?.string() ?: throw IOException("Empty refresh response"))
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
                "?status=$status&fields=list_status,alternative_titles,main_picture,start_season,media_type&limit=1000&nsfw=true"
            while (url != null) {
                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer $token")
                    .build()
                http.newCall(req).execute().use { resp ->
                    if (resp.code == 401) throw Exception("TOKEN_EXPIRED")
                    check(resp.isSuccessful) { "Anime list fetch failed: ${resp.code}" }
                    val json = JSONObject(resp.body?.string() ?: throw IOException("Empty anime list response"))
                    val data = json.getJSONArray("data")
                    for (i in 0 until data.length()) {
                        val item = data.getJSONObject(i)
                        val node = item.getJSONObject("node")
                        val title = node.getString("title")
                        val listStatus = item.optJSONObject("list_status")
                        val alt = node.optJSONObject("alternative_titles")
                        val picture = node.optJSONObject("main_picture")
                        entries.add(
                            MalAnimeEntry(
                                title = title,
                                score = listStatus?.optInt("score", 0) ?: 0,
                                englishTitle = alt?.optString("en").orEmpty(),
                                synonyms = alt?.optJSONArray("synonyms")?.let { a -> List(a.length()) { i -> a.optString(i) } }
                                    ?.filter { it.isNotBlank() }.orEmpty(),
                                picture = picture?.optString("large")?.ifBlank { null } ?: picture?.optString("medium").orEmpty(),
                                status = listStatus?.optString("status").orEmpty().ifBlank { status },
                                year = node.optJSONObject("start_season")?.optInt("year") ?: 0,
                                mediaType = node.optString("media_type"),
                            )
                        )
                    }
                    url = json.optJSONObject("paging")?.optString("next")?.takeIf { it.isNotBlank() }
                }
            }
        }
        entries.distinctBy { it.title }
    }
}
