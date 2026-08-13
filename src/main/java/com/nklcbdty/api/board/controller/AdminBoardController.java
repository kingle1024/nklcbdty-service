package com.nklcbdty.api.board.controller;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nklcbdty.api.board.dto.BoardActor;
import com.nklcbdty.api.board.dto.BoardCommentCreateRequest;
import com.nklcbdty.api.board.dto.BoardCommentDto;
import com.nklcbdty.api.board.dto.BoardPostCreateRequest;
import com.nklcbdty.api.board.dto.BoardPostDetailDto;
import com.nklcbdty.api.board.dto.BoardPostUpdateRequest;
import com.nklcbdty.api.board.service.BoardService;
import com.nklcbdty.api.board.vo.BoardType;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 관리자용 게시판 API. 공지사항 작성/수정/삭제와 자유게시판 강제 삭제(신고 처리 등)에 쓴다.
 *
 * - POST   /api/admin/boards/{boardType}/posts                   : 작성(공지사항 포함, pinned 지정 가능)
 * - PUT    /api/admin/boards/{boardType}/posts/{postId}          : 수정
 * - DELETE /api/admin/boards/{boardType}/posts/{postId}          : 삭제(비밀번호 없이 강제)
 * - POST   /api/admin/boards/{boardType}/posts/{postId}/comments : 관리자 댓글
 * - DELETE /api/admin/boards/comments/{commentId}                : 댓글 강제 삭제
 *
 * /api/admin/** 는 AuthFilter 가 role=ADMIN 토큰을 검증하고 adminUsername 속성을 채워 준다.
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/boards")
public class AdminBoardController {

    private final BoardService boardService;

    public AdminBoardController(BoardService boardService) {
        this.boardService = boardService;
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
        HttpServletRequest httpRequest
    ) {
        boardService.delete(BoardType.from(boardType), postId, null, actor(httpRequest));
        return ResponseEntity.ok(Map.of("status", "deleted", "postId", postId));
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

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, HttpServletRequest httpRequest) {
        boardService.deleteComment(commentId, null, actor(httpRequest));
        return ResponseEntity.ok(Map.of("status", "deleted", "commentId", commentId));
    }

    /** AuthFilter 가 ADMIN 토큰 검증 후 넣어 준 관리자 계정 */
    private BoardActor actor(HttpServletRequest request) {
        return BoardActor.admin((String) request.getAttribute("adminUsername"));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
