package com.dna.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.compose.collectAsLazyPagingItems
import coil3.compose.AsyncImage
import com.dna.app.domain.model.DressItem

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onAddDress: () -> Unit,
    onGenerate: () -> Unit,
    onDressClick: (DressItem) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val dresses = viewModel.dresses.collectAsLazyPagingItems()
    val count by viewModel.count.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Library") }) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                ExtendedFloatingActionButton(
                    onClick = onGenerate,
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    text = { Text("Design new") },
                )
                Spacer(Modifier.height(12.dp))
                ExtendedFloatingActionButton(
                    onClick = onAddDress,
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add dress") },
                )
            }
        },
    ) { padding ->
        if (count == 0 && dresses.itemCount == 0) {
            EmptyLibrary(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        } else {
            LazyVerticalStaggeredGrid(
                columns = StaggeredGridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalItemSpacing = 12.dp,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(
                    count = dresses.itemCount,
                    key = { index -> dresses[index]?.id ?: "placeholder-$index" },
                ) { index ->
                    val dress = dresses[index] ?: return@items
                    DressCard(dress = dress, onClick = { onDressClick(dress) })
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DressCard(dress: DressItem, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
    ) {
        AsyncImage(
            model = dress.imageThumbUrl.ifBlank { dress.imageDisplayUrl },
            contentDescription = null,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(3f / 4f)
                .clip(RoundedCornerShape(20.dp)),
        )
        dress.designSpec?.let { spec ->
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                MiniTag(spec.occasion.name)
                MiniTag(spec.silhouette.name)
                MiniTag(spec.sleeve.name)
            }
        }
    }
}

@Composable
private fun MiniTag(text: String) {
    androidx.compose.material3.Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = text.replace('_', ' '),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun EmptyLibrary(modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Add photos of your salwar kameez and kurti designs. " +
                    "They become the DNA for everything you design next.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
