package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val HARNESS_PREFS_NAME = "app_check_harness_prefs"
private const val KEY_HARNESS_TOKEN_PRESENT = "has_harness_debug_token"
private const val KEY_FORCE_DEBUG_PROVIDER = "force_manual_debug_provider"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var appCheckConfigured = false

@Volatile
var activeAppCheckProviderLabel: String = "Sin configurar"
    private set

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        val debugToken = intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
        if (appCheckConfigured && debugToken.isNullOrEmpty()) {
            return
        }

        if (BuildConfig.DEBUG) {
            val harnessPrefs = context.getSharedPreferences(HARNESS_PREFS_NAME, Context.MODE_PRIVATE)
            if (!debugToken.isNullOrEmpty()) {
                val persistenceKey = FirebaseApp.getInstance().persistenceKey
                val prefs = context.getSharedPreferences(
                    "$DEBUG_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                )
                // Synchronous commit() is required before DebugAppCheckProviderFactory reads it
                prefs.edit()
                    .putString(DEBUG_SECRET_KEY, debugToken)
                    .commit()
                harnessPrefs.edit()
                    .putBoolean(KEY_HARNESS_TOKEN_PRESENT, true)
                    .putBoolean(KEY_FORCE_DEBUG_PROVIDER, false)
                    .commit()
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                activeAppCheckProviderLabel = "Debug Provider (token de entorno)"
                appCheckConfigured = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed with harness debug token.")
                return
            }

            if (harnessPrefs.getBoolean(KEY_HARNESS_TOKEN_PRESENT, false)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                activeAppCheckProviderLabel = "Debug Provider (persistido)"
                appCheckConfigured = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed from persisted harness flag.")
                return
            }

            if (harnessPrefs.getBoolean(KEY_FORCE_DEBUG_PROVIDER, false)) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                activeAppCheckProviderLabel = "Debug Token manual"
                appCheckConfigured = true
                Log.d(TAG, "DebugAppCheckProviderFactory installed from manual debug token preference.")
                return
            }

            // No harness debug token (e.g., APK installed directly on a physical phone):
            // Use reCAPTCHA Enterprise site key so App Check tokens are accepted by Firebase.
            if (installRecaptchaProvider(context)) {
                activeAppCheckProviderLabel = "reCAPTCHA Enterprise"
                appCheckConfigured = true
                return
            }

            Log.w(TAG, "No debug token or recaptcha_site_key found; falling back to DebugAppCheckProviderFactory.")
            FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            activeAppCheckProviderLabel = "Debug Provider (sin site key)"
            appCheckConfigured = true
        } else {
            if (installRecaptchaProvider(context)) {
                activeAppCheckProviderLabel = "reCAPTCHA Enterprise (release)"
                appCheckConfigured = true
            }
        }
    } catch (e: Exception) {
        activeAppCheckProviderLabel = "Error al iniciar App Check: ${e.localizedMessage}"
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}

fun switchToDebugTokenProvider(context: Context): String {
    if (FirebaseApp.getApps(context).isEmpty()) {
        FirebaseApp.initializeApp(context)
    }
    val persistenceKey = FirebaseApp.getInstance().persistenceKey
    val prefs = context.getSharedPreferences(
        "$DEBUG_STORE_PREFIX$persistenceKey",
        Context.MODE_PRIVATE
    )
    var token = prefs.getString(DEBUG_SECRET_KEY, null)
    if (token.isNullOrBlank()) {
        token = java.util.UUID.randomUUID().toString()
        prefs.edit().putString(DEBUG_SECRET_KEY, token).commit()
    }
    context.getSharedPreferences(HARNESS_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_FORCE_DEBUG_PROVIDER, true)
        .commit()
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
    activeAppCheckProviderLabel = "Debug Token manual"
    appCheckConfigured = true
    return token
}

fun switchToRecaptchaProvider(context: Context): Boolean {
    context.getSharedPreferences(HARNESS_PREFS_NAME, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(KEY_FORCE_DEBUG_PROVIDER, false)
        .commit()
    val ok = installRecaptchaProvider(context)
    if (ok) {
        activeAppCheckProviderLabel = "reCAPTCHA Enterprise"
        appCheckConfigured = true
    }
    return ok
}

private fun installRecaptchaProvider(context: Context): Boolean {
    val resId = context.resources.getIdentifier(
        RECAPTCHA_SITE_KEY_RES,
        "string",
        context.packageName
    )
    if (resId == 0) {
        Log.e(TAG, "recaptcha_site_key string resource not found in package.")
        return false
    }
    val siteKey = context.getString(resId)
    if (siteKey.isBlank()) {
        Log.e(TAG, "recaptcha_site_key resource string is blank.")
        return false
    }
    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
        RecaptchaAppCheckProviderFactory.getInstance(siteKey)
    )
    Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
    return true
}
