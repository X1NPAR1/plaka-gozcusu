package com.plakagozcusu.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.plakagozcusu.core.designsystem.component.PgCard
import com.plakagozcusu.core.designsystem.component.PgSecondaryButton
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.core.model.AnalysisLoad
import com.plakagozcusu.core.model.AppSettings
import com.plakagozcusu.core.model.ThemeMode
import kotlin.math.roundToInt

@Composable
internal fun AiCard(
    settings: AppSettings,
    viewModel: SettingsViewModel,
) {
    val hasKey by viewModel.hasAiKey.collectAsStateWithLifecycle()
    val models by viewModel.models.collectAsStateWithLifecycle()
    val loading by viewModel.loadingModels.collectAsStateWithLifecycle()
    val error by viewModel.modelsError.collectAsStateWithLifecycle()
    var key by remember { mutableStateOf("") }
    LaunchedEffect(hasKey) { if (hasKey && models == null) viewModel.saveKeyAndLoadModels("") }
    PgCard(Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.ai_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.ai_body), style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.ai_privacy), style = MaterialTheme.typography.bodySmall, color = PgTheme.status.warning)
        Text(stringResource(R.string.ai_step_provider), style = MaterialTheme.typography.labelLarge)
        // Gemini is the only provider with a free tier we could verify; listed as a choice for clarity.
        FilterChip(selected = true, onClick = {}, label = { Text(stringResource(R.string.ai_provider_gemini)) })
        Text(stringResource(R.string.ai_step_key), style = MaterialTheme.typography.labelLarge)
        OutlinedTextField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(if (hasKey) R.string.ai_key_saved else R.string.ai_key)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm)) {
            PgSecondaryButton(stringResource(R.string.ai_save), {
                viewModel.saveKeyAndLoadModels(key)
                key = ""
            })
            if (hasKey) PgSecondaryButton(stringResource(R.string.ai_remove), { viewModel.saveAiKey("") })
        }
        if (hasKey) ModelPicker(settings.aiModel, models, loading, error) { m -> viewModel.update { it.copy(aiModel = m) } }
        ToggleRow(stringResource(R.string.ai_enable), settings.aiEnabled && hasKey) { v ->
            if (hasKey) viewModel.update { it.copy(aiEnabled = v) }
        }
    }
}

@Composable
private fun ModelPicker(
    selected: String,
    models: List<String>?,
    loading: Boolean,
    error: Boolean,
    onSelect: (String) -> Unit,
) {
    Text(stringResource(R.string.ai_step_model), style = MaterialTheme.typography.labelLarge)
    when {
        loading -> {
            Text(stringResource(R.string.ai_models_loading))
        }

        error -> {
            Text(stringResource(R.string.ai_models_error), color = MaterialTheme.colorScheme.error)
        }

        models.isNullOrEmpty() -> {
            Text(stringResource(R.string.ai_models_empty))
        }

        else -> {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(PgTheme.spacing.sm),
                verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.xs),
            ) {
                models.forEach { m -> FilterChip(selected = selected == m, onClick = { onSelect(m) }, label = { Text(m) }) }
            }
        }
    }
}
