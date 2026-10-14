package com.municipalidadsanjose.archivo.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class FileSystemStorageServiceTest {
    @TempDir Path directory;
    private final byte[] pdf = "%PDF-complete".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    private FileSystemStorageService storage() {
        return new FileSystemStorageService(directory.toString(), 20 * 1024 * 1024, "application/pdf");
    }
    private String hash(byte[] data) throws Exception { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)); }
    private long files() throws Exception {
        try (var entries = Files.walk(directory)) { return entries.filter(Files::isRegularFile).count(); }
    }

    @Test
    void publishesVerifiedStreamAtomically() throws Exception {
        var storage = storage();
        String path = storage.guardarPdfStream("exp", new ByteArrayInputStream(pdf), pdf.length, hash(pdf), 100);
        assertArrayEquals(pdf, storage.cargarComoRecurso(path).getContentAsByteArray());
        assertEquals(1, files());
        try (var entries = Files.walk(directory)) { assertFalse(entries.anyMatch(p -> p.toString().endsWith(".part"))); }
    }

    @Test
    void rejectsTruncatedAndOverlongBodiesWithoutPartialFiles() throws Exception {
        for (long declared : new long[]{pdf.length - 1, pdf.length + 1}) {
            assertThrows(IllegalStateException.class, () -> storage().guardarPdfStream("exp", new ByteArrayInputStream(pdf), declared, hash(pdf), 100));
            assertEquals(0, files());
        }
    }

    @Test
    void rejectsSignatureHashAndAdmissionLimits() throws Exception {
        assertThrows(IllegalStateException.class, () -> storage().guardarPdfStream("exp", new ByteArrayInputStream("not PDF".getBytes()), 7, hash(pdf), 100));
        assertThrows(IllegalStateException.class, () -> storage().guardarPdfStream("exp", new ByteArrayInputStream(pdf), pdf.length, "0".repeat(64), 100));
        assertThrows(IllegalStateException.class, () -> storage().guardarPdfStream("exp", new ByteArrayInputStream(pdf), pdf.length, hash(pdf), 5));
        assertEquals(0, files());
    }

    @Test
    void failedStreamPreservesOldPublishedPdf() throws Exception {
        var storage = storage();
        String old = storage.guardarPdf("exp", pdf);
        InputStream broken = new InputStream() {
            int count;
            @Override public int read() throws IOException {
                if (count == 6) throw new IOException("synthetic stream failure");
                return pdf[count++];
            }
        };
        assertThrows(IllegalStateException.class, () -> storage.guardarPdfStream("exp", broken, pdf.length, hash(pdf), 100));
        assertArrayEquals(pdf, storage.cargarComoRecurso(old).getContentAsByteArray());
        assertEquals(1, files());
    }

    @Test
    void rejectsFolderTraversal() throws Exception {
        assertThrows(IllegalStateException.class, () -> storage().guardarPdfStream("../outside", new ByteArrayInputStream(pdf), pdf.length, hash(pdf), 100));
        assertEquals(0, files());
    }
}
