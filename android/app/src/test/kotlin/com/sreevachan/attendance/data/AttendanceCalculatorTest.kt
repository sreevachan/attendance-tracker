package com.sreevachan.attendance.data

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AttendanceCalculatorTest {
    @Test
    fun timetableHistoryProtectsPastDaysAndComputesSubjectStats() {
        val subjects = listOf(
            SubjectItem(id = "eng", name = "English", baseA = 4, baseC = 5),
            SubjectItem(id = "math", name = "Maths", baseA = 5, baseC = 5),
        )
        // Week 1: 2 classes/day; Week 2 (effective 2026-11-09): 3 classes/day
        val tt = listOf(
            TimetableVersion(
                from = "2026-11-01",
                days = listOf(
                    emptyList(),
                    listOf("eng", "math"),
                    listOf("eng", "math"),
                    listOf("eng", "math"),
                    listOf("eng", "math"),
                    listOf("eng", "math"),
                    listOf("eng"),
                ),
            ),
            TimetableVersion(
                from = "2026-11-09",
                days = listOf(
                    emptyList(),
                    listOf("eng", "math", "eng"),
                    listOf("eng", "math", "math"),
                    listOf("eng", "math", "eng"),
                    listOf("eng", "math", "math"),
                    listOf("eng", "math", "eng"),
                    listOf("eng", "math"),
                ),
            ),
        )
        val state = AttendanceState(
            v = 4,
            startDate = "2026-11-02",
            base = BaseAttendance(a = 0, c = 0, asOf = "2026-11-01"),
            subjects = subjects,
            timetable = tt,
            settings = AppSettings(minPct = 75),
            days = mapOf(
                "2026-11-02" to DayMark(
                    s = "X",
                    n = 2,
                    slots = listOf(DaySlot("eng", "P"), DaySlot("math", "A")),
                ),
            ),
            exam = "2026-11-11",
        )

        // Past unmarked day (2026-11-03 Tue) still uses Week 1 (2 periods), NOT Week 2 (3 periods)
        assertEquals(2, AttendanceCalculator.periodsFor(state, LocalDate.of(2026, 11, 3)))
        // Future day (2026-11-09 Mon) uses Week 2 (3 periods)
        assertEquals(3, AttendanceCalculator.periodsFor(state, LocalDate.of(2026, 11, 9)))

        val stats = AttendanceCalculator.subjectStats(state)
        assertEquals(2, stats.size)
        // English: base 4/5 + 1/1 = 5/6
        assertEquals(5, stats[0].attended)
        assertEquals(6, stats[0].conducted)
        // Maths: base 5/5 + 0/1 = 5/6
        assertEquals(5, stats[1].attended)
        assertEquals(6, stats[1].conducted)

        val (baseA, baseC) = AttendanceCalculator.overallBase(state)
        val totals = AttendanceCalculator.totals(state)
        assertEquals(10, baseA + totals.attended)
        assertEquals(12, baseC + totals.conducted)

        val plan = AttendanceCalculator.planAhead(
            state = state,
            attendedOverall = baseA + totals.attended,
            conductedOverall = baseC + totals.conducted,
            today = LocalDate.of(2026, 11, 9),
        )
        assertNotNull(plan)
        // Nov 9 (Mon=3) + Nov 10 (Tue=3) = 6 classes left before Nov 11
        assertEquals(6, plan!!.classesLeft)
        assertTrue(plan.hasClasses)
    }

    @Test
    fun v3MigrationAndCryptoRoundTrip() {
        val v3 = LegacyV3State(
            base = BaseAttendance(a = 30, c = 40, asOf = "2026-10-31"),
            days = mapOf("2026-11-02" to DayMark(s = "P", n = 6)),
            exam = "2027-06-30",
        )
        val v4 = AttendanceCalculator.migrateFromV3(v3)
        assertEquals(4, v4.v)
        assertEquals("2026-11-01", v4.startDate)
        assertEquals(6, AttendanceCalculator.totals(v4).attended)

        val setup = CryptoHelper.setupKeys("my-secret-passphrase")
        val payload = """{"v":4,"profile":{"name":"Test"}}""".encodeToByteArray()
        val sealed = CryptoHelper.seal(setup.dataKey, payload)

        val (passKey, _) = CryptoHelper.unlockWith("my-secret-passphrase", setup.meta, isRecovery = false)
        assertEquals("{\"v\":4,\"profile\":{\"name\":\"Test\"}}", CryptoHelper.unseal(passKey, sealed).decodeToString())

        val formatted = CryptoHelper.formatRecoveryCode(setup.recoveryCode)
        val normalized = CryptoHelper.normalizeRecoveryInput(formatted)
        val (recKey, _) = CryptoHelper.unlockWith(normalized, setup.meta, isRecovery = true)
        assertEquals("{\"v\":4,\"profile\":{\"name\":\"Test\"}}", CryptoHelper.unseal(recKey, sealed).decodeToString())
    }
}
