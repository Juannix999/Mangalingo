package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BorderColor
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.core.SupportedLanguage
import com.example.core.activeAppCheckProviderLabel
import com.example.core.switchToDebugTokenProvider
import com.example.core.switchToRecaptchaProvider
import com.example.data.repository.AppSettings
import com.example.data.repository.SettingsRepository
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    onSignOutClick: () -> Unit,
    onShowMessage: (String) -> Unit
) {
    val settings by settingsRepository.settingsFlow.collectAsStateWithLifecycle(
        initialValue = AppSettings()
    )
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val currentUser = remember { Firebase.auth.currentUser }

    var providerLabel by remember { mutableStateOf(activeAppCheckProviderLabel) }
    var debugTokenDialogUuid by remember { mutableStateOf<String?>(null) }
    var copiedTokenNotice by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 680.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "AJUSTES",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Personaliza el lector, la traducción con IA y tu cuenta",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Section 1: Traducción e IA
            item {
                SettingsSectionCard(title = "Traducción e IA") {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.AutoAwesome,
                        title = "Escaneo automático al abrir página",
                        subtitle = "Desactivado por defecto para ahorrar cuota. Usa «Detectar textos» o «Recorte» manualmente cuando quieras traducir.",
                        checked = settings.autoScanEnabled,
                        onCheckedChange = { enabled ->
                            scope.launch { settingsRepository.setAutoScanEnabled(enabled) }
                        },
                        testTag = "setting_auto_scan_switch"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Idioma de traducción predeterminado",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Idioma al que se traducen palabras, frases y recortes al abrir un capítulo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            val selected = settings.defaultTargetLang == lang
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    scope.launch { settingsRepository.setDefaultTargetLang(lang) }
                                },
                                label = {
                                    Text("${lang.shortBadge} · ${lang.spanishName}")
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("setting_lang_${lang.code}")
                            )
                        }
                    }
                }
            }

            // Section 2: Lector de Manga
            item {
                SettingsSectionCard(title = "Lector de manga") {
                    SettingsSwitchRow(
                        icon = Icons.Rounded.MenuBook,
                        title = "Lectura de derecha a izquierda",
                        subtitle = "Invierte la dirección al pasar páginas para leer en orden japonés tradicional.",
                        checked = settings.rtlReadingDirection,
                        onCheckedChange = { rtl ->
                            scope.launch { settingsRepository.setRtlReadingDirection(rtl) }
                        },
                        testTag = "setting_rtl_switch"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.BorderColor,
                        title = "Mostrar recuadros en globos detectados",
                        subtitle = "Resalta con un borde los diálogos detectados después de pulsar «Detectar textos».",
                        checked = settings.showBubbleOutlines,
                        onCheckedChange = { show ->
                            scope.launch { settingsRepository.setShowBubbleOutlines(show) }
                        },
                        testTag = "setting_bubble_outlines_switch"
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    SettingsSwitchRow(
                        icon = Icons.Rounded.DarkMode,
                        title = "Tema oscuro",
                        subtitle = "Alterna entre el tema oscuro de tinta y el modo claro de papel.",
                        checked = settings.darkThemeEnabled,
                        onCheckedChange = { dark ->
                            scope.launch { settingsRepository.setDarkThemeEnabled(dark) }
                        },
                        testTag = "setting_dark_theme_switch"
                    )
                }
            }

            // Section 3: Cuenta y Seguridad
            item {
                SettingsSectionCard(title = "Cuenta y App Check") {
                    val displayName = currentUser?.displayName?.takeIf { it.isNotBlank() } ?: "Lector de MangaLingo"
                    val email = currentUser?.email?.takeIf { it.isNotBlank() } ?: "Sesión iniciada con Google"
                    val initial = displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "M"

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = initial,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = email,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Modo App Check activo",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$providerLabel · MangaLingo v${BuildConfig.VERSION_NAME}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                copiedTokenNotice = false
                                val token = switchToDebugTokenProvider(context)
                                providerLabel = activeAppCheckProviderLabel
                                debugTokenDialogUuid = token
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setting_debug_token_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VerifiedUser,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Token Debug")
                        }

                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    settingsRepository.resetToDefaults()
                                    onShowMessage("Ajustes restablecidos a los valores predeterminados")
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("setting_reset_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.RestartAlt,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Restablecer")
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = onSignOutClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("setting_sign_out_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Logout,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cerrar sesión")
                    }
                }
            }
        }
    }

    val tokenUuid = debugTokenDialogUuid
    if (tokenUuid != null) {
        AlertDialog(
            onDismissRequest = { debugTokenDialogUuid = null },
            title = { Text("Token Debug de App Check") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Usa este UUID en Firebase Console → App Check → Apps → MangaLingo → Administrar tokens de depuración:",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Text(
                            text = tokenUuid,
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    if (copiedTokenNotice) {
                        Text(
                            text = "Token copiado al portapapeles.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    TextButton(
                        onClick = {
                            val switched = switchToRecaptchaProvider(context)
                            providerLabel = activeAppCheckProviderLabel
                            debugTokenDialogUuid = null
                            if (switched) {
                                onShowMessage("App Check cambiado a reCAPTCHA Enterprise")
                            }
                        }
                    ) {
                        Text("Cambiar de vuelta a reCAPTCHA Enterprise")
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(tokenUuid))
                        copiedTokenNotice = true
                    }
                ) {
                    Text("Copiar token")
                }
            },
            dismissButton = {
                TextButton(onClick = { debugTokenDialogUuid = null }) {
                    Text("Cerrar")
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, MaterialTheme.shapes.medium)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
