package com.westly.oneuilauncher.feature.widgets

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin
import java.time.format.TextStyle as JavaTextStyle

private fun textStyle(
    size: TextUnit,
    weight: FontWeight = FontWeight.Normal,
    color: Color = Color.White
) = TextStyle(
    color = color,
    fontSize = size,
    fontWeight = weight,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.35f),
        offset = Offset(0f, 1f),
        blurRadius = 5f
    )
)

/** Current time, refreshed every second. */
@Composable
private fun rememberNow(): ZonedDateTime {
    val millis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000L)
        }
    }
    return Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
}

/** Dark translucent rounded card used by the clock and calendar widgets. */
@Composable
private fun WidgetCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0x8C12143C))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        content = content
    )
}

// ---------------------------------------------------------------------------------------
// Clock
// ---------------------------------------------------------------------------------------

@Composable
fun ClockWidget(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val time = now.format(DateTimeFormatter.ofPattern("h:mm"))
    val date = now.format(DateTimeFormatter.ofPattern("EEE, MMM d", Locale.getDefault()))
    val hourAngle = (now.hour % 12 + now.minute / 60.0) * 30.0
    val minuteAngle = (now.minute + now.second / 60.0) * 6.0

    WidgetCard(modifier = modifier) {
        Canvas(modifier = Modifier.size(100.dp)) {
            val radius = size.minDimension / 2f
            drawCircle(Color.White.copy(alpha = 0.14f), radius)
            for (i in 0 until 12) {
                val angle = Math.toRadians(i * 30.0 - 90.0)
                val outer = radius * 0.9f
                val inner = if (i % 3 == 0) radius * 0.74f else radius * 0.82f
                drawLine(
                    color = Color.White,
                    start = Offset(
                        center.x + (cos(angle) * inner).toFloat(),
                        center.y + (sin(angle) * inner).toFloat()
                    ),
                    end = Offset(
                        center.x + (cos(angle) * outer).toFloat(),
                        center.y + (sin(angle) * outer).toFloat()
                    ),
                    strokeWidth = if (i % 3 == 0) 4f else 2f,
                    cap = StrokeCap.Round
                )
            }
            drawHand(hourAngle, radius * 0.5f, 8f, Color.White)
            drawHand(minuteAngle, radius * 0.72f, 5f, Color.White)
            drawCircle(Color(0xFFFF7A8A), radius * 0.07f)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = time, style = textStyle(22.sp, FontWeight.Medium))
        Text(text = date, style = textStyle(12.sp, color = Color.White.copy(alpha = 0.8f)))
    }
}

private fun DrawScope.drawHand(angleDegrees: Double, length: Float, width: Float, color: Color) {
    val angle = Math.toRadians(angleDegrees - 90.0)
    drawLine(
        color = color,
        start = center,
        end = Offset(
            center.x + (cos(angle) * length).toFloat(),
            center.y + (sin(angle) * length).toFloat()
        ),
        strokeWidth = width,
        cap = StrokeCap.Round
    )
}

// ---------------------------------------------------------------------------------------
// Calendar
// ---------------------------------------------------------------------------------------

@Composable
fun CalendarWidget(modifier: Modifier = Modifier) {
    val now = rememberNow()
    val month = now.month.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()).uppercase()
    val weekday = now.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())

    WidgetCard(modifier = modifier) {
        Text(
            text = month,
            style = textStyle(13.sp, FontWeight.SemiBold, Color(0xFFFF7A8A))
        )
        Text(text = "${now.dayOfMonth}", style = textStyle(56.sp, FontWeight.Light))
        Text(text = weekday, style = textStyle(14.sp, color = Color.White.copy(alpha = 0.85f)))
    }
}

// ---------------------------------------------------------------------------------------
// Google search bar
// ---------------------------------------------------------------------------------------

@Composable
fun GoogleSearchWidget(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val interaction = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .height(46.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.96f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { openGoogleSearch(context) }
            )
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        GoogleLogo(modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.weight(1f))
        MicIcon(modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.size(14.dp))
        LensIcon(modifier = Modifier.size(22.dp))
    }
}

private fun openGoogleSearch(context: Context) {
    val attempts = listOf(
        Intent(Intent.ACTION_WEB_SEARCH),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com"))
    )
    for (intent in attempts) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: Exception) {
            // Try the next way of opening search.
        }
    }
}

@Composable
private fun GoogleLogo(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.18f
        val inset = stroke / 2f
        val arcSize = Size(size.width - stroke, size.height - stroke)
        val topLeft = Offset(inset, inset)
        val arcs = listOf(
            Triple(Color(0xFF4285F4), -40f, 80f),
            Triple(Color(0xFF34A853), 50f, 80f),
            Triple(Color(0xFFFBBC05), 140f, 80f),
            Triple(Color(0xFFEA4335), 230f, 80f)
        )
        for ((color, start, sweep) in arcs) {
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke)
            )
        }
        drawLine(
            color = Color(0xFF4285F4),
            start = center,
            end = Offset(size.width - inset, center.y),
            strokeWidth = stroke
        )
    }
}

@Composable
private fun MicIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val blue = Color(0xFF4285F4)
        drawRoundRect(
            color = blue,
            topLeft = Offset(w * 0.36f, h * 0.06f),
            size = Size(w * 0.28f, h * 0.55f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.14f)
        )
        drawArc(
            color = blue,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.22f, h * 0.3f),
            size = Size(w * 0.56f, h * 0.45f),
            style = Stroke(width = w * 0.07f)
        )
        drawLine(blue, Offset(w * 0.5f, h * 0.75f), Offset(w * 0.5f, h * 0.94f), strokeWidth = w * 0.07f)
    }
}

@Composable
private fun LensIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        drawCircle(
            color = Color(0xFFEA4335),
            radius = w * 0.4f,
            center = center,
            style = Stroke(width = w * 0.09f)
        )
        drawCircle(Color(0xFF34A853), w * 0.14f, center)
    }
}
