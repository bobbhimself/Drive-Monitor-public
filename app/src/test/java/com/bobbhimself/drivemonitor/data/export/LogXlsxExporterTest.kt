package com.bobbhimself.drivemonitor.data.export

import com.bobbhimself.drivemonitor.data.model.AlertSeverity
import com.bobbhimself.drivemonitor.data.model.MotionCategory
import com.bobbhimself.drivemonitor.data.model.TripEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

class LogXlsxExporterTest {

    private val templateBytes: ByteArray by lazy {
        LogXlsxExporterTest::class.java
            .getResourceAsStream("/drive_monitor_template.xltx")!!
            .readBytes()
    }

    private fun buildOutput(events: List<TripEvent>): ByteArray =
        LogXlsxExporter.buildXlsx(templateBytes, events)

    private fun readZipEntry(bytes: ByteArray, name: String): String {
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == name) return zip.readBytes().toString(Charsets.UTF_8)
                entry = zip.nextEntry
            }
        }
        error("Entry '$name' not found in ZIP")
    }

    @Test
    fun zipStructure_containsRequiredEntries() {
        val bytes = buildOutput(emptyList())
        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                names.add(entry.name)
                entry = zip.nextEntry
            }
        }
        assertTrue("[Content_Types].xml missing", "[Content_Types].xml" in names)
        assertTrue("xl/worksheets/sheet1.xml missing", "xl/worksheets/sheet1.xml" in names)
        assertTrue("xl/styles.xml missing", "xl/styles.xml" in names)
        assertTrue("xl/sharedStrings.xml missing", "xl/sharedStrings.xml" in names)
    }

    @Test
    fun contentType_isPatchedToSheet() {
        val content = readZipEntry(buildOutput(emptyList()), "[Content_Types].xml")
        assertTrue(
            "Expected sheet content type",
            "spreadsheetml.sheet.main+xml" in content
        )
        assertFalse(
            "Template content type should have been replaced",
            "spreadsheetml.template.main+xml" in content
        )
    }

    @Test
    fun rowCount_matchesInputPlusHeader() {
        val events = listOf(
            TripEvent(1_000L, MotionCategory.BRAKING, AlertSeverity.CAUTION),
            TripEvent(2_000L, MotionCategory.TURNING, AlertSeverity.ALERT),
            TripEvent(3_000L, MotionCategory.ACCELERATION, AlertSeverity.CAUTION),
        )
        val sheet = readZipEntry(buildOutput(events), "xl/worksheets/sheet1.xml")
        val rowCount = Regex("<row ").findAll(sheet).count()
        assertEquals("Expected 1 header + 3 data rows", 4, rowCount)
    }

    @Test
    fun dataValues_areLowercaseAndIso8601() {
        val epochMillis = 1_744_761_600_000L // a fixed point in time
        val event = TripEvent(epochMillis, MotionCategory.BRAKING, AlertSeverity.ALERT)
        val sheet = readZipEntry(buildOutput(listOf(event)), "xl/worksheets/sheet1.xml")

        // Category and severity must be lowercase
        assertTrue("Expected 'braking' in sheet", "braking" in sheet)
        assertTrue("Expected 'alert' in sheet", "alert" in sheet)

        // Timestamp must be ISO 8601 with timezone offset
        val expectedTimestamp = ZonedDateTime
            .ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault())
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
        assertTrue(
            "Expected ISO 8601 timestamp '$expectedTimestamp' in sheet",
            expectedTimestamp in sheet
        )
    }
}
