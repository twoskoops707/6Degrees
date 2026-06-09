package com.twoskoops707.sixdegrees.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubjectIntakeParserTest {

    @Test
    fun parseFreeformText_extractsPhoneFromDump() {
        val dump = "John Smith, Austin TX, works at Dell, phone 512-555-0100"
        val parsed = SubjectIntakeParser.parseFreeformText(dump)
        assertEquals("5125550100", parsed.phone)
        assertTrue(parsed.name.contains("John"))
    }

    @Test
    fun mergeWithForm_keepsParsedPhoneWhenFormOmitsPhone() {
        val parsed = SubjectIntakeParser.parseFreeformText(
            "John Smith, Austin TX, works at Dell, phone 512-555-0100"
        )
        val merged = SubjectIntakeParser.mergeWithForm(parsed, mapOf("email" to "john@example.com"))
        assertEquals("5125550100", merged.phone)
        assertEquals("john@example.com", merged.email)
    }

    @Test
    fun looksLikeFreeform_detectsMultiFieldDump() {
        val dump = "John Smith, Austin TX, works at Dell, phone 512-555-0100"
        assertTrue(SubjectIntakeParser.looksLikeFreeform(dump))
    }
}
