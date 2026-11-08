package com.factstore

import org.assertj.core.api.Assertions.assertThatCode
import org.junit.jupiter.api.Test
import org.springframework.boot.env.YamlPropertySourceLoader
import org.springframework.core.io.FileSystemResource
import java.io.File

/**
 * Every shipped profile file must load the way Spring Boot loads it.
 *
 * The test suite replaces `application.yml` on the classpath, so a broken profile file (for
 * example a key such as `security:` declared twice, which Spring refuses) would otherwise only
 * be found when someone starts the application with that profile.
 */
class ProfileConfigurationTest {

    @Test
    fun `every application yml in main resources parses without duplicate keys`() {
        val files = File("src/main/resources").listFiles { f -> f.name.matches(Regex("application(-.+)?\\.ya?ml")) }!!
        check(files.isNotEmpty())
        files.forEach { file ->
            assertThatCode { YamlPropertySourceLoader().load(file.name, FileSystemResource(file)) }
                .describedAs(file.name)
                .doesNotThrowAnyException()
        }
    }
}
