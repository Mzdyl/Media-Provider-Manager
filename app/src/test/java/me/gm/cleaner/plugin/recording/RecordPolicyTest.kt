package me.gm.cleaner.plugin.recording

import me.gm.cleaner.plugin.dao.MediaProviderRecord
import org.junit.Assert.*
import org.junit.Test

class RecordPolicyTest {
    private fun event(now: Long = 1_000_000, data: List<String> = listOf("/one.png")) = MediaProviderRecord(
        0, now, "test.app", 1, 0, data, data.map { "image/png" }, data.map { true })

    @Test fun hugeDetailsAndEscapedCharactersStayWithinSerializedBudget() {
        val source = event(data = List(1000) { "\u0001\"\\😀".repeat(20_000) })
        val result = RecordPolicy.bound(source)!!
        assertTrue(result.detailsTruncated)
        assertTrue(result.data.size <= RecordPolicy.MAX_DETAILS)
        assertTrue(RecordPolicy.detailBytes(result) <= RecordPolicy.MAX_DETAIL_BYTES)
        assertEquals(result.data.size, result.intercepted.size)
        assertTrue(result.intercepted.all { it })
        assertEquals(1000, source.data.size)
    }

    @Test fun clippingDoesNotSplitUnicodeOrWalkTheWholeHugeString() {
        assertEquals("汉😀", RecordPolicy.clip("汉😀字", 7))
        assertEquals("汉", RecordPolicy.clip("汉😀字", 6))
        assertEquals("abc", RecordPolicy.clip("abcdef", 3))
    }

    @Test fun malformedAlignedDataAndOversizedPackageAreRejected() {
        assertNull(RecordPolicy.bound(event().copy(intercepted = emptyList())))
        assertNull(RecordPolicy.bound(event().copy(packageName = "x".repeat(10_000))))
    }

    @Test fun auxiliaryLimitPreservesOffsetAndNeverExpandsCallerLimit() {
        assertEquals("17", RecordPolicy.capSqlLimit(null, 17))
        assertEquals("5 OFFSET 12", RecordPolicy.capSqlLimit("5 OFFSET 12", 17))
        assertEquals("17 OFFSET 12", RecordPolicy.capSqlLimit("12,1000", 17))
        assertEquals("0", RecordPolicy.capSqlLimit("0", 17))
        assertEquals("17", RecordPolicy.capSqlLimit("-1", 17))
        assertNull(RecordPolicy.capSqlLimit("10; DELETE FROM x", 17))
        assertNull(RecordPolicy.capSqlLimit("999999999999999999999999999999", 17))
    }

    @Test fun repeatQueriesKeepCountsAndOnlyOneClearlyIdentifiedSample() {
        val buffer = RecordBuffer(true, "session")
        repeat(20_000) {
            val ticket = buffer.query("test.app", 1, true, 1_000_000)!!
            assertEquals(it == 0, ticket.capture)
            val record = if (ticket.capture) event() else event().copy(data = emptyList(), mimeType = emptyList(),
                intercepted = emptyList(), sampleKind = RecordPolicy.SAMPLE_NONE)
            assertTrue(buffer.offer(record, ticket))
        }
        val row = buffer.drain().single()
        assertEquals(20_000, row.eventCount)
        assertEquals(listOf("/one.png"), row.data)
        assertEquals(listOf(true), row.intercepted)
    }

    @Test fun concurrentFirstSampleArrivingLateReplacesOnlyMissingDetails() {
        val buffer = RecordBuffer(true)
        val first = buffer.query("test.app", 1, true, 1_000_000)!!
        val second = buffer.query("test.app", 1, true, 1_000_001)!!
        buffer.offer(event(1_000_001).copy(data = emptyList(), mimeType = emptyList(), intercepted = emptyList(), sampleKind = 0), second)
        buffer.offer(event(), first)
        val row = buffer.drain().single()
        assertEquals(2, row.eventCount)
        assertEquals(1_000_000, row.sampleTimeMillis)
        assertEquals(1_000_001, row.lastTimeMillis)
        assertEquals(listOf(true), row.intercepted)
    }

    @Test fun windowAndFilterChangesCannotMergeDifferentGroups() {
        val buffer = RecordBuffer(true)
        val a = buffer.query("test.app", 1, true, 59_999)!!
        val b = buffer.query("test.app", 1, true, 60_000)!!
        val c = buffer.query("test.app", 1, false, 60_000)!!
        val d = buffer.query("other.app", 1, true, 60_000)!!
        assertEquals(4, setOf(a.key,b.key,c.key,d.key).size)
        assertTrue(listOf(a,b,c,d).all { it.capture })
    }

    @Test fun clearAndDisableInvalidateInflightSamplesAndQueuedWork() {
        val buffer = RecordBuffer(true)
        val old = buffer.query("test.app", 1, true, 1_000_000)!!
        buffer.offer(event(),old)
        buffer.reset()
        assertFalse(buffer.offer(event(), old))
        assertTrue(buffer.drain().isEmpty())
        val current = buffer.query("test.app",1,true,1_000_000)!!
        assertNotEquals(old.key,current.key)
        buffer.setEnabled(false)
        assertFalse(buffer.offer(event(),current))
        assertNull(buffer.query("test.app",1,true,1_000_000))
        buffer.setEnabled(true)
        assertTrue(buffer.query("test.app",1,true,1_000_000)!!.capture)
    }

    @Test fun pendingQueueHasAHardBoundUnderDistinctFloods() {
        val buffer=RecordBuffer(true)
        repeat(10_000) { buffer.offer(event().copy(operation=1)) }
        var rows=0
        while(buffer.hasPending()) rows+=buffer.drain().size
        assertEquals(RecordPolicy.MAX_PENDING,rows)
        assertEquals((10_000-RecordPolicy.MAX_PENDING).toLong(),buffer.dropped)
    }

    @Test fun concurrentProducersDoNotLoseAcceptedAggregateCounts() {
        val buffer=RecordBuffer(true)
        val threads=List(8) { Thread {
            repeat(500) {
                val ticket=buffer.query("test.app",1,true,1_000_000)!!
                buffer.offer(event(),ticket)
            }
        } }
        threads.forEach { it.start() }; threads.forEach { it.join() }
        assertEquals(4_000,buffer.drain().single().eventCount)
    }
}
