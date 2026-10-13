package com.moa.image;

import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

// 이미지는 글보다 먼저 올리고 키만 받는다 (03 문서 Q-3 A). DB 트랜잭션은 쓰지 않는다
@Service
public class ImageService {

    // UUID + 확장자. 서버가 정한 이름만 받아서 경로 조작과 추측을 막는다
    private static final Pattern IMAGE_KEY_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)$");

    private final ImageStorage imageStorage;
    private final ImageProperties properties;

    public ImageService(ImageStorage imageStorage, ImageProperties properties) {
        this.imageStorage = imageStorage;
        this.properties = properties;
    }

    public ImageUploadResponse upload(MultipartFile file) {
        if (file.getSize() > properties.maxFileSize().toBytes()) {
            throw new ApiException(ErrorCode.IMAGE_TOO_LARGE);
        }
        ImageType type = detectType(file);
        String imageKey = UUID.randomUUID() + "." + type.getExtension();
        try (InputStream content = file.getInputStream()) {
            imageStorage.save(imageKey, content);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽을 수 없음", e);
        }
        return new ImageUploadResponse(imageKey, imageStorage.urlOf(imageKey));
    }

    // 글 저장 전에 부른다. 저장소 조회(S3라면 외부 호출)가 DB 트랜잭션 안에 들어가지 않게 하려고
    public void requireUploaded(List<String> imageKeys) {
        if (imageKeys.size() > properties.maxCountPerPost()) {
            throw new ApiException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }
        for (String imageKey : imageKeys) {
            if (!isValidKey(imageKey) || !imageStorage.exists(imageKey)) {
                throw new ApiException(ErrorCode.IMAGE_NOT_UPLOADED);
            }
        }
    }

    public StoredImage load(String imageKey) {
        if (!isValidKey(imageKey)) {
            throw new ApiException(ErrorCode.IMAGE_NOT_FOUND);
        }
        Resource resource = imageStorage.load(imageKey)
                .orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_FOUND));
        String extension = imageKey.substring(imageKey.lastIndexOf('.') + 1);
        ImageType type = ImageType.fromExtension(extension)
                .orElseThrow(() -> new ApiException(ErrorCode.IMAGE_NOT_FOUND));
        return new StoredImage(resource, type.getMediaType());
    }

    private ImageType detectType(MultipartFile file) {
        byte[] header;
        try (InputStream content = file.getInputStream()) {
            header = content.readNBytes(ImageType.HEADER_LENGTH);
        } catch (IOException e) {
            throw new UncheckedIOException("업로드 파일을 읽을 수 없음", e);
        }
        Optional<ImageType> type = ImageType.detect(header);
        return type.orElseThrow(() -> new ApiException(ErrorCode.IMAGE_INVALID_TYPE));
    }

    private boolean isValidKey(String imageKey) {
        return imageKey != null && IMAGE_KEY_PATTERN.matcher(imageKey).matches();
    }

    public record StoredImage(Resource resource, String mediaType) {
    }
}
