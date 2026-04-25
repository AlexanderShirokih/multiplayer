package com.mplayeraudio.feature.library

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mplayeraudio.core.ui.components.MultiplayerSurface
import com.mplayeraudio.core.ui.theme.MultiplayerTheme

@Composable
fun EditPlaylistAddBar(
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MultiplayerSurface(
        modifier = modifier.fillMaxWidth(),
        color = MultiplayerTheme.colors.surfacePrimary,
    ) {
        Button(
            onClick = onAddClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MultiplayerTheme.colors.accent,
                contentColor = MultiplayerTheme.colors.surfaceContentPrimary,
            )
        ) {
            Text(stringResource(R.string.playlist_edit_add_track))
        }
    }
}
