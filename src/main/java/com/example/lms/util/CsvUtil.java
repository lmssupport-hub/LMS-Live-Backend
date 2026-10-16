package com.example.lms.util;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC-4180 CSV reader/writer (no third-party dependency).
 * - Reader enforces row-count and per-row size caps so a hostile 100 MB upload cannot exhaust memory.
 * - Writer neutralises spreadsheet formula injection (=, +, -, @).
 */
public final class CsvUtil {

    private static final String FORMULA_TRIGGERS = "=+-@\t\r";

    private CsvUtil() {
    }

    /** @throws IllegalArgumentException for malformed / oversized input (message is user-safe). */
    public static List<List<String>> parse(Reader reader, int maxRecords, int maxRecordChars) throws IOException {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean quotePending = false;
        boolean fieldQuoted = false;
        boolean first = true;
        int recordChars = 0;
        int c;

        while ((c = reader.read()) != -1) {
            char ch = (char) c;
            if (first) {
                first = false;
                if (ch == '\uFEFF') continue; // strip UTF-8 BOM (Excel)
            }
            if (++recordChars > maxRecordChars) {
                throw new IllegalArgumentException("A row in the file exceeds the maximum allowed size.");
            }
            if (inQuotes) {
                if (quotePending) {
                    if (ch == '"') {            // escaped quote ""
                        field.append('"');
                        quotePending = false;
                        continue;
                    }
                    inQuotes = false;           // closing quote; reprocess ch below
                    quotePending = false;
                } else if (ch == '"') {
                    quotePending = true;
                    continue;
                } else {
                    field.append(ch);
                    continue;
                }
            }
            if (ch == '"' && field.length() == 0 && !fieldQuoted) {
                inQuotes = true;
                fieldQuoted = true;
            } else if (ch == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldQuoted = false;
            } else if (ch == '\n') {
                row = endRecord(records, row, field, maxRecords);
                fieldQuoted = false;
                recordChars = 0;
            } else if (ch != '\r') {
                field.append(ch);
            }
        }

        if (inQuotes && !quotePending) {
            throw new IllegalArgumentException("The file contains an unterminated quoted value.");
        }
        if (field.length() > 0 || !row.isEmpty()) {
            endRecord(records, row, field, maxRecords);
        }
        return records;
    }

    private static List<String> endRecord(List<List<String>> records, List<String> row,
                                          StringBuilder field, int maxRecords) {
        row.add(field.toString());
        field.setLength(0);
        boolean blankLine = row.size() == 1 && row.get(0).isEmpty();
        if (!blankLine) {
            records.add(row);
            if (records.size() > maxRecords) {
                throw new IllegalArgumentException("The file contains too many rows.");
            }
        }
        return new ArrayList<>();
    }

    public static String toLine(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    public static String escape(String value) {
        if (value == null || value.isEmpty()) return "";
        String s = value;
        if (FORMULA_TRIGGERS.indexOf(s.charAt(0)) >= 0) {
            s = "'" + s;
        }
        if (s.indexOf(',') >= 0 || s.indexOf('"') >= 0 || s.indexOf('\n') >= 0 || s.indexOf('\r') >= 0) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }

    /** Reverses the formula-injection guard applied by {@link #escape(String)} on export. */
    public static String unprotect(String value) {
        if (value != null && value.length() > 1 && value.charAt(0) == '\''
                && FORMULA_TRIGGERS.indexOf(value.charAt(1)) >= 0) {
            return value.substring(1);
        }
        return value;
    }
}