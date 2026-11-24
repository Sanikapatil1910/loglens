package com.loglens.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.loglens.config.LogLensProperties;
import com.loglens.exception.FileTooLargeException;
import com.loglens.exception.InvalidLogFileException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class FileValidationServiceTest {

    private final FileValidationService service =
            new FileValidationService(new LogLensProperties(1024, List.of("log", "txt"), List.of()));

    @Test
    void acceptsLogAndTxtFilesCaseInsensitively() {
        service.validateUpload("app.log", 10);
        service.validateUpload("notes.txt", 10);
        service.validateUpload("APP.LOG", 10);
    }

    @Test
    void rejectsUnsupportedExtensionsAndMissingNames() {
        InvalidLogFileException e = assertThrows(InvalidLogFileException.class,
                () -> service.validateUpload("malware.exe", 10));
        assertTrue(e.getMessage().contains("Unsupported file type"));
        assertThrows(InvalidLogFileException.class, () -> service.validateUpload("noextension", 10));
        assertThrows(InvalidLogFileException.class, () -> service.validateUpload("trailingdot.", 10));
        assertThrows(InvalidLogFileException.class, () -> service.validateUpload(null, 10));
        assertThrows(InvalidLogFileException.class, () -> service.validateUpload("   ", 10));
    }

    @Test
    void rejectsEmptyAndOversizedUploads() {
        InvalidLogFileException empty = assertThrows(InvalidLogFileException.class,
                () -> service.validateUpload("app.log", 0));
        assertTrue(empty.getMessage().contains("empty"));
        assertThrows(FileTooLargeException.class, () -> service.validateUpload("app.log", 1025));
        service.validateUpload("app.log", 1024);
    }

    @Test
    void sanitisesFilenamesAndStripsPathsForDisplay() {
        assertEquals("passwd.log", service.sanitizeFilename("../../etc/passwd.log"));
        assertEquals("app.log", service.sanitizeFilename("C:\\Users\\bob\\app.log"));
        assertEquals("we_ird__name.log", service.sanitizeFilename("we<ird>|name.log"));
        assertEquals("unnamed", service.sanitizeFilename(null));
        assertEquals("unnamed", service.sanitizeFilename("  "));
        assertEquals(FileValidationService.MAX_DISPLAY_NAME, service.sanitizeFilename("a".repeat(300) + ".log").length());
    }

    @Test
    void decodesValidUtf8() {
        assertEquals("ERROR caf\u00e9", service.decode("ERROR caf\u00e9".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsEmptyWhitespaceOnlyAndBinaryContent() {
        assertThrows(InvalidLogFileException.class, () -> service.decode(new byte[0]));
        assertThrows(InvalidLogFileException.class, () -> service.decode(null));
        assertThrows(InvalidLogFileException.class, () -> service.decode("  \n\t ".getBytes(StandardCharsets.UTF_8)));
        InvalidLogFileException binary = assertThrows(InvalidLogFileException.class,
                () -> service.decode(new byte[] {'E', 'R', 0, 'O', 'R'}));
        assertTrue(binary.getMessage().contains("binary"));
    }

    @Test
    void rejectsOversizedContentAndToleratesInvalidUtf8() {
        assertThrows(FileTooLargeException.class, () -> service.decode(new byte[1025]));
        String decoded = service.decode(new byte[] {'E', 'R', 'R', 'O', 'R', ' ', (byte) 0xFF});
        assertTrue(decoded.startsWith("ERROR "));
    }

    @Test
    void validatesSubmittedText() {
        assertEquals("ERROR x", service.validateText("ERROR x"));
        assertThrows(InvalidLogFileException.class, () -> service.validateText(null));
        assertThrows(InvalidLogFileException.class, () -> service.validateText("   "));
        assertThrows(FileTooLargeException.class, () -> service.validateText("x".repeat(1025)));
    }

    @Test
    void errorMessagesNeverEchoFileContentOrPaths() {
        InvalidLogFileException e = assertThrows(InvalidLogFileException.class,
                () -> service.validateUpload("../../secret/passwords.exe", 10));
        assertTrue(!e.getMessage().contains("secret") && !e.getMessage().contains("passwords"));
    }
}
