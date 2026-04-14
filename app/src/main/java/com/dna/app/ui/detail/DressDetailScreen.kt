package com.dna.app.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.dna.app.domain.model.DesignSpec

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DressDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DressDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is DetailUiState.Deleted || state is DetailUiState.NotFound) onDeleted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Dress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is DetailUiState.Loaded) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is DetailUiState.Loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            is DetailUiState.Loaded -> LoadedContent(
                dress = s.dress,
                modifier = Modifier.padding(padding),
            )

            is DetailUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp),
                )
            }

            // Deleted / NotFound are handled by LaunchedEffect → onDeleted().
            else -> Unit
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this dress?") },
            text = { Text("This removes it from every device. Generated designs that used it as a reference keep their copy.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteDress()
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LoadedContent(
    dress: com.dna.app.domain.model.DressItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        AsyncImage(
            model = dress.imageDisplayUrl.ifBlank { dress.imageThumbUrl },
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(24.dp)),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            dress.garmentType.name.replace('_', ' '),
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        val spec = dress.designSpec
        if (spec == null) {
            Text(
                "Auto-tagging in progress…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SpecChips(spec)
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SpecChips(spec: DesignSpec) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Chip("Neckline · ${spec.neckline.name}")
        Chip("Sleeve · ${spec.sleeve.name}")
        Chip("Silhouette · ${spec.silhouette.name}")
        Chip("Occasion · ${spec.occasion.name}")
        spec.embellishments.forEach { Chip("Embellishment · ${it.name}") }
        spec.dominantColors.forEach { Chip("Color · $it") }
    }
}

@Composable
private fun Chip(text: String) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text.replace('_', ' ')) },
        colors = AssistChipDefaults.assistChipColors(),
    )
}
