package com.factstore

import com.factstore.application.SlackCommand
import com.factstore.application.SlackCommandParser
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.UUID

class SlackCommandParserTest {

    private lateinit var parser: SlackCommandParser

    @BeforeEach
    fun setUp() {
        parser = SlackCommandParser()
    }

    @Test
    fun `empty text returns Help command`() {
        assertEquals(SlackCommand.Help, parser.parse(""))
    }

    @Test
    fun `blank text returns Help command`() {
        assertEquals(SlackCommand.Help, parser.parse("   "))
    }

    @Test
    fun `help sub-command returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("help"))
    }

    @Test
    fun `help sub-command is case-insensitive`() {
        assertEquals(SlackCommand.Help, parser.parse("HELP"))
    }

    @Test
    fun `search with sha prefix returns Search command`() {
        val cmd = parser.parse("search abc123") as SlackCommand.Search
        assertEquals("abc123", cmd.shaPrefix)
    }

    @Test
    fun `search without argument returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("search"))
    }

    @Test
    fun `env with name returns Env command`() {
        val cmd = parser.parse("env production") as SlackCommand.Env
        assertEquals("production", cmd.name)
    }

    @Test
    fun `env without argument returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("env"))
    }

    @Test
    fun `trail with valid UUID returns TrailDetails command`() {
        val id = UUID.randomUUID()
        val cmd = parser.parse("trail $id") as SlackCommand.TrailDetails
        assertEquals(id, cmd.trailId)
    }

    @Test
    fun `trail with invalid UUID returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("trail not-a-uuid"))
    }

    @Test
    fun `trail without argument returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("trail"))
    }

    @Test
    fun `approve with approval id returns Approve command without comment`() {
        val cmd = parser.parse("approve APR-42") as SlackCommand.Approve
        assertEquals("APR-42", cmd.approvalId)
        assertNull(cmd.comment)
    }

    @Test
    fun `approve with approval id and comment returns Approve with comment`() {
        val cmd = parser.parse("approve APR-42 looks good to me") as SlackCommand.Approve
        assertEquals("APR-42", cmd.approvalId)
        assertEquals("looks good to me", cmd.comment)
    }

    @Test
    fun `approve without argument returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("approve"))
    }

    @Test
    fun `reject with approval id returns Reject command without comment`() {
        val cmd = parser.parse("reject APR-99") as SlackCommand.Reject
        assertEquals("APR-99", cmd.approvalId)
        assertNull(cmd.comment)
    }

    @Test
    fun `reject with approval id and comment returns Reject with comment`() {
        val cmd = parser.parse("reject APR-99 security issues found") as SlackCommand.Reject
        assertEquals("APR-99", cmd.approvalId)
        assertEquals("security issues found", cmd.comment)
    }

    @Test
    fun `reject without argument returns Help`() {
        assertEquals(SlackCommand.Help, parser.parse("reject"))
    }

    @Test
    fun `unrecognised sub-command returns Unknown`() {
        val cmd = parser.parse("deploy production") as SlackCommand.Unknown
        assertTrue(cmd.input.contains("deploy"))
    }

    @Test
    fun `leading and trailing whitespace is ignored`() {
        val cmd = parser.parse("  help  ") as SlackCommand.Help
        assertNotNull(cmd)
    }
}
