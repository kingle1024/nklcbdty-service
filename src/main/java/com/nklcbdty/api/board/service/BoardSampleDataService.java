package com.nklcbdty.api.board.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.nklcbdty.api.board.vo.BoardType;

import lombok.extern.slf4j.Slf4j;

/**
 * 게시판 첫 글(샘플)을 넣는다. 새 게시판이 완전히 비어 있으면 사용자가 뭘 하는 곳인지 모르고,
 * 배포가 됐는지도 화면만 보고는 알 수 없다. 그래서 공지사항·자유게시판에 몇 건을 심어 둔다.
 *
 * <p>같은 제목의 글이 이미 있으면(관리자가 지운 글도 포함) 다시 넣지 않으므로, 기동마다 실행돼도
 * 글이 늘어나지 않는다. 자유게시판 샘플은 닉네임으로 보이지만 비밀번호가 없어서 아무나 고치거나
 * 지울 수 없고, 관리자만 정리할 수 있다.</p>
 */
@Slf4j
@Service
public class BoardSampleDataService {

    /**
     * 감사용 표시. 공지사항 샘플처럼 실제로 관리자가 쓴 글에만 넣는다.
     * 자유게시판 샘플에 넣으면 화면에 관리자 뱃지가 붙어 사용자 글로 보이지 않는다.
     */
    private static final String ADMIN_AUTHOR = "system-sample";

    private final BoardSystemWriter writer;

    public BoardSampleDataService(BoardSystemWriter writer) {
        this.writer = writer;
    }

    /**
     * 샘플 글/댓글을 넣는다.
     *
     * @return 이번에 새로 넣은 글 수
     */
    public int seed() {
        int created = 0;
        for (Sample sample : samples()) {
            Optional<BoardSystemWriter.PostRef> ref = writer.ensurePost(sample.post());
            if (ref.isEmpty()) {
                continue;
            }
            if (ref.get().created()) {
                created++;
            }
            for (SampleComment comment : sample.comments()) {
                writer.ensureComment(ref.get().id(), comment.content(), comment.authorName(),
                    comment.byAdmin() ? ADMIN_AUTHOR : null, comment.writtenAt());
            }
        }
        log.info("[Board] 샘플 글 {}건 등록", created);
        return created;
    }

    private record Sample(BoardSystemPost post, List<SampleComment> comments) {
    }

    /** byAdmin 이면 관리자 뱃지가 붙는다. */
    private record SampleComment(String authorName, String content, boolean byAdmin, LocalDateTime writtenAt) {
    }

    private static List<Sample> samples() {
        List<Sample> samples = new ArrayList<>();

        samples.add(notice(
            "[공지] 게시판 이용 안내",
            """
            채용공고를 모아 보는 서비스의 게시판입니다.

            - 공지사항: 서비스 변경 사항과 점검 안내를 올립니다. 읽기는 누구나 가능합니다.
            - 자유게시판: 취업·이직 준비 이야기를 자유롭게 나누는 곳입니다.

            자유게시판은 로그인하지 않아도 닉네임과 비밀번호(4~20자)만으로 글을 쓸 수 있습니다.
            비밀번호는 나중에 그 글을 수정하거나 지울 때 필요하니 기억해 주세요.

            광고·욕설·개인정보가 담긴 글은 예고 없이 삭제될 수 있습니다.
            """,
            true,
            LocalDateTime.of(2026, 8, 13, 10, 0)));

        samples.add(notice(
            "[공지] 공고가 안 보이거나 링크가 열리지 않을 때",
            """
            공고 목록은 회사 채용 페이지를 매일 아침 다시 읽어 만듭니다. 그래서 이런 일이 있을 수 있습니다.

            - 회사가 공고 제목을 바꾸면 잠깐 사라졌다가 다음 갱신에 다시 나타납니다.
            - 회사 채용 페이지가 점검 중이면 그 회사 공고만 비어 보일 수 있습니다.

            링크를 눌렀는데 "공고를 찾을 수 없다"는 화면이 나오면 이미 마감된 공고입니다.
            계속 같은 문제가 보이면 자유게시판에 회사 이름과 함께 남겨 주세요. 확인해서 고치겠습니다.
            """,
            false,
            LocalDateTime.of(2026, 8, 18, 14, 20)));

        samples.add(notice(
            "[공지] 공고 갱신 시간과 구독 메일 안내",
            """
            - 공고 수집: 매일 아침 7시
            - 링크 점검(마감된 공고 정리): 매일 아침 9시 30분

            구독을 신청하면 새로 올라온 공고를 메일로 받을 수 있습니다.
            메일이 오지 않으면 스팸함을 먼저 확인해 주세요.
            """,
            false,
            LocalDateTime.of(2026, 8, 21, 9, 10)));

        samples.add(free(
            "마감일 놓치고 나서 캘린더 쓰기 시작했습니다",
            """
            가고 싶던 회사 공고를 저장만 해 두고 미루다가 마감을 이틀 넘겼습니다.
            그 뒤로는 공고 볼 때마다 바로 캘린더에 적어 둡니다. 다들 저 같은 실수 하지 마세요.
            """,
            "야근왕",
            LocalDateTime.of(2026, 8, 14, 21, 40),
            List.of()));

        samples.add(free(
            "백엔드 신입, 서류 몇 군데 넣으셨어요?",
            """
            준비 4개월 차입니다. 지금까지 12곳 넣고 서류 통과는 2곳입니다.
            숫자를 늘리는 게 맞는지, 자기소개서를 회사마다 다시 쓰는 게 맞는지 고민이라 여쭤 봅니다.
            """,
            "취준4개월",
            LocalDateTime.of(2026, 8, 16, 12, 5),
            List.of(new SampleComment("삼수생", "저는 30곳 넣고 4곳 붙었어요. 회사마다 첫 문단만 다시 쓰는 걸로 타협했습니다.",
                false, LocalDateTime.of(2026, 8, 16, 13, 22)))));

        samples.add(free(
            "상시채용도 캘린더에 저장되네요",
            """
            마감일이 없는 공고는 그냥 눈으로 기억했는데, 이제 저장해 두고 지원한 날에 완료 표시를 합니다.
            어디까지 넣었는지 세는 게 훨씬 편해졌습니다.
            """,
            "이직준비중",
            LocalDateTime.of(2026, 8, 22, 19, 30),
            List.of(new SampleComment("야근왕", "완료 누른 날짜가 남아서 회고할 때 좋더라고요.",
                false, LocalDateTime.of(2026, 8, 22, 22, 11)))));

        samples.add(free(
            "카카오 공고 링크가 자꾸 바뀌는데 저만 그런가요",
            """
            어제 저장해 둔 링크를 오늘 눌렀더니 목록 화면으로 넘어갑니다.
            공고는 아직 열려 있는 것 같은데 주소만 달라진 것 같아요.
            """,
            "링크수집가",
            LocalDateTime.of(2026, 8, 25, 10, 15),
            List.of(new SampleComment("관리자", "회사가 공고 주소를 다시 발급하면 그렇습니다. 목록의 링크는 매일 아침 갱신되니 목록에서 다시 들어가 주세요.",
                true, LocalDateTime.of(2026, 8, 25, 11, 40)))));

        samples.add(free(
            "포트폴리오 정리하다 얻은 교훈 세 줄",
            """
            1. 만든 것보다 왜 그렇게 만들었는지가 질문으로 돌아옵니다.
            2. 장애를 고친 이야기가 기능을 추가한 이야기보다 잘 통합니다.
            3. 링크는 꼭 시크릿 창에서 열어 확인하세요. 저는 권한 걸린 링크를 그대로 냈습니다.
            """,
            "사이드프로젝트러",
            LocalDateTime.of(2026, 8, 29, 23, 12),
            List.of()));

        return samples;
    }

    private static Sample notice(String title, String content, boolean pinned, LocalDateTime writtenAt) {
        // 공지사항은 관리자 글이다. dedupeKey 는 제목 전체 — 제목을 고치면 새 글로 인식되니 고치지 않는다.
        return new Sample(new BoardSystemPost(BoardType.NOTICE, title, title, content.strip(),
            "관리자", ADMIN_AUTHOR, pinned, writtenAt), List.of());
    }

    private static Sample free(String title, String content, String authorName,
                               LocalDateTime writtenAt, List<SampleComment> comments) {
        // adminAuthor 를 비워 둔다 — 자유게시판 글에 관리자 뱃지가 붙으면 안 된다.
        // authorId·passwordHash 도 없으므로(BoardSystemWriter 가 NULL 로 넣는다) 정리는 관리자만 할 수 있다.
        return new Sample(new BoardSystemPost(BoardType.FREE, title, title, content.strip(),
            authorName, null, false, writtenAt), comments);
    }
}
