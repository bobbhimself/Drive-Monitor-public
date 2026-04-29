package com.bobbhimself.drivemonitor.data.export

import com.bobbhimself.drivemonitor.data.model.TripEvent
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object LogXlsxExporter {

    private val TIMESTAMP_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /**
     * Builds an XLSX file by injecting data rows into the bundled template.
     * The template supplies all formatting: column widths, header styles, and
     * conditional formatting (caution=yellow, alert=red on the Severity column).
     *
     * @param templateBytes Raw bytes of drive_monitor_template.xltx from assets.
     * @param events        Events to write, newest-first as supplied by the repository.
     */
    fun buildXlsx(templateBytes: ByteArray, events: List<TripEvent>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zipOut ->
            ZipInputStream(ByteArrayInputStream(templateBytes)).use { zipIn ->
                var entry = zipIn.nextEntry
                while (entry != null) {
                    val entryBytes = zipIn.readBytes()
                    val patched = when (entry.name) {
                        "[Content_Types].xml" -> patchContentTypes(entryBytes)
                        "xl/worksheets/sheet1.xml" -> buildSheet(events).toByteArray(Charsets.UTF_8)
                        else -> entryBytes
                    }
                    zipOut.putNextEntry(ZipEntry(entry.name))
                    zipOut.write(patched)
                    zipOut.closeEntry()
                    entry = zipIn.nextEntry
                }
            }
        }
        return out.toByteArray()
    }

    /** Patches the content-type declaration so the output is treated as a workbook, not a template. */
    private fun patchContentTypes(bytes: ByteArray): ByteArray =
        bytes.toString(Charsets.UTF_8)
            .replace("spreadsheetml.template.main+xml", "spreadsheetml.sheet.main+xml")
            .toByteArray(Charsets.UTF_8)

    /** Builds the full xl/worksheets/sheet1.xml content with header + data rows. */
    private fun buildSheet(events: List<TripEvent>): String {
        val rowCount = events.size + 1 // header row + data rows
        val sb = StringBuilder()

        // Namespace declarations match the template so x14ac:dyDescent is valid.
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("\n")
        sb.append(
            """<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" """ +
            """xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships" """ +
            """xmlns:mc="http://schemas.openxmlformats.org/markup-compatibility/2006" """ +
            """mc:Ignorable="x14ac xr xr2 xr3" """ +
            """xmlns:x14ac="http://schemas.microsoft.com/office/spreadsheetml/2009/9/ac" """ +
            """xmlns:xr="http://schemas.microsoft.com/office/spreadsheetml/2014/revision" """ +
            """xmlns:xr2="http://schemas.microsoft.com/office/spreadsheetml/2015/revision2" """ +
            """xmlns:xr3="http://schemas.microsoft.com/office/spreadsheetml/2016/revision3">"""
        )

        // Dimension covers header row + all data rows.
        sb.append("""<dimension ref="A1:C$rowCount"/>""")

        // Sheet view — single tab, no freeze pane needed.
        sb.append("""<sheetViews><sheetView tabSelected="1" workbookViewId="0"/>""")
        sb.append("""</sheetViews>""")

        sb.append("""<sheetFormatPr defaultRowHeight="14.5" x14ac:dyDescent="0.35"/>""")

        // Column widths and styles copied from template:
        //   A (timestamp) = 29.82, s=1 (centered); B (type) = 11.09, s=1; C (severity) = 8.73, s=1
        sb.append(
            """<cols>""" +
            """<col min="1" max="1" width="29.81640625" style="1" customWidth="1"/>""" +
            """<col min="2" max="2" width="11.08984375" style="1" customWidth="1"/>""" +
            """<col min="3" max="3" width="8.7265625" style="1"/>""" +
            """</cols>"""
        )

        sb.append("<sheetData>")

        // Header row — uses shared strings (indices 0=Timestamp, 1=Type, 2=Severity).
        // s="2" = bold+centered style; row s="3" = bold font on row.
        sb.append(
            """<row r="1" spans="1:3" s="3" customFormat="1" x14ac:dyDescent="0.35">""" +
            """<c r="A1" s="2" t="s"><v>0</v></c>""" +
            """<c r="B1" s="2" t="s"><v>1</v></c>""" +
            """<c r="C1" s="2" t="s"><v>2</v></c>""" +
            """</row>"""
        )

        // Data rows — inline strings so sharedStrings.xml stays unchanged.
        events.forEachIndexed { index, event ->
            val rowNum = index + 2 // row 1 is header
            val timestamp = ZonedDateTime
                .ofInstant(Instant.ofEpochMilli(event.timestampUtcMillis), ZoneId.systemDefault())
                .format(TIMESTAMP_FORMATTER)
            val category = event.category.name.lowercase()
            val severity = event.severity.name.lowercase()
            sb.append("""<row r="$rowNum">""")
            sb.append("""<c r="A$rowNum" t="inlineStr"><is><t>${escapeXml(timestamp)}</t></is></c>""")
            sb.append("""<c r="B$rowNum" t="inlineStr"><is><t>${escapeXml(category)}</t></is></c>""")
            sb.append("""<c r="C$rowNum" t="inlineStr"><is><t>${escapeXml(severity)}</t></is></c>""")
            sb.append("</row>")
        }

        sb.append("</sheetData>")

        // Conditional formatting copied from template:
        //   dxfId=1 → "caution" → yellow background
        //   dxfId=0 → "alert"   → red background
        sb.append(
            """<conditionalFormatting sqref="C1:C1048576">""" +
            """<cfRule type="containsText" dxfId="1" priority="1" operator="containsText" text="caution">""" +
            """<formula>NOT(ISERROR(SEARCH("caution",C1)))</formula></cfRule>""" +
            """<cfRule type="containsText" dxfId="0" priority="2" operator="containsText" text="alert">""" +
            """<formula>NOT(ISERROR(SEARCH("alert",C1)))</formula></cfRule>""" +
            """</conditionalFormatting>"""
        )

        sb.append("""<pageMargins left="0.7" right="0.7" top="0.75" bottom="0.75" header="0.3" footer="0.3"/>""")
        sb.append("</worksheet>")

        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
