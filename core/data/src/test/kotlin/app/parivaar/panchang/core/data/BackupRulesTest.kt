package app.parivaar.panchang.core.data

import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackupRulesTest {
    private val root = File(System.getProperty("pp.root.dir"))
    private val declaration = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"

    private fun tree(e: Element): String {
        val attrs =
            (0 until e.attributes.length).map { e.attributes.item(it) }
                .map { "${it.nodeName}=${it.nodeValue}" }.sorted()
        val kids = (0 until e.childNodes.length).map { e.childNodes.item(it) }.filterIsInstance<Element>().map(::tree)
        return "${e.tagName}$attrs[${kids.joinToString(",")}]"
    }

    private fun parse(f: File): Element =
        checkNotNull(DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(f).documentElement)

    private val include = "include[domain=file, path=backup/snapshot.bin]"

    @Test
    fun dataExtractionRulesAreExact() {
        val f = File(root, "core/data/src/main/res/xml/pp_data_extraction_rules.xml")
        assertTrue(f.readText().startsWith(declaration))
        val expected =
            "data-extraction-rules[][cloud-backup[disableIfNoEncryptionCapabilities=true][$include[]]," +
                "device-transfer[][$include[]]]"
        assertEquals(expected, tree(parse(f)))
    }

    @Test
    fun fullBackupContentIsExact() {
        val f = File(root, "core/data/src/main/res/xml/pp_backup_rules.xml")
        assertTrue(f.readText().startsWith(declaration))
        val expected =
            "full-backup-content[][include[domain=file, path=backup/snapshot.bin, " +
                "requireFlags=clientSideEncryption][]]"
        assertEquals(expected, tree(parse(f)))
        assertFalse(f.readText().contains("exclude"))
    }

    @Test
    fun manifestEnablesBackupWithBothRulesAndAddsNoPermission() {
        val manifest = parse(File(root, "app/src/main/AndroidManifest.xml"))
        val app = manifest.getElementsByTagName("application").item(0) as Element
        assertEquals("true", app.getAttribute("android:allowBackup"))
        assertEquals("@xml/pp_data_extraction_rules", app.getAttribute("android:dataExtractionRules"))
        assertEquals("@xml/pp_backup_rules", app.getAttribute("android:fullBackupContent"))
        assertEquals(0, manifest.getElementsByTagName("uses-permission").length)
        assertEquals(0, manifest.getElementsByTagName("uses-permission-sdk-23").length)
    }
}
