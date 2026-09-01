package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.board.service.BoardSystemWriter.PostRef;
import com.nklcbdty.api.board.vo.BoardType;

@ExtendWith(MockitoExtension.class)
class BoardSampleDataServiceTest {

    @Mock
    private BoardSystemWriter writer;

    private BoardSampleDataService service() {
        return new BoardSampleDataService(writer);
    }

    @Test
    void 공지사항과_자유게시판에_샘플을_넣는다() {
        AtomicLong nextId = new AtomicLong(1);
        when(writer.ensurePost(any()))
            .thenAnswer(invocation -> Optional.of(new PostRef(nextId.getAndIncrement(), true)));

        int created = service().seed();

        ArgumentCaptor<BoardSystemPost> captor = ArgumentCaptor.forClass(BoardSystemPost.class);
        verify(writer, atLeastOnce()).ensurePost(captor.capture());
        List<BoardSystemPost> posts = captor.getAllValues();

        assertThat(created).isEqualTo(posts.size());
        assertThat(posts).filteredOn(post -> post.boardType() == BoardType.NOTICE).isNotEmpty();
        assertThat(posts).filteredOn(post -> post.boardType() == BoardType.FREE).isNotEmpty();
        // 공지사항 하나는 상단 고정 — 게시판을 처음 여는 사람이 이용 안내를 먼저 보게 한다.
        assertThat(posts).filteredOn(BoardSystemPost::pinned).hasSize(1);
    }

    @Test
    void 샘플글은_제목으로_중복을_막고_비밀번호가_없다() {
        AtomicLong nextId = new AtomicLong(1);
        when(writer.ensurePost(any()))
            .thenAnswer(invocation -> Optional.of(new PostRef(nextId.getAndIncrement(), true)));

        service().seed();

        ArgumentCaptor<BoardSystemPost> captor = ArgumentCaptor.forClass(BoardSystemPost.class);
        verify(writer, atLeastOnce()).ensurePost(captor.capture());

        assertThat(captor.getAllValues()).allSatisfy(post -> {
            assertThat(post.dedupeKey()).isEqualTo(post.title());
            assertThat(post.title()).isNotBlank().hasSizeLessThanOrEqualTo(300);
            assertThat(post.content()).isNotBlank();
            assertThat(post.authorName()).isNotBlank().hasSizeLessThanOrEqualTo(50);
            // 공지사항은 관리자 글(뱃지 O), 자유게시판 샘플은 사용자 글처럼 보여야 한다(뱃지 X).
            // BoardSystemWriter 가 author_id/password_hash 를 넣지 않으므로 정리는 어느 쪽이든 관리자만 할 수 있다.
            if (post.boardType() == BoardType.NOTICE) {
                assertThat(post.adminAuthor()).isEqualTo("system-sample");
            } else {
                assertThat(post.adminAuthor()).isNull();
            }
        });
        assertThat(captor.getAllValues()).extracting(BoardSystemPost::title).doesNotHaveDuplicates();
    }

    @Test
    void 댓글은_그_글의_id_로_달린다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(77L, true)));

        service().seed();

        verify(writer, atLeastOnce())
            .ensureComment(eq(77L), anyString(), anyString(), nullable(String.class), any(LocalDateTime.class));
    }

    @Test
    void 관리자가_단_샘플_댓글만_관리자로_기록된다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(9L, true)));

        service().seed();

        ArgumentCaptor<String> adminAuthor = ArgumentCaptor.forClass(String.class);
        verify(writer, atLeastOnce())
            .ensureComment(anyLong(), anyString(), anyString(), adminAuthor.capture(), any(LocalDateTime.class));

        // 작성자명이 "관리자" 인 댓글만 뱃지가 붙어야 한다 — 나머지는 일반 사용자 댓글이다.
        assertThat(adminAuthor.getAllValues()).contains("system-sample").containsNull();
    }

    @Test
    void 글_저장이_실패하면_댓글을_달지_않는다() {
        when(writer.ensurePost(any())).thenReturn(Optional.empty());

        assertThat(service().seed()).isZero();
        verify(writer, never())
            .ensureComment(anyLong(), anyString(), anyString(), nullable(String.class), any());
    }

    @Test
    void 이미_있는_샘플은_다시_세지_않지만_댓글은_보충한다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(5L, false)));

        assertThat(service().seed()).isZero();
        verify(writer, atLeastOnce())
            .ensureComment(anyLong(), anyString(), anyString(), nullable(String.class), any(LocalDateTime.class));
    }
}
