package com.factstore.adapter.outbound.retention

import com.factstore.core.port.outbound.IRetentionArchive
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

/**
 * Writes archived records under `factstore.retention.archive-dir` (#251), one JSON file per record.
 * Point it at a volume backed by object storage (an S3 or GCS mount) to keep archives off the
 * database host. Each file is written to a temporary name, synced, then moved into place, so a
 * record is only removed from the database once its archive copy is complete.
 */
@Component
class FileSystemRetentionArchive(
    @Value("\${factstore.retention.archive-dir:./data/retention-archive}") archiveDir: String
) : IRetentionArchive {

    private val root: Path = Paths.get(archiveDir).toAbsolutePath().normalize()

    override fun store(key: String, content: String): String {
        val target = root.resolve(key).normalize()
        require(target.startsWith(root)) { "Archive key escapes the archive directory: $key" }
        Files.createDirectories(target.parent)
        val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
        Files.newOutputStream(temp, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.SYNC).use {
            it.write(content.toByteArray(Charsets.UTF_8))
        }
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        return target.toString()
    }
}
