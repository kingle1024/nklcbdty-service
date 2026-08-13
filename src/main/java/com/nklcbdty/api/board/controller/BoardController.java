package com.nklcbdty.api.board.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.board.dto.BoardActor;
import com.nklcbdty.api.board.dto.BoardCommentCreateRequest;
import com.nklcbdty.api.board.dto.BoardCommentDto;
import com.nklcbdty.api.board.dto.BoardCommentUpdateRequest;
import com.nklcbdty.api.board.dto.BoardPageResponse;
import com.nklcbdty.api.board.dto.BoardPasswordRequest;
import com.nklcbdty.api.board.dto.BoardPostCreateRequest;
import com.nklcbdty.api.board.dto.BoardPostDetailDto;
import com.nklcbdty.api.board.dto.BoardPostUpdateRequest;
import com.nklcbdty.api.board.service.BoardService;
import com.nklcbdty.api.board.vo.BoardType;
import com.nklcbdty.api.common.filter.AuthFilter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 사용자용 게시판 API. {boardType} 은 notice(공지사항) 또는 free(자유게시판).
 *
 * - GET    /api/boards/{boardType}/posts?page=&size=&keyword=  : 목록(고정글 우선, 최신순)
 * - GET    /api/boards/{boardType}/posts/{postId}              : 상세(조회수 +1, 댓글 포함)
 * - POST   /api/boards/{boardType}/posts                       : 작성(공지사항은 관리자 API 사용)
 * - PUT    /api/boards/{boardType}/posts/{postId}              : 수정
 * - DELETE /api/boards/{boardType}/posts/{postId}              : 삭제
 * - GET    /api/boards/{boardType}/posts/{postId}/comments     : 댓글 목록
 * - POST   /api/boards/{boardType}/posts/{postId}/comments     : 댓글 작성
 * - PUT    /api/boards/comments/{commentId}                    : 댓글 수정
 * - DELETE /api/boards/comments/{commentId}                    : 댓글 삭제
 *
 * 이 경로는 AllowedPaths 에 등록돼 AuthFilter 를 그대로 통과하므로(비로그인 열람 허용),
 * 로그인 여부는 컨트롤러에서 Authorization 헤더를 직접 해석해 판별한다.
 */
@Slf4j
@RestController
@RequestMapping("/api/boards")
public class BoardController {

    private final BoardService boardService;
    private final AuthFilter authFilter;

    public BoardController(BoardService boardService, AuthFilter authFilter) {
        this.boardService = boardService;
        this.authFilter = authFilter;
    }

    @GetMapping("/{boardType}/posts")
    public ResponseEntity<BoardPageResponse> list(
        @PathVariable String boardType,
        @RequestParam(required = false) String keyword,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return ResponseEntity.ok(boardService.list(BoardType.from(boardType), keyword, page, size));
    }

    @GetMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<BoardPostDetailDto> read(@PathVariable String boardType, @PathVariable Long postId,
                                                   HttpServletRequest httpRequest) {
        return ResponseEntity.ok(boardService.read(BoardType.from(boardType), postId, actor(httpRequest)));
    }

    @PostMapping("/{boardType}/posts")
    public ResponseEntity<BoardPostDetailDto> create(
        @PathVariable String boardType,
        @RequestBody BoardPostCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        BoardPostDetailDto created = boardService.create(
            BoardType.from(boardType), request, actor(httpRequest), resolveClientIp(httpRequest));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<BoardPostDetailDto> update(
        @PathVariable String boardType,
        @PathVariable Long postId,
        @RequestBody BoardPostUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
            boardService.update(BoardType.from(boardType), postId, request, actor(httpRequest)));
    }

    @DeleteMapping("/{boardType}/posts/{postId}")
    public ResponseEntity<?> delete(
        @PathVariable String boardType,
        @PathVariable Long postId,
        @RequestBody(required = false) BoardPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        boardService.delete(BoardType.from(boardType), postId, passwordOf(request), actor(httpRequest));
        return ResponseEntity.ok(Map.of("status", "deleted", "postId", postId));
    }

    @GetMapping("/{boardType}/posts/{postId}/comments")
    public ResponseEntity<List<BoardCommentDto>> listComments(
        @PathVariable String boardType,
        @PathVariable Long postId,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(
            boardService.listComments(BoardType.from(boardType), postId, actor(httpRequest)));
    }

    @PostMapping("/{boardType}/posts/{postId}/comments")
    public ResponseEntity<BoardCommentDto> createComment(
        @PathVariable String boardType,
        @PathVariable Long postId,
        @RequestBody BoardCommentCreateRequest request,
        HttpServletRequest httpRequest
    ) {
        BoardCommentDto created = boardService.createComment(
            BoardType.from(boardType), postId, request, actor(httpRequest), resolveClientIp(httpRequest));
        return ResponseEntity.ok(created);
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<BoardCommentDto> updateComment(
        @PathVariable Long commentId,
        @RequestBody BoardCommentUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        return ResponseEntity.ok(boardService.updateComment(commentId, request, actor(httpRequest)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(
        @PathVariable Long commentId,
        @RequestBody(required = false) BoardPasswordRequest request,
        HttpServletRequest httpRequest
    ) {
        boardService.deleteComment(commentId, passwordOf(request), actor(httpRequest));
        return ResponseEntity.ok(Map.of("status", "deleted", "commentId", commentId));
    }

    /** 토큰이 있으면 로그인 사용자, 없거나 유효하지 않으면 익명으로 취급한다. */
    private BoardActor actor(HttpServletRequest request) {
        String userId = authFilter.getUserIdByRequest(request);
        return userId == null ? BoardActor.ANONYMOUS : BoardActor.user(userId);
    }

    private String passwordOf(BoardPasswordRequest request) {
        return request == null ? null : request.getPassword();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
