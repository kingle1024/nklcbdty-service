package com.nklcbdty.api.board.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class PatchNoteParserTest {

    @Test
    void 날짜_머리글마다_항목이_하나씩_생긴다() {
        List<PatchNote> notes = PatchNoteParser.parse("""
            # 패치노트
            이 위 설명은 항목이 아니다.

            ## 2026-08-04 | 자유게시판 오픈
            - 글을 쓸 수 있습니다.

            ## 2026-08-13 | 공지사항 추가
            - 공지사항이 생겼습니다.
            """);

        assertThat(notes).hasSize(2);
        assertThat(notes.get(0).date()).isEqualTo(LocalDate.of(2026, 8, 4));
        assertThat(notes.get(0).title()).isEqualTo("자유게시판 오픈");
        assertThat(notes.get(0).body()).isEqualTo("- 글을 쓸 수 있습니다.");
        assertThat(notes.get(0).noticeTitle()).isEqualTo("[패치노트] 2026-08-04 자유게시판 오픈");
        assertThat(notes.get(1).noticeTitle()).isEqualTo("[패치노트] 2026-08-13 공지사항 추가");
    }

    @Test
    void 제목없이_날짜만_써도_된다() {
        List<PatchNote> notes = PatchNoteParser.parse("""
            ## 2026-09-01
            - 잔잔한 수정들.
            """);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).title()).isEmpty();
        assertThat(notes.get(0).noticeTitle()).isEqualTo("[패치노트] 2026-09-01");
    }

    @Test
    void 같은_날짜가_여러번이면_열쇠에_순번이_붙는다() {
        List<PatchNote> notes = PatchNoteParser.parse("""
            ## 2026-09-01 | 오전 배포
            - 첫 배포.

            ## 2026-09-01 | 오후 배포
            - 두 번째 배포.
            """);

        assertThat(notes).extracting(PatchNote::dedupeKey)
            .containsExactly("[패치노트] 2026-09-01", "[패치노트] 2026-09-01 (2)");
    }

    @Test
    void 본문이_비면_공지로_올리지_않는다() {
        List<PatchNote> notes = PatchNoteParser.parse("""
            ## 2026-09-01 | 본문 없음

            ## 2026-09-02 | 본문 있음
            - 내용.
            """);

        assertThat(notes).extracting(PatchNote::title).containsExactly("본문 있음");
    }

    @Test
    void 날짜가_아닌_머리글은_본문으로_흘린다() {
        List<PatchNote> notes = PatchNoteParser.parse("""
            ## 2026-09-01 | 배포
            - 내용.

            ## 참고
            - 문서 소제목은 새 항목이 아니다.
            """);

        assertThat(notes).hasSize(1);
        assertThat(notes.get(0).body()).contains("## 참고");
    }

    @Test
    void 빈_입력은_빈_목록() {
        assertThat(PatchNoteParser.parse(null)).isEmpty();
        assertThat(PatchNoteParser.parse("   ")).isEmpty();
    }

    /**
     * 실제로 배포에 들어가는 파일을 검증한다. 여기서 깨지면 운영에서 공지가 안 올라가거나
     * 같은 공지가 두 번 올라간다.
     */
    @Test
    void 저장소의_패치노트_파일은_열쇠가_겹치지_않고_본문이_있다() throws IOException {
        List<PatchNote> notes = PatchNoteParser.parse(readPatchNotes());

        assertThat(notes).isNotEmpty();
        assertThat(notes).extracting(PatchNote::dedupeKey).doesNotHaveDuplicates();
        assertThat(notes).allSatisfy(note -> {
            assertThat(note.body()).isNotBlank();
            assertThat(note.noticeTitle()).startsWith("[패치노트] ").hasSizeLessThanOrEqualTo(300);
        });
        // 파일에 적힌 순서가 곧 공지 등록 순서다. 오래된 것부터 적어 두어야 목록이 시간순으로 쌓인다.
        List<LocalDate> dates = notes.stream().map(PatchNote::date).toList();
        assertThat(dates).isEqualTo(dates.stream().sorted().toList());
    }

    private String readPatchNotes() throws IOException {
        try (InputStream in = new ClassPathResource("board/patch-notes.md").getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
    }
}
