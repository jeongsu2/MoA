package com.moa.image;

import java.io.InputStream;
import java.util.Optional;
import org.springframework.core.io.Resource;

// 파일을 어디에 두는지는 구현체만 안다 (D-17). 글과 상품 코드는 imageKey와 urlOf만 쓴다.
// S3로 바꾸면 구현체 하나와 설정만 바뀌고, DB의 image_key와 글 저장 로직은 그대로다
public interface ImageStorage {

    void save(String imageKey, InputStream content);

    boolean exists(String imageKey);

    Optional<Resource> load(String imageKey);

    // 저장소 키와 공개 URL을 나눠 둔다. S3면 CDN 주소를 돌려주면 된다
    String urlOf(String imageKey);
}
