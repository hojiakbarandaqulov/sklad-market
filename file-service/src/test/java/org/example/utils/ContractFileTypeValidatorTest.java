package org.example.utils;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ContractFileTypeValidatorTest {
    @Test
    void acceptsPdfByContent() throws Exception {
        var pdf = new MockMultipartFile("file", "contract.pdf", "application/pdf", "%PDF-1.7\n%%EOF".getBytes());
        assertEquals("application/pdf", FileTypeValidator.validateAndGetMime(pdf));
    }

    @Test
    void acceptsWordPackage() throws Exception {
        var docx = new MockMultipartFile("file", "contract.docx", "application/octet-stream",
                zip("[Content_Types].xml", "word/document.xml"));
        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                FileTypeValidator.validateAndGetMime(docx));
    }

    @Test
    void rejectsRenamedZipAndFakePdf() throws Exception {
        var zip = new MockMultipartFile("file", "contract.docx", "application/zip", zip("unrelated.txt"));
        assertThrows(IllegalArgumentException.class, () -> FileTypeValidator.validateAndGetMime(zip));
        var fakePdf = new MockMultipartFile("file", "contract.pdf", "application/pdf", "plain text".getBytes());
        assertThrows(IllegalArgumentException.class, () -> FileTypeValidator.validateAndGetMime(fakePdf));
    }

    private byte[] zip(String... paths) throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (String path : paths) {
                zip.putNextEntry(new ZipEntry(path));
                zip.write("<document/>".getBytes());
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }
}
