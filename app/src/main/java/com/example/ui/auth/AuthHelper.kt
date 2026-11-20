package com.example.ui.auth

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential.Companion.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.OAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

private const val TAG = "Auth"
private const val DUMMY_CONFIG_ERROR =
    "Este APK se compiló sin google-services.json real (usa 'dummy-project'). Configura el secret GOOGLE_SERVICES_JSON en GitHub Actions y vuelve a generar el APK."

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun isDummyFirebaseConfig(context: Context): Boolean {
    val clientId = runCatching { context.getString(R.string.default_web_client_id) }.getOrDefault("")
    val options = runCatching { FirebaseApp.getInstance().options }.getOrNull()
    val projectId = options?.projectId.orEmpty()
    val apiKey = options?.apiKey.orEmpty()
    return clientId.contains("dummy", ignoreCase = true) ||
        projectId.equals("dummy-project", ignoreCase = true) ||
        apiKey.contains("Dummy", ignoreCase = true)
}

fun attemptAutoSignIn(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onUnauthenticated: () -> Unit,
    scope: CoroutineScope,
    onAuthError: (String) -> Unit = {}
) {
    if (Firebase.auth.currentUser != null) {
        onAuthSuccess()
        return
    }
    if (isDummyFirebaseConfig(context)) {
        onUnauthenticated()
        return
    }
    scope.launch {
        try {
            val pending = Firebase.auth.pendingAuthResult
            if (pending != null) {
                pending.await()
                onAuthSuccess()
                return@launch
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Pending web auth result failed", e)
            onAuthError(formatAuthError(e))
            onUnauthenticated()
            return@launch
        }

        val clientId = try {
            context.getString(R.string.default_web_client_id)
        } catch (_: Exception) {
            onUnauthenticated()
            return@launch
        }
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(true)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        try {
            val activity = context.findActivity()
            if (activity == null) {
                onUnauthenticated()
                return@launch
            }
            val result = withTimeoutOrNull(4_000L) {
                credentialManager.getCredential(activity, request)
            }
            val cred = result?.credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                onUnauthenticated()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            onUnauthenticated()
        }
    }
}

fun onGoogleSignInClicked(
    context: Context,
    credentialManager: CredentialManager,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope,
    onAuthCancelled: (String?) -> Unit = {}
) {
    if (isDummyFirebaseConfig(context)) {
        onAuthError(DUMMY_CONFIG_ERROR)
        return
    }
    val clientId = try {
        context.getString(R.string.default_web_client_id)
    } catch (_: Exception) {
        onAuthError("Falta la configuración de Google Sign-In. Prueba el acceso web con Gmail abajo.")
        return
    }
    val signInOption = GetSignInWithGoogleOption.Builder(serverClientId = clientId).build()
    val request = GetCredentialRequest.Builder()
        .addCredentialOption(signInOption)
        .build()
    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("No se encontró la pantalla activa para iniciar sesión.")
            return@launch
        }
        try {
            val cred = credentialManager.getCredential(activity, request).credential
            if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
                Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
                onAuthSuccess()
            } else {
                launchWebGoogleOAuth(activity, onAuthSuccess, onAuthError)
            }
        } catch (e: GetCredentialCancellationException) {
            Log.w(TAG, "Google Sign-In flow cancelled or dismissed: ${e.message}", e)
            val detail = e.errorMessage?.toString()?.takeIf { it.isNotBlank() } ?: e.message?.takeIf { it.isNotBlank() }
            onAuthCancelled(detail)
        } catch (e: CancellationException) {
            throw e
        } catch (e: NoCredentialException) {
            Log.w(TAG, "NoCredentialException on GetSignInWithGoogleOption, trying GetGoogleIdOption", e)
            trySecondaryGoogleIdOption(activity, credentialManager, clientId, onAuthSuccess, onAuthError)
        } catch (e: GetCredentialException) {
            Log.e(TAG, "Credential Manager Google Sign-In failed: ${e.type} - ${e.message}", e)
            trySecondaryGoogleIdOption(activity, credentialManager, clientId, onAuthSuccess, onAuthError)
        } catch (e: Exception) {
            Log.e(TAG, "Google Sign-In failed", e)
            launchWebGoogleOAuth(activity, onAuthSuccess, onAuthError)
        }
    }
}

private suspend fun trySecondaryGoogleIdOption(
    activity: Activity,
    credentialManager: CredentialManager,
    clientId: String,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    try {
        val fallbackOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(clientId)
            .setAutoSelectEnabled(false)
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(fallbackOption)
            .build()
        val cred = credentialManager.getCredential(activity, request).credential
        if (cred is CustomCredential && cred.type == TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            val token = GoogleIdTokenCredential.createFrom(cred.data).idToken
            Firebase.auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null)).await()
            onAuthSuccess()
        } else {
            launchWebGoogleOAuth(activity, onAuthSuccess, onAuthError)
        }
    } catch (e: GetCredentialCancellationException) {
        Log.w(TAG, "Fallback GoogleIdOption cancelled: ${e.message}", e)
        onAuthError("No se seleccionó ninguna cuenta. Puedes ingresar tu Gmail directamente con 'Ingresar Gmail por navegador' abajo.")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG, "Fallback GoogleIdOption failed (${e.message}), falling back to web OAuth")
        launchWebGoogleOAuth(activity, onAuthSuccess, onAuthError)
    }
}

fun onGoogleWebSignInClicked(
    context: Context,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit,
    scope: CoroutineScope
) {
    if (isDummyFirebaseConfig(context)) {
        onAuthError(DUMMY_CONFIG_ERROR)
        return
    }
    scope.launch {
        val activity = context.findActivity()
        if (activity == null) {
            onAuthError("No se encontró la pantalla activa para iniciar sesión.")
            return@launch
        }
        launchWebGoogleOAuth(activity, onAuthSuccess, onAuthError)
    }
}

private suspend fun launchWebGoogleOAuth(
    activity: Activity,
    onAuthSuccess: () -> Unit,
    onAuthError: (String) -> Unit
) {
    try {
        val pending = Firebase.auth.pendingAuthResult
        if (pending != null) {
            pending.await()
            onAuthSuccess()
            return
        }
        val provider = OAuthProvider.newBuilder("google.com")
            .addCustomParameter("prompt", "select_account")
            .build()
        Firebase.auth.startActivityForSignInWithProvider(activity, provider).await()
        onAuthSuccess()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e(TAG, "Web Google OAuth sign-in failed", e)
        onAuthError(formatAuthError(e))
    }
}

private fun formatAuthError(e: Exception): String {
    val raw = e.localizedMessage ?: e.message ?: "Error desconocido"
    return when {
        raw.contains("API_KEY_ANDROID_APP_BLOCKED", ignoreCase = true) ||
            raw.contains("10:", ignoreCase = true) ||
            raw.contains("DEVELOPER_ERROR", ignoreCase = true) ->
            "La firma SHA-1 del APK no coincide con Firebase (asegúrate de configurar DEBUG_KEYSTORE_BASE64 en GitHub Secrets). Detalle: $raw"
        raw.contains("access_denied", ignoreCase = true) ||
            raw.contains("403", ignoreCase = true) ->
            "Google bloqueó esa cuenta (si la app está en modo prueba en Google Cloud, agrega ese correo como usuario de prueba). Detalle: $raw"
        raw.contains("network", ignoreCase = true) ->
            "Error de red al conectar con Google/Firebase. Revisa tu internet e intenta de nuevo. ($raw)"
        else -> "No se pudo iniciar sesión con Gmail: $raw"
    }
}

fun signOut(
    credentialManager: CredentialManager,
    scope: CoroutineScope,
    onSignOutComplete: () -> Unit = {}
) {
    scope.launch {
        withContext(NonCancellable) {
            try {
                credentialManager.clearCredentialState(ClearCredentialStateRequest())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear credential state: ${e.message}")
            } finally {
                Firebase.auth.signOut()
                onSignOutComplete()
            }
        }
    }
}
