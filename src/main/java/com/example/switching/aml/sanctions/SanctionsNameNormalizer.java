package com.example.switching.aml.sanctions;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Produces a stable screening key without changing meaningful Lao/Thai marks.
 *
 * <p>Latin accents are folded (for example, "José" becomes "jose"), while
 * combining marks belonging to Lao, Thai, and other non-Latin scripts are
 * preserved because removing them changes the written name. Punctuation and
 * repeated whitespace are normalized to a single space.</p>
 */
public final class SanctionsNameNormalizer {

    private SanctionsNameNormalizer() {
    }

    public static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }

        String decomposed = Normalizer.normalize(
                value.toLowerCase(Locale.ROOT), Normalizer.Form.NFKD);
        StringBuilder folded = new StringBuilder(decomposed.length());
        Character.UnicodeScript baseScript = Character.UnicodeScript.UNKNOWN;
        boolean pendingSpace = false;

        for (int offset = 0; offset < decomposed.length();) {
            int codePoint = decomposed.codePointAt(offset);
            offset += Character.charCount(codePoint);
            int type = Character.getType(codePoint);

            if (Character.isLetterOrDigit(codePoint)) {
                if (pendingSpace && folded.length() > 0) {
                    folded.append(' ');
                }
                folded.appendCodePoint(codePoint);
                pendingSpace = false;
                if (Character.isLetter(codePoint)) {
                    baseScript = Character.UnicodeScript.of(codePoint);
                }
                continue;
            }

            if (isCombiningMark(type)) {
                if (preserveMarks(baseScript) && folded.length() > 0 && !pendingSpace) {
                    folded.appendCodePoint(codePoint);
                }
                continue;
            }

            pendingSpace = folded.length() > 0;
            baseScript = Character.UnicodeScript.UNKNOWN;
        }

        return Normalizer.normalize(folded.toString().trim(), Normalizer.Form.NFC);
    }

    private static boolean isCombiningMark(int type) {
        return type == Character.NON_SPACING_MARK
                || type == Character.COMBINING_SPACING_MARK
                || type == Character.ENCLOSING_MARK;
    }

    private static boolean preserveMarks(Character.UnicodeScript script) {
        return script != Character.UnicodeScript.LATIN
                && script != Character.UnicodeScript.GREEK
                && script != Character.UnicodeScript.CYRILLIC
                && script != Character.UnicodeScript.UNKNOWN
                && script != Character.UnicodeScript.COMMON
                && script != Character.UnicodeScript.INHERITED;
    }
}
