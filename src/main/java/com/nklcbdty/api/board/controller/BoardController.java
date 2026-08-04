package com.nklcbdty.api.board.controller;

import java.util.Map;

import org.springframework.http.HttpStatus;
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

import com.nklcbdty.api.board.dto.BoardCommentRequest;
import com.nklcbdty.api.board.dto.BoardPostRequest;
import com.nklcbdty.api.board.exception.BoardForbiddenException;
import com.nklcbdty.api.board.exception.BoardNotFoundException;
import com.nklcbdty.api.board.service.BoardService;
import com.nklcbdty.api.board.vo.BoardComment;
import com.nklcbdty.api.board.vo.BoardPost;
import com.nklcbdty.api.common.filter.AuthFilter;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

/**
 * 자유게시판 API.
 * - GET    /api/board/posts                    : 목록(page, size, keyword)
 * - GET    /api/board/posts/{id}               : 상세 + 댓글
 * - POST   /api/board/posts                    : 글 작성(로그인 필요)
 * - PUT    /api/board/posts/{id}               : 글 수정(작성자만)
 * - DELETE /api/board/posts/{id}               : 글 삭제(작성자만)
 * - POST   /api/board/posts/{id}/comments      : 댓글 작성(로그인 필요)
 * - DELETE /api/board/comments/{commentId}     : 댓글 삭제(작성자만)
 *
 * 목록/상세는 로그인 없이 볼 수 있어야 해서 경로 전체를 AllowedPaths 에 등록했다.
 * 그래서 AuthFilter 가 userId 속성을 넣어주지 않으므로, 로그인 사용자는 이 컨트롤러에서
 * 토큰을 직접 해석해 확인한다(작성 계열은 userId 가 없으면 401).
 */
@Slf4j
@RestController
@RequestMapping("/api/board")
public class BoardController {

    private final BoardService boardService;
    private final AuthFilter authFilter;

    public BoardController(BoardService boardService, AuthFilter authFilter) {
        this.boardService = boardService;
        this.authFilter = authFilter;
    }

    @GetMapping("/posts")
    public ResponseEntity<?> list(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) String keyword
    ) {
        return ResponseEntity.ok(boardService.list(page, size, keyword));
    }

    @GetMapping("/posts/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id, HttpServletRequest request) {
        try {
            return ResponseEntity.ok(boardService.detail(id, currentUserId(request)));
        } catch (BoardNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping("/posts")
    public ResponseEntity<?> create(@RequestBody BoardPostRequest dto, HttpServletRequest request) {
        String userId = currentUserId(request);
        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        try {
            BoardPost saved = boardService.create(dto.getTitle(), dto.getContent(), userId);
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PutMapping("/posts/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody BoardPostRequest dto,
                                    HttpServletRequest request) {
        String userId = currentUserId(request);
        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        try {
            BoardPost saved = boardService.update(id, dto.getTitle(), dto.getContent(), userId);
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (BoardNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (BoardForbiddenException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @DeleteMapping("/posts/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id, HttpServletRequest request) {
        String userId = currentUserId(request);
        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        try {
            boardService.delete(id, userId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (BoardNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (BoardForbiddenException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping("/posts/{id}/comments")
    public ResponseEntity<?> addComment(@PathVariable Long id, @RequestBody BoardCommentRequest dto,
                                        HttpServletRequest request) {
        String userId = currentUserId(request);
        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        try {
            BoardComment saved = boardService.addComment(id, dto.getContent(), userId);
            return ResponseEntity.ok(Map.of("id", saved.getId()));
        } catch (IllegalArgumentException e) {
            return error(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (BoardNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<?> deleteComment(@PathVariable Long commentId, HttpServletRequest request) {
        String userId = currentUserId(request);
        if (userId == null) {
            return error(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다.");
        }

        try {
            boardService.deleteComment(commentId, userId);
            return ResponseEntity.ok(Map.of("status", "deleted"));
        } catch (BoardNotFoundException e) {
            return error(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (BoardForbiddenException e) {
            return error(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    /** Authorization 헤더의 토큰에서 userId. 토큰이 없거나 유효하지 않으면 null(=비로그인). */
    private String currentUserId(HttpServletRequest request) {
        return authFilter.getUserIdByRequest(request);
    }

    private ResponseEntity<Map<String, String>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
            .body(Map.of("message", message == null ? "요청을 처리할 수 없습니다." : message));
    }
}
