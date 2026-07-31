package com.labellens.app.ui.scanner

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.labellens.app.R
import com.labellens.app.ui.components.ScanFrame
import com.labellens.app.ui.theme.BrandGreen
import com.labellens.app.ui.theme.BrandGreenDeep

@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onModeChange: (ScanMode) -> Unit,
    onToggleTorch: () -> Unit,
    onBarcode: (String) -> Unit,
    onLabelText: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAbout: () -> Unit,
    onDismissMessage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            context.checkSelfPermission(Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionAsked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        permissionAsked = true
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) launcher.launch(Manifest.permission.CAMERA)
    }

    if (!hasPermission) {
        CameraPermissionGate(
            showSettingsShortcut = permissionAsked,
            onRequest = { launcher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                )
            },
            modifier = modifier
        )
        return
    }

    // The analyzer reads the mode through this holder, so switching modes never
    // requires tearing down and rebinding the camera.
    val modeHolder = remember { mutableStateOf(state.mode) }
    LaunchedEffect(state.mode) { modeHolder.value = state.mode }

    val analyzer = remember {
        FrameAnalyzer(
            currentMode = { modeHolder.value },
            onBarcode = onBarcode,
            onLabelText = onLabelText
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        CameraPreview(
            analyzer = analyzer,
            torchOn = state.torchOn,
            modifier = Modifier.fillMaxSize()
        )

        // Dim outside the viewfinder so attention lands inside the frame.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Row {
                    IconButton(onClick = onToggleTorch) {
                        Icon(
                            painter = painterResource(R.drawable.ic_flash),
                            contentDescription = "Toggle torch",
                            tint = if (state.torchOn) BrandGreen else Color.White
                        )
                    }
                    IconButton(onClick = onOpenProfile) {
                        Icon(
                            painter = painterResource(R.drawable.ic_person),
                            contentDescription = "Your profile",
                            tint = Color.White
                        )
                    }
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            painter = painterResource(R.drawable.ic_history),
                            contentDescription = "Scan history",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(if (state.mode == ScanMode.BARCODE) 1.6f else 1.05f),
                contentAlignment = Alignment.Center
            ) {
                ScanFrame(
                    modifier = Modifier.fillMaxSize(),
                    accent = BrandGreen,
                    active = !state.isProcessing
                )
                if (state.isProcessing) {
                    CircularProgressIndicator(color = BrandGreen)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when {
                    state.isProcessing -> "Reading..."
                    state.mode == ScanMode.BARCODE -> "Point at the barcode"
                    else -> "Fill the frame with the ingredient list, then tap capture"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(visible = state.message != null) {
                MessageCard(message = state.message.orEmpty(), onDismiss = onDismissMessage)
            }

            Spacer(Modifier.height(12.dp))

            ModeSwitch(mode = state.mode, onModeChange = onModeChange)

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier.height(72.dp),
                contentAlignment = Alignment.Center
            ) {
                if (state.mode == ScanMode.LABEL) {
                    CaptureButton(
                        enabled = !state.isProcessing,
                        onClick = { analyzer.requestLabelCapture() }
                    )
                }
            }

            TextButton(onClick = onOpenAbout) {
                Text(
                    text = "Sources and disclaimer",
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}

@Composable
private fun ModeSwitch(mode: ScanMode, onModeChange: (ScanMode) -> Unit) {
    Row(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(50))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        ModeTab("Barcode", mode == ScanMode.BARCODE) { onModeChange(ScanMode.BARCODE) }
        ModeTab("Label text", mode == ScanMode.LABEL) { onModeChange(ScanMode.LABEL) }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color(0xFF00281C) else Color.White,
        modifier = Modifier
            .background(
                color = if (selected) BrandGreen else Color.Transparent,
                shape = RoundedCornerShape(50)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun CaptureButton(enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(70.dp)
            .background(
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .background(BrandGreenDeep, CircleShape)
        )
    }
}

@Composable
private fun MessageCard(message: String, onDismiss: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
            Text("Got it")
        }
    }
}

@Composable
private fun CameraPermissionGate(
    showSettingsShortcut: Boolean,
    onRequest: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.camera_permission_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.camera_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(stringResource(R.string.camera_permission_grant))
        }
        if (showSettingsShortcut) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.camera_permission_settings))
            }
        }
    }
}
