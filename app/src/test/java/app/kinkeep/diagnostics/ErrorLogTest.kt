package app.kinkeep.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ErrorLogTest {
    private fun tempFile(): File = Files.createTempFile("kinkeep", ".log").toFile().also { it.delete() }

    @Test fun entriesNeverContainTheErrorMessage() {
        val entry = ErrorLog.format(0L, "sync", IllegalStateException("Ada Obi 0803 123 4567 secret note"), "v1")
        assertFalse(entry.contains("Ada"))
        assertFalse(entry.contains("0803"))
        assertFalse(entry.contains("secret"))
        assertTrue(entry.contains("java.lang.IllegalStateException"))
        assertTrue(entry.contains("ErrorLogTest")) // the stack still shows where it happened
    }

    @Test fun causesAreListedByTypeOnly() {
        val entry = ErrorLog.format(0L, "x", RuntimeException("outer", IllegalArgumentException("private detail")), "v1")
        assertTrue(entry.contains("Caused by: java.lang.IllegalArgumentException"))
        assertFalse(entry.contains("private detail"))
    }

    @Test fun appendReadAndClear() {
        val log = ErrorLog(tempFile())
        assertEquals("", log.read())
        log.record("a", RuntimeException("x"), "v1")
        log.record("b", RuntimeException("y"), "v1")
        assertTrue(log.read().contains("[a]") && log.read().contains("[b]"))
        log.clear()
        assertEquals("", log.read())
    }

    @Test fun logIsCappedKeepingTheNewestEntries() {
        val log = ErrorLog(tempFile(), maxChars = 200)
        repeat(20) { log.append("entry-$it-" + "x".repeat(30)) }
        val text = log.read()
        assertTrue(text.length <= 200)
        assertTrue(text.contains("entry-19"))
        assertFalse(text.contains("entry-0-"))
    }

    @Test fun loggingNeverThrowsEvenWhenTheFileCannotBeWritten() {
        val log = ErrorLog(File("/this/folder/does/not/exist/error.log"))
        log.record("x", RuntimeException("boom"))
        assertEquals("", log.read())
    }
}
