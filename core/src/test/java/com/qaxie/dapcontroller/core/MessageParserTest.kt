package com.qaxie.dapcontroller.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageParserTest {

    // --- parseCommand: valid commands ---

    @Test
    fun `parse PLAY_PAUSE command`() {
        assertEquals(Command.PlayPause, MessageParser.parseCommand("""{"type":"PLAY_PAUSE"}"""))
    }

    @Test
    fun `parse NEXT command`() {
        assertEquals(Command.Next, MessageParser.parseCommand("""{"type":"NEXT"}"""))
    }

    @Test
    fun `parse PREVIOUS command`() {
        assertEquals(Command.Previous, MessageParser.parseCommand("""{"type":"PREVIOUS"}"""))
    }

    @Test
    fun `parse VOLUME_UP command`() {
        assertEquals(Command.VolumeUp, MessageParser.parseCommand("""{"type":"VOLUME_UP"}"""))
    }

    @Test
    fun `parse VOLUME_DOWN command`() {
        assertEquals(Command.VolumeDown, MessageParser.parseCommand("""{"type":"VOLUME_DOWN"}"""))
    }

    // --- parseCommand: invalid / unknown input ---

    @Test
    fun `parse unknown type returns null`() {
        assertNull(MessageParser.parseCommand("""{"type":"UNKNOWN"}"""))
    }

    @Test
    fun `parse update type as command returns null`() {
        assertNull(MessageParser.parseCommand("""{"type":"TRACK_INFO"}"""))
    }

    @Test
    fun `parse invalid JSON command returns null`() {
        assertNull(MessageParser.parseCommand("not json"))
    }

    @Test
    fun `parse empty string command returns null`() {
        assertNull(MessageParser.parseCommand(""))
    }

    @Test
    fun `parse blank string command returns null`() {
        assertNull(MessageParser.parseCommand("   "))
    }

    // --- parseUpdate: TRACK_INFO ---

    @Test
    fun `parse TRACK_INFO update with null art`() {
        val line = """{"type":"TRACK_INFO","title":"A","artist":"B","album":"C","durationMs":1000,"albumArtBase64":null}"""
        val result = MessageParser.parseUpdate(line)
        assertNotNull(result)
        assertTrue(result is Update.Track)
        val track = (result as Update.Track).info
        assertEquals("A", track.title)
        assertEquals("B", track.artist)
        assertEquals("C", track.album)
        assertEquals(1000L, track.durationMs)
        assertNull(track.albumArtBase64)
    }

    @Test
    fun `parse TRACK_INFO update with art`() {
        val line = """{"type":"TRACK_INFO","title":"Retrograde","artist":"James Blake","album":"Overgrown","durationMs":268000,"albumArtBase64":"abc123"}"""
        val result = MessageParser.parseUpdate(line)
        assertNotNull(result)
        val track = (result as Update.Track).info
        assertEquals("Retrograde", track.title)
        assertEquals("abc123", track.albumArtBase64)
    }

    @Test
    fun `parse TRACK_INFO with missing title returns null`() {
        val line = """{"type":"TRACK_INFO","artist":"B","album":"C","durationMs":1000,"albumArtBase64":null}"""
        assertNull(MessageParser.parseUpdate(line))
    }

    @Test
    fun `parse TRACK_INFO with missing durationMs returns null`() {
        val line = """{"type":"TRACK_INFO","title":"A","artist":"B","album":"C","albumArtBase64":null}"""
        assertNull(MessageParser.parseUpdate(line))
    }

    @Test
    fun `parse TRACK_INFO with no fields returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"TRACK_INFO"}"""))
    }

    // --- parseUpdate: PLAYBACK_STATE ---

    @Test
    fun `parse PLAYBACK_STATE update playing`() {
        val line = """{"type":"PLAYBACK_STATE","isPlaying":true,"positionMs":42300}"""
        val result = MessageParser.parseUpdate(line)
        assertNotNull(result)
        assertTrue(result is Update.State)
        val state = (result as Update.State).state
        assertTrue(state.isPlaying)
        assertEquals(42300L, state.positionMs)
    }

    @Test
    fun `parse PLAYBACK_STATE update paused at zero`() {
        val line = """{"type":"PLAYBACK_STATE","isPlaying":false,"positionMs":0}"""
        val result = MessageParser.parseUpdate(line)
        assertNotNull(result)
        val state = (result as Update.State).state
        assertFalse(state.isPlaying)
        assertEquals(0L, state.positionMs)
    }

    @Test
    fun `parse PLAYBACK_STATE with missing isPlaying returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"PLAYBACK_STATE","positionMs":0}"""))
    }

    @Test
    fun `parse PLAYBACK_STATE with missing positionMs returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"PLAYBACK_STATE","isPlaying":true}"""))
    }

    // --- parseUpdate: VOLUME ---

    @Test
    fun `parse VOLUME update`() {
        val line = """{"type":"VOLUME","level":8,"maxLevel":15}"""
        val result = MessageParser.parseUpdate(line)
        assertNotNull(result)
        assertTrue(result is Update.Volume)
        val volume = result as Update.Volume
        assertEquals(8, volume.level)
        assertEquals(15, volume.maxLevel)
    }

    @Test
    fun `parse VOLUME with missing level returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"VOLUME","maxLevel":15}"""))
    }

    @Test
    fun `parse VOLUME with missing maxLevel returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"VOLUME","level":8}"""))
    }

    // --- parseUpdate: invalid / unknown input ---

    @Test
    fun `parse unknown update type returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"UNKNOWN"}"""))
    }

    @Test
    fun `parse command type as update returns null`() {
        assertNull(MessageParser.parseUpdate("""{"type":"PLAY_PAUSE"}"""))
    }

    @Test
    fun `parse invalid JSON update returns null`() {
        assertNull(MessageParser.parseUpdate("not json"))
    }

    @Test
    fun `parse empty string update returns null`() {
        assertNull(MessageParser.parseUpdate(""))
    }

    @Test
    fun `parse blank string update returns null`() {
        assertNull(MessageParser.parseUpdate("   "))
    }
}
