package com.anthonyla.hotaru.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meta.spatial.uiset.theme.SpatialTheme

/** A labelled segmented control: the options share a recessed track, and the chosen one is raised out of it. */
@Composable
fun <T> ChoiceRow(label: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = HotaruDimens.lg, vertical = HotaruDimens.md), verticalArrangement = Arrangement.spacedBy(HotaruDimens.sm)) {
        Text(label, style = SpatialTheme.typography.body1)
        Row(Modifier.fillMaxWidth().background(HotaruColors.Recess, HotaruShapes.pill).padding(HotaruDimens.xs).selectableGroup()) {
            options.forEach { (value, text) ->
                val current = value == selected
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 40.dp)
                        .clip(HotaruShapes.pill)
                        .background(if (current) HotaruColors.FillSelected else Color.Transparent)
                        .selectable(current, role = Role.RadioButton) { onSelect(value) }
                        .padding(horizontal = HotaruDimens.sm),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        style = if (current) SpatialTheme.typography.body1Strong else SpatialTheme.typography.body1,
                        color = if (current) HotaruColors.Text else HotaruColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
