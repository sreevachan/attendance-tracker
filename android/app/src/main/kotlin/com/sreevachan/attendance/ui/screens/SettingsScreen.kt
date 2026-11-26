package com.sreevachan.attendance.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sreevachan.attendance.BuildConfig
import com.sreevachan.attendance.data.AttendanceCalculator
import com.sreevachan.attendance.data.AttendanceState
import com.sreevachan.attendance.data.AuthUser
import com.sreevachan.attendance.data.SubjectItem
import com.sreevachan.attendance.ui.theme.LocalAttendanceColors
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    state: AttendanceState,
    isFirstSetup: Boolean,
    authUser: AuthUser?,
    syncStatus: String,
    cloudReady: Boolean,
    errorMsg: String,
    successMsg: String,
    acctError: String,
    busy: Boolean,
    onClose: () -> Unit,
    onThemeChange: (String) -> Unit,
    onSave: (
        name: String,
        college: String,
        roll: String,
        year: String,
        sem: String,
        mid: String,
        startFromDayOne: Boolean,
        startDate: String,
        asOfInput: String,
        overallAttendedText: String,
        overallConductedText: String,
        minPct: Int,
        subjects: List<SubjectItem>,
        timetableDays: List<List<String>>,
        timetableEffectiveFrom: String,
        fallbackWeekdayCounts: List<Int>,
    ) -> Unit,
    onStartNewSemester: (
        year: String,
        sem: String,
        mid: String,
        startDate: String,
        keepSubjects: Boolean,
    ) -> Unit,
    onConnectDrive: () -> Unit,
    onTurnOnEncrypted: () -> Unit,
    onUnlockBackup: () -> Unit,
    onSwitchToStandard: () -> Unit,
    onTurnOffBackup: () -> Unit,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onDeleteAccount: () -> Unit,
) {
    val c = LocalAttendanceColors.current
    val uriHandler = LocalUriHandler.current

    var name by remember(state.profile) { mutableStateOf(state.profile?.name.orEmpty()) }
    var college by remember(state.profile) { mutableStateOf(state.profile?.college.orEmpty()) }
    var roll by remember(state.profile) { mutableStateOf(state.profile?.roll.orEmpty()) }
    var year by remember(state.profile) { mutableStateOf(state.profile?.year.orEmpty()) }
    var sem by remember(state.profile) { mutableStateOf(state.profile?.sem.orEmpty()) }
    var mid by remember(state.profile) { mutableStateOf(state.profile?.mid.orEmpty()) }

    val initialHasBaseline = remember(state.base, state.subjects) {
        state.base.c > 0 || state.subjects.any { it.baseC > 0 }
    }
    var startFromDayOne by remember(state.base, state.subjects) { mutableStateOf(!initialHasBaseline) }
    var startDate by remember(state.startDate) { mutableStateOf(state.startDate) }
    var asOf by remember(state.base.asOf) { mutableStateOf(state.base.asOf) }
    var attended by remember(state.base.a) { mutableStateOf(state.base.a.toString()) }
    var conducted by remember(state.base.c) { mutableStateOf(state.base.c.toString()) }
    var knownPct by remember { mutableStateOf("") }
    var minPct by remember(state.settings.minPct) { mutableIntStateOf(state.settings.minPct) }

    // Subjects editor state
    val subjectsList = remember(state.subjects) {
        mutableStateListOf<SubjectItem>().apply { addAll(state.subjects) }
    }
    var newSubjectName by remember { mutableStateOf("") }

    // Timetable editor state (7 weekdays, 0=Sun..6=Sat)
    val latestTt = remember(state.timetable) {
        state.timetable.lastOrNull()?.days ?: List(7) { emptyList() }
    }
    val ttDays = remember(state.timetable) {
        mutableStateListOf<List<String>>().apply {
            for (i in 0..6) add(latestTt.getOrElse(i) { emptyList() })
        }
    }
    var selectedWeekday by remember { mutableIntStateOf(1) } // 1=Mon..6=Sat
    var effectiveFrom by remember(state.startDate) {
        mutableStateOf(
            if (isFirstSetup) state.startDate else AttendanceCalculator.keyOf(AttendanceCalculator.today()),
        )
    }

    val weekdayInputs = remember(state.pw) {
        mutableStateListOf(
            state.pw.getOrElse(1) { 6 }.toString(),
            state.pw.getOrElse(2) { 5 }.toString(),
            state.pw.getOrElse(3) { 6 }.toString(),
            state.pw.getOrElse(4) { 6 }.toString(),
            state.pw.getOrElse(5) { 6 }.toString(),
            state.pw.getOrElse(6) { 5 }.toString(),
        )
    }

    var localMsg by remember { mutableStateOf("") }
    var localMsgBad by remember { mutableStateOf(false) }

    var showStartPicker by remember { mutableStateOf(false) }
    var showAsOfPicker by remember { mutableStateOf(false) }
    var showEffectivePicker by remember { mutableStateOf(false) }
    var showNewSemDialog by remember { mutableStateOf(false) }
    var showArchives by remember { mutableStateOf(false) }

    var stdArmed by remember { mutableStateOf(false) }
    var offArmed by remember { mutableStateOf(false) }
    var delArmed by remember { mutableStateOf(false) }

    LaunchedEffect(stdArmed) { if (stdArmed) { delay(4000); stdArmed = false } }
    LaunchedEffect(offArmed) { if (offArmed) { delay(4000); offArmed = false } }
    LaunchedEffect(delArmed) { if (delArmed) { delay(4000); delArmed = false } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.card)
            .safeDrawingPadding(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 500.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (isFirstSetup) "Welcome" else "Settings",
                    color = c.ink,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (!isFirstSetup) {
                    OutlinedButton(
                        onClick = onClose,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.line),
                    ) {
                        Text("Close", color = c.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            if (isFirstSetup) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "Set up your profile, college start date, and subjects/timetable once to get started.",
                    color = c.mute,
                    fontSize = 13.sp,
                )
            }

            Spacer(Modifier.height(14.dp))
            Text("Profile & Term", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = college, onValueChange = { college = it }, label = { Text("College") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = roll, onValueChange = { roll = it }, label = { Text("Roll number") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    placeholder = { Text("2nd Year") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = sem,
                    onValueChange = { sem = it },
                    label = { Text("Semester") },
                    placeholder = { Text("Sem 1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = mid,
                    onValueChange = { mid = it },
                    label = { Text("Mid") },
                    placeholder = { Text("Mid 1") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = c.line)
            Spacer(Modifier.height(12.dp))
            Text("College Start & Starting Numbers", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("College / semester start date", color = c.mute, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            DateFieldBox(startDate) { showStartPicker = true }

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModePill("Start from Day 1 (0/0)", startFromDayOne, Modifier.weight(1f)) {
                    startFromDayOne = true
                }
                ModePill("Manual / Mid-sem entry", !startFromDayOne, Modifier.weight(1f)) {
                    startFromDayOne = false
                }
            }

            if (!startFromDayOne) {
                Spacer(Modifier.height(10.dp))
                Text("Numbers are up to (date)", color = c.mute, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                DateFieldBox(asOf) { showAsOfPicker = true }

                if (subjectsList.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Enter attended & conducted per subject (for colleges without a sheet or mid-semester):",
                        color = c.mute,
                        fontSize = 12.sp,
                    )
                    Spacer(Modifier.height(6.dp))
                    subjectsList.forEachIndexed { idx, sub ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(sub.name, color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                            OutlinedTextField(
                                value = sub.baseA.toString(),
                                onValueChange = { v ->
                                    subjectsList[idx] = sub.copy(baseA = v.toIntOrNull() ?: 0)
                                },
                                label = { Text("Att") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp),
                            )
                            OutlinedTextField(
                                value = sub.baseC.toString(),
                                onValueChange = { v ->
                                    subjectsList[idx] = sub.copy(baseC = v.toIntOrNull() ?: 0)
                                },
                                label = { Text("Cond") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.width(80.dp),
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = attended,
                            onValueChange = { attended = it },
                            label = { Text("Attended") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = conducted,
                            onValueChange = { conducted = it },
                            label = { Text("Conducted") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = knownPct,
                        onValueChange = { knownPct = it },
                        label = { Text("Only know your percentage? Enter it here") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val a = attended.toIntOrNull()
                            val p = knownPct.toDoubleOrNull()
                            if (a == null || a < 0 || p == null || p <= 0.0 || p > 100.0) {
                                localMsg = "Enter attended and a percentage between 1 and 100."
                                localMsgBad = true
                            } else {
                                val computed = (a * 100.0 / p).roundToInt()
                                conducted = computed.toString()
                                localMsg = "Conducted set to $computed. Change it if it looks off."
                                localMsgBad = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.line),
                    ) {
                        Text("Work out conducted", color = c.ink, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Text("Minimum attendance: $minPct%", color = c.ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Slider(
                value = minPct.toFloat(),
                onValueChange = { minPct = it.roundToInt().coerceIn(50, 100) },
                valueRange = 50f..100f,
                colors = SliderDefaults.colors(thumbColor = c.accent, activeTrackColor = c.accent),
            )

            // Subjects section
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = c.line)
            Spacer(Modifier.height(12.dp))
            Text("Subjects (e.g. English, Maths, CRT)", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("Add your subjects to track attendance per class and build your weekly timetable.", color = c.mute, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = newSubjectName,
                    onValueChange = { newSubjectName = it },
                    label = { Text("Subject name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        val trimmed = newSubjectName.trim()
                        if (trimmed.isNotEmpty() && subjectsList.none { it.name.equals(trimmed, ignoreCase = true) }) {
                            val id = "s_${System.currentTimeMillis()}_${subjectsList.size}"
                            subjectsList.add(SubjectItem(id = id, name = trimmed))
                            newSubjectName = ""
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                ) {
                    Text("Add")
                }
            }

            if (subjectsList.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subjectsList.forEach { sub ->
                        Box(
                            modifier = Modifier
                                .background(c.bg, RoundedCornerShape(8.dp))
                                .border(1.dp, c.line, RoundedCornerShape(8.dp))
                                .clickable {
                                    subjectsList.remove(sub)
                                    for (d in 0..6) {
                                        ttDays[d] = ttDays[d].filterNot { it == sub.id }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        ) {
                            Text("${sub.name}  ×", color = c.ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Weekly subject timetable builder
                Spacer(Modifier.height(14.dp))
                Text("Weekly Timetable (Mon–Sat)", color = c.ink, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Select a day and tap subjects to add periods in order. Tap a scheduled period to remove it.", color = c.mute, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))

                val dayLabels = listOf(1 to "Mon", 2 to "Tue", 3 to "Wed", 4 to "Thu", 5 to "Fri", 6 to "Sat")
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEach { (dow, label) ->
                        val active = selectedWeekday == dow
                        val count = ttDays[dow].size
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(if (active) c.accent else c.bg, RoundedCornerShape(8.dp))
                                .border(1.dp, if (active) c.accent else c.line, RoundedCornerShape(8.dp))
                                .clickable { selectedWeekday = dow }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(label, color = if (active) Color.White else c.ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                Text("$count", color = if (active) Color.White.copy(alpha = 0.85f) else c.mute, fontSize = 11.sp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                val currentSlots = ttDays[selectedWeekday]
                val subMap = subjectsList.associateBy { it.id }
                if (currentSlots.isEmpty()) {
                    Text("No periods set for this day yet. Tap a subject below to add:", color = c.mute, fontSize = 12.sp)
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        currentSlots.forEachIndexed { slotIdx, subId ->
                            val nm = subMap[subId]?.name ?: "?"
                            Box(
                                modifier = Modifier
                                    .background(c.accent.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                    .border(1.dp, c.accent, RoundedCornerShape(8.dp))
                                    .clickable {
                                        val mutable = ttDays[selectedWeekday].toMutableList()
                                        mutable.removeAt(slotIdx)
                                        ttDays[selectedWeekday] = mutable
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            ) {
                                Text("${slotIdx + 1}. $nm ×", color = c.accent, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text("Tap to add period:", color = c.mute, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    subjectsList.forEach { sub ->
                        OutlinedButton(
                            onClick = {
                                ttDays[selectedWeekday] = ttDays[selectedWeekday] + sub.id
                            },
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, c.line),
                        ) {
                            Text("+ ${sub.name}", color = c.ink, fontSize = 12.sp)
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Apply timetable changes from (protects past attendance)", color = c.mute, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                DateFieldBox(effectiveFrom) { showEffectivePicker = true }
            } else {
                Spacer(Modifier.height(10.dp))
                Text("Periods per weekday (if not using named subjects)", color = c.mute, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
                val dayNames = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    dayNames.forEachIndexed { idx, label ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label, color = c.ink, fontSize = 14.sp)
                            OutlinedTextField(
                                value = weekdayInputs[idx],
                                onValueChange = { weekdayInputs[idx] = it },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center),
                                modifier = Modifier.width(82.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            HorizontalDivider(color = c.line)
            Spacer(Modifier.height(12.dp))
            Text("Appearance", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("system" to "System", "light" to "Light", "dark" to "Dark").forEach { (key, label) ->
                    val selected = state.settings.theme == key
                    OutlinedButton(
                        onClick = { onThemeChange(key) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (selected) c.accent.copy(alpha = 0.12f) else c.bg,
                            contentColor = if (selected) c.accent else c.ink,
                        ),
                    ) {
                        Text(label, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    localMsg = ""
                    val pw = weekdayInputs.map { it.toIntOrNull() ?: -1 }
                    onSave(
                        name, college, roll, year, sem, mid,
                        startFromDayOne, startDate, asOf, attended, conducted, minPct,
                        subjectsList.toList(), ttDays.toList(), effectiveFrom, pw,
                    )
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
            ) {
                Text(if (isFirstSetup) "Save and continue" else "Save", fontWeight = FontWeight.SemiBold)
            }

            val displayMsg = when {
                errorMsg.isNotEmpty() -> errorMsg to true
                localMsg.isNotEmpty() -> localMsg to localMsgBad
                successMsg.isNotEmpty() -> successMsg to false
                else -> null
            }
            if (displayMsg != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    displayMsg.first,
                    color = if (displayMsg.second) c.bad else c.good,
                    fontSize = 14.sp,
                )
            }

            if (!isFirstSetup) {
                // Semester Reset & Past Semesters
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Text("Semester & History", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                OutlinedButton(
                    onClick = { showNewSemDialog = true },
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, c.line),
                ) {
                    Text("Start New Semester (Reset to 0)", color = c.ink, fontWeight = FontWeight.SemiBold)
                }

                if (state.archives.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { showArchives = !showArchives },
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, c.line),
                    ) {
                        Text(
                            if (showArchives) "Hide past semesters" else "View past semesters (${state.archives.size})",
                            color = c.ink,
                        )
                    }
                    if (showArchives) {
                        Spacer(Modifier.height(8.dp))
                        state.archives.forEach { arch ->
                            val pct = if (arch.conducted > 0) AttendanceCalculator.formatPct(arch.attended * 100.0 / arch.conducted) else "–"
                            val label = listOf(arch.year, arch.sem, arch.mid).filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "Past Semester" }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(c.bg, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                            ) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(label, color = c.ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(pct, color = c.accent, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                                }
                                Text("${arch.attended} / ${arch.conducted} classes (${arch.startDate} to ${arch.endDate})", color = c.mute, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Account & Google Drive Backup
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Text("Account & Google Drive Backup", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))

                val isGuest = authUser == null && state.guest
                val acctText = when {
                    authUser != null -> "Signed in as ${authUser.email.ifBlank { authUser.name.ifBlank { "your Google account" } }}"
                    isGuest -> "Using offline mode. Sign in with Google to back up into your own Google Drive."
                    else -> ""
                }
                if (acctText.isNotEmpty()) {
                    Text(acctText, color = c.mute, fontSize = 13.sp)
                }
                if (authUser != null && syncStatus.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(syncStatus, color = c.mute, fontSize = 13.sp)
                }

                Spacer(Modifier.height(10.dp))
                if (authUser != null) {
                    val bk = state.backup
                    if (!cloudReady && bk != "off") {
                        Button(
                            onClick = onConnectDrive,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        ) {
                            Text("Sync with Google Drive", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (bk != "enc") {
                        Button(
                            onClick = onTurnOnEncrypted,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        ) {
                            Text("Turn on end-to-end encryption", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (bk == "enc" && !cloudReady) {
                        Button(
                            onClick = onUnlockBackup,
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        ) {
                            Text("Unlock your backup", fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if ((bk == "enc" && cloudReady) || (bk != "enc" && bk != "std")) {
                        OutlinedButton(
                            onClick = {
                                if (bk == "enc" && !stdArmed) stdArmed = true else {
                                    stdArmed = false
                                    onSwitchToStandard()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.line),
                        ) {
                            Text(
                                text = when {
                                    bk == "enc" && stdArmed -> "Tap again: store unencrypted in your Google Drive"
                                    bk == "enc" -> "Switch to standard Drive backup"
                                    else -> "Turn on standard Drive backup"
                                },
                                color = c.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (bk == "std" || bk == "enc") {
                        OutlinedButton(
                            onClick = {
                                if (!offArmed) offArmed = true else {
                                    offArmed = false
                                    onTurnOffBackup()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.line),
                        ) {
                            Text(
                                text = if (offArmed) "Tap again to delete your Drive backup" else "Turn off backup",
                                color = c.ink,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = onSignOut,
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.line),
                        ) {
                            Text("Sign out", color = c.ink, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = {
                                if (!delArmed) delArmed = true else {
                                    delArmed = false
                                    onDeleteAccount()
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, c.bad),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = c.badBg, contentColor = c.bad),
                        ) {
                            Text(
                                if (delArmed) "Tap again to erase" else "Delete backup & data",
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                } else if (isGuest) {
                    Button(
                        onClick = onSignIn,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                    ) {
                        Text("Sign in with Google", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (acctError.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(acctError, color = c.bad, fontSize = 13.sp)
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = c.line)
                Spacer(Modifier.height(12.dp))
                Text("About", color = c.ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Your attendance stays on this device and, if enabled, backs up directly to your own Google Drive app folder. No user list or data is kept on developer servers.",
                    color = c.mute,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Report a bug or suggest a feature",
                    color = c.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        uriHandler.openUri("https://github.com/sreevachan/attendance-tracker/issues/new")
                    },
                )
                Spacer(Modifier.height(8.dp))
                Text("Version ${BuildConfig.VERSION_NAME}", color = c.mute, fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showStartPicker) {
        IsoDatePickerDialog(startDate, { showStartPicker = false }) { picked ->
            showStartPicker = false
            startDate = picked
            if (isFirstSetup) effectiveFrom = picked
        }
    }
    if (showAsOfPicker) {
        IsoDatePickerDialog(asOf, { showAsOfPicker = false }) { picked ->
            showAsOfPicker = false
            asOf = picked
        }
    }
    if (showEffectivePicker) {
        IsoDatePickerDialog(effectiveFrom, { showEffectivePicker = false }) { picked ->
            showEffectivePicker = false
            effectiveFrom = picked
        }
    }
    if (showNewSemDialog) {
        StartNewSemesterDialog(
            initialYear = year,
            initialSem = sem,
            onDismiss = { showNewSemDialog = false },
            onConfirm = { ny, ns, nm, nDate, keep ->
                showNewSemDialog = false
                year = ny; sem = ns; mid = nm; startDate = nDate
                onStartNewSemester(ny, ns, nm, nDate, keep)
            },
        )
    }
}

@Composable
private fun DateFieldBox(value: String, onClick: () -> Unit) {
    val c = LocalAttendanceColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg, RoundedCornerShape(10.dp))
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(value, color = c.ink, fontSize = 15.sp)
    }
}

@Composable
private fun ModePill(label: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = LocalAttendanceColors.current
    Box(
        modifier = modifier
            .background(if (selected) c.accent.copy(alpha = 0.12f) else c.bg, RoundedCornerShape(10.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) c.accent else c.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) c.accent else c.ink,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun StartNewSemesterDialog(
    initialYear: String,
    initialSem: String,
    onDismiss: () -> Unit,
    onConfirm: (year: String, sem: String, mid: String, startDate: String, keepSubjects: Boolean) -> Unit,
) {
    var ny by remember { mutableStateOf(initialYear) }
    var ns by remember { mutableStateOf(initialSem) }
    var nm by remember { mutableStateOf("Mid 1") }
    var nDate by remember { mutableStateOf(AttendanceCalculator.keyOf(AttendanceCalculator.today())) }
    var keep by remember { mutableStateOf(true) }
    var showDatePicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start New Semester") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This archives your current semester summary and resets active attendance to 0.")
                OutlinedTextField(value = ny, onValueChange = { ny = it }, label = { Text("New Year") }, singleLine = true)
                OutlinedTextField(value = ns, onValueChange = { ns = it }, label = { Text("New Semester") }, singleLine = true)
                OutlinedTextField(value = nm, onValueChange = { nm = it }, label = { Text("Mid") }, singleLine = true)
                TextButton(onClick = { showDatePicker = true }) {
                    Text("Start date: $nDate (tap to change)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { keep = !keep }) {
                    Checkbox(checked = keep, onCheckedChange = { keep = it })
                    Text("Keep existing subjects & timetable")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(ny, ns, nm, nDate, keep) }) { Text("Reset & Start") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )

    if (showDatePicker) {
        IsoDatePickerDialog(nDate, { showDatePicker = false }) { picked ->
            showDatePicker = false
            nDate = picked
        }
    }
}
