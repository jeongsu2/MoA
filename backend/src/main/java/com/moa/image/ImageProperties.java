package com.moa.image;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

// 상한은 설정값이라 코드 수정 없이 바꾼다 (03 문서 Q-3). dir은 로컬 디스크 구현만 쓴다
@ConfigurationProperties("moa.image")
public record ImageProperties(Path dir, DataSize maxFileSize, int maxCountPerPost) {
}
