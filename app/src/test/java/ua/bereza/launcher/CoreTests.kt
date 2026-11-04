package ua.bereza.launcher

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import ua.bereza.launcher.files.DownloadException
import ua.bereza.launcher.files.Downloader
import ua.bereza.launcher.files.SafePath
import ua.bereza.launcher.files.Sha256
import ua.bereza.launcher.files.ZipExtractor
import ua.bereza.launcher.model.InstallState
import ua.bereza.launcher.model.InstalledFile
import ua.bereza.launcher.model.Manifest
import ua.bereza.launcher.model.ManifestFile
import ua.bereza.launcher.net.DropboxLinks
import ua.bereza.launcher.net.Urls
import ua.bereza.launcher.server.SampQuery
import ua.bereza.launcher.settings.Nickname
import ua.bereza.launcher.update.UpdatePlanner
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.random.Random

class SampQueryTest {
    private val cp1251 = charset("windows-1251")

    private fun response(opcode: Char, body: ByteBuffer.() -> Unit): ByteArray {
        val b = ByteBuffer.allocate(2048).order(ByteOrder.LITTLE_ENDIAN)
        b.put(SampQuery.buildPacket(byteArrayOf(188.toByte(), 127, 241.toByte(), 74), 2406, opcode))
        b.body()
        return b.array().copyOf(b.position())
    }

    private fun ByteBuffer.s32(s: String) { val x = s.toByteArray(cp1251); putInt(x.size); put(x) }
    private fun ByteBuffer.s8(s: String) { val x = s.toByteArray(cp1251); put(x.size.toByte()); put(x) }

    @Test fun packetLayout() {
        val p = SampQuery.buildPacket(byteArrayOf(188.toByte(), 127, 241.toByte(), 74), 2406, 'i')
        // "SAMP", IP, порт 2406 = 0x0966 little-endian, опкод
        assertArrayEquals(byteArrayOf(0x53, 0x41, 0x4D, 0x50, 188.toByte(), 127, 241.toByte(), 74, 0x66, 0x09, 'i'.code.toByte()), p)
    }

    @Test fun parsesInfoWithUkrainianCp1251() {
        val raw = response('i') {
            put(0); putShort(12); putShort(100)
            s32("Bereza Role Play | Україна"); s32("Bereza RP"); s32("Українська")
        }
        val info = SampQuery.parseInfo(raw)
        assertFalse(info.passworded)
        assertEquals(12, info.players); assertEquals(100, info.maxPlayers)
        assertEquals("Bereza Role Play | Україна", info.hostname)
        assertEquals("Українська", info.language)
    }

    @Test fun parsesRulesAndPlayers() {
        val rules = SampQuery.parseRules(response('r') { putShort(2); s8("version"); s8("0.3.7-R2"); s8("weather"); s8("10") })
        assertEquals("0.3.7-R2", rules["version"])
        val players = SampQuery.parsePlayers(response('c') { putShort(2); s8("Ivan_Bereza"); putInt(15); s8("Olena_Koval"); putInt(3) })
        assertEquals(listOf(SampQuery.Player("Ivan_Bereza", 15), SampQuery.Player("Olena_Koval", 3)), players)
    }

    @Test(expected = SampQuery.QueryException::class)
    fun rejectsTruncated() { SampQuery.parseInfo(response('i') { put(0); putShort(1) }) }

    @Test(expected = SampQuery.QueryException::class)
    fun rejectsWrongOpcode() { SampQuery.parseInfo(response('r') { putShort(0) }) }

    /** Живий запит до 188.127.241.74:2406 — лише в CI з LIVE_QUERY=1. */
    @Test fun liveServer() {
        assumeTrue(System.getenv("LIVE_QUERY") == "1")
        val (raw, ms) = SampQuery.request("188.127.241.74", 2406, 'i', timeoutMs = 4000, attempts = 3)
        val info = SampQuery.parseInfo(raw)
        println("LIVE: ${info.hostname} | ${info.players}/${info.maxPlayers} | ${info.gamemode} | ${info.language} | ${ms}ms")
        val rules = SampQuery.parseRules(SampQuery.request("188.127.241.74", 2406, 'r').first)
        println("LIVE rules: $rules")
        assertTrue(info.maxPlayers > 0)
    }
}

class LinksAndPathsTest {
    @Test fun dropboxShareLinkBecomesDirect() {
        assertEquals("https://www.dropbox.com/scl/fi/abc/cache.zip?rlkey=xyz&dl=1",
            DropboxLinks.toDirect("https://www.dropbox.com/scl/fi/abc/cache.zip?rlkey=xyz&dl=0"))
        assertEquals("https://www.dropbox.com/s/abc/file.zip?dl=1", DropboxLinks.toDirect("https://dropbox.com/s/abc/file.zip"))
        val direct = "https://dl.dropboxusercontent.com/s/abc/file.zip"
        assertEquals(direct, DropboxLinks.toDirect(direct))
        assertTrue(DropboxLinks.isFolderLink("https://www.dropbox.com/scl/fo/abc/xyz?rlkey=1"))
    }

    @Test fun relativeUrlsResolve() {
        assertEquals("https://cdn.example.com/b/files/a.zip", Urls.resolve("https://cdn.example.com/b/", "files/a.zip"))
        assertEquals("https://x.y/z.zip", Urls.resolve("https://cdn.example.com/b/", "https://x.y/z.zip"))
    }

    @Test fun safePathBlocksEscapes() {
        val root = Files.createTempDirectory("root").toFile()
        assertEquals(File(root, "models/a.txd").path, SafePath.resolve(root, "models\\a.txd").path)
        listOf("../x", "a/../../x", "/etc/passwd", "C:/x", "").forEach {
            try { SafePath.resolve(root, it); fail("пропущено: $it") } catch (_: SecurityException) {}
        }
    }

    @Test fun nicknameRules() {
        assertEquals(Nickname.Check.Ok, Nickname.check("Ivan_Bereza"))
        assertTrue(Nickname.check("ivan") is Nickname.Check.Warning)
        assertTrue(Nickname.check("Іван_Береза") is Nickname.Check.Error)
        assertTrue(Nickname.check("ab") is Nickname.Check.Error)
    }

    @Test fun sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256.of("abc".toByteArray()))
    }
}

class ZipAndPlannerTest {
    private fun zip(vararg entries: Pair<String, ByteArray>): File {
        val f = File.createTempFile("t", ".zip")
        ZipOutputStream(f.outputStream()).use { z -> entries.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return f
    }

    @Test fun extractsWithHashes() = runBlocking {
        val root = Files.createTempDirectory("g").toFile()
        val data = Random.nextBytes(300_000)
        val list = ZipExtractor.extract(zip("models/x.dff" to data, "readme.txt" to "hi".toByteArray()), root, "mods/bereza")
        assertEquals(setOf("mods/bereza/models/x.dff", "mods/bereza/readme.txt"), list.map { it.path }.toSet())
        assertEquals(Sha256.of(data), list.first { it.path.endsWith("x.dff") }.sha256)
        assertArrayEquals(data, File(root, "mods/bereza/models/x.dff").readBytes())
    }

    @Test fun zipSlipRejected() = runBlocking {
        val root = Files.createTempDirectory("g").toFile()
        try { ZipExtractor.extract(zip("../../evil.sh" to byteArrayOf(1)), root, ""); fail() } catch (_: SecurityException) {}
        assertFalse(File(root.parentFile, "evil.sh").exists())
    }

    private val f1 = ManifestFile("a.bin", "a.bin", 10, "a".repeat(64))
    private val f2 = ManifestFile("b.zip", "b.zip", 20, "b".repeat(64), extractTo = "b/")

    @Test fun plannerPicksOnlyInvalid() {
        val m = Manifest(version = "1", files = listOf(f1, f2), remove = listOf("old.txt", "gone.txt"))
        val state = InstallState("0", mapOf("a.bin" to InstalledFile("a".repeat(64), 10)))
        val plan = UpdatePlanner.plan(m, state, isValid = { f, s -> s.files[f.path]?.sha256 == f.sha256 }, exists = { it == "old.txt" })
        assertEquals(listOf(f2), plan.toDownload)
        assertEquals(listOf("old.txt"), plan.toRemove)
        assertEquals(20, plan.downloadBytes)
    }

    @Test fun validateCatchesErrors() {
        assertTrue(UpdatePlanner.validate(Manifest(version = "1", files = listOf(f1, f2))).isEmpty())
        val bad = Manifest(version = "1", files = listOf(f1, f1.copy(sha256 = "zz", size = 0)))
        assertEquals(3, UpdatePlanner.validate(bad).size) // дубль, sha, розмір
    }
}

class DownloaderTest {
    private lateinit var server: MockWebServer
    private val data = Random(1).nextBytes(1_000_000)
    private val client = OkHttpClient()
    private var ignoreRange = false
    private var cutAt = -1L

    @Before fun setUp() {
        server = MockWebServer()
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.path == "/html") return MockResponse().setHeader("Content-Type", "text/html").setBody("<html>")
                if (request.path == "/missing") return MockResponse().setResponseCode(404)
                val range = request.getHeader("Range")?.let { Regex("bytes=(\\d+)-").find(it)!!.groupValues[1].toInt() }
                if (range != null && !ignoreRange) {
                    return MockResponse().setResponseCode(206)
                        .setHeader("Content-Range", "bytes $range-${data.size - 1}/${data.size}")
                        .setBody(Buffer().write(data, range, data.size - range))
                }
                val resp = MockResponse().setBody(Buffer().write(data))
                if (cutAt > 0) { val c = cutAt; cutAt = -1; return MockResponse().setBody(Buffer().write(data, 0, c.toInt())) }
                return resp
            }
        }
        server.start()
    }

    @After fun tearDown() { server.shutdown() }

    private fun tmpPart() = File(Files.createTempDirectory("d").toFile(), "x.part")

    @Test fun resumesWithRange() = runBlocking {
        val part = tmpPart()
        part.writeBytes(data.copyOf(400_000))
        Downloader(client, retryDelayMs = 1).download(server.url("/f").toString(), part, data.size.toLong()) { _, _ -> }
        assertArrayEquals(data, part.readBytes())
        assertEquals("bytes=400000-", server.takeRequest().getHeader("Range"))
    }

    @Test fun restartsWhenServerIgnoresRange() = runBlocking {
        ignoreRange = true
        val part = tmpPart()
        part.writeBytes(ByteArray(1234) { 7 }) // сміття, яке має бути перезаписане
        Downloader(client, retryDelayMs = 1).download(server.url("/f").toString(), part, data.size.toLong()) { _, _ -> }
        assertArrayEquals(data, part.readBytes())
    }

    @Test fun retriesAfterBrokenConnection() = runBlocking {
        cutAt = 300_000
        val part = tmpPart()
        Downloader(client, retryDelayMs = 1).download(server.url("/f").toString(), part, data.size.toLong()) { _, _ -> }
        assertArrayEquals(data, part.readBytes())
        assertEquals(2, server.requestCount)
    }

    @Test fun htmlPageIsRejected() = runBlocking {
        try {
            Downloader(client, retryDelayMs = 1).download(server.url("/html").toString(), tmpPart(), 0) { _, _ -> }
            fail()
        } catch (e: DownloadException) { assertTrue(e.message!!.contains("вебсторінку")) }
    }

    @Test fun notFoundIsNotRetried() = runBlocking {
        try {
            Downloader(client, retryDelayMs = 1).download(server.url("/missing").toString(), tmpPart(), 10) { _, _ -> }
            fail()
        } catch (e: DownloadException) { assertEquals(1, server.requestCount) }
    }
}

@Suppress("unused")
private fun ByteArrayOutputStream.bytes() = toByteArray()
