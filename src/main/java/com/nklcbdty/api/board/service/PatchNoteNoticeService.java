package com.nklcbdty.api.board.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import com.nklcbdty.api.board.vo.BoardType;

import lombok.extern.slf4j.Slf4j;

/**
 * 패치된 내용을 공지사항에 자동으로 올린다.
 *
 * <p>원본은 저장소 안의 {@code src/main/resources/board/patch-notes.md} 다. 배포에 들어가는
 * 파일이라 리뷰를 거치고, 실행 환경에 git 이 없어도(도커 이미지에 {@code .git} 이 없다) 읽을 수 있다.</p>
 *
 * <p>기동할 때마다 실행되지만 아직 올라가지 않은 항목만 넣는다. 판정은 제목 접두사
 * {@code [패치노트] yyyy-MM-dd} 로 하므로, 이미 올라간 항목의 제목·본문을 파일에서 고쳐도
 * 중복 공지가 생기지 않는다(그 대신 올라간 공지의 내용도 바뀌지 않는다).</p>
 */
@Slf4j
@Service
public class PatchNoteNoticeService {

    private static final String RESOURCE_PATH = "board/patch-notes.md";

    /** 패치노트에는 날짜만 있다. 목록에 보일 시각은 하루 중 한 시점으로 고정한다. */
    private static final LocalTime PUBLISHED_AT = LocalTime.of(10, 0);

    private static final String AUTHOR_NAME = "관리자";
    private static final String ADMIN_AUTHOR = "system-patch-note";

    private final BoardSystemWriter writer;

    public PatchNoteNoticeService(BoardSystemWriter writer) {
        this.writer = writer;
    }

    /**
     * 패치노트 파일을 읽어 아직 공지로 올라가지 않은 항목을 등록한다.
     *
     * @return 이번에 새로 올린 공지 수
     */
    public int publishNew() {
        String markdown = readResource();
        if (markdown == null) {
            return 0;
        }
        return publish(PatchNoteParser.parse(markdown));
    }

    /** 파싱된 항목을 등록한다. 파일에 적힌 순서(오래된 것 → 최신)대로 넣어 목록 순서를 맞춘다. */
    int publish(List<PatchNote> notes) {
        int published = 0;
        for (PatchNote note : notes) {
            BoardSystemPost post = new BoardSystemPost(
                BoardType.NOTICE,
                note.dedupeKey(),
                note.noticeTitle(),
                note.body(),
                AUTHOR_NAME,
                ADMIN_AUTHOR,
                false,
                LocalDateTime.of(note.date(), PUBLISHED_AT));

            if (writer.ensurePost(post).filter(BoardSystemWriter.PostRef::created).isPresent()) {
                published++;
            }
        }
        if (published > 0) {
            log.info("[PatchNote] 공지사항에 패치노트 {}건 등록 (전체 {}건)", published, notes.size());
        } else {
            log.info("[PatchNote] 새로 올릴 패치노트 없음 (전체 {}건)", notes.size());
        }
        return published;
    }

    /** 파일이 없거나 읽기에 실패하면 null. 공지 자동 등록만 건너뛰고 기동은 그대로 진행한다. */
    private String readResource() {
        Resource resource = new ClassPathResource(RESOURCE_PATH);
        if (!resource.exists()) {
            log.warn("[PatchNote] {} 가 없어 자동 공지를 건너뛴다", RESOURCE_PATH);
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("[PatchNote] {} 읽기 실패: {}", RESOURCE_PATH, e.getMessage(), e);
            return null;
        }
    }
}
