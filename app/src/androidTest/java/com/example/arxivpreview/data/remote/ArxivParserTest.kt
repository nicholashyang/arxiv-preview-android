package com.example.arxivpreview.data.remote

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ArxivParserTest {
    @Test
    fun parsesAtomMetadataAndNormalizesVersion() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/"
              xmlns:arxiv="http://arxiv.org/schemas/atom">
              <opensearch:totalResults>1</opensearch:totalResults>
              <opensearch:startIndex>0</opensearch:startIndex>
              <opensearch:itemsPerPage>1</opensearch:itemsPerPage>
              <entry>
                <id>https://arxiv.org/abs/2607.12345v2</id>
                <updated>2026-07-30T12:00:00Z</updated>
                <published>2026-07-29T12:00:00Z</published>
                <title> A multiline
                  title </title>
                <summary> The abstract. </summary>
                <author><name>Alice Example</name></author>
                <author><name>Bob Example</name></author>
                <arxiv:primary_category term="cs.AI"/>
                <category term="cs.AI"/>
                <link href="https://arxiv.org/abs/2607.12345v2" rel="alternate" type="text/html"/>
                <link title="pdf" href="https://arxiv.org/pdf/2607.12345v2" rel="related" type="application/pdf"/>
              </entry>
            </feed>
        """.trimIndent()

        val page = ArxivParser().parse(ByteArrayInputStream(xml.toByteArray()))

        assertEquals(1, page.totalResults)
        assertEquals("2607.12345", page.papers.single().id)
        assertEquals("2607.12345v2", page.papers.single().versionedId)
        assertEquals("A multiline title", page.papers.single().title)
        assertEquals(listOf("Alice Example", "Bob Example"), page.papers.single().authors)
        assertEquals("cs.AI", page.papers.single().primaryCategory)
        assertNull(page.papers.single().doi)
    }

    @Test
    fun parsesEmptyFeed() {
        val xml = """
            <feed xmlns="http://www.w3.org/2005/Atom"
              xmlns:opensearch="http://a9.com/-/spec/opensearch/1.1/">
              <opensearch:totalResults>0</opensearch:totalResults>
            </feed>
        """.trimIndent()
        val page = ArxivParser().parse(ByteArrayInputStream(xml.toByteArray()))
        assertEquals(0, page.totalResults)
        assertEquals(emptyList<Any>(), page.papers)
    }
}
