package com.nklcbdty.api.board.dto;

/**
 * 요청을 보낸 주체. 컨트롤러가 판별해서 서비스로 넘긴다.
 * - 공개 API(/api/boards/**)   : 토큰이 있으면 userId, 없으면 익명
 * - 관리자 API(/api/admin/**) : AuthFilter 가 세팅한 adminUsername
 */
public record BoardActor(String userId, String adminUsername) {

    public static final BoardActor ANONYMOUS = new BoardActor(null, null);

    /** 로그인 사용자(또는 userId 가 null 이면 익명) */
    public static BoardActor user(String userId) {
        return new BoardActor(userId, null);
    }

    public static BoardActor admin(String adminUsername) {
        return new BoardActor(null, adminUsername);
    }

    public boolean isAdmin() {
        return adminUsername != null && !adminUsername.isBlank();
    }

    public boolean isLoggedIn() {
        return userId != null && !userId.isBlank();
    }
}
