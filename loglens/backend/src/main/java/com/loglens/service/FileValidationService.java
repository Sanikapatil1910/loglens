package com.loglens.service;

import com.loglens.config.LogLensProperties;
import com.loglens.exception.FileTooLargeException;
import com.loglens.exception.InvalidLogFileException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Validates uploads and submitted text. Uploaded content is only ever decoded to a String:
 * it is never written to disk, executed, or used to build a file-system path, and the client's
 * file name is only sanitised for display.
 */
@Service
public class FileValidationService {

    static final int BINARY_SNIFF_BYTES = 8192;
    static final int MAX_DISPLAY_NAME = 100;

    private static final Pattern CONTROL_CHARS = Pattern.compile("\\p{Cntrl}");
    private static final Pattern UNSAFE_NAME_CHARS = Pattern.compile("[^\\p{L}\\p{N}._\\- ()]");

    private final LogLensProperties properties;

    public FileValidationService(LogLensProperties properties) {
        this.properties = properties;
    }

    /** Last path segment only, control and unusual characters replaced, length limited. */
    public String sanitizeFilename(String originalFilename) {
        if (originalFilename == null) {
            return "unnamed";
        }
        String name = originalFilename;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        name = CONTROL_CHARS.matcher(name).replaceAll("");
        name = UNSAFE_NAME_CHARS.matcher(name).replaceAll("_").trim();
        if (name.length() > MAX_DISPLAY_NAME) {
            name = name.substring(0, MAX_DISPLAY_NAME);
        }
        return name.isEmpty() ? "unnamed" : name;
    }

    /** Checks name, extension and declared size before the content is read. */
    public void validateUpload(String originalFilename, long sizeBytes) {
        String name = sanitizeFilename(originalFilename);
        if (originalFilename == null || originalFilename.isBlank() || name.equals("unnamed")) {
            throw new InvalidLogFileException("The uploaded file has no usable name.");
        }
        String extension = extensionOf(name);
        List<String> allowed = properties.allowedExtensions();
        if (extension.isEmpty() || !allowed.contains(extension)) {
            throw new InvalidLogFileException("Unsupported file type. Allowed types: "
                    + String.join(", ", allowed.stream().map(e -> "." + e).toList()) + ".");
        }
        if (sizeBytes <= 0) {
            throw new InvalidLogFileException("The uploaded file is empty.");
        }
        if (sizeBytes > properties.maxUploadBytes()) {
            throw tooLarge();
        }
    }

    /** Decodes upload bytes as UTF-8 after rejecting empty, oversized and binary content. */
    public String decode(byte[] content) {
        if (content == null || content.length == 0) {
            throw new InvalidLogFileException("The uploaded file is empty.");
        }
        if (content.length > properties.maxUploadBytes()) {
            throw tooLarge();
        }
        int sniff = Math.min(content.length, BINARY_SNIFF_BYTES);
        for (int i = 0; i < sniff; i++) {
            if (content[i] == 0) {
                throw new InvalidLogFileException("The file looks like binary data, not a text log.");
            }
        }
        String text = new String(content, StandardCharsets.UTF_8);
        if (text.isBlank()) {
            throw new InvalidLogFileException("The file contains only whitespace.");
        }
        return text;
    }

    /** Validates text submitted as JSON. */
    public String validateText(String text) {
        if (text == null || text.isBlank()) {
            throw new InvalidLogFileException("Log text must not be empty.");
        }
        if (text.getBytes(StandardCharsets.UTF_8).length > properties.maxUploadBytes()) {
            throw tooLarge();
        }
        return text;
    }

    private FileTooLargeException tooLarge() {
        long limitMb = Math.max(1, properties.maxUploadBytes() / (1024 * 1024));
        return new FileTooLargeException("The log exceeds the maximum allowed size of " + limitMb + " MB.");
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 || dot == name.length() - 1 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
