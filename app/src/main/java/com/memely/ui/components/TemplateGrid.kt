package com.memely.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.memely.data.FavoritesManager
import com.memely.data.MemeTemplate
import com.memely.ui.viewmodels.TemplateGridScrollState
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun TemplateGrid(
    templates: List<MemeTemplate>,
    isLoading: Boolean,
    error: String?,
    modifier: Modifier = Modifier,
    onTemplateClick: (MemeTemplate) -> Unit,
    onFavoriteChanged: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(
        initialFirstVisibleItemIndex = TemplateGridScrollState.getSavedScrollIndex(),
        initialFirstVisibleItemScrollOffset = TemplateGridScrollState.getSavedScrollOffset()
    )
) {
    val favorites by FavoritesManager.favoritesFlow.collectAsState()

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        when {
            isLoading -> {
                // Show loading spinner
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                // Show error message
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "⚠️ Error loading templates",
                            style = MaterialTheme.typography.h6,
                            color = MaterialTheme.colors.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
            templates.isEmpty() -> {
                // Show empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No templates available",
                        style = MaterialTheme.typography.body1,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            else -> {
                // Save scroll position whenever grid state changes
                LaunchedEffect(gridState) {
                    snapshotFlow {
                        gridState.firstVisibleItemIndex to (gridState.firstVisibleItemScrollOffset / 80)
                    }
                    .distinctUntilChanged()
                    .collect {
                        TemplateGridScrollState.saveScrollPosition(gridState)
                    }
                }
                
                // Show grid of templates with managed scroll state
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = templates,
                        key = { it.url },
                        contentType = { "template-card" }
                    ) { template ->
                        TemplateCard(
                            template = template,
                            isFavorite = favorites.contains(template.url),
                            onClick = onTemplateClick,
                            onFavoriteToggle = { _, _ ->
                                onFavoriteChanged()
                            }
                        )
                    }
                }
            }
        }
    }
}
