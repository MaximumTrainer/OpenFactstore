package com.factstore.application.attestation

import com.factstore.core.domain.Attestation
import com.factstore.core.domain.AttestationStatus
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

@Component
class JunitAttestationProcessor : AttestationTypeProcessor {
    override val typeName = "junit"

    override fun process(evidenceContent: ByteArray, attestation: Attestation): Attestation {
        return runCatching {
            val xml = String(evidenceContent, Charsets.UTF_8)
            val factory = DocumentBuilderFactory.newInstance()
            factory.isNamespaceAware = false
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(xml)))

            val suites = doc.getElementsByTagName("testsuite")
            var tests = 0; var failures = 0; var errors = 0; var skipped = 0
            for (i in 0 until suites.length) {
                val suite = suites.item(i)
                val attrs = suite.attributes
                tests += attrs.getNamedItem("tests")?.nodeValue?.toIntOrNull() ?: 0
                failures += attrs.getNamedItem("failures")?.nodeValue?.toIntOrNull() ?: 0
                errors += attrs.getNamedItem("errors")?.nodeValue?.toIntOrNull() ?: 0
                skipped += attrs.getNamedItem("skipped")?.nodeValue?.toIntOrNull() ?: 0
            }

            attestation.status = if (failures > 0 || errors > 0) AttestationStatus.FAILED else AttestationStatus.PASSED
            attestation.details = """{"tests":$tests,"failures":$failures,"errors":$errors,"skipped":$skipped}"""
            attestation
        }.getOrElse {
            attestation.status = AttestationStatus.FAILED
            attestation.details = """{"error":"Failed to parse JUnit XML: ${it.message?.replace("\"", "'")}"}"""
            attestation
        }
    }
}
