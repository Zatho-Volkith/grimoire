package dev.grimoire

import dev.grimoire.template.AutofillRule
import dev.grimoire.template.FieldType
import dev.grimoire.template.LinkDir
import dev.grimoire.template.LinkSpec
import dev.grimoire.template.TemplateField
import dev.grimoire.template.TemplateFields
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TemplateFieldsTest {

    @Test
    fun `plain field encodes in the old shape`() {
        assertEquals("""[{"key":"age","label":"Age"}]""", TemplateFields.encode(listOf(TemplateField("age", "Age"))))
    }

    @Test
    fun `every member round-trips`() {
        val fields = listOf(
            TemplateField(
                "home", "Home", FieldType.LINK, group = "Origins",
                link = LinkSpec("hails_from", LinkDir.IN, targetType = "location", targetTemplate = "tpl-1"),
                autofill = listOf(AutofillRule("member_of", "capital"), AutofillRule("lives_in", "region")),
            ),
            TemplateField("mood", "Mood", options = listOf("calm", "angry")),
            TemplateField("bio", "Bio", FieldType.LONG),
        )
        assertEquals(fields, TemplateFields.decode(TemplateFields.encode(fields)))
    }

    @Test
    fun `old rows decode as text`() {
        assertEquals(listOf(TemplateField("a", "A")), TemplateFields.decode("""[{"key":"a","label":"A"}]"""))
    }

    @Test
    fun `unknown type and link-without-spec fall back to text`() {
        val out = TemplateFields.decode("""[{"key":"a","label":"A","type":"rainbow"},{"key":"b","label":"B","type":"link"}]""")
        assertEquals(2, out.size)
        out.forEach {
            assertEquals(FieldType.TEXT, it.type)
            assertNull(it.link)
        }
    }

    @Test
    fun `garbage decodes to empty`() {
        assertEquals(emptyList(), TemplateFields.decode("{not json"))
        assertEquals(emptyList(), TemplateFields.decode("""{"key":"a"}"""))
    }
}
