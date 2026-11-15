package com.moa.image;

import java.util.Arrays;
import java.util.Optional;

// 형식은 파일 앞부분 바이트(매직 넘버)로 판단한다. 파일 이름과 Content-Type은 클라이언트가 마음대로 바꿀 수 있어서
public enum ImageType {
    JPEG("jpg", "image/jpeg"),
    PNG("png", "image/png"),
    WEBP("webp", "image/webp");

    public static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};
    private static final byte[] RIFF_MAGIC = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP_MAGIC = {'W', 'E', 'B', 'P'};

    private final String extension;
    private final String mediaType;

    ImageType(String extension, String mediaType) {
        this.extension = extension;
        this.mediaType = mediaType;
    }

    public String getExtension() {
        return extension;
    }

    public String getMediaType() {
        return mediaType;
    }

    public static Optional<ImageType> detect(byte[] header) {
        if (startsWith(header, 0, JPEG_MAGIC)) {
            return Optional.of(JPEG);
        }
        if (startsWith(header, 0, PNG_MAGIC)) {
            return Optional.of(PNG);
        }
        // WebP는 "RIFF" + 파일 크기 4바이트 + "WEBP"
        if (startsWith(header, 0, RIFF_MAGIC) && startsWith(header, 8, WEBP_MAGIC)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    public static Optional<ImageType> fromExtension(String extension) {
        return Arrays.stream(values())
                .filter(type -> type.extension.equals(extension))
                .findFirst();
    }

    private static boolean startsWith(byte[] header, int offset, byte[] magic) {
        if (header.length < offset + magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[offset + i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
