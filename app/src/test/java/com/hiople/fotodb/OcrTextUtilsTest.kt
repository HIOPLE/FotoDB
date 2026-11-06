package com.hiople.fotodb

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrTextUtilsTest {
    @Test
    fun normalizeKeepsLinesAndRemovesOnlyMeaninglessWhitespace() {
        assertEquals("标题\nEnglish text\n第二行", OcrTextUtils.normalize("  标题  \r\n\n English   text \n 第二行 "))
    }

    @Test
    fun searchNormalizationUnifiesSimplifiedAndTraditionalAndPreservesManyToOneMappings() {
        assertEquals("头发和后面", ImageDatabase.normalizeForSearch("頭髮和後面"))
        assertEquals(ImageDatabase.normalizeForSearch("頭髮"), ImageDatabase.normalizeForSearch("头发"))
        assertEquals(ImageDatabase.normalizeForSearch("後"), ImageDatabase.normalizeForSearch("后"))
        assertEquals("mixed english", ImageDatabase.normalizeForSearch("MIXED English"))
    }

    @Test
    fun searchNormalizationRetainsLiteralLikeMetacharacters() {
        assertEquals("a%_\\\\b", ImageDatabase.normalizeForSearch("A%_\\\\B"))
    }

    @Test
    fun appendUsesVisibleSeparatorAndDoesNotAddEmptySections() {
        assertEquals("原有描述\n\n识别 文字", OcrTextUtils.append("原有描述", "  识别  文字 "))
        assertEquals("识别文字", OcrTextUtils.append("", "识别文字"))
        assertEquals("原有描述", OcrTextUtils.append("原有描述", "\n\t"))
    }
}
