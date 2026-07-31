package com.labellens.app.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labellens.app.R
import com.labellens.app.data.local.ScanEntity
import com.labellens.app.domain.model.Grade
import com.labellens.app.ui.components.gradeColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    scans: List<ScanEntity>,
    onBack: () -> Unit,
    onDelete: (Long) -> Unit,
    onClearAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(
            title = "Scan history",
            onBack = onBack,
            trailing = {
                if (scans.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("Clear all") }
                }
            }
        )

        if (scans.isEmpty()) {
            EmptyState(
                title = "No scans yet",
                body = "Everything you scan is saved here, on this device only."
            )
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(scans, key = { it.id }) { scan ->
                HistoryRow(scan = scan, onDelete = { onDelete(scan.id) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun HistoryRow(scan: ScanEntity, onDelete: () -> Unit) {
    val grade = Grade.of(scan.score)
    val formatter = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(gradeColor(grade).copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${scan.score}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = gradeColor(grade)
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scan.productName,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = listOfNotNull(
                    scan.brand,
                    formatter.format(Date(scan.timestamp))
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                painter = painterResource(R.drawable.ic_delete),
                contentDescription = "Delete scan",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun TopBar(
    title: String,
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

@Composable
fun EmptyState(title: String, body: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
