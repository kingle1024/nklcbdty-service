package com.nklcbdty.api.board.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.board.dto.BoardActor;
import com.nklcbdty.api.board.dto.BoardCommentCreateRequest;
import com.nklcbdty.api.board.dto.BoardCommentDto;
import com.nklcbdty.api.board.dto.BoardCommentUpdateRequest;
import com.nklcbdty.api.board.dto.BoardPageResponse;
import com.nklcbdty.api.board.dto.BoardPostCreateRequest;
import com.nklcbdty.api.board.dto.BoardPostDetailDto;
import com.nklcbdty.api.board.dto.BoardPostSummaryDto;
import com.nklcbdty.api.board.dto.BoardPostUpdateRequest;
import com.nklcbdty.api.board.exception.BoardAccessDeniedException;
import com.nklcbdty.api.board.exception.BoardNotFoundException;
import com.nklcbdty.api.board.repository.BoardCommentRepository;
import com.nklcbdty.api.board.repository.BoardPostRepository;
import com.nklcbdty.api.board.vo.BoardComment;
import com.nklcbdty.api.board.vo.BoardPost;
import com.nklcbdty.api.board.vo.BoardType;
import com.nklcbdty.api.board.vo.OwnedContent;

import lombok.extern.slf4j.Slf4j;

/**
 * 게시판(공지사항/자유게시판) 도메인 로직.
 *
 * 권한 규칙
 * - 읽기: 누구나
 * - 공지사항 쓰기/수정/삭제: 관리자만
 * - 자유게시판 쓰기: 로그인 사용자(userId) 또는 익명(닉네임+비밀번호)
 * - 수정/삭제: 작성자 본인(userId 일치) 또는 익명 글 비밀번호 일치, 관리자는 항상 가능
 */
@Slf4j
@Service
public class BoardService {

    /** 목록 기본/최대 페이지 크기 */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private static final int MAX_TITLE_LENGTH = 300;
    private static final int MAX_CONTENT_LENGTH = 20_000;
    private static final int MAX_COMMENT_LENGTH = 1_000;
    private static final int MAX_AUTHOR_NAME_LENGTH = 50;
    private static final int MIN_PASSWORD_LENGTH = 4;
    private static final int MAX_PASSWORD_LENGTH = 20;

    /** 고정글 먼저, 그 다음 최신순 */
    private static final Sort LIST_SORT =
        Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("insertDts"), Sort.Order.desc("id"));

    private final BoardPostRepository postRepository;
    private final BoardCommentRepository commentRepository;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public BoardService(BoardPostRepository postRepository, BoardCommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    // ------------------------------------------------------------------ 게시글

    /** 목록 조회. keyword 가 있으면 제목/내용/작성자 검색. */
    @Transactional(readOnly = true)
    public BoardPageResponse list(BoardType boardType, String keyword, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampPageSize(size), LIST_SORT);

        Page<BoardPost> found = (keyword == null || keyword.isBlank())
            ? postRepository.findByBoardTypeAndDeletedFalse(boardType, pageable)
            : postRepository.search(boardType, keyword.trim(), pageable);

        Map<Long, Long> commentCounts = commentCounts(found.getContent());
        List<BoardPostSummaryDto> rows = found.getContent().stream()
            .map(post -> BoardPostSummaryDto.from(post, commentCounts.getOrDefault(post.getId(), 0L)))
            .toList();

        return BoardPageResponse.builder()
            .rows(rows)
            .totalElements(found.getTotalElements())
            .totalPages(found.getTotalPages())
            .pageNumber(found.getNumber())
            .pageSize(found.getSize())
            .build();
    }

    /** 상세 조회. 호출 시 조회수를 1 증가시킨다. */
    @Transactional
    public BoardPostDetailDto read(BoardType boardType, Long postId) {
        BoardPost post = loadPost(boardType, postId);
        postRepository.increaseViewCount(post.getId());

        List<BoardCommentDto> comments =
            commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(post.getId()).stream()
                .map(BoardCommentDto::from)
                .toList();

        // increaseViewCount 는 DB 에 직접 UPDATE 하므로 엔티티 값은 그대로다. 응답에만 +1 반영.
        return BoardPostDetailDto.from(post, post.getViewCount() + 1, comments);
    }

    /** 글 작성 */
    @Transactional
    public BoardPostDetailDto create(BoardType boardType, BoardPostCreateRequest request,
                                     BoardActor actor, String clientIp) {
        if (boardType.isAdminOnlyWrite() && !actor.isAdmin()) {
            throw new BoardAccessDeniedException(boardType.getLabel() + "은(는) 관리자만 작성할 수 있습니다.");
        }

        BoardPost post = new BoardPost();
        post.setBoardType(boardType);
        post.setTitle(requireText(request.getTitle(), "제목", MAX_TITLE_LENGTH));
        post.setContent(requireText(request.getContent(), "내용", MAX_CONTENT_LENGTH));
        post.setAuthorName(resolveAuthorName(request.getAuthorName(), actor));
        post.setAuthorIp(clientIp);
        post.setPinned(actor.isAdmin() && request.isPinned());

        if (actor.isAdmin()) {
            post.setAdminAuthor(actor.adminUsername());
        } else if (actor.isLoggedIn()) {
            post.setAuthorId(actor.userId());
        } else {
            post.setPasswordHash(passwordEncoder.encode(requirePassword(request.getPassword())));
        }

        BoardPost saved = postRepository.save(post);
        log.info("[Board] 글 작성 boardType={} id={} author={}", boardType, saved.getId(), saved.getAuthorName());
        return BoardPostDetailDto.from(saved, saved.getViewCount(), List.of());
    }

    /** 글 수정 */
    @Transactional
    public BoardPostDetailDto update(BoardType boardType, Long postId,
                                     BoardPostUpdateRequest request, BoardActor actor) {
        BoardPost post = loadPost(boardType, postId);
        authorize(post, boardType, request.getPassword(), actor, "수정");

        post.setTitle(requireText(request.getTitle(), "제목", MAX_TITLE_LENGTH));
        post.setContent(requireText(request.getContent(), "내용", MAX_CONTENT_LENGTH));
        if (actor.isAdmin() && request.getPinned() != null) {
            post.setPinned(request.getPinned());
        }

        BoardPost saved = postRepository.save(post);
        List<BoardCommentDto> comments =
            commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(postId).stream()
                .map(BoardCommentDto::from)
                .toList();
        return BoardPostDetailDto.from(saved, saved.getViewCount(), comments);
    }

    /** 글 삭제(soft delete). 딸린 댓글도 함께 감춘다. */
    @Transactional
    public void delete(BoardType boardType, Long postId, String password, BoardActor actor) {
        BoardPost post = loadPost(boardType, postId);
        authorize(post, boardType, password, actor, "삭제");

        post.setDeleted(true);
        postRepository.save(post);
        int hiddenComments = commentRepository.softDeleteByPostId(postId);
        log.info("[Board] 글 삭제 boardType={} id={} 댓글 {}건 함께 처리", boardType, postId, hiddenComments);
    }

    // -------------------------------------------------------------------- 댓글

    @Transactional(readOnly = true)
    public List<BoardCommentDto> listComments(BoardType boardType, Long postId) {
        loadPost(boardType, postId);
        return commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(postId).stream()
            .map(BoardCommentDto::from)
            .toList();
    }

    /** 댓글 작성. 공지사항/자유게시판 모두 로그인 사용자와 익명 모두 가능. */
    @Transactional
    public BoardCommentDto createComment(BoardType boardType, Long postId,
                                         BoardCommentCreateRequest request, BoardActor actor, String clientIp) {
        BoardPost post = loadPost(boardType, postId);

        BoardComment comment = new BoardComment();
        comment.setPostId(post.getId());
        comment.setContent(requireText(request.getContent(), "댓글 내용", MAX_COMMENT_LENGTH));
        comment.setAuthorName(resolveAuthorName(request.getAuthorName(), actor));
        comment.setAuthorIp(clientIp);

        if (actor.isAdmin()) {
            comment.setAdminAuthor(actor.adminUsername());
        } else if (actor.isLoggedIn()) {
            comment.setAuthorId(actor.userId());
        } else {
            comment.setPasswordHash(passwordEncoder.encode(requirePassword(request.getPassword())));
        }

        return BoardCommentDto.from(commentRepository.save(comment));
    }

    @Transactional
    public BoardCommentDto updateComment(Long commentId, BoardCommentUpdateRequest request, BoardActor actor) {
        BoardComment comment = loadComment(commentId);
        authorizeOwner(comment, request.getPassword(), actor, "수정");

        comment.setContent(requireText(request.getContent(), "댓글 내용", MAX_COMMENT_LENGTH));
        return BoardCommentDto.from(commentRepository.save(comment));
    }

    @Transactional
    public void deleteComment(Long commentId, String password, BoardActor actor) {
        BoardComment comment = loadComment(commentId);
        authorizeOwner(comment, password, actor, "삭제");

        comment.setDeleted(true);
        commentRepository.save(comment);
    }

    // ------------------------------------------------------------------ 내부용

    private BoardPost loadPost(BoardType boardType, Long postId) {
        if (postId == null) {
            throw new IllegalArgumentException("글 번호가 필요합니다.");
        }
        BoardPost post = postRepository.findByIdAndDeletedFalse(postId)
            .orElseThrow(() -> new BoardNotFoundException("글을 찾을 수 없습니다. id=" + postId));

        // 다른 게시판 경로로 남의 글에 접근하는 것을 막는다.
        if (post.getBoardType() != boardType) {
            throw new BoardNotFoundException("글을 찾을 수 없습니다. id=" + postId);
        }
        return post;
    }

    private BoardComment loadComment(Long commentId) {
        if (commentId == null) {
            throw new IllegalArgumentException("댓글 번호가 필요합니다.");
        }
        return commentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> new BoardNotFoundException("댓글을 찾을 수 없습니다. id=" + commentId));
    }

    /** 게시글 권한 검사. 공지사항은 관리자 전용이라 소유권 검사보다 먼저 막는다. */
    private void authorize(BoardPost post, BoardType boardType, String password, BoardActor actor, String action) {
        if (boardType.isAdminOnlyWrite() && !actor.isAdmin()) {
            throw new BoardAccessDeniedException(boardType.getLabel() + "은(는) 관리자만 " + action + "할 수 있습니다.");
        }
        authorizeOwner(post, password, actor, action);
    }

    /**
     * 소유권 검사.
     * 관리자는 무조건 통과, 로그인 글은 userId 일치, 익명 글은 비밀번호 일치를 요구한다.
     */
    private void authorizeOwner(OwnedContent content, String password, BoardActor actor, String action) {
        if (actor.isAdmin()) {
            return;
        }
        if (content.getAuthorId() != null) {
            if (!content.getAuthorId().equals(actor.userId())) {
                throw new BoardAccessDeniedException("본인이 작성한 글만 " + action + "할 수 있습니다.");
            }
            return;
        }
        if (content.getPasswordHash() != null) {
            if (password == null || !passwordEncoder.matches(password, content.getPasswordHash())) {
                throw new BoardAccessDeniedException("비밀번호가 일치하지 않습니다.");
            }
            return;
        }
        // authorId/passwordHash 가 모두 없는 글 = 관리자가 쓴 글
        throw new BoardAccessDeniedException("관리자만 " + action + "할 수 있습니다.");
    }

    private String resolveAuthorName(String requested, BoardActor actor) {
        String name = requested == null ? null : requested.trim();
        if (name != null && name.length() > MAX_AUTHOR_NAME_LENGTH) {
            throw new IllegalArgumentException("작성자 이름은 " + MAX_AUTHOR_NAME_LENGTH + "자 이하로 입력해주세요.");
        }
        if (name != null && !name.isEmpty()) {
            return name;
        }
        if (actor.isAdmin()) {
            return "관리자";
        }
        if (actor.isLoggedIn()) {
            return maskUserId(actor.userId());
        }
        throw new IllegalArgumentException("작성자 이름을 입력해주세요.");
    }

    /** 로그인 사용자가 닉네임을 안 보냈을 때 쓰는 기본 표시명. userId 를 그대로 노출하지 않는다. */
    private String maskUserId(String userId) {
        return userId.length() <= 4 ? userId : userId.substring(0, 4) + "***";
    }

    private String requirePassword(String password) {
        if (password == null || password.isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요. (익명 작성 시 수정/삭제에 필요합니다)");
        }
        if (password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                "비밀번호는 " + MIN_PASSWORD_LENGTH + "~" + MAX_PASSWORD_LENGTH + "자로 입력해주세요.");
        }
        return password;
    }

    private String requireText(String value, String fieldLabel, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldLabel + "을(를) 입력해주세요.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException(fieldLabel + "은(는) " + maxLength + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }

    private int clampPageSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    /** 목록의 글들에 달린 댓글 수를 한 번의 쿼리로 모아 온다. */
    private Map<Long, Long> commentCounts(List<BoardPost> posts) {
        if (posts.isEmpty()) {
            return Map.of();
        }
        List<Long> postIds = posts.stream().map(BoardPost::getId).toList();
        Map<Long, Long> counts = new HashMap<>();
        for (Object[] row : commentRepository.countByPostIds(postIds)) {
            counts.put((Long) row[0], (Long) row[1]);
        }
        return counts;
    }
}
