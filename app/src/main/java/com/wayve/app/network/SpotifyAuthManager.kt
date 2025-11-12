package com.wayve.app.network

import android.content.Context
import android.content.SharedPreferences
import com.spotify.sdk.android.auth.AuthorizationClient
import com.spotify.sdk.android.auth.AuthorizationRequest
import com.spotify.sdk.android.auth.AuthorizationResponse
import java.security.MessageDigest
import java.security.SecureRandom
import android.util.Base64

/**
 * Spotify OAuth manager using Authorization Code with PKCE
 * This is the secure modern way recommended by Spotify for mobile apps
 */
class SpotifyAuthManager(private val context: Context) {
    
    private val authPrefs: SharedPreferences = 
        context.getSharedPreferences("spotify_auth", Context.MODE_PRIVATE)
    
    private val configPrefs: SharedPreferences =
        context.getSharedPreferences("wayve_prefs", Context.MODE_PRIVATE)
    
    companion object {
        private const val REDIRECT_URI = "wayve://spotify-callback"
        
        // Scopes needed to create playlists
        private val SCOPES = arrayOf(
            "playlist-modify-public",
            "playlist-modify-private"
        )
        
        private const val PREF_ACCESS_TOKEN = "access_token"
        private const val PREF_EXPIRES_AT = "expires_at"
        private const val PREF_CODE_VERIFIER = "code_verifier"
    }
    
    /**
     * Generate a random code verifier for PKCE
     */
    private fun generateCodeVerifier(): String {
        val random = SecureRandom()
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    
    /**
     * Generate code challenge from verifier (SHA256 hash)
     */
    private fun generateCodeChallenge(verifier: String): String {
        val bytes = verifier.toByteArray(Charsets.US_ASCII)
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(bytes)
        return Base64.encodeToString(hash, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }
    
    /**
     * Get the Client ID from app settings
     */
    private fun getClientId(): String {
        return configPrefs.getString("spotify_client_id", "") ?: ""
    }
    
    /**
     * Get the Client Secret from app settings (not used for PKCE, but kept for configuration)
     */
    fun getClientSecret(): String {
        return configPrefs.getString("spotify_client_secret", "") ?: ""
    }
    
    /**
     * Build the authorization URL for PKCE flow
     * Returns the URL to open in browser
     */
    fun buildAuthorizationUrl(): String {
        val clientId = getClientId()
        
        // Generate PKCE parameters
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)
        
        // Save code verifier for later token exchange
        authPrefs.edit()
            .putString(PREF_CODE_VERIFIER, codeVerifier)
            .apply()
        
        android.util.Log.d("SpotifyAuth", "Generated code verifier: ${codeVerifier.take(20)}...")
        android.util.Log.d("SpotifyAuth", "Generated code challenge: ${codeChallenge.take(20)}...")
        
        val scopes = SCOPES.joinToString(" ")
        
        val authUrl = "https://accounts.spotify.com/authorize?" +
            "client_id=${java.net.URLEncoder.encode(clientId, "UTF-8")}" +
            "&response_type=code" +  // Use 'code' for PKCE, not 'token'
            "&redirect_uri=${java.net.URLEncoder.encode(REDIRECT_URI, "UTF-8")}" +
            "&scope=${java.net.URLEncoder.encode(scopes, "UTF-8")}" +
            "&code_challenge_method=S256" +
            "&code_challenge=${java.net.URLEncoder.encode(codeChallenge, "UTF-8")}" +
            "&show_dialog=false"
        
        android.util.Log.d("SpotifyAuth", "Authorization URL: $authUrl")
        return authUrl
    }
    
    /**
     * Exchange authorization code for access token using PKCE
     * Call this after receiving the code from the redirect
     */
    suspend fun exchangeCodeForToken(code: String): Boolean {
        val clientId = getClientId()
        val codeVerifier = authPrefs.getString(PREF_CODE_VERIFIER, null)
        
        if (codeVerifier == null) {
            android.util.Log.e("SpotifyAuth", "Code verifier not found!")
            return false
        }
        
        android.util.Log.d("SpotifyAuth", "Exchanging code for token...")
        android.util.Log.d("SpotifyAuth", "Code: ${code.take(20)}...")
        android.util.Log.d("SpotifyAuth", "Code verifier: ${codeVerifier.take(20)}...")
        
        try {
            val client = okhttp3.OkHttpClient()
            val requestBody = okhttp3.FormBody.Builder()
                .add("client_id", clientId)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()
            
            val request = okhttp3.Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(requestBody)
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""
            
            android.util.Log.d("SpotifyAuth", "Token response code: ${response.code}")
            android.util.Log.d("SpotifyAuth", "Token response: $responseBody")
            
            if (response.isSuccessful) {
                val json = org.json.JSONObject(responseBody)
                val accessToken = json.getString("access_token")
                val expiresIn = json.getInt("expires_in")
                
                saveToken(accessToken, expiresIn)
                
                // Clear code verifier
                authPrefs.edit().remove(PREF_CODE_VERIFIER).apply()
                
                return true
            } else {
                android.util.Log.e("SpotifyAuth", "Token exchange failed: $responseBody")
                return false
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyAuth", "Token exchange error", e)
            return false
        }
    }
    
    /**
     * Save the access token after successful auth
     */
    private fun saveToken(token: String, expiresIn: Int) {
        val expiresAt = System.currentTimeMillis() + (expiresIn * 1000L)
        authPrefs.edit()
            .putString(PREF_ACCESS_TOKEN, token)
            .putLong(PREF_EXPIRES_AT, expiresAt)
            .apply()
        
        android.util.Log.i("SpotifyAuth", "✅ Token saved, expires at: $expiresAt")
    }
    
    /**
     * Get the current access token (if valid)
     */
    fun getAccessToken(): String? {
        val token = authPrefs.getString(PREF_ACCESS_TOKEN, null)
        val expiresAt = authPrefs.getLong(PREF_EXPIRES_AT, 0)
        
        if (token != null && System.currentTimeMillis() < expiresAt) {
            return token
        }
        
        // Token expired or doesn't exist
        return null
    }
    
    /**
     * Check if user is signed in with a valid token
     */
    fun isSignedIn(): Boolean {
        return getAccessToken() != null
    }
    
    /**
     * Sign out (clear token)
     */
    fun signOut() {
        authPrefs.edit().clear().apply()
        android.util.Log.i("SpotifyAuth", "✅ Signed out")
    }
    
    /**
     * Check if Client ID and Client Secret are configured
     */
    fun isConfigured(): Boolean {
        val clientId = getClientId()
        val clientSecret = getClientSecret()
        return clientId.isNotEmpty() && clientSecret.isNotEmpty()
    }
}

