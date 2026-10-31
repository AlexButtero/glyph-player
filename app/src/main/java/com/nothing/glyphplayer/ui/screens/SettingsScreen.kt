package com.nothing.glyphplayer.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.nothing.glyphplayer.R
import com.nothing.glyphplayer.playback.EqualizerManager
import com.nothing.glyphplayer.ui.PlayerViewModel
import com.nothing.glyphplayer.ui.theme.Red

@Composable
fun SettingsScreen(vm: PlayerViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var eqOn by remember { mutableStateOf(EqualizerManager.isEnabled()) }
    var showLang by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }

    val appLocales = AppCompatDelegate.getApplicationLocales()
    val currentLang: String? = if (appLocales.isEmpty) null else appLocales.get(0)?.language
    val currentLangLabel = when (currentLang) {
        "en" -> stringResource(R.string.lang_english); "ru" -> stringResource(R.string.lang_russian); else -> stringResource(R.string.lang_system)
    }

    Column(Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(24.dp))

        SectionTitle(stringResource(R.string.settings_library))
        Button(onClick = { vm.scanLibrary { } }, colors = ButtonDefaults.buttonColors(containerColor = Red), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_rescan)) }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_audio))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_equalizer), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Switch(checked = eqOn, onCheckedChange = { eqOn = it; EqualizerManager.setEnabled(it) }, colors = SwitchDefaults.colors(checkedTrackColor = Red))
        }
        Row(Modifier.fillMaxWidth().clickable { showSleep = true }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.settings_sleep), color = Color.White, style = MaterialTheme.typography.bodyMedium)
            val sleepLeft = state.sleepSecondsRemaining
            Text(if (sleepLeft != null) stringResource(R.string.settings_sleep_minutes, sleepLeft / 60) else stringResource(R.string.settings_sleep_off), color = if (sleepLeft != null) Red else Color.Gray)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_language))
        Row(Modifier.fillMaxWidth().clickable { showLang = true }.padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(currentLangLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("›", color = Red, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_about))
        Text(stringResource(R.string.settings_version), color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_subtitle), color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
    }

    if (showLang) {
        AlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text(stringResource(R.string.settings_language), color = Color.White) },
            text = {
                Column {
                    LangOption(stringResource(R.string.lang_system), currentLang == null) { applyLocale(null); showLang = false }
                    LangOption(stringResource(R.string.lang_english), currentLang == "en") { applyLocale("en"); showLang = false }
                    LangOption(stringResource(R.string.lang_russian), currentLang == "ru") { applyLocale("ru"); showLang = false }
                }
            },
            confirmButton = { TextButton(onClick = { showLang = false }) { Text(stringResource(R.string.player_close), color = Color.Gray) } },
            containerColor = Color(0xFF121212)
        )
    }

    if (showSleep) {
        AlertDialog(
            onDismissRequest = { showSleep = false },
            title = { Text(stringResource(R.string.settings_sleep), color = Color.White) },
            text = {
                Column {
                    listOf(0, 15, 30, 45, 60).forEach { m ->
                        TextButton(onClick = { vm.setSleepTimer(m); showSleep = false }) {
                            Text(if (m == 0) stringResource(R.string.settings_sleep_off) else stringResource(R.string.settings_sleep_minutes, m), color = Color.White)
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showSleep = false }) { Text(stringResource(R.string.player_close), color = Color.Gray) } },
            containerColor = Color(0xFF121212)
        )
    }
}

@Composable
private fun LangOption(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = if (selected) Red else Color.White)
            if (selected) Text("✓", color = Red)
        }
    }
}

private fun applyLocale(tag: String?) {
    AppCompatDelegate.setApplicationLocales(if (tag == null) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Red, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
}
