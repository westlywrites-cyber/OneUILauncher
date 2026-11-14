package com.westly.oneuilauncher.feature.widgets

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.westly.oneuilauncher.data.WeatherKind
import com.westly.oneuilauncher.data.WeatherRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private fun widgetText(size: TextUnit, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = Color.White,
    fontSize = size,
    fontWeight = weight,
    shadow = Shadow(
        color = Color.Black.copy(alpha = 0.45f),
        offset = Offset(0f, 1f),
        blurRadius = 6f
    )
)

/**
 * Weather widget: icon, temperature, description and city.
 *
 * @param refreshKey changes whenever the launcher comes to the front, so old data is refreshed.
 */
@Composable
fun WeatherWidget(refreshKey: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var info by remember { mutableStateOf(WeatherRepository.cached(context)) }

    LaunchedEffect(refreshKey) {
        if (!WeatherRepository.isFresh(info)) {
            val fresh = withContext(Dispatchers.IO) { WeatherRepository.fetch(context) }
            if (fresh != null) info = fresh
        }
    }

    val current = info
    val kind = WeatherRepository.kindOf(current?.code ?: 2)
    val isDay = current?.isDay ?: true
    val temperature = if (current != null) "${current.temperatureC}°" else "--°"
    val description = if (current != null) {
        WeatherRepository.describe(current.code)
    } else {
        "Weather unavailable"
    }

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        WeatherIcon(kind = kind, isDay = isDay, modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = temperature, style = widgetText(32.sp, FontWeight.Light))
            Text(text = description, style = widgetText(11.sp), maxLines = 1)
            Text(text = WeatherRepository.CITY, style = widgetText(11.sp), maxLines = 1)
        }
    }
}

@Composable
private fun WeatherIcon(kind: WeatherKind, isDay: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        when (kind) {
            WeatherKind.CLEAR -> drawSun(w * 0.5f, h * 0.5f, w * 0.28f, isDay)
            WeatherKind.PARTLY -> {
                drawSun(w * 0.36f, h * 0.36f, w * 0.2f, isDay)
                drawCloud(Color.White)
            }
            WeatherKind.CLOUDY -> drawCloud(Color(0xFFE6E9F5))
            WeatherKind.FOG -> {
                drawCloud(Color(0xFFD5D9EA))
                drawLine(
                    Color.White.copy(alpha = 0.8f), Offset(w * 0.2f, h * 0.88f),
                    Offset(w * 0.8f, h * 0.88f), strokeWidth = h * 0.05f, cap = StrokeCap.Round
                )
            }
            WeatherKind.RAIN -> {
                drawCloud(Color(0xFFDDE2F2))
                for (x in listOf(0.34f, 0.5f, 0.66f)) {
                    drawLine(
                        Color(0xFF5CB8FF), Offset(w * x, h * 0.84f),
                        Offset(w * (x - 0.04f), h * 0.97f), strokeWidth = h * 0.05f, cap = StrokeCap.Round
                    )
                }
            }
            WeatherKind.STORM -> {
                drawCloud(Color(0xFFC9CEE3))
                val bolt = Path().apply {
                    moveTo(w * 0.54f, h * 0.76f)
                    lineTo(w * 0.42f, h * 0.92f)
                    lineTo(w * 0.51f, h * 0.92f)
                    lineTo(w * 0.46f, h * 1.0f)
                    lineTo(w * 0.62f, h * 0.83f)
                    lineTo(w * 0.53f, h * 0.83f)
                    close()
                }
                drawPath(bolt, Color(0xFFFFC83A))
            }
            WeatherKind.SNOW -> {
                drawCloud(Color(0xFFE6E9F5))
                for (x in listOf(0.34f, 0.5f, 0.66f)) {
                    drawCircle(Color.White, h * 0.04f, Offset(w * x, h * 0.9f))
                }
            }
        }
    }
}

private fun DrawScope.drawSun(cx: Float, cy: Float, radius: Float, isDay: Boolean) {
    drawCircle(
        color = if (isDay) Color(0xFFFFC83A) else Color(0xFFDDE3FF),
        radius = radius,
        center = Offset(cx, cy)
    )
}

private fun DrawScope.drawCloud(color: Color) {
    val w = size.width
    val h = size.height
    drawCircle(color, w * 0.17f, Offset(w * 0.33f, h * 0.62f))
    drawCircle(color, w * 0.24f, Offset(w * 0.55f, h * 0.52f))
    drawCircle(color, w * 0.16f, Offset(w * 0.76f, h * 0.64f))
    drawRoundRect(
        color = color,
        topLeft = Offset(w * 0.2f, h * 0.62f),
        size = Size(w * 0.64f, h * 0.2f),
        cornerRadius = CornerRadius(h * 0.1f)
    )
}
