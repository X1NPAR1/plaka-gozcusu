package com.plakagozcusu.feature.onboarding.permission

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.plakagozcusu.core.designsystem.component.PgPrimaryButton
import com.plakagozcusu.core.designsystem.theme.PgTheme
import com.plakagozcusu.feature.onboarding.R

/**
 * Shows [content] only when the camera permission is granted; otherwise explains why it is needed
 * and offers the right action for not-requested / denied / permanently-denied. Re-checks on resume
 * so returning from the settings page updates the screen.
 */
@Composable
fun CameraPermissionGate(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    var requestedBefore by rememberSaveable { mutableStateOf(false) }
    var status by rememberSaveable { mutableStateOf(currentStatus(context, requestedBefore)) }
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            requestedBefore = true
            status = currentStatus(context, requestedBefore = true)
        }
    LifecycleResumeEffect(requestedBefore) {
        status = currentStatus(context, requestedBefore)
        onPauseOrDispose { }
    }

    if (status == PermissionStatus.GRANTED) {
        content()
        return
    }
    Column(
        modifier = modifier.fillMaxSize().padding(PgTheme.spacing.xl),
        verticalArrangement = Arrangement.spacedBy(PgTheme.spacing.lg, Alignment.CenterVertically),
    ) {
        Text(
            stringResource(R.string.permission_camera_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.semantics { heading() },
        )
        val body =
            when (status) {
                PermissionStatus.DENIED -> R.string.permission_camera_denied_body
                PermissionStatus.PERMANENTLY_DENIED -> R.string.permission_camera_permanent_body
                else -> R.string.permission_camera_body
            }
        Text(stringResource(body), style = MaterialTheme.typography.bodyLarge)
        if (status == PermissionStatus.PERMANENTLY_DENIED) {
            PgPrimaryButton(stringResource(R.string.permission_open_settings), onClick = { openAppSettings(context) })
        } else {
            PgPrimaryButton(
                stringResource(R.string.permission_camera_request),
                onClick = { launcher.launch(Manifest.permission.CAMERA) },
            )
        }
    }
}

private fun currentStatus(
    context: Context,
    requestedBefore: Boolean,
): PermissionStatus {
    val granted =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val rationale = context.findActivity()?.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) ?: false
    return resolvePermissionStatus(granted, rationale, requestedBefore)
}

private fun openAppSettings(context: Context) {
    val intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private tailrec fun Context.findActivity(): Activity? =
    when (this) {
        is Activity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }
