package com.moa.image;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

// 서버 로컬 디스크 구현 (D-17). 서버를 바꾸면 파일이 사라지는 위험은 D-17에 적어 둔 대로 감수한다
@Component
public class LocalImageStorage implements ImageStorage {

    public static final String URL_PREFIX = "/images/";

    private final Path dir;

    public LocalImageStorage(ImageProperties properties) {
        this.dir = properties.dir().toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new UncheckedIOException("이미지 폴더를 만들 수 없음: " + dir, e);
        }
    }

    @Override
    public void save(String imageKey, InputStream content) {
        Path target = resolve(imageKey);
        try {
            // 덮어쓰지 않는다. 키는 UUID라 겹치면 오류로 드러나야 한다
            Files.copy(content, target);
        } catch (IOException e) {
            deleteQuietly(target);
            throw new UncheckedIOException("이미지 저장 실패: " + imageKey, e);
        }
    }

    @Override
    public boolean exists(String imageKey) {
        return Files.isRegularFile(resolve(imageKey));
    }

    @Override
    public Optional<Resource> load(String imageKey) {
        Path path = resolve(imageKey);
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }
        return Optional.of(new FileSystemResource(path));
    }

    @Override
    public String urlOf(String imageKey) {
        return URL_PREFIX + imageKey;
    }

    // 키 형식은 ImageService가 먼저 막지만, 폴더 밖 경로(../)가 들어와도 여기서 한 번 더 막는다
    private Path resolve(String imageKey) {
        Path path = dir.resolve(imageKey).normalize();
        if (!dir.equals(path.getParent())) {
            throw new IllegalArgumentException("이미지 폴더 밖 경로: " + imageKey);
        }
        return path;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            // 지우지 못한 조각 파일은 남는다. 키를 아무도 모르므로 서빙되지 않는다
        }
    }
}
