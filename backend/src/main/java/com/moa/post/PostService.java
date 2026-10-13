package com.moa.post;

import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 이미지 키 확인(저장소 조회)은 컨트롤러에서 이 서비스를 부르기 전에 끝낸다. 트랜잭션 안에는 DB 작업만 둔다
@Service
public class PostService {

    private final UserRepository userRepository;
    private final SalePostRepository salePostRepository;
    private final PostImageRepository postImageRepository;
    private final ProductRepository productRepository;

    public PostService(UserRepository userRepository, SalePostRepository salePostRepository,
            PostImageRepository postImageRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.salePostRepository = salePostRepository;
        this.postImageRepository = postImageRepository;
        this.productRepository = productRepository;
    }

    // 글, 사진, 상품은 한 트랜잭션. 상품 하나라도 실패하면 글도 안 생긴다 (03 문서 AC-1)
    @Transactional
    public PostCreateResponse createPost(Long sellerId, PostCreateRequest request) {
        requireNoDuplicateItemNo(request.products());
        User seller = userRepository.getReferenceById(sellerId);
        SalePost post = salePostRepository.save(
                SalePost.draft(seller, request.title(), request.content(), LocalDateTime.now()));
        saveImages(post, request.imageKeys());
        saveProducts(post, request.products());
        return new PostCreateResponse(post.getId(), post.getStatus());
    }

    // sort_order는 보낸 순서 그대로
    private void saveImages(SalePost post, List<String> imageKeys) {
        List<PostImage> images = new ArrayList<>();
        for (int i = 0; i < imageKeys.size(); i++) {
            images.add(new PostImage(post, imageKeys.get(i), i));
        }
        postImageRepository.saveAll(images);
    }

    private void saveProducts(SalePost post, List<ProductRequest> requests) {
        List<Product> products = requests.stream()
                .map(request -> new Product(post, request.itemNo(), request.name(), request.price()))
                .toList();
        try {
            productRepository.saveAllAndFlush(products);
        } catch (DataIntegrityViolationException e) {
            // 대소문자만 다른 번호처럼 DB 정렬 규칙(collation)에서만 겹치는 경우가 여기로 온다
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(Product.UK_POST_ITEM_NO)) {
                throw new ApiException(ErrorCode.DUPLICATE_ITEM_NO);
            }
            throw e;
        }
    }

    private void requireNoDuplicateItemNo(List<ProductRequest> products) {
        if (ProductRequest.hasDuplicateItemNo(products)) {
            throw new ApiException(ErrorCode.DUPLICATE_ITEM_NO);
        }
    }
}
