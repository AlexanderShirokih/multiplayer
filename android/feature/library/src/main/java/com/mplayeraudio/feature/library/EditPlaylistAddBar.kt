package com.mplayeraudio.feature.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
fun EditPlaylistAddBar(
    onAddClick: () -> Unit,
    onDeleteClick: () -> Unit,
    isDeleting: Boolean,
    modifier: Modifier = Modifier,
) {
    val spacing = MultiplayerTheme.spacing
    MultiplayerSurface(
        modifier = modifier.fillMaxWidth(),
        color = MultiplayerTheme.colors.surfacePrimary,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(spacing.md),
            horizontalArrangement = Arrangement.spacedBy(spacing.md),
        ) {
            OutlinedButton(
                onClick = onDeleteClick,
                enabled = !isDeleting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MultiplayerTheme.materialColorScheme.error,
                ),
            ) {
                Text(stringResource(R.string.playlist_edit_delete_playlist))
            }
            Button(
                onClick = onAddClick,
                enabled = !isDeleting,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MultiplayerTheme.colors.accent,
                    contentColor = MultiplayerTheme.colors.surfaceContentPrimary,
                ),
            ) {
                Text(stringResource(R.string.playlist_edit_add_track))
            }
        }
    }
}
