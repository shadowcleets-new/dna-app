package com.dna.app.ui.upload

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dna.app.domain.taxonomy.GarmentType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadDressScreen(
    onBack: () -> Unit,
    onUploaded: () -> Unit,
    viewModel: UploadDressViewModel = hiltViewModel(),
) {
    val garment by viewModel.garmentType.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) viewModel.upload(uri)
    }

    LaunchedEffect(state) {
        if (state is UploadState.Done) {
            viewModel.reset()
            onUploaded()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Add dress") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Pick a photo of a dress you've already made.",
                style = MaterialTheme.typography.bodyLarge,
            )
            Spacer(Modifier.height(24.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GarmentType.entries.forEach { type ->
                    FilterChip(
                        selected = garment == type,
                        onClick = { viewModel.setGarmentType(type) },
                        label = { Text(label(type)) },
                    )
                }
            }
            Spacer(Modifier.height(32.dp))

            val working = state is UploadState.Working
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                enabled = !working,
            ) {
                Text(if (working) "Saving…" else "Pick photo")
            }

            (state as? UploadState.Error)?.let { err ->
                Spacer(Modifier.height(16.dp))
                Text(
                    text = err.message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

private fun label(type: GarmentType): String = when (type) {
    GarmentType.KURTI -> "Kurti"
    GarmentType.SALWAR_KAMEEZ -> "Salwar kameez"
}
