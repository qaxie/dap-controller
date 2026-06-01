package com.qaxie.dapcontroller.core

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSerializerTest {

    // --- Commands ---

    @Test
    fun `serialize PlayPause command`() {
        val result = MessageSerializer.serialize(Command.PlayPause)
        val json = JSONObject(result.trim())
        assertEquals("PLAY_PAUSE", json.getString("type"))
    }

    @Test
    fun `serialize Next command`() {
        val result = MessageSerializer.serialize(Command.Next)
        val json = JSONObject(result.trim())
        assertEquals("NEXT", json.getString("type"))
    }

    @Test
    fun `serialize Previous command`() {
        val result = MessageSerializer.serialize(Command.Previous)
        val json = JSONObject(result.trim())
        assertEquals("PREVIOUS", json.getString("type"))
    }

    @Test
    fun `serialized command ends with newline`() {
        assertTrue(MessageSerializer.serialize(Command.PlayPause).endsWith("\n"))
        assertTrue(MessageSerializer.serialize(Command.Next).endsWith("\n"))
        assertTrue(MessageSerializer.serialize(Command.Previous).endsWith("\n"))
    }

    // --- Updates: TRACK_INFO ---

    @Test
    fun `serialize Track update with all fields`() {
        val info = TrackInfo("Retrograde", "James Blake", "Overgrown", 268000L, "abc123")
        val result = MessageSerializer.serialize(Update.Track(info))
        val json = JSONObject(result.trim())
        assertEquals("TRACK_INFO", json.getString("type"))
        assertEquals("Retrograde", json.getString("title"))
        assertEquals("James Blake", json.getString("artist"))
        assertEquals("Overgrown", json.getString("album"))
        assertEquals(268000L, json.getLong("durationMs"))
        assertEquals("abc123", json.getString("albumArtBase64"))
    }

    @Test
    fun `serialize Track update with null albumArt`() {
        val info = TrackInfo("A", "B", "C", 1000L, null)
        val result = MessageSerializer.serialize(Update.Track(info))
        val json = JSONObject(result.trim())
        assertEquals("TRACK_INFO", json.getString("type"))
        assertTrue(json.isNull("albumArtBase64"))
    }

    @Test
    fun `serialized Track update ends with newline`() {
        val info = TrackInfo("A", "B", "C", 1000L, null)
        assertTrue(MessageSerializer.serialize(Update.Track(info)).endsWith("\n"))
    }

    // --- Updates: PLAYBACK_STATE ---

    @Test
    fun `serialize State update playing`() {
        val state = PlaybackState(isPlaying = true, positionMs = 42300L)
        val result = MessageSerializer.serialize(Update.State(state))
        val json = JSONObject(result.trim())
        assertEquals("PLAYBACK_STATE", json.getString("type"))
        assertTrue(json.getBoolean("isPlaying"))
        assertEquals(42300L, json.getLong("positionMs"))
    }

    @Test
    fun `serialize State update paused at zero`() {
        val state = PlaybackState(isPlaying = false, positionMs = 0L)
        val result = MessageSerializer.serialize(Update.State(state))
        val json = JSONObject(result.trim())
        assertEquals("PLAYBACK_STATE", json.getString("type"))
        assertTrue(!json.getBoolean("isPlaying"))
        assertEquals(0L, json.getLong("positionMs"))
    }

    @Test
    fun `serialized State update ends with newline`() {
        val state = PlaybackState(isPlaying = true, positionMs = 500L)
        assertTrue(MessageSerializer.serialize(Update.State(state)).endsWith("\n"))
    }
}
