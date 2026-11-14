package com.factstore.retention

import com.factstore.adapter.outbound.retention.FileSystemRetentionArchive
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

/** #251: archived records land as complete files under the archive directory, and nowhere else. */
class FileSystemRetentionArchiveTest {

    @TempDir lateinit var dir: Path

    @Test
    fun `a record is written under its key and nothing temporary is left behind`() {
        val archive = FileSystemRetentionArchive(dir.toString())

        val location = archive.store("environment-snapshots/default/env-1/1-abc.json", """{"id":"abc"}""")

        val file = dir.resolve("environment-snapshots/default/env-1/1-abc.json")
        assertThat(location).isEqualTo(file.toAbsolutePath().normalize().toString())
        assertThat(Files.readString(file)).isEqualTo("""{"id":"abc"}""")
        assertThat(Files.list(file.parent).map { it.fileName.toString() }.toList()).containsExactly("1-abc.json")
    }

    @Test
    fun `a key cannot escape the archive directory`() {
        val archive = FileSystemRetentionArchive(dir.resolve("archive").toString())

        assertThrows<IllegalArgumentException> { archive.store("../outside.json", "{}") }
        assertThat(Files.exists(dir.resolve("outside.json"))).isFalse()
    }
}
