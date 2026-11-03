package com.israrxy.raazi.data.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BengaliRomanizerTest {

    @Test
    fun verifyContainsBengaliDetection() {
        assertTrue(BengaliRomanizer.containsBengali("আমার সোনার বাংলা"))
        assertTrue(BengaliRomanizer.containsBengali("তুমি"))
        assertFalse(BengaliRomanizer.containsBengali("Hello world"))
        assertFalse(BengaliRomanizer.containsBengali("मिलकर खुशी हुई!"))
        assertFalse(BengaliRomanizer.containsBengali("12345!@#"))
    }

    @Test
    fun verifyBengaliLyricTransliteration() {
        val line1 = "আমার সোনার বাংলা আমি তোমায় ভালোবাসি"
        val roman1 = BengaliRomanizer.romanize(line1)
        assertEquals("amar sonar bangla ami tomay bhalobashi", roman1)

        val line2 = "যদি তোর ডাক শুনে কেউ না আসে তবে একলা চলো রে"
        val roman2 = BengaliRomanizer.romanize(line2)
        assertEquals("jodi tor dak shune keu na ase tobe ekla cholo re", roman2)

        val line3 = "তুমি রবে নীরবে হৃদয়ে মম"
        val roman3 = BengaliRomanizer.romanize(line3)
        assertEquals("tumi robe nirobe hridoye momo", roman3)

        val line4 = "চোখ মেলে দেখো না"
        val roman4 = BengaliRomanizer.romanize(line4)
        assertEquals("chokh mele dekho na", roman4)
    }

    @Test
    fun verifyBengaliPhoneticWordRules() {
        assertEquals("korbo", BengaliRomanizer.romanizeBengaliWord("করব"))
        assertEquals("bolbo", BengaliRomanizer.romanizeBengaliWord("বলব"))
        assertEquals("shopno", BengaliRomanizer.romanizeBengaliWord("স্বপ্ন"))
        assertEquals("snan", BengaliRomanizer.romanizeBengaliWord("স্নান"))
        assertEquals("bangla", BengaliRomanizer.romanizeBengaliWord("বাংলা"))
    }

    @Test
    fun verifyRefineNeuralBengaliLine() {
        val refined = BengaliRomanizer.refineNeuralBengaliLine(
            sourceLine = "আমার সোনার বাংলা",
            neuralRomanLine = "aamar sōnār bāṁlā"
        )
        assertEquals("amar sonar bangla", refined)
    }

    @Test
    fun verifyLyricsTextVariantsWithBengaliAndHindi() {
        // Bengali
        val bengaliVariants = LyricsTextVariants.availableVariants("আমি তোমাকে ভালোবাসি")
        assertTrue(bengaliVariants.contains(LyricsViewVariant.ROMANIZED))
        assertEquals(
            "ami tomake bhalobashi",
            LyricsTextVariants.transform("আমি তোমাকে ভালোবাসি", LyricsViewVariant.ROMANIZED)
        )

        // Hindi remains intact (no global regression)
        val hindiVariants = LyricsTextVariants.availableVariants("मिलकर खुशी हुई")
        assertTrue(hindiVariants.contains(LyricsViewVariant.ROMANIZED))
        assertEquals(
            "milkar khushi hui",
            LyricsTextVariants.transform("मिलकर खुशी हुई", LyricsViewVariant.ROMANIZED)
        )
    }
}
