package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.nklcbdty.api.board.service.BoardSystemWriter.PostRef;
import com.nklcbdty.api.board.vo.BoardType;

@ExtendWith(MockitoExtension.class)
class PatchNoteNoticeServiceTest {

    @Mock
    private BoardSystemWriter writer;

    private PatchNoteNoticeService service() {
        return new PatchNoteNoticeService(writer);
    }

    @Test
    void 패치노트를_공지사항에_등록한다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(10L, true)));

        int published = service().publish(List.of(
            new PatchNote(LocalDate.of(2026, 9, 1), "게시판 정리", "- 내용", "[패치노트] 2026-09-01")));

        assertThat(published).isEqualTo(1);

        ArgumentCaptor<BoardSystemPost> captor = ArgumentCaptor.forClass(BoardSystemPost.class);
        verify(writer).ensurePost(captor.capture());
        BoardSystemPost post = captor.getValue();

        assertThat(post.boardType()).isEqualTo(BoardType.NOTICE);
        assertThat(post.title()).isEqualTo("[패치노트] 2026-09-01 게시판 정리");
        assertThat(post.dedupeKey()).isEqualTo("[패치노트] 2026-09-01");
        assertThat(post.content()).isEqualTo("- 내용");
        // 공지 작성 시각은 패치 날짜다 — 배포 시각이 아니라 목록에서 패치 순서로 보이게 한다.
        assertThat(post.writtenAt().toLocalDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        // 패치노트가 서비스 안내 공지를 밀어내지 않도록 고정하지 않는다.
        assertThat(post.pinned()).isFalse();
    }

    @Test
    void 이미_올라간_패치노트는_세지_않는다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(10L, false)));

        int published = service().publish(List.of(
            new PatchNote(LocalDate.of(2026, 9, 1), "이미 올린 것", "- 내용", "[패치노트] 2026-09-01")));

        assertThat(published).isZero();
    }

    @Test
    void 저장에_실패해도_예외를_던지지_않는다() {
        when(writer.ensurePost(any())).thenReturn(Optional.empty());

        assertThat(service().publish(List.of(
            new PatchNote(LocalDate.of(2026, 9, 1), "실패", "- 내용", "[패치노트] 2026-09-01")))).isZero();
    }

    @Test
    void 항목이_없으면_아무것도_하지_않는다() {
        assertThat(service().publish(List.of())).isZero();
        verify(writer, never()).ensurePost(any());
    }

    /** 실제 리소스 파일을 읽어 파일에 적힌 항목 수만큼 등록을 시도하는지 확인한다. */
    @Test
    void publishNew_는_저장소의_패치노트_파일을_읽는다() {
        when(writer.ensurePost(any())).thenReturn(Optional.of(new PostRef(1L, true)));

        int published = service().publishNew();

        assertThat(published).isGreaterThanOrEqualTo(7);
        verify(writer, times(published)).ensurePost(any());
    }
}
