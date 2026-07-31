package com.labeltruth.app.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.labeltruth.app.BuildConfig
import com.labeltruth.app.R
import com.labeltruth.app.ui.history.TopBar

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(title = "Sources and disclaimer", onBack = onBack)

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            Block(
                title = "Important",
                body = stringResource(R.string.disclaimer_body)
            )
            Block(
                title = "Where the data comes from",
                body = stringResource(R.string.attribution)
            )
            Block(
                title = "Your privacy",
                body = "LabelTruth has no account and no analytics. Camera frames are " +
                    "processed on your device and are never uploaded. Your health " +
                    "profile and scan history stay on this phone.\n\n" +
                    "The only network request the app makes is looking up a barcode " +
                    "you scanned, which sends that barcode to Open Food Facts."
            )
            Block(
                title = "Coverage",
                body = "Our ingredient dictionary is comprehensive but not exhaustive, " +
                    "and no such complete list exists anywhere. If an ingredient is " +
                    "not recognised we say so rather than guessing."
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun Block(title: String, body: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(24.dp))
}
