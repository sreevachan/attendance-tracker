package com.sreevachan.attendance.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreevachan.attendance.BuildConfig
import com.sreevachan.attendance.data.AttendanceCalculator
import com.sreevachan.attendance.data.AttendanceRepository
import com.sreevachan.attendance.data.AttendanceState
import com.sreevachan.attendance.data.DaySlot
import com.sreevachan.attendance.data.SubjectItem
import com.sreevachan.attendance.data.SubjectStat
import com.sreevachan.attendance.data.UpdateInfo
import com.sreevachan.attendance.ui.theme.LocalAttendanceColors
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    state: AttendanceState,
    currentMonth: YearMonth,
    selectedDayKey: String?,
    updateInfo: UpdateInfo?,
    updateProgress: Float?,
    updateError: String,
    celebrateTick: Int,
    onOpenSettings: () -> Unit,
    onInstallUpdate: () -> Unit,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onOpenDay: (String) -> Unit,
    onCloseDay: () -> Unit,
    onMarkDay: (String, String, Int?) -> Unit,
    onMarkDaySlots: (String, List<DaySlot>) -> Unit,
    onClearDay: (String) -> Unit,
    onMarkAllPresent: () -> Unit,
    onExamDateChange: (String) -> Unit,
) {
    val c = LocalAttendanceColors.current
    val uriHandler = LocalUriHandler.current
    val today = AttendanceCalculator.today()
    val todayKey = AttendanceCalculator.keyOf(today)

    val (baseA, baseC) = AttendanceCalculator.overallBase(state)
    val overallTotals = AttendanceCalculator.totals(state)
    val a = baseA + overallTotals.attended
    val cTotal = baseC + overallTotals.conducted
    val pct = if (cTotal > 0) a * 100.0 / cTotal else 0.0
    val minPct = state.settings.minPct
    val ok = cTotal > 0 && a * 100 >= minPct * cTotal

    val monthPrefix = "%04d-%02d".format(currentMonth.year, currentMonth.monthValue)
    val monthTotals = AttendanceCalculator.totals(state, monthPrefix)
    val unmarked = AttendanceCalculator.unmarkedPastDays(state, today)
    val subjectStats = AttendanceCalculator.subjectStats(state)
    val plan = AttendanceCalculator.planAhead(state, a, cTotal, today)

    var showExamPicker by remember { mutableStateOf(false) }

    val termText = remember(state.profile) {
        listOfNotNull(
            state.profile?.year?.takeIf { it.isNotBlank() },
            state.profile?.sem?.takeIf { it.isNotBlank() },
            state.profile?.mid?.takeIf { it.isNotBlank() },
        ).joinToString(" · ")
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.bg)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("Attendance", color = c.ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    if (termText.isNotEmpty()) {
                        Text(termText, color = c.mute, fontSize = 12.sp)
                    }
                }
                OutlinedButton(
                    onClick = onOpenSettings,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.card, contentColor = c.ink),
                ) {
                    Text("\u2699\uFE0E", fontSize = 20.sp, color = c.ink)
                }
            }

            // Update banner with direct in-app APK installer
            if (updateInfo != null) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = {
                            if (updateInfo.apkUrl?.startsWith(AttendanceRepository.GITHUB_BASE) == true) {
                                onInstallUpdate()
                            } else {
                                val url = updateInfo.releaseUrl?.takeIf { it.startsWith(AttendanceRepository.GITHUB_BASE) }
                                    ?: "${AttendanceRepository.GITHUB_BASE}attendance-tracker/releases/latest"
                                uriHandler.openUri(url)
                            }
                        },
                        enabled = updateProgress == null,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                    ) {
                        val label = when {
                            updateProgress != null -> "Downloading update (${(updateProgress * 100).roundToInt()}%)..."
                            else -> "Update available: ${updateInfo.version.take(20)}. Tap to install"
                        }
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                    if (updateProgress != null) {
                        LinearProgressIndicator(
                            progress = { updateProgress },
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                            color = c.accent,
                            trackColor = c.line,
                        )
                    }
                    if (updateError.isNotEmpty()) {
                        Text(updateError, color = c.bad, fontSize = 12.sp)
                    }
                }
            }

            // Hero card
            HeroCard(
                pct = pct,
                minPct = minPct,
                ok = ok,
                attendedOverall = a,
                conductedOverall = cTotal,
                monthDaysPresent = monthTotals.daysPresent,
                monthDaysTotal = monthTotals.daysTotal,
                monthAttended = monthTotals.attended,
                monthConducted = monthTotals.conducted,
                celebrateTick = celebrateTick,
            )

            // Subject-wise breakdown
            if (subjectStats.isNotEmpty()) {
                SubjectsCard(stats = subjectStats)
            }

            // Unmarked past days card
            if (unmarked.isNotEmpty()) {
                UnmarkedDaysCard(
                    unmarked = unmarked,
                    onTapDay = onOpenDay,
                    onMarkAll = onMarkAllPresent,
                )
            }

            // Monthly calendar card
            CalendarCard(
                state = state,
                month = currentMonth,
                todayKey = todayKey,
                onPrevMonth = onPrevMonth,
                onNextMonth = onNextMonth,
                onTapDay = onOpenDay,
            )

            // Plan ahead card
            Surface(
                color = c.card,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, c.line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Plan ahead", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Text("Plan until (date)", color = c.mute, fontSize = 12.sp)
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.bg, RoundedCornerShape(10.dp))
                            .border(1.dp, c.line, RoundedCornerShape(10.dp))
                            .clickable { showExamPicker = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                    ) {
                        Text(state.exam.ifBlank { "Pick a date" }, color = c.ink, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))

                    when {
                        plan == null -> Text("Pick a date.", color = c.ink, fontSize = 15.sp)
                        !plan.hasClasses -> Text("Add your starting numbers or timetable to see the plan.", color = c.ink, fontSize = 15.sp)
                        else -> {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${plan.classesLeft}") }
                                    append(" classes left until that date. Full attendance gets you to ")
                                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                        append(AttendanceCalculator.formatPct(plan.maxPct))
                                    }
                                    append(".")
                                },
                                color = c.ink,
                                fontSize = 15.sp,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = buildAnnotatedString {
                                    append("$minPct%: ")
                                    if (plan.canMiss >= 0) {
                                        append("you can miss up to ")
                                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("${plan.canMiss}") }
                                        append(" classes.")
                                    } else {
                                        withStyle(SpanStyle(color = c.bad, fontWeight = FontWeight.SemiBold)) {
                                            append("not reachable")
                                        }
                                        append(" by then.")
                                    }
                                },
                                color = c.ink,
                                fontSize = 15.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every unmarked day (except Sundays) before that date counts as a working day. Mark holidays on the calendar and this updates.",
                        color = c.mute,
                        fontSize = 12.sp,
                    )
                }
            }

            Text(
                text = "Made by Sreevachan · Version ${BuildConfig.VERSION_NAME}",
                color = c.mute,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
        }
    }

    if (selectedDayKey != null) {
        val currentMark = state.days[selectedDayKey]
        val scheduledIds = AttendanceCalculator.scheduledSubjectsForDate(state, selectedDayKey)
        val initialSlots = currentMark?.slots ?: scheduledIds.map { DaySlot(sub = it, st = "P") }
        val dayN = AttendanceCalculator.dayPeriods(state, selectedDayKey)

        DayMarkSheet(
            dateKey = selectedDayKey,
            subjects = state.subjects,
            hasSlots = currentMark?.slots != null || scheduledIds.isNotEmpty(),
            initialSlots = initialSlots,
            periods = dayN,
            initialPartial = if (currentMark?.s == "X") currentMark.a?.toString().orEmpty() else "",
            onDismiss = onCloseDay,
            onMarkQuick = { status, partial -> onMarkDay(selectedDayKey, status, partial) },
            onMarkSlots = { slots -> onMarkDaySlots(selectedDayKey, slots) },
            onClear = { onClearDay(selectedDayKey) },
        )
    }

    if (showExamPicker) {
        IsoDatePickerDialog(
            initialDateKey = state.exam,
            onDismiss = { showExamPicker = false },
            onConfirm = { picked ->
                showExamPicker = false
                onExamDateChange(picked)
            },
        )
    }
}

@Composable
private fun SubjectsCard(stats: List<SubjectStat>) {
    val c = LocalAttendanceColors.current
    var expanded by remember { mutableStateOf(false) }
    Surface(
        color = c.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Subjects (${stats.size})", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(if (expanded) "\u25BE" else "\u25B8", color = c.mute, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            if (expanded) {
                stats.forEach { st ->
                    val color = if (st.conducted == 0) c.mute else if (st.ok) c.good else c.bad
                    val hint = when {
                        st.conducted == 0 -> "No classes yet"
                        st.ok -> "Can miss ${st.canMiss}"
                        st.needToAttend < 0 -> "Needs 100%"
                        else -> "Attend next ${st.needToAttend}"
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(c.bg, RoundedCornerShape(10.dp))
                            .padding(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(st.subject.name, color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = if (st.conducted > 0) AttendanceCalculator.formatPct(st.pct) else "–",
                                color = color,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(c.line),
                        ) {
                            val frac = if (st.conducted > 0) (st.pct / 100.0).toFloat().coerceIn(0f, 1f) else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(frac)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(color),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${st.attended} / ${st.conducted} classes", color = c.mute, fontSize = 12.sp)
                            Text("$hint (${st.minPct}% min)", color = c.mute, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroCard(
    pct: Double,
    minPct: Int,
    ok: Boolean,
    attendedOverall: Int,
    conductedOverall: Int,
    monthDaysPresent: Int,
    monthDaysTotal: Int,
    monthAttended: Int,
    monthConducted: Int,
    celebrateTick: Int,
) {
    val c = LocalAttendanceColors.current
    val sparkProgress = remember { Animatable(0f) }
    val popScale = remember { Animatable(1f) }
    var showSparks by remember { mutableStateOf(false) }

    LaunchedEffect(celebrateTick) {
        if (celebrateTick > 0) {
            showSparks = true
            sparkProgress.snapTo(0f)
            popScale.animateTo(1.12f, tween(200))
            popScale.animateTo(1f, tween(300))
            sparkProgress.animateTo(1f, tween(800))
            showSparks = false
        }
    }

    Surface(
        color = c.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box {
            if (showSparks) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val colors = listOf(c.good, c.accent, c.warn)
                    val cx = size.width * 0.3f
                    val cy = size.height * 0.25f
                    val p = sparkProgress.value
                    for (i in 0 until 14) {
                        val angle = i / 14.0 * 2.0 * PI
                        val dist = (40f + (i % 3) * 28f) * p
                        drawCircle(
                            color = colors[i % 3].copy(alpha = (1f - p).coerceIn(0f, 1f)),
                            radius = (5f * (1f - p * 0.6f)).dp.toPx(),
                            center = Offset(
                                x = cx + (cos(angle) * dist).dp.toPx(),
                                y = cy + (sin(angle) * dist).dp.toPx(),
                            ),
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column {
                        Text("Overall attendance", color = c.mute, fontSize = 12.sp)
                        Text(
                            text = AttendanceCalculator.formatPct(pct),
                            color = if (ok) c.good else c.bad,
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = (-1.5).sp,
                            modifier = Modifier.scale(popScale.value),
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Status", color = c.mute, fontSize = 12.sp)
                        Text(
                            text = if (ok) "Above $minPct%" else "Below $minPct%",
                            color = if (ok) c.good else c.bad,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val fullW = maxWidth
                    val frac = (pct / 100.0).toFloat().coerceIn(0f, 1f)
                    val minFrac = (minPct / 100f).coerceIn(0f, 1f)
                    Column {
                        Box(modifier = Modifier.fillMaxWidth().height(18.dp), contentAlignment = Alignment.CenterStart) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(10.dp)
                                    .clip(RoundedCornerShape(5.dp))
                                    .background(c.line),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(frac)
                                        .height(10.dp)
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(c.accent),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .offset(x = (fullW * minFrac - 1.dp).coerceAtLeast(0.dp))
                                    .width(2.dp)
                                    .height(18.dp)
                                    .background(c.ink),
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text("0", color = c.mute, fontSize = 12.sp)
                            Text("$minPct% minimum", color = c.mute, fontSize = 12.sp)
                            Text("100%", color = c.mute, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "Days attended this month",
                        value = "$monthDaysPresent of $monthDaysTotal days",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Classes attended (overall)",
                        value = "$attendedOverall / $conductedOverall",
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(
                        label = "This month",
                        value = if (monthConducted > 0) AttendanceCalculator.formatPct(monthAttended * 100.0 / monthConducted) else "–",
                        modifier = Modifier.weight(1f),
                    )
                    StatTile(
                        label = "Classes this month",
                        value = "$monthAttended / $monthConducted",
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    val c = LocalAttendanceColors.current
    Column(
        modifier = modifier
            .background(c.bg, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Text(label, color = c.mute, fontSize = 12.sp)
        Spacer(Modifier.height(2.dp))
        Text(value, color = c.ink, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UnmarkedDaysCard(
    unmarked: List<String>,
    onTapDay: (String) -> Unit,
    onMarkAll: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    var armed by remember { mutableStateOf(false) }

    LaunchedEffect(armed) {
        if (armed) {
            delay(4000)
            armed = false
        }
    }

    Surface(
        color = c.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Unmarked days", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("These past days are not counted yet. Tap one to mark it.", color = c.mute, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                unmarked.take(12).forEach { k ->
                    OutlinedButton(
                        onClick = { onTapDay(k) },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, c.line),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = c.bg, contentColor = c.ink),
                    ) {
                        Text(AttendanceCalculator.niceChipDate(k), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (unmarked.size > 12) {
                    Text(
                        "+${unmarked.size - 12} more",
                        color = c.mute,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.CenterVertically).padding(start = 4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    if (!armed) armed = true else {
                        armed = false
                        onMarkAll()
                    }
                },
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.line),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = c.bg, contentColor = c.ink),
            ) {
                Text(
                    if (armed) "Tap again to mark ${unmarked.size} days present" else "Mark all as present",
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalendarCard(
    state: AttendanceState,
    month: YearMonth,
    todayKey: String,
    onPrevMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onTapDay: (String) -> Unit,
) {
    val c = LocalAttendanceColors.current
    val firstDayOfWeek = month.atDay(1).dayOfWeek.value % 7
    val daysInMonth = month.lengthOfMonth()

    Surface(
        color = c.card,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, c.line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onPrevMonth,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.bg, contentColor = c.ink),
                ) {
                    Text("\u2039", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
                Text(
                    AttendanceCalculator.monthTitle(month),
                    color = c.ink,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                OutlinedButton(
                    onClick = onNextMonth,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                    colors = ButtonDefaults.outlinedButtonColors(containerColor = c.bg, contentColor = c.ink),
                ) {
                    Text("\u203A", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = c.ink)
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                    Text(
                        d,
                        color = c.mute,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(6.dp))
            val totalCells = firstDayOfWeek + daysInMonth
            val rows = (totalCells + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                for (r in 0 until rows) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        for (col in 0 until 7) {
                            val idx = r * 7 + col
                            val dayNum = idx - firstDayOfWeek + 1
                            if (dayNum < 1 || dayNum > daysInMonth) {
                                Spacer(modifier = Modifier.weight(1f).aspectRatio(1f))
                            } else {
                                val date = month.atDay(dayNum)
                                val key = AttendanceCalculator.keyOf(date)
                                val mark = state.days[key]
                                val isSunday = AttendanceCalculator.dowOf(date) == 0
                                val isDisabled = isSunday || key <= state.base.asOf
                                val isToday = key == todayKey
                                val isUnmarked = mark == null &&
                                    key > state.base.asOf &&
                                    key < todayKey &&
                                    !isSunday &&
                                    AttendanceCalculator.periodsFor(state, date) > 0

                                val (bg, fg, borderColor) = when (mark?.s) {
                                    "P" -> Triple(c.goodBg, c.good, c.good)
                                    "A" -> Triple(c.badBg, c.bad, c.bad)
                                    "H" -> Triple(c.holBg, c.hol, c.hol)
                                    "X" -> Triple(c.warnBg, c.warn, c.warn)
                                    else -> Triple(c.card, c.ink, if (isUnmarked) c.warn else c.line)
                                }

                                val finalBorderColor = if (isToday) c.accent else borderColor
                                val borderWidth = if (isToday) 2.dp else 1.dp

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .alpha(if (isDisabled) 0.35f else 1f)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(borderWidth, finalBorderColor, RoundedCornerShape(10.dp))
                                        .then(
                                            if (!isDisabled) Modifier.clickable { onTapDay(key) } else Modifier,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = dayNum.toString(),
                                        color = fg,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                LegendItem("Present", c.good)
                LegendItem("Absent", c.bad)
                LegendItem("Partly", c.warn)
                LegendItem("Holiday", c.hol)
                LegendItem("Unmarked", c.warn, outlined = true)
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "Tap a date to mark it. Sundays are off. College start: ${AttendanceCalculator.niceShortDate(state.startDate)}.",
                color = c.mute,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color, outlined: Boolean = false) {
    val c = LocalAttendanceColors.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .then(
                    if (outlined) Modifier.border(1.dp, color, RoundedCornerShape(3.dp))
                    else Modifier.background(color),
                ),
        )
        Text(label, color = c.mute, fontSize = 12.sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayMarkSheet(
    dateKey: String,
    subjects: List<SubjectItem>,
    hasSlots: Boolean,
    initialSlots: List<DaySlot>,
    periods: Int,
    initialPartial: String,
    onDismiss: () -> Unit,
    onMarkQuick: (String, Int?) -> Unit,
    onMarkSlots: (List<DaySlot>) -> Unit,
    onClear: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    val subjectNames = remember(subjects) { subjects.associate { it.id to it.name } }
    val slotList = remember(dateKey) { mutableStateListOf<DaySlot>().apply { addAll(initialSlots) } }
    var partialText by remember(dateKey) { mutableStateOf(initialPartial) }
    var err by remember(dateKey) { mutableStateOf("") }
    var showAddMenu by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        contentColor = c.ink,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "${AttendanceCalculator.niceFullDate(dateKey)} (${if (hasSlots) slotList.count { it.st != "H" } else periods} classes)",
                color = c.ink,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton("All Present", c.goodBg, c.good, c.good, Modifier.weight(1f)) {
                    if (hasSlots) {
                        onMarkSlots(slotList.map { it.copy(st = "P") })
                    } else {
                        onMarkQuick("P", null)
                    }
                }
                SheetButton("All Absent", c.badBg, c.bad, c.bad, Modifier.weight(1f)) {
                    if (hasSlots) {
                        onMarkSlots(slotList.map { it.copy(st = "A") })
                    } else {
                        onMarkQuick("A", null)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SheetButton("Holiday", c.holBg, c.hol, c.hol, Modifier.weight(1f)) { onMarkQuick("H", null) }
                SheetButton("Clear", c.bg, c.ink, c.line, Modifier.weight(1f), onClick = onClear)
            }

            if (hasSlots) {
                Spacer(Modifier.height(14.dp))
                Text("Or mark each class", color = c.mute, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slotList.forEachIndexed { idx, slot ->
                        val subName = subjectNames[slot.sub] ?: "Class ${idx + 1}"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(c.bg, RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("${idx + 1}. $subName", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SlotStateChip("P", slot.st == "P", c.goodBg, c.good) {
                                    slotList[idx] = slot.copy(st = "P")
                                }
                                SlotStateChip("A", slot.st == "A", c.badBg, c.bad) {
                                    slotList[idx] = slot.copy(st = "A")
                                }
                                SlotStateChip("Off", slot.st == "H", c.holBg, c.hol) {
                                    slotList[idx] = slot.copy(st = "H")
                                }
                            }
                        }
                    }
                }

                if (subjects.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Box {
                        OutlinedButton(
                            onClick = { showAddMenu = true },
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.line),
                        ) {
                            Text("+ Add extra class for today", color = c.ink, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = showAddMenu, onDismissRequest = { showAddMenu = false }) {
                            subjects.forEach { sub ->
                                DropdownMenuItem(
                                    text = { Text(sub.name) },
                                    onClick = {
                                        showAddMenu = false
                                        slotList.add(DaySlot(sub = sub.id, st = "P"))
                                    },
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onMarkSlots(slotList.toList()) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                ) {
                    Text("Save classes", fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = partialText,
                        onValueChange = { partialText = it },
                        placeholder = { Text("Classes attended") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(
                        onClick = {
                            val v = partialText.toIntOrNull()
                            if (v == null || v < 0 || v > periods) {
                                err = "Enter a number from 0 to $periods."
                            } else {
                                onMarkQuick("X", v)
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.line),
                        modifier = Modifier.height(54.dp),
                    ) {
                        Text("Partly present", color = c.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (err.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(err, color = c.bad, fontSize = 13.sp)
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, c.line),
            ) {
                Text("Close", color = c.ink, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun SlotStateChip(
    label: String,
    selected: Boolean,
    bg: Color,
    fg: Color,
    onClick: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) bg else c.card)
            .border(1.dp, if (selected) fg else c.line, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) fg else c.mute,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun SheetButton(
    label: String,
    bg: Color,
    fg: Color,
    border: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = bg, contentColor = fg),
    ) {
        Text(label, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IsoDatePickerDialog(
    initialDateKey: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val initDate = AttendanceCalculator.parseDate(initialDateKey) ?: LocalDate.now()
    val initMillis = initDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initMillis)

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val ms = pickerState.selectedDateMillis
                    if (ms != null) {
                        val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                        onConfirm(AttendanceCalculator.keyOf(d))
                    } else {
                        onDismiss()
                    }
                },
            ) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    ) {
        DatePicker(state = pickerState)
    }
}
