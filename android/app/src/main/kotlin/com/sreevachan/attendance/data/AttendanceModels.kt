package com.sreevachan.attendance.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val name: String = "",
    val college: String = "",
    val roll: String = "",
    val year: String = "",
    val sem: String = "",
    val mid: String = "",
)

@Serializable
data class AppSettings(
    val minPct: Int = 75,
    val theme: String = "system",
)

@Serializable
data class BaseAttendance(
    val a: Int = 0,
    val c: Int = 0,
    val asOf: String = AttendanceCalculator.keyOf(AttendanceCalculator.today().minusDays(1)),
)

@Serializable
data class SubjectItem(
    val id: String,
    val name: String,
    val minPct: Int? = null,
    val baseA: Int = 0,
    val baseC: Int = 0,
)

@Serializable
data class TimetableVersion(
    val from: String,
    val days: List<List<String>>, // size 7: 0=Sun .. 6=Sat; each element is list of subject IDs
)

@Serializable
data class DaySlot(
    val sub: String,
    val st: String, // "P", "A", "H"
)

@Serializable
data class DayMark(
    val s: String = "P",
    val n: Int = 0,
    val a: Int? = null,
    val slots: List<DaySlot>? = null,
)

@Serializable
data class ArchivedSubject(
    val name: String,
    val attended: Int,
    val conducted: Int,
)

@Serializable
data class SemesterArchive(
    val year: String = "",
    val sem: String = "",
    val mid: String = "",
    val startDate: String = "",
    val endDate: String = "",
    val attended: Int = 0,
    val conducted: Int = 0,
    val subjects: List<ArchivedSubject> = emptyList(),
)

@Serializable
data class AttendancePayload(
    val v: Int = 4,
    val profile: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val pw: List<Int> = listOf(0, 6, 5, 6, 6, 6, 5),
    val subjects: List<SubjectItem> = emptyList(),
    val timetable: List<TimetableVersion> = emptyList(),
    val startDate: String = AttendanceCalculator.keyOf(AttendanceCalculator.today()),
    val base: BaseAttendance = BaseAttendance(),
    val days: Map<String, DayMark> = emptyMap(),
    val exam: String = AttendanceCalculator.keyOf(AttendanceCalculator.today().plusMonths(4)),
    val archives: List<SemesterArchive> = emptyList(),
)

@Serializable
data class AttendanceState(
    val v: Int = 4,
    val profile: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val pw: List<Int> = listOf(0, 6, 5, 6, 6, 6, 5),
    val subjects: List<SubjectItem> = emptyList(),
    val timetable: List<TimetableVersion> = emptyList(),
    val startDate: String = AttendanceCalculator.keyOf(AttendanceCalculator.today()),
    val base: BaseAttendance = BaseAttendance(),
    val days: Map<String, DayMark> = emptyMap(),
    val exam: String = AttendanceCalculator.keyOf(AttendanceCalculator.today().plusMonths(4)),
    val archives: List<SemesterArchive> = emptyList(),
    val updatedAt: Long = 0L,
    val uid: String? = null,
    val guest: Boolean = false,
    val backup: String = "",
) {
    fun toPayload(): AttendancePayload = AttendancePayload(
        v = 4,
        profile = profile,
        settings = settings,
        pw = pw,
        subjects = subjects,
        timetable = timetable,
        startDate = startDate,
        base = base,
        days = days,
        exam = exam,
        archives = archives,
    )

    fun withPayload(payload: AttendancePayload, timestamp: Long): AttendanceState = copy(
        v = 4,
        profile = payload.profile,
        settings = payload.settings.copy(theme = payload.settings.theme.ifBlank { "system" }),
        pw = if (payload.pw.size == 7) payload.pw else listOf(0, 6, 5, 6, 6, 6, 5),
        subjects = payload.subjects,
        timetable = payload.timetable,
        startDate = payload.startDate,
        base = payload.base,
        days = payload.days,
        exam = payload.exam,
        archives = payload.archives,
        updatedAt = timestamp,
    )
}

@Serializable
data class LegacyV3State(
    val v: Int = 3,
    val profile: UserProfile? = null,
    val settings: AppSettings = AppSettings(),
    val pw: List<Int> = listOf(0, 6, 5, 6, 6, 6, 5),
    val base: BaseAttendance = BaseAttendance(),
    val days: Map<String, DayMark> = emptyMap(),
    val exam: String = "",
    val updatedAt: Long = 0L,
    val uid: String? = null,
    val guest: Boolean = false,
    val backup: String = "",
)

@Serializable
data class UpdateInfo(
    val version: String = "",
    val build: Int = 0,
    val notes: String = "",
    val releaseUrl: String? = null,
    val apkUrl: String? = null,
)

data class TotalsResult(
    val attended: Int,
    val conducted: Int,
    val daysPresent: Int,
    val daysTotal: Int,
)

data class SubjectStat(
    val subject: SubjectItem,
    val attended: Int,
    val conducted: Int,
    val pct: Double,
    val minPct: Int,
    val ok: Boolean,
    val canMiss: Int,
    val needToAttend: Int,
)

data class PlanResult(
    val classesLeft: Int,
    val maxPct: Double,
    val canMiss: Int,
    val hasClasses: Boolean,
)

@Serializable
data class AuthUser(
    val uid: String,
    val name: String,
    val email: String,
)

sealed interface CloudDoc {
    val updatedAt: Long

    data class Standard(
        val dataJson: String,
        override val updatedAt: Long,
    ) : CloudDoc

    data class Encrypted(
        val meta: CryptoMeta,
        val blob: String,
        override val updatedAt: Long,
    ) : CloudDoc
}

sealed interface CloudGetResult {
    data object NetworkError : CloudGetResult
    data object NotFound : CloudGetResult
    data class Found(val doc: CloudDoc) : CloudGetResult
}

data class CryptoMeta(
    val salt: String,
    val iters: Int,
    val wrapPass: String,
    val recSalt: String,
    val wrapRec: String,
)

object AttendanceCalculator {
    private val isoFmt = DateTimeFormatter.ISO_LOCAL_DATE

    fun today(): LocalDate = LocalDate.now()

    fun keyOf(date: LocalDate): String = date.format(isoFmt)

    fun parseDate(key: String): LocalDate? = runCatching { LocalDate.parse(key, isoFmt) }.getOrNull()

    /** Matches JS Date.getDay(): 0 = Sunday, 1 = Monday .. 6 = Saturday. */
    fun dowOf(date: LocalDate): Int = date.dayOfWeek.value % 7

    fun timetableForDate(state: AttendanceState, dateKey: String): List<List<String>>? {
        if (state.timetable.isEmpty()) return null
        val sorted = state.timetable.sortedBy { it.from }
        return sorted.lastOrNull { it.from <= dateKey }?.days ?: sorted.first().days
    }

    fun scheduledSubjectsForDate(state: AttendanceState, dateKey: String): List<String> {
        val date = parseDate(dateKey) ?: return emptyList()
        val dow = dowOf(date)
        if (dow == 0) return emptyList()
        val tt = timetableForDate(state, dateKey) ?: return emptyList()
        return tt.getOrElse(dow) { emptyList() }
    }

    fun periodsFor(state: AttendanceState, date: LocalDate): Int {
        val key = keyOf(date)
        val dow = dowOf(date)
        if (dow == 0) return 0
        val tt = timetableForDate(state, key)
        if (tt != null) {
            return tt.getOrElse(dow) { emptyList() }.size
        }
        return state.pw.getOrElse(dow) { 0 }
    }

    fun dayPeriods(state: AttendanceState, key: String): Int {
        val existing = state.days[key]
        if (existing?.slots != null) {
            return existing.slots.count { it.st != "H" }
        }
        if (existing != null && existing.n > 0) return existing.n
        val date = parseDate(key) ?: return 0
        return periodsFor(state, date)
    }

    fun dayCounts(mark: DayMark): Pair<Int, Int> {
        val slots = mark.slots
        if (slots != null) {
            var a = 0
            var c = 0
            for (slot in slots) {
                when (slot.st) {
                    "P" -> { a++; c++ }
                    "A" -> c++
                }
            }
            return a to c
        }
        if (mark.s == "H" || mark.n <= 0) return 0 to 0
        val a = when (mark.s) {
            "P" -> mark.n
            "X" -> min((mark.a ?: 0).coerceAtLeast(0), mark.n)
            else -> 0
        }
        return a to mark.n
    }

    fun overallBase(state: AttendanceState): Pair<Int, Int> {
        if (state.subjects.isEmpty()) return state.base.a to state.base.c
        val subA = state.subjects.sumOf { it.baseA }
        val subC = state.subjects.sumOf { it.baseC }
        return if (subC > 0 || subA > 0) {
            (subA + state.base.a) to (subC + state.base.c)
        } else {
            state.base.a to state.base.c
        }
    }

    fun totals(state: AttendanceState, monthPrefix: String? = null): TotalsResult {
        var a = 0
        var c = 0
        var days = 0
        var tot = 0
        for ((k, mark) in state.days) {
            if (monthPrefix != null && !k.startsWith(monthPrefix)) continue
            val (da, dc) = dayCounts(mark)
            if (dc <= 0) continue
            a += da
            c += dc
            tot++
            if (da == dc) days++
        }
        return TotalsResult(a, c, days, tot)
    }

    fun subjectStats(state: AttendanceState): List<SubjectStat> {
        if (state.subjects.isEmpty()) return emptyList()
        val attendedMap = mutableMapOf<String, Int>()
        val conductedMap = mutableMapOf<String, Int>()

        for (mark in state.days.values) {
            val slots = mark.slots ?: continue
            for (slot in slots) {
                when (slot.st) {
                    "P" -> {
                        attendedMap[slot.sub] = (attendedMap[slot.sub] ?: 0) + 1
                        conductedMap[slot.sub] = (conductedMap[slot.sub] ?: 0) + 1
                    }
                    "A" -> {
                        conductedMap[slot.sub] = (conductedMap[slot.sub] ?: 0) + 1
                    }
                }
            }
        }

        return state.subjects.map { sub ->
            val a = sub.baseA + (attendedMap[sub.id] ?: 0)
            val c = sub.baseC + (conductedMap[sub.id] ?: 0)
            val minP = sub.minPct ?: state.settings.minPct
            val pct = if (c > 0) a * 100.0 / c else 0.0
            val ok = c > 0 && a * 100 >= minP * c
            val canMiss = if (c > 0 && ok && minP > 0) {
                floor((a * 100.0 - minP * c) / minP.toDouble()).toInt().coerceAtLeast(0)
            } else {
                0
            }
            val need = if (c > 0 && !ok) {
                if (minP >= 100) -1
                else ceil((minP * c - 100.0 * a) / (100.0 - minP)).toInt().coerceAtLeast(0)
            } else {
                0
            }
            SubjectStat(sub, a, c, pct, minP, ok, canMiss, need)
        }
    }

    fun unmarkedPastDays(state: AttendanceState, today: LocalDate = today()): List<String> {
        val asOf = parseDate(state.base.asOf) ?: return emptyList()
        val todayKey = keyOf(today)
        val out = mutableListOf<String>()
        var d = asOf.plusDays(1)
        repeat(800) {
            val k = keyOf(d)
            if (k >= todayKey) return out
            if (!state.days.containsKey(k) && periodsFor(state, d) > 0) {
                out.add(k)
            }
            d = d.plusDays(1)
        }
        return out
    }

    fun planAhead(
        state: AttendanceState,
        attendedOverall: Int,
        conductedOverall: Int,
        today: LocalDate = today(),
    ): PlanResult? {
        if (state.exam.isBlank()) return null
        var left = 0
        var d = today
        repeat(800) {
            val k = keyOf(d)
            if (k >= state.exam) return@repeat
            if (!state.days.containsKey(k) && k > state.base.asOf) {
                left += periodsFor(state, d)
            }
            d = d.plusDays(1)
        }
        val total = conductedOverall + left
        if (total == 0) {
            return PlanResult(left, 0.0, 0, hasClasses = false)
        }
        val minPct = state.settings.minPct
        val maxPct = (attendedOverall + left) * 100.0 / total
        val mm = min(
            left,
            floor(((attendedOverall + left) * 100.0 - minPct * total) / 100.0).toInt(),
        )
        return PlanResult(left, maxPct, mm, hasClasses = true)
    }

    fun deriveWeekdayCounts(days: List<List<String>>): List<Int> =
        (0..6).map { i -> days.getOrElse(i) { emptyList() }.size }

    fun migrateFromV3(v3: LegacyV3State): AttendanceState {
        val asOfDate = parseDate(v3.base.asOf)
        val start = asOfDate?.plusDays(1)?.let(::keyOf) ?: keyOf(today())
        return AttendanceState(
            v = 4,
            profile = v3.profile,
            settings = v3.settings,
            pw = if (v3.pw.size == 7) v3.pw else listOf(0, 6, 5, 6, 6, 6, 5),
            subjects = emptyList(),
            timetable = emptyList(),
            startDate = start,
            base = v3.base,
            days = v3.days,
            exam = v3.exam.ifBlank { keyOf(today().plusMonths(4)) },
            archives = emptyList(),
            updatedAt = v3.updatedAt,
            uid = v3.uid,
            guest = v3.guest,
            backup = v3.backup,
        )
    }

    fun formatPct(pct: Double): String = String.format(Locale.US, "%.1f%%", pct)

    fun niceShortDate(key: String): String {
        val d = parseDate(key) ?: return key
        val mon = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return "${d.dayOfMonth} $mon"
    }

    fun niceChipDate(key: String): String {
        val d = parseDate(key) ?: return key
        val wk = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        val mon = d.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        return "$wk, ${d.dayOfMonth} $mon"
    }

    fun niceFullDate(key: String): String {
        val d = parseDate(key) ?: return key
        val wk = d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
        val mon = d.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$wk, ${d.dayOfMonth} $mon"
    }

    fun monthTitle(ym: YearMonth): String {
        val mon = ym.month.getDisplayName(TextStyle.FULL, Locale.getDefault())
        return "$mon ${ym.year}"
    }
}
