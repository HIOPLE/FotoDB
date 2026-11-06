package com.hiople.fotodb

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ImageDatabaseTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val filename = "fotodb-search-test.sqlite"
    private lateinit var database: ImageDatabase

    @Before fun setUp() {
        context.deleteDatabase(filename)
        database = ImageDatabase(context, filename)
        database.writableDatabase
    }

    @After fun tearDown() {
        database.close()
        context.deleteDatabase(filename)
    }

    @Test fun searchMatchesBothScriptsAndUpdatesSearchableText() {
        database.replaceScanned("root", listOf(item("a", "臺灣圖書ABC.jpg"), item("b", "头发.jpg")))
        assertTrue(database.updateText("a", "後面的說明", "重要標籤"))
        assertIds("台湾图书abc", "a")
        assertIds("頭髮", "b")
        assertIds("后面的说明", "a")
        assertIds("重要标签", "a")
        database.replaceScanned("root", listOf(item("a", "新的發展.jpg"), item("b", "头发.jpg")))
        assertIds("新的发展", "a")
        assertIds("台湾", *emptyArray())
        assertIds("重要标签", "a") // Scanning must preserve edited metadata.
        val original = database.search(listOf("root"), "发展", false).single()
        assertEquals("新的發展.jpg", original.name)
        assertEquals("後面的說明", original.description)
        assertEquals("重要標籤", original.tags)
        assertTrue(database.updateText("a", "新的描述", "新标签"))
        assertIds("重要标签", *emptyArray())
        assertIds("新標籤", "a")
        assertTrue(database.search(listOf("elsewhere"), "", false).isEmpty())
        assertTrue(database.search(emptyList(), "", false).isEmpty())
        database.replaceScanned("root", listOf(item("b", "头发.jpg")))
        assertIds("新的", *emptyArray())
    }

    @Test fun manyToOneAndMixedScriptsMatchInEveryField() {
        database.replaceScanned("root", listOf(item("a", "發髮後后.jpg"), item("b", "发发后后.jpg")))
        for (query in listOf("发发后后", "發髮後后", "发髮后後")) assertIds(query, "a", "b")
        database.updateText("a", "头髮ABC後面", "發展与圖書")
        database.updateText("b", "頭发abc后面", "发展与图书")
        assertIds("頭髮abc后面", "a", "b")
        assertIds("發展与图書", "a", "b")
    }

    @Test fun literalWildcardsBackslashesAndRegexUseTheirRespectiveSemantics() {
        database.replaceScanned("root", listOf(item("special", "A%_\\b.jpg"), item("other", "AXYb.jpg"), item("traditional", "頭髮.jpg")))
        assertIds("%", "special")
        assertIds("_", "special")
        assertIds("\\", "special")
        assertIds("%_\\", "special")
        assertIds("A%_\\B", "special")
        assertIds("頭髮", "traditional")
        assertTrue(database.search(listOf("root"), "头发", true).isEmpty())
        assertEquals(listOf("traditional"), database.search(listOf("root"), "^頭髮[.]jpg$", true).map { it.uri })
        assertEquals(3, database.search(listOf("root"), "", true).size)
        assertEquals(2, database.search(listOf("root"), "^a", true).size)
        assertThrows(java.util.regex.PatternSyntaxException::class.java) {
            database.search(listOf("root"), "[", true)
        }
    }

    @Test fun versionOneRowsAreBackfilledWithoutChangingOriginalsAndSurviveReopen() {
        database.close()
        context.deleteDatabase(filename)
        context.openOrCreateDatabase(filename, 0, null).use { old ->
            old.execSQL("CREATE TABLE images (uri TEXT PRIMARY KEY, root TEXT NOT NULL, name TEXT NOT NULL, mime TEXT NOT NULL, description TEXT NOT NULL DEFAULT '', tags TEXT NOT NULL DEFAULT '')")
            old.execSQL("CREATE INDEX images_root_idx ON images(root)")
            old.execSQL("INSERT INTO images VALUES('legacy','root','後髮.jpg','image/jpeg','繁體說明','標籤')")
            old.execSQL("INSERT INTO images VALUES('empty','root','empty.jpg','image/jpeg','','')")
            old.version = 1
        }
        database = ImageDatabase(context, filename)
        assertIds("后发", "legacy")
        assertIds("繁体说明", "legacy")
        assertIds("标签", "legacy")
        val item = database.search(listOf("root"), "后发", false).single()
        assertEquals("後髮.jpg", item.name)
        assertEquals("繁體說明", item.description)
        assertEquals("標籤", item.tags)
        assertEquals(2, database.writableDatabase.version)
        database.close()
        database = ImageDatabase(context, filename)
        assertIds("后发", "legacy")
        assertEquals(2, database.search(listOf("root"), " ", false).size)
    }

    private fun assertIds(query: String, vararg expected: String) {
        assertEquals(query, expected.toSet(), database.search(listOf("root"), query, false).map { it.uri }.toSet())
    }

    private fun item(uri: String, name: String) = ImageDatabase.ImageItem(uri, "root", name, "image/jpeg", "", "")
}
