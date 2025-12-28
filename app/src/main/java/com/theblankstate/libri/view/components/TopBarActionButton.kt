package com.theblankstate.libri.view.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color

@Composable
fun TopBarActionButton(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
) {
    FilledIconButton(
        onClick = onClick,
        modifier = modifier.padding(8.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = containerColor
        ),
        enabled = enabled
    ) {
        Icon(icon, contentDescription)
    }
}
