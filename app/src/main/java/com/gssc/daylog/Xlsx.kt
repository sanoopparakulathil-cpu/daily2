package com.gssc.daylog

import java.io.ByteArrayOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Writes a real .xlsx file by hand. No Apache POI, no dependencies. */
object Xlsx {

    private fun esc(s: String) = s
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")

    private fun colName(i: Int): String {
        var n = i
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
            if (n < 0) break
        }
        return sb.toString()
    }

    private fun sheetXml(rows: List<List<Any>>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
        sb.append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
        sb.append("<cols>")
        sb.append("<col min=\"1\" max=\"1\" width=\"12\"/>")
        sb.append("<col min=\"2\" max=\"2\" width=\"10\"/>")
        sb.append("<col min=\"3\" max=\"3\" width=\"30\"/>")
        sb.append("<col min=\"4\" max=\"4\" width=\"10\"/>")
        sb.append("<col min=\"5\" max=\"5\" width=\"12\"/>")
        sb.append("<col min=\"6\" max=\"8\" width=\"14\"/>")
        sb.append("</cols><sheetData>")

        for ((ri, row) in rows.withIndex()) {
            sb.append("<row r=\"").append(ri + 1).append("\">")
            for ((ci, cell) in row.withIndex()) {
                val ref = colName(ci) + (ri + 1)
                if (cell is Number) {
                    sb.append("<c r=\"").append(ref).append("\"><v>")
                        .append(String.format(Locale.US, "%s", cell.toString()))
                        .append("</v></c>")
                } else {
                    sb.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                        .append(esc(cell.toString()))
                        .append("</t></is></c>")
                }
            }
            sb.append("</row>")
        }
        sb.append("</sheetData></worksheet>")
        return sb.toString()
    }

    fun build(rows: List<List<Any>>, sheetName: String): ByteArray {
        val bos = ByteArrayOutputStream()
        val zos = ZipOutputStream(bos)

        fun put(name: String, data: String) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(data.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        put(
            "[Content_Types].xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>" +
                "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>" +
                "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>" +
                "</Types>"
        )
        put(
            "_rels/.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>" +
                "</Relationships>"
        )
        put(
            "xl/workbook.xml",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" " +
                "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">" +
                "<sheets><sheet name=\"" + esc(sheetName) + "\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>"
        )
        put(
            "xl/_rels/workbook.xml.rels",
            "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>" +
                "</Relationships>"
        )
        put("xl/worksheets/sheet1.xml", sheetXml(rows))

        zos.close()
        return bos.toByteArray()
    }

    val HEADER = listOf<Any>(
        "Date", "Time in", "Place", "Time out", "Stay", "Distance km", "Latitude", "Longitude"
    )

    fun row(day: String, s: Stop): List<Any> = listOf(
        day,
        Fmt.hhmm(s.start),
        if (s.name.isBlank()) "Unnamed" else s.name,
        Fmt.hhmm(s.end),
        Fmt.dur(s.end - s.start),
        String.format(Locale.US, "%.2f", s.km).toDouble(),
        String.format(Locale.US, "%.6f", s.lat).toDouble(),
        String.format(Locale.US, "%.6f", s.lng).toDouble()
    )
}
