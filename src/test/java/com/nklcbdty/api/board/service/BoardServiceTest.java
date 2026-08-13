package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.nklcbdty.api.board.dto.BoardActor;
import com.nklcbdty.api.board.dto.BoardCommentCreateRequest;
import com.nklcbdty.api.board.dto.BoardCommentDto;
import com.nklcbdty.api.board.dto.BoardPostCreateRequest;
import com.nklcbdty.api.board.dto.BoardPostDetailDto;
import com.nklcbdty.api.board.dto.BoardPostUpdateRequest;
import com.nklcbdty.api.board.exception.BoardAccessDeniedException;
import com.nklcbdty.api.board.exception.BoardNotFoundException;
import com.nklcbdty.api.board.repository.BoardCommentRepository;
import com.nklcbdty.api.board.repository.BoardPostRepository;
import com.nklcbdty.api.board.vo.BoardComment;
import com.nklcbdty.api.board.vo.BoardPost;
import com.nklcbdty.api.board.vo.BoardType;

@ExtendWith(MockitoExtension.class)
class BoardServiceTest {

    @Mock
    private BoardPostRepository postRepository;

    @Mock
    private BoardCommentRepository commentRepository;

    @InjectMocks
    private BoardService service;

    // ------------------------------------------------------------------- 작성

    @Test
    void create_공지사항은_관리자가_아니면_작성할수없다() {
        BoardPostCreateRequest request = postRequest("점검 안내", "내용", null, null);

        assertThatThrownBy(() -> service.create(BoardType.NOTICE, request, BoardActor.user("user-1"), "1.2.3.4"))
            .isInstanceOf(BoardAccessDeniedException.class)
            .hasMessageContaining("관리자만");

        verify(postRepository, never()).save(any(BoardPost.class));
    }

    @Test
    void create_관리자가_공지사항을_작성하면_고정글로_저장할수있다() {
        BoardPostCreateRequest request = postRequest("점검 안내", "내용", null, null);
        request.setPinned(true);
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardPostDetailDto created = service.create(BoardType.NOTICE, request, BoardActor.admin("admin"), "1.2.3.4");

        assertThat(created.getBoardType()).isEqualTo("NOTICE");
        assertThat(created.getAuthorName()).isEqualTo("관리자");
        assertThat(created.isPinned()).isTrue();
        assertThat(created.isWrittenByAdmin()).isTrue();
        assertThat(created.isPasswordProtected()).isFalse();
    }

    @Test
    void create_로그인사용자는_비밀번호없이_자유게시판에_글을쓴다() {
        BoardPostCreateRequest request = postRequest("질문", "내용입니다", "개발자A", null);
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(BoardType.FREE, request, BoardActor.user("kakao-12345"), "1.2.3.4");

        BoardPost saved = savedPost();
        assertThat(saved.getAuthorId()).isEqualTo("kakao-12345");
        assertThat(saved.getAuthorName()).isEqualTo("개발자A");
        assertThat(saved.getPasswordHash()).isNull();
    }

    @Test
    void create_로그인사용자가_닉네임을_생략하면_userId를_마스킹해_표시한다() {
        BoardPostCreateRequest request = postRequest("질문", "내용입니다", null, null);
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(BoardType.FREE, request, BoardActor.user("kakao-12345"), "1.2.3.4");

        assertThat(savedPost().getAuthorName()).isEqualTo("kaka***");
    }

    @Test
    void create_익명은_닉네임과_비밀번호가_있어야_글을쓴다() {
        BoardPostCreateRequest request = postRequest("질문", "내용입니다", "익명이", "pw1234");
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));

        service.create(BoardType.FREE, request, BoardActor.ANONYMOUS, "1.2.3.4");

        BoardPost saved = savedPost();
        assertThat(saved.getAuthorId()).isNull();
        assertThat(saved.getPasswordHash()).isNotNull();
        assertThat(new BCryptPasswordEncoder().matches("pw1234", saved.getPasswordHash())).isTrue();
    }

    @Test
    void create_익명인데_비밀번호가_없으면_거절한다() {
        BoardPostCreateRequest request = postRequest("질문", "내용입니다", "익명이", null);

        assertThatThrownBy(() -> service.create(BoardType.FREE, request, BoardActor.ANONYMOUS, "1.2.3.4"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("비밀번호");
    }

    @Test
    void create_익명인데_닉네임이_없으면_거절한다() {
        BoardPostCreateRequest request = postRequest("질문", "내용입니다", "  ", "pw1234");

        assertThatThrownBy(() -> service.create(BoardType.FREE, request, BoardActor.ANONYMOUS, "1.2.3.4"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("작성자 이름");
    }

    @Test
    void create_제목이_비어있으면_거절한다() {
        BoardPostCreateRequest request = postRequest("   ", "내용입니다", "익명이", "pw1234");

        assertThatThrownBy(() -> service.create(BoardType.FREE, request, BoardActor.ANONYMOUS, "1.2.3.4"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("제목");
    }

    // ------------------------------------------------------------------- 조회

    @Test
    void read_상세를_조회하면_조회수가_증가하고_응답에도_반영된다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        post.setViewCount(10);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(1L)).thenReturn(List.of());

        BoardPostDetailDto detail = service.read(BoardType.FREE, 1L);

        assertThat(detail.getViewCount()).isEqualTo(11);
        verify(postRepository, times(1)).increaseViewCount(1L);
    }

    @Test
    void read_다른게시판_경로로_접근하면_찾을수없다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> service.read(BoardType.NOTICE, 1L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    @Test
    void read_삭제된글은_찾을수없다() {
        when(postRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.read(BoardType.FREE, 99L))
            .isInstanceOf(BoardNotFoundException.class);
    }

    // --------------------------------------------------------------- 수정/삭제

    @Test
    void update_작성자본인이면_수정할수있다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(1L)).thenReturn(List.of());

        BoardPostUpdateRequest request = new BoardPostUpdateRequest();
        request.setTitle("수정된 제목");
        request.setContent("수정된 내용");

        BoardPostDetailDto updated = service.update(BoardType.FREE, 1L, request, BoardActor.user("kakao-1"));

        assertThat(updated.getTitle()).isEqualTo("수정된 제목");
    }

    @Test
    void update_다른사용자는_남의글을_수정할수없다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));

        BoardPostUpdateRequest request = new BoardPostUpdateRequest();
        request.setTitle("수정된 제목");
        request.setContent("수정된 내용");

        assertThatThrownBy(() -> service.update(BoardType.FREE, 1L, request, BoardActor.user("kakao-2")))
            .isInstanceOf(BoardAccessDeniedException.class)
            .hasMessageContaining("본인");
    }

    @Test
    void update_익명글은_비밀번호가_맞아야_수정된다() {
        String hash = new BCryptPasswordEncoder().encode("pw1234");
        BoardPost post = freePost(1L, null, hash);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));

        BoardPostUpdateRequest wrong = new BoardPostUpdateRequest();
        wrong.setTitle("수정된 제목");
        wrong.setContent("수정된 내용");
        wrong.setPassword("wrong");

        assertThatThrownBy(() -> service.update(BoardType.FREE, 1L, wrong, BoardActor.ANONYMOUS))
            .isInstanceOf(BoardAccessDeniedException.class)
            .hasMessageContaining("비밀번호");
    }

    @Test
    void update_관리자는_비밀번호없이도_남의글을_수정하고_고정할수있다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(1L)).thenReturn(List.of());

        BoardPostUpdateRequest request = new BoardPostUpdateRequest();
        request.setTitle("관리자 수정");
        request.setContent("내용");
        request.setPinned(true);

        BoardPostDetailDto updated = service.update(BoardType.FREE, 1L, request, BoardActor.admin("admin"));

        assertThat(updated.isPinned()).isTrue();
    }

    @Test
    void update_일반사용자는_pinned를_바꿀수없다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.findByPostIdAndDeletedFalseOrderByInsertDtsAscIdAsc(1L)).thenReturn(List.of());

        BoardPostUpdateRequest request = new BoardPostUpdateRequest();
        request.setTitle("제목");
        request.setContent("내용");
        request.setPinned(true);

        BoardPostDetailDto updated = service.update(BoardType.FREE, 1L, request, BoardActor.user("kakao-1"));

        assertThat(updated.isPinned()).isFalse();
    }

    @Test
    void delete_삭제하면_실제삭제대신_deleted플래그와_댓글까지_함께_처리된다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(postRepository.save(any(BoardPost.class))).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.softDeleteByPostId(1L)).thenReturn(2);

        service.delete(BoardType.FREE, 1L, null, BoardActor.user("kakao-1"));

        assertThat(post.isDeleted()).isTrue();
        verify(postRepository, never()).deleteById(anyLong());
        verify(commentRepository, times(1)).softDeleteByPostId(1L);
    }

    @Test
    void delete_공지사항은_관리자만_삭제할수있다() {
        BoardPost notice = new BoardPost();
        notice.setId(5L);
        notice.setBoardType(BoardType.NOTICE);
        notice.setAdminAuthor("admin");
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(notice));

        assertThatThrownBy(() -> service.delete(BoardType.NOTICE, 5L, null, BoardActor.user("kakao-1")))
            .isInstanceOf(BoardAccessDeniedException.class);

        assertThat(notice.isDeleted()).isFalse();
    }

    // ------------------------------------------------------------------- 댓글

    @Test
    void createComment_익명댓글은_비밀번호를_해시로_저장한다() {
        BoardPost post = freePost(1L, "kakao-1", null);
        when(postRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(post));
        when(commentRepository.save(any(BoardComment.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardCommentCreateRequest request = new BoardCommentCreateRequest();
        request.setContent("좋은 글이네요");
        request.setAuthorName("지나가던사람");
        request.setPassword("pw1234");

        BoardCommentDto created = service.createComment(BoardType.FREE, 1L, request, BoardActor.ANONYMOUS, "1.2.3.4");

        assertThat(created.getAuthorName()).isEqualTo("지나가던사람");
        assertThat(created.isPasswordProtected()).isTrue();
    }

    @Test
    void createComment_공지사항에도_로그인사용자가_댓글을_달수있다() {
        BoardPost notice = new BoardPost();
        notice.setId(5L);
        notice.setBoardType(BoardType.NOTICE);
        when(postRepository.findByIdAndDeletedFalse(5L)).thenReturn(Optional.of(notice));
        when(commentRepository.save(any(BoardComment.class))).thenAnswer(inv -> inv.getArgument(0));

        BoardCommentCreateRequest request = new BoardCommentCreateRequest();
        request.setContent("확인했습니다");

        BoardCommentDto created = service.createComment(BoardType.NOTICE, 5L, request, BoardActor.user("kakao-1"), null);

        assertThat(created.isPasswordProtected()).isFalse();
    }

    @Test
    void deleteComment_남의댓글은_삭제할수없다() {
        BoardComment comment = new BoardComment();
        comment.setId(7L);
        comment.setPostId(1L);
        comment.setAuthorId("kakao-1");
        when(commentRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> service.deleteComment(7L, null, BoardActor.user("kakao-2")))
            .isInstanceOf(BoardAccessDeniedException.class);

        assertThat(comment.isDeleted()).isFalse();
    }

    @Test
    void deleteComment_관리자는_어떤댓글이든_삭제할수있다() {
        BoardComment comment = new BoardComment();
        comment.setId(7L);
        comment.setPostId(1L);
        comment.setAuthorId("kakao-1");
        when(commentRepository.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(any(BoardComment.class))).thenAnswer(inv -> inv.getArgument(0));

        service.deleteComment(7L, null, BoardActor.admin("admin"));

        assertThat(comment.isDeleted()).isTrue();
    }

    // ------------------------------------------------------------------ 헬퍼

    private BoardPostCreateRequest postRequest(String title, String content, String authorName, String password) {
        BoardPostCreateRequest request = new BoardPostCreateRequest();
        request.setTitle(title);
        request.setContent(content);
        request.setAuthorName(authorName);
        request.setPassword(password);
        return request;
    }

    private BoardPost freePost(Long id, String authorId, String passwordHash) {
        BoardPost post = new BoardPost();
        post.setId(id);
        post.setBoardType(BoardType.FREE);
        post.setTitle("원래 제목");
        post.setContent("원래 내용");
        post.setAuthorId(authorId);
        post.setAuthorName("작성자");
        post.setPasswordHash(passwordHash);
        return post;
    }

    /** postRepository.save 로 넘어간 엔티티를 꺼내 온다. */
    private BoardPost savedPost() {
        org.mockito.ArgumentCaptor<BoardPost> captor = org.mockito.ArgumentCaptor.forClass(BoardPost.class);
        verify(postRepository).save(captor.capture());
        return captor.getValue();
    }
}
