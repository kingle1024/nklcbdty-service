package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

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

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    private static final String AUTHOR = "local@7";
    private static final String OTHER = "kakao@99";

    @Mock
    private BoardPostRepository postRepository;

    @Mock
    private BoardCommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private BoardService service;

    private BoardPost post(Long id, String authorId) {
        BoardPost post = new BoardPost();
        post.setId(id);
        post.setTitle("제목");
        post.setContent("내용");
        post.setAuthorId(authorId);
        post.setAuthorName("작성자");
        return post;
    }

    // ------------------------------------------------------------------- 작성

    @Test
    void create_작성시점_닉네임을_스냅샷으로_저장한다() {
        when(userRepository.findByUserId(AUTHOR))
            .thenReturn(UserVo.builder().userId(AUTHOR).username("길동이").build());
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardPost saved = service.create("  첫 글  ", "  내용입니다  ", AUTHOR);

        assertThat(saved.getTitle()).isEqualTo("첫 글");
        assertThat(saved.getContent()).isEqualTo("내용입니다");
        assertThat(saved.getAuthorId()).isEqualTo(AUTHOR);
        assertThat(saved.getAuthorName()).isEqualTo("길동이");
    }

    @Test
    void create_닉네임이_없으면_userId를_보여준다() {
        when(userRepository.findByUserId(AUTHOR)).thenReturn(null);
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardPost saved = service.create("제목", "내용", AUTHOR);

        assertThat(saved.getAuthorName()).isEqualTo(AUTHOR);
    }

    /**
     * board_post 는 다른 게시판과 한 테이블을 쓰므로 글에 FREE 를 반드시 찍어야 한다.
     * (board_type 은 NOT NULL 이라 비워두면 INSERT 자체가 실패한다)
     */
    @Test
    void create_자유게시판_종류를_찍어_저장한다() {
        when(userRepository.findByUserId(AUTHOR)).thenReturn(null);
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardPost saved = service.create("제목", "내용", AUTHOR);

        assertThat(saved.getBoardType()).isEqualTo(BoardPost.TYPE_FREE);
    }

    /** 목록은 자유게시판 글만 봐야 한다 — 같은 테이블의 다른 게시판 글이 섞이면 안 된다. */
    @Test
    void list_자유게시판_글만_조회한다() {
        when(postRepository.findByBoardTypeAndDeletedFalseOrderByIdDesc(
            org.mockito.ArgumentMatchers.eq(BoardPost.TYPE_FREE), any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(post(1L, AUTHOR))));

        BoardPostPageDto result = service.list(0, 20, null);

        assertThat(result.items()).hasSize(1);
        verify(postRepository).findByBoardTypeAndDeletedFalseOrderByIdDesc(
            org.mockito.ArgumentMatchers.eq(BoardPost.TYPE_FREE), any(Pageable.class));
    }

    @Test
    void create_제목이_비어있으면_저장하지_않는다() {
        assertThatThrownBy(() -> service.create("   ", "내용", AUTHOR))
            .isInstanceOf(IllegalArgumentException.class);

        verify(postRepository, never()).save(any(BoardPost.class));
    }

    // --------------------------------------------------------------- 수정/삭제

    @Test
    void update_작성자가_아니면_거부한다() {
        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.of(post(1L, AUTHOR)));

        assertThatThrownBy(() -> service.update(1L, "바뀐 제목", "바뀐 내용", OTHER))
            .isInstanceOf(BoardForbiddenException.class);

        verify(postRepository, never()).save(any(BoardPost.class));
    }

    @Test
    void delete_글과_딸린_댓글을_함께_삭제표시한다() {
        BoardComment comment = new BoardComment();
        comment.setId(11L);
        comment.setPostId(1L);
        comment.setAuthorId(OTHER);

        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.of(post(1L, AUTHOR)));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByIdAsc(1L)).thenReturn(List.of(comment));
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.delete(1L, AUTHOR);

        ArgumentCaptor<BoardPost> captor = ArgumentCaptor.forClass(BoardPost.class);
        verify(postRepository).save(captor.capture());
        assertThat(captor.getValue().isDeleted()).isTrue();
        assertThat(comment.isDeleted()).isTrue();
        verify(commentRepository, times(1)).saveAll(anyList());
    }

    @Test
    void delete_없는_글이면_404로_다룬다() {
        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(404L, BoardPost.TYPE_FREE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(404L, AUTHOR))
            .isInstanceOf(BoardNotFoundException.class);
    }

    // ------------------------------------------------------------------- 댓글

    @Test
    void addComment_등록하면_글의_댓글수를_실제개수로_맞춘다() {
        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.of(post(1L, AUTHOR)));
        when(userRepository.findByUserId(OTHER))
            .thenReturn(UserVo.builder().userId(OTHER).username("지나가던행인").build());
        when(commentRepository.save(any(BoardComment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countByPostIdAndDeletedFalse(1L)).thenReturn(3);

        BoardComment saved = service.addComment(1L, "좋은 글이네요", OTHER);

        assertThat(saved.getPostId()).isEqualTo(1L);
        assertThat(saved.getAuthorName()).isEqualTo("지나가던행인");
        verify(postRepository, times(1)).updateCommentCount(1L, 3);
    }

    @Test
    void addComment_삭제된_글에는_달_수_없다() {
        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.addComment(1L, "댓글", OTHER))
            .isInstanceOf(BoardNotFoundException.class);

        verify(commentRepository, never()).save(any(BoardComment.class));
    }

    @Test
    void deleteComment_남의_댓글은_지울_수_없다() {
        BoardComment comment = new BoardComment();
        comment.setId(11L);
        comment.setPostId(1L);
        comment.setAuthorId(AUTHOR);

        when(commentRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.deleteComment(11L, OTHER))
            .isInstanceOf(BoardForbiddenException.class);

        assertThat(comment.isDeleted()).isFalse();
        verify(postRepository, never()).updateCommentCount(anyLong(), anyInt());
    }

    // ------------------------------------------------------------------- 상세

    @Test
    void detail_조회수를_올리고_내글여부를_알려준다() {
        BoardComment comment = new BoardComment();
        comment.setId(11L);
        comment.setPostId(1L);
        comment.setContent("댓글 내용");
        comment.setAuthorId(OTHER);

        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.of(post(1L, AUTHOR)));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByIdAsc(1L)).thenReturn(List.of(comment));

        BoardPostDetailDto detail = service.detail(1L, AUTHOR);

        verify(postRepository, times(1)).increaseViewCount(1L);
        assertThat(detail.mine()).isTrue();
        assertThat(detail.comments()).hasSize(1);
        // 댓글은 다른 사람이 썼으므로 삭제 버튼이 보이면 안 된다
        assertThat(detail.comments().get(0).mine()).isFalse();
    }

    @Test
    void detail_비로그인이면_내글여부는_모두_false다() {
        when(postRepository.findByIdAndBoardTypeAndDeletedFalse(1L, BoardPost.TYPE_FREE)).thenReturn(Optional.of(post(1L, AUTHOR)));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByIdAsc(1L)).thenReturn(List.of());

        BoardPostDetailDto detail = service.detail(1L, null);

        assertThat(detail.mine()).isFalse();
    }
}
