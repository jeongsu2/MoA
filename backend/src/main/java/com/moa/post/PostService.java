package com.moa.post;

import com.moa.global.ApiException;
import com.moa.global.ErrorCode;
import com.moa.image.ImageStorage;
import com.moa.user.User;
import com.moa.user.UserRepository;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
    private final ImageStorage imageStorage;

    public PostService(UserRepository userRepository, SalePostRepository salePostRepository,
            PostImageRepository postImageRepository, ProductRepository productRepository,
            ImageStorage imageStorage) {
        this.userRepository = userRepository;
        this.salePostRepository = salePostRepository;
        this.postImageRepository = postImageRepository;
        this.productRepository = productRepository;
        this.imageStorage = imageStorage;
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

    // 사진은 늘 통째로 바꾼다. 상품은 DRAFT일 때만, 보냈을 때만 통째로 바꾼다 (03 문서 Q-1 A).
    // DRAFT 상품은 아직 댓글과 당첨이 가리키지 않아서 지우고 새로 넣어도 된다. 번호를 서로 바꾸는 수정도 유니크에 안 걸린다
    @Transactional
    public PostDetailResponse updatePost(Long sellerId, Long postId, PostUpdateRequest request) {
        SalePost post = salePostRepository.findMyPostForUpdate(postId, sellerId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (request.products() != null) {
            if (!post.isDraft()) {
                throw new ApiException(ErrorCode.PRODUCTS_LOCKED);
            }
            requireNoDuplicateItemNo(request.products());
            productRepository.deleteByPostId(postId);
            saveProducts(post, request.products());
        }
        post.changeText(request.title(), request.content());
        postImageRepository.deleteByPostId(postId);
        saveImages(post, request.imageKeys());
        return toDetail(post);
    }

    // 같은 요청을 여러 번 보내도 결과가 같다. 이미 OPEN이면 200이고 opened_at은 처음 값 그대로 (03 문서 AC-10)
    @Transactional
    public PostStatusResponse openPost(Long sellerId, Long postId) {
        // DB 컬럼 정밀도(마이크로초)에 맞춰 두어 응답 값과 나중에 조회한 값이 같게 한다
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        if (salePostRepository.open(postId, sellerId, now) == 1) {
            return new PostStatusResponse(PostStatus.OPEN, now);
        }
        SalePost post = findMyPost(sellerId, postId);
        return switch (post.getStatus()) {
            case OPEN -> new PostStatusResponse(PostStatus.OPEN, post.getOpenedAt());
            case DRAFT -> throw new ApiException(ErrorCode.PRODUCT_REQUIRED);
            case CLOSED -> throw new ApiException(ErrorCode.POST_STATUS_CONFLICT);
        };
    }

    // OPEN에서만 닫는다. 이미 CLOSED면 200 (멱등), DRAFT면 409
    @Transactional
    public PostStatusResponse closePost(Long sellerId, Long postId) {
        salePostRepository.close(postId, sellerId);
        SalePost post = findMyPost(sellerId, postId);
        if (post.getStatus() != PostStatus.CLOSED) {
            throw new ApiException(ErrorCode.POST_STATUS_CONFLICT);
        }
        return new PostStatusResponse(post.getStatus(), post.getOpenedAt());
    }

    private SalePost findMyPost(Long sellerId, Long postId) {
        return salePostRepository.findByIdAndSellerId(postId, sellerId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
    }

    // 비로그인이면 viewerId가 null. DRAFT는 작성자에게만 보이고 남에게는 없는 글과 같은 404 (03 문서 AC-13)
    @Transactional(readOnly = true)
    public PostDetailResponse getPost(Long postId, Long viewerId) {
        SalePost post = salePostRepository.findById(postId)
                .orElseThrow(() -> new ApiException(ErrorCode.POST_NOT_FOUND));
        if (post.isDraft() && !post.isWrittenBy(viewerId)) {
            throw new ApiException(ErrorCode.POST_NOT_FOUND);
        }
        return toDetail(post);
    }

    @Transactional(readOnly = true)
    public List<MyPostResponse> getMyPosts(Long sellerId) {
        return salePostRepository.findMyPosts(sellerId);
    }

    private PostDetailResponse toDetail(SalePost post) {
        List<PostImageResponse> images = postImageRepository.findByPostIdOrderBySortOrderAsc(post.getId()).stream()
                .map(image -> new PostImageResponse(image.getImageKey(), imageStorage.urlOf(image.getImageKey()),
                        image.getSortOrder()))
                .toList();
        List<ProductResponse> products = productRepository.findByPostIdOrderByIdAsc(post.getId()).stream()
                .map(ProductResponse::from)
                .toList();
        return new PostDetailResponse(post.getId(), post.getTitle(), post.getContent(), post.getStatus(),
                post.getOpenedAt(), post.getSeller().getNickname(), images, products, post.getShippingFee(),
                post.getRemoteShippingFee());
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
