package com.nklcbdty.api.board.service;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.api.board.dto.BoardCommentDto;
import com.nklcbdty.api.board.dto.BoardPostDetailDto;
import com.nklcbdty.api.board.dto.BoardPostPageDto;
import com.nklcbdty.api.board.exception.BoardForbiddenException;
import com.nklcbdty.api.board.exception.BoardNotFoundException;
import com.nklcbdty.api.board.repository.BoardCommentRepository;
import com.nklcbdty.api.board.repository.BoardPostRepository;
import com.nklcbdty.api.board.vo.BoardComment;
import com.nklcbdty.api.board.vo.BoardPost;
import com.nklcbdty.common.user.repository.UserRepository;
import com.nklcbdty.common.vo.UserVo;

import lombok.extern.slf4j.Slf4j;

/**
 * 자유게시판. 목록/상세는 로그인 없이 볼 수 있고 글/댓글 작성은 로그인이 필요하다.
 * 수정/삭제는 작성자 본인만 가능하며, 삭제는 deleted 플래그로 처리한다.
 */
@Slf4j
@Service
public class BoardService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final BoardPostRepository postRepository;
    private final BoardCommentRepository commentRepository;
    private final UserRepository userRepository;

    public BoardService(BoardPostRepository postRepository, BoardCommentRepository commentRepository,
                        UserRepository userRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userRepository = userRepository;
    }

    /** 목록. keyword 가 있으면 제목/본문 검색. */
    @Transactional(readOnly = true)
    public BoardPostPageDto list(int page, int size, String keyword) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), normalizeSize(size));
        Page<BoardPost> found = (keyword == null || keyword.isBlank())
            ? postRepository.findByBoardTypeAndDeletedFalseOrderByIdDesc(BoardPost.TYPE_FREE, pageable)
            : postRepository.searchByKeyword(BoardPost.TYPE_FREE, keyword.trim(), pageable);
        return BoardPostPageDto.of(found);
    }

    /**
     * 상세. 조회수를 1 올린 뒤 읽으므로 화면에 방금 올라간 값이 그대로 보인다.
     * viewerId 는 로그인 사용자(없으면 null) — 수정/삭제 버튼 표시 여부에 쓰인다.
     */
    @Transactional
    public BoardPostDetailDto detail(Long id, String viewerId) {
        postRepository.increaseViewCount(id);
        BoardPost post = postRepository.findByIdAndBoardTypeAndDeletedFalse(id, BoardPost.TYPE_FREE)
            .orElseThrow(() -> new BoardNotFoundException("글을 찾을 수 없습니다. id=" + id));

        List<BoardCommentDto> comments = commentRepository
            .findByPostIdAndDeletedFalseOrderByIdAsc(id)
            .stream()
            .map(comment -> BoardCommentDto.of(comment, viewerId))
            .toList();

        return BoardPostDetailDto.of(post, comments, viewerId);
    }

    @Transactional
    public BoardPost create(String title, String content, String authorId) {
        BoardPost post = new BoardPost();
        post.setTitle(validateTitle(title));
        post.setContent(validateContent(content, BoardPost.MAX_CONTENT_LENGTH));
        post.setAuthorId(authorId);
        post.setAuthorName(resolveAuthorName(authorId));
        return postRepository.save(post);
    }

    @Transactional
    public BoardPost update(Long id, String title, String content, String requesterId) {
        BoardPost post = findWritablePost(id, requesterId);
        post.setTitle(validateTitle(title));
        post.setContent(validateContent(content, BoardPost.MAX_CONTENT_LENGTH));
        return postRepository.save(post);
    }

    /** 글 삭제(플래그). 딸린 댓글도 같이 감춘다. */
    @Transactional
    public void delete(Long id, String requesterId) {
        BoardPost post = findWritablePost(id, requesterId);
        post.setDeleted(true);
        postRepository.save(post);

        List<BoardComment> comments = commentRepository.findByPostIdAndDeletedFalseOrderByIdAsc(id);
        comments.forEach(comment -> comment.setDeleted(true));
        commentRepository.saveAll(comments);
        log.info("[Board] 글 삭제 id={} 댓글 {}건 함께 삭제", id, comments.size());
    }

    @Transactional
    public BoardComment addComment(Long postId, String content, String authorId) {
        // 삭제된 글에는 댓글을 달 수 없다
        postRepository.findByIdAndBoardTypeAndDeletedFalse(postId, BoardPost.TYPE_FREE)
            .orElseThrow(() -> new BoardNotFoundException("글을 찾을 수 없습니다. id=" + postId));

        BoardComment comment = new BoardComment();
        comment.setPostId(postId);
        comment.setContent(validateContent(content, BoardComment.MAX_CONTENT_LENGTH));
        comment.setAuthorId(authorId);
        comment.setAuthorName(resolveAuthorName(authorId));
        BoardComment saved = commentRepository.save(comment);

        refreshCommentCount(postId);
        return saved;
    }

    @Transactional
    public void deleteComment(Long commentId, String requesterId) {
        BoardComment comment = commentRepository.findByIdAndDeletedFalse(commentId)
            .orElseThrow(() -> new BoardNotFoundException("댓글을 찾을 수 없습니다. id=" + commentId));
        if (!comment.getAuthorId().equals(requesterId)) {
            throw new BoardForbiddenException("본인이 쓴 댓글만 삭제할 수 있습니다.");
        }

        comment.setDeleted(true);
        commentRepository.save(comment);
        refreshCommentCount(comment.getPostId());
    }

    /** 목록에 보여줄 댓글 수를 실제 개수로 맞춘다. */
    private void refreshCommentCount(Long postId) {
        postRepository.updateCommentCount(postId, commentRepository.countByPostIdAndDeletedFalse(postId));
    }

    /** 수정/삭제 대상 글을 찾고 작성자 본인인지 확인한다. */
    private BoardPost findWritablePost(Long id, String requesterId) {
        BoardPost post = postRepository.findByIdAndBoardTypeAndDeletedFalse(id, BoardPost.TYPE_FREE)
            .orElseThrow(() -> new BoardNotFoundException("글을 찾을 수 없습니다. id=" + id));
        if (!post.getAuthorId().equals(requesterId)) {
            throw new BoardForbiddenException("본인이 쓴 글만 수정/삭제할 수 있습니다.");
        }
        return post;
    }

    /** user 테이블의 닉네임. 없으면 userId 를 대신 보여준다. */
    private String resolveAuthorName(String userId) {
        UserVo user = userRepository.findByUserId(userId);
        if (user != null && user.getUsername() != null && !user.getUsername().isBlank()) {
            return user.getUsername();
        }
        return userId;
    }

    private String validateTitle(String title) {
        String trimmed = title == null ? "" : title.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("제목을 입력해주세요.");
        }
        if (trimmed.length() > BoardPost.MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("제목은 " + BoardPost.MAX_TITLE_LENGTH + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }

    private String validateContent(String content, int maxLength) {
        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("내용을 입력해주세요.");
        }
        if (trimmed.length() > maxLength) {
            throw new IllegalArgumentException("내용은 " + maxLength + "자 이하로 입력해주세요.");
        }
        return trimmed;
    }

    private int normalizeSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
