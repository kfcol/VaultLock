package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.delay

@Composable
fun AppIconThumbnail(
  packageName: String,
  contentDescription: String?,
  modifier: Modifier = Modifier,
  size: Dp = 42.dp,
) {
  val context = LocalContext.current
  val bitmap: ImageBitmap? =
    remember(packageName) {
      runCatching {
          context.packageManager.getApplicationIcon(packageName).toBitmap(96, 96).asImageBitmap()
        }
        .getOrNull()
    }

  if (bitmap != null) {
    Image(
      bitmap = bitmap,
      contentDescription = contentDescription,
      contentScale = ContentScale.Crop,
      modifier = modifier.size(size).clip(RoundedCornerShape(12.dp)),
    )
  } else {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        modifier
          .size(size)
          .clip(RoundedCornerShape(12.dp))
          .background(MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
      Icon(
        imageVector = Icons.Filled.Apps,
        contentDescription = contentDescription,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(size * 0.55f),
      )
    }
  }
}

@Composable
fun StatusPill(
  text: String,
  containerColor: Color,
  contentColor: Color,
  modifier: Modifier = Modifier,
  dotColor: Color? = null,
) {
  Surface(
    color = containerColor,
    contentColor = contentColor,
    shape = RoundedCornerShape(50),
    modifier = modifier,
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
      if (dotColor != null) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(dotColor))
      }
      Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
      )
    }
  }
}

@Composable
fun HoldToActionButton(
  idleText: String,
  holdingText: String,
  holdDurationMillis: Long = 2400L,
  onCompleted: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var isPressed by remember { mutableStateOf(false) }
  var rawProgress by remember { mutableFloatStateOf(0f) }
  val animatedProgress by animateFloatAsState(targetValue = rawProgress, label = "hold_progress")

  LaunchedEffect(isPressed) {
    if (isPressed) {
      val stepMs = 40L
      val steps = (holdDurationMillis / stepMs).coerceAtLeast(1)
      for (i in 1..steps) {
        if (!isPressed) break
        rawProgress = i.toFloat() / steps.toFloat()
        delay(stepMs)
      }
      if (isPressed) {
        rawProgress = 0f
        isPressed = false
        onCompleted()
      }
    } else {
      rawProgress = 0f
    }
  }

  Surface(
    shape = RoundedCornerShape(16.dp),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    modifier =
      modifier
        .fillMaxWidth()
        .height(54.dp)
        .clip(RoundedCornerShape(16.dp))
        .pointerInput(Unit) {
          detectTapGestures(
            onPress = {
              isPressed = true
              tryAwaitRelease()
              isPressed = false
            }
          )
        },
  ) {
    Box(modifier = Modifier.fillMaxWidth()) {
      Box(
        modifier =
          Modifier.fillMaxHeight()
            .fillMaxWidth(animatedProgress)
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.28f))
      )
      Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = 16.dp),
      ) {
        Text(
          text = if (isPressed) holdingText else idleText,
          style = MaterialTheme.typography.labelLarge,
          color =
            if (isPressed) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}
