package com.sreevachan.attendance.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.sreevachan.attendance.MainActivity
import com.sreevachan.attendance.data.AttendanceRepository
import java.util.Locale

data class WidgetSnapshot(
    val hasData: Boolean,
    val pct: Double,
    val attended: Int,
    val conducted: Int,
    val minPct: Int,
)

class AttendanceWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = loadSnapshot(context)
        provideContent {
            AttendanceWidgetContent(snap)
        }
    }

    private fun loadSnapshot(context: Context): WidgetSnapshot {
        val prefs = context.getSharedPreferences(
            AttendanceRepository.WIDGET_PREFS_NAME,
            Context.MODE_PRIVATE,
        )
        return WidgetSnapshot(
            hasData = prefs.getBoolean("hasData", false),
            pct = prefs.getFloat("pct", 0f).toDouble(),
            attended = prefs.getInt("a", 0),
            conducted = prefs.getInt("c", 0),
            minPct = prefs.getInt("min", 75),
        )
    }
}

@Composable
private fun AttendanceWidgetContent(s: WidgetSnapshot) {
    val bgColor = ColorProvider(day = Color(0xFFFFFFFF), night = Color(0xFF18223A))
    val inkColor = ColorProvider(day = Color(0xFF14213D), night = Color(0xFFE8EDF7))
    val muteColor = ColorProvider(day = Color(0xFF5B6577), night = Color(0xFF9AA6BD))
    val trackColor = ColorProvider(day = Color(0xFFD5DCE8), night = Color(0xFF2B3856))
    val ok = s.conducted > 0 && s.attended * 100 >= s.minPct * s.conducted
    val statusColor = if (ok) {
        ColorProvider(day = Color(0xFF17803D), night = Color(0xFF4ADE80))
    } else {
        ColorProvider(day = Color(0xFFC62828), night = Color(0xFFF87171))
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bgColor)
            .cornerRadius(16.dp)
            .clickable(actionStartActivity<MainActivity>())
            .padding(14.dp),
        verticalAlignment = Alignment.Vertical.CenterVertically,
        horizontalAlignment = Alignment.Horizontal.Start,
    ) {
        Text(
            text = "Attendance",
            style = TextStyle(color = muteColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
        )
        Spacer(modifier = GlanceModifier.height(6.dp))
        if (s.hasData) {
            Text(
                text = String.format(Locale.US, "%.1f%%", s.pct),
                style = TextStyle(color = statusColor, fontSize = 30.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            LinearProgressIndicator(
                progress = (s.pct / 100.0).toFloat().coerceIn(0f, 1f),
                modifier = GlanceModifier.fillMaxWidth().height(8.dp).cornerRadius(4.dp),
                color = statusColor,
                backgroundColor = trackColor,
            )
            Spacer(modifier = GlanceModifier.height(8.dp))
            Text(
                text = "${s.attended} / ${s.conducted} classes",
                style = TextStyle(color = inkColor, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
            Text(
                text = "Minimum ${s.minPct}%",
                style = TextStyle(color = muteColor, fontSize = 11.sp),
            )
        } else {
            Text(
                text = "Open the app",
                style = TextStyle(color = inkColor, fontSize = 16.sp, fontWeight = FontWeight.Bold),
            )
            Spacer(modifier = GlanceModifier.height(2.dp))
            Text(
                text = "to load your attendance",
                style = TextStyle(color = muteColor, fontSize = 12.sp),
            )
        }
    }
}
