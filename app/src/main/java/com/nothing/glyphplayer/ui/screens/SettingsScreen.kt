package com.nothing.glyphplayer.ui.screens

import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
    var eqOn by remember { mutableStateOf(EqualizerManager.isEnabled()) }
    var showLang by remember { mutableStateOf(false) }

    val appLocales = AppCompatDelegate.getApplicationLocales()
    val currentLang: String? = if (appLocales.isEmpty) null else appLocales.get(0)?.language

    val currentLabel = when (currentLang) {
        null -> stringResource(R.string.lang_system)
        "en" -> stringResource(R.string.lang_english)
        "ru" -> stringResource(R.string.lang_russian)
        else -> stringResource(R.string.lang_system)
    }

    Column(
        Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())
    ) {
        Text(stringResource(R.string.settings_title),
            style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(24.dp))

        SectionTitle(stringResource(R.string.settings_library))
        Button(
            onClick = { vm.scanLibrary { } },
            colors = ButtonDefaults.buttonColors(containerColor = Red),
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.settings_rescan)) }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_audio))

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.settings_equalizer),
                color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Switch(
                checked = eqOn,
                onCheckedChange = { eqOn = it; EqualizerManager.setEnabled(it) },
                colors = SwitchDefaults.colors(checkedTrackColor = Red)
            )
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_language))

        Row(
            Modifier.fillMaxWidth().clickable { showLang = true }.padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(currentLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            Text("›", color = Red, style = MaterialTheme.typography.headlineSmall)
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle(stringResource(R.string.settings_about))
        Text(stringResource(R.string.settings_version),
            color = Color.Gray, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.settings_subtitle),
            color = Color.Gray, style = MaterialTheme.typography.bodyMedium)

        Spacer(Modifier.height(24.dp))
        HorizontalDivider(color = Color.DarkGray.copy(alpha = 0.3f))
    }

    if (showLang) {
        AlertDialog(
            onDismissRequest = { showLang = false },
            title = { Text(stringResource(R.string.settings_language), color = Color.White) },
            text = {
                Column {
                    LanguageOption(
                        label = stringResource(R.string.lang_system),
                        selected = currentLang == null,
                        onClick = { applyLocale(null); showLang = false }
                    )
                    LanguageOption(
                        label = stringResource(R.string.lang_english),
                        selected = currentLang == "en",
                        onClick = { applyLocale("en"); showLang = false }
                    )
                    LanguageOption(
                        label = stringResource(R.string.lang_russian),
                        selected = currentLang == "ru",
                        onClick = { applyLocale("ru"); showLang = false }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showLang = false }) {
                    Text(stringResource(R.string.lang_cancel), color = Color.Gray)
                }
            },
            containerColor = Color(0xFF121212)
        )
    }
}

@Composable
private fun LanguageOption(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = if (selected) Red else Color.White)
            if (selected) Text("✓", color = Red)
        }
    }
}

private fun applyLocale(languageTag: String?) {
    val locales = if (languageTag == null) LocaleListCompat.getEmptyLocaleList()
                  else LocaleListCompat.forLanguageTags(languageTag)
    AppCompatDelegate.setApplicationLocales(locales)
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Red, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(8.dp))
}
