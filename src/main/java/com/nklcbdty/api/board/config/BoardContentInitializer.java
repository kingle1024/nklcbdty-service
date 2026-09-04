package com.nklcbdty.api.board.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import com.nklcbdty.api.board.service.BoardSampleDataService;
import com.nklcbdty.api.board.service.PatchNoteNoticeService;

import lombok.extern.slf4j.Slf4j;

/**
 * 기동할 때 게시판 내용을 채운다.
 *
 * <ol>
 *   <li>샘플 글 — 게시판이 비어 있을 때 뭘 하는 곳인지 보이도록 공지/자유게시판에 몇 건.</li>
 *   <li>패치노트 — {@code board/patch-notes.md} 에서 아직 공지로 안 올라간 항목을 공지사항에 등록.</li>
 * </ol>
 *
 * <p>둘 다 같은 글을 두 번 넣지 않으므로 배포마다 실행돼도 안전하다. 이 클래스는
 * {@link BoardSchemaInitializer} 가 테이블을 확인·보정한 뒤에 실행돼야 하므로 순서를 뒤로 둔다.</p>
 *
 * <p>실패해도 기동을 막지 않는다 — 게시판 글 때문에 서비스 전체를 내릴 이유는 없다.</p>
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BoardContentInitializer implements ApplicationRunner {

    private final BoardSampleDataService sampleDataService;
    private final PatchNoteNoticeService patchNoteNoticeService;

    /** 샘플 글 자동 등록 여부. 이미 글이 있는 게시판에는 영향이 없다. */
    @Value("${nklcb.board.sample-data.enabled:true}")
    private boolean sampleDataEnabled;

    /** 패치노트 자동 공지 여부. */
    @Value("${nklcb.board.patch-notes.enabled:true}")
    private boolean patchNotesEnabled;

    public BoardContentInitializer(BoardSampleDataService sampleDataService,
                                   PatchNoteNoticeService patchNoteNoticeService) {
        this.sampleDataService = sampleDataService;
        this.patchNoteNoticeService = patchNoteNoticeService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (sampleDataEnabled) {
            try {
                sampleDataService.seed();
            } catch (Exception e) {
                log.error("[Board] 샘플 글 등록 실패: {}", e.getMessage(), e);
            }
        }
        if (patchNotesEnabled) {
            try {
                patchNoteNoticeService.publishNew();
            } catch (Exception e) {
                log.error("[PatchNote] 자동 공지 실패: {}", e.getMessage(), e);
            }
        }
    }
}
