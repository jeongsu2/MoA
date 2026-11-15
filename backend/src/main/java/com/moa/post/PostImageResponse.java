package com.moa.post;

// imageKey는 수정 화면이 PUT에 그대로 다시 보내려고 같이 준다
public record PostImageResponse(String imageKey, String url, int sortOrder) {
}
