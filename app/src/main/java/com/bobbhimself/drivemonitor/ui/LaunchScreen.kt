package com.bobbhimself.drivemonitor.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import com.bobbhimself.drivemonitor.R
import com.bobbhimself.drivemonitor.ui.theme.DriveMonitorTheme
import kotlinx.coroutines.delay

private const val DEFAULT_LAUNCH_SCREEN_DURATION_MILLIS = 3_000L

@Composable
fun DriveMonitorLaunchGate(
    modifier: Modifier = Modifier,
    durationMillis: Long = DEFAULT_LAUNCH_SCREEN_DURATION_MILLIS,
    content: @Composable () -> Unit
) {
    var showLaunchScreen by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(durationMillis) {
        delay(durationMillis)
        showLaunchScreen = false
    }

    if (showLaunchScreen) {
        LaunchScreen(modifier = modifier)
    } else {
        content()
    }
}

@Composable
fun LaunchScreen(
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxSize()
            .background(colorResource(id = R.color.ic_launcher_background))
            .testTag("launch-screen")
    ) {
        Image(
            painter = painterResource(id = R.drawable.launch),
            contentDescription = "Drive Monitor launch screen",
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PreviewLaunchScreen() {
    DriveMonitorTheme {
        LaunchScreen()
    }
}
