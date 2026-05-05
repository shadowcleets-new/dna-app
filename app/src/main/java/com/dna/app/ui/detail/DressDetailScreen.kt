package com.dna.app.ui.detail

import android.graphics.Bitmap
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
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.dna.app.data.imageloader.disableHardwareForPixelSampling
import com.dna.app.domain.color.ColorCorrection
import com.dna.app.domain.color.ColorCorrectionMath
import com.dna.app.domain.model.DesignSpec
import com.dna.app.domain.model.DressItem
import com.dna.app.domain.taxonomy.MediaType
import com.dna.app.ui.video.VideoPlayer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DressDetailScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: DressDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val correction by viewModel.draftCorrection.collectAsStateWithLifecycle()
    val neutralTap by viewModel.neutralTapEnabled.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var showCorrection by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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
                        IconButton(onClick = { showCorrection = true }) {
                            Icon(Icons.Filled.Tune, contentDescription = "Colour correction")
                        }
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
                correction = correction,
                neutralTapEnabled = neutralTap,
                onPixelSampled = viewModel::applyWhiteBalance,
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

    if (showCorrection) {
        ModalBottomSheet(
            onDismissRequest = { showCorrection = false },
            sheetState = sheetState,
        ) {
            ColorCorrectionPanel(
                correction = correction,
                neutralTapEnabled = neutralTap,
                onCorrectionChange = viewModel::updateDraft,
                onNeutralTapToggle = viewModel::toggleNeutralTap,
                onPreset = viewModel::applyPreset,
                onReset = viewModel::resetCorrection,
                onSave = {
                    viewModel.saveCorrection()
                    showCorrection = false
                },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun LoadedContent(
    dress: DressItem,
    correction: ColorCorrection,
    neutralTapEnabled: Boolean,
    onPixelSampled: (FloatArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        MediaHero(
            dress = dress,
            correction = correction,
            neutralTapEnabled = neutralTapEnabled,
            onPixelSampled = onPixelSampled,
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
                if (dress.mediaType == MediaType.VIDEO) "Video clip" else "Auto-tagging in progress…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            SpecChips(spec)
        }
    }
}

@Composable
private fun MediaHero(
    dress: DressItem,
    correction: ColorCorrection,
    neutralTapEnabled: Boolean,
    onPixelSampled: (FloatArray) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val matrixValues = remember(correction) { ColorCorrectionMath.toMatrix4x5(correction) }
    val colorFilter = remember(matrixValues) { ColorFilter.colorMatrix(ColorMatrix(matrixValues)) }
    val sampledBitmap = remember { mutableStateOf<Bitmap?>(null) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(3f / 4f)
            .clip(RoundedCornerShape(24.dp)),
    ) {
        when (dress.mediaType) {
            MediaType.VIDEO -> {
                val url = dress.videoOriginalUrl ?: dress.imageOriginalUrl ?: dress.imageDisplayUrl
                VideoPlayer(
                    videoUrl = url,
                    modifier = Modifier.fillMaxSize(),
                    autoPlay = false,
                    colorCorrection = correction,
                )
            }
            MediaType.IMAGE -> {
                val originalUrl = dress.imageOriginalUrl?.takeIf { it.isNotBlank() }
                val displayUrl = dress.imageDisplayUrl.ifBlank { dress.imageThumbUrl }
                val target = originalUrl ?: displayUrl
                val request = ImageRequest.Builder(context)
                    .data(target)
                    .placeholderMemoryCacheKey(displayUrl)
                    .crossfade(true)
                    .apply { if (neutralTapEnabled) disableHardwareForPixelSampling() }
                    .build()
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    colorFilter = colorFilter,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { result ->
                        // Cache the decoded bitmap so the neutral-tap overlay can sample it.
                        sampledBitmap.value = (result.result.image as? coil3.BitmapImage)?.bitmap
                    },
                )
                NeutralTapOverlay(
                    enabled = neutralTapEnabled,
                    bitmap = sampledBitmap,
                    onSampled = onPixelSampled,
                )
            }
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
