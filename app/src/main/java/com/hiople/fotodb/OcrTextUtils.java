package com.hiople.fotodb;


final class OcrTextUtils {
    private OcrTextUtils() { }

    static String normalize(String value) {
        if (value == null || value.isEmpty()) return "";
        String[] lines = value.replace('\r', '\n').split("\\n", -1);
        StringBuilder cleaned = new StringBuilder();
        for (String line : lines) {
            String normalized = line.trim().replaceAll("[ \\t]+", " ");
            if (!normalized.isEmpty()) {
                if (cleaned.length() > 0) cleaned.append('\n');
                cleaned.append(normalized);
            }
        }
        return cleaned.toString();

    }
    static String append(String existing, String recognized) {
        String right = normalize(recognized);
        String left = existing == null ? "" : existing.trim();
        if (left.isEmpty()) return right;
        if (right.isEmpty()) return left;
        return left + "\n\n" + right;
    }
}
