package com.theblankstate.libri.view.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun LibriTopAppBar(
    title: String,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    LibriTopAppBar(
        titleContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        onBackClick = onBackClick,
        modifier = modifier,
        centerTitle = centerTitle,
        actions = actions
    )
}

@Composable
fun LibriTopAppBar(
    titleContent: @Composable RowScope.() -> Unit,
    onBackClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    centerTitle: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val titleLift = (-3).dp
    val compactBarHeight = 40.dp

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        tonalElevation = 1.dp,
        shadowElevation = 0.dp
    ) {
        if (centerTitle) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(compactBarHeight)
                    .padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BackButton(onBackClick)
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(y = titleLift),
                    verticalAlignment = Alignment.CenterVertically,
                    content = titleContent
                )
                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .height(compactBarHeight)
                    .padding(start = 4.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BackButton(onBackClick)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = titleLift),
                    verticalAlignment = Alignment.CenterVertically,
                    content = titleContent
                )
                Row(
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions
                )
            }
        }
    }
}

@Composable
private fun BackButton(onBackClick: (() -> Unit)?) {
    if (onBackClick != null) {
        IconButton(onClick = onBackClick) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
    }
}
