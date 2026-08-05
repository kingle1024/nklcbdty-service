-- 자유게시판 첫 글 3개. V4__board_tables.sql 적용 후 한 번만 실행한다.
--
-- author_id 는 실제 계정의 userId 여야 그 계정으로 로그인했을 때 수정/삭제 버튼이 보인다.
-- 아래 SET 두 줄을 본인 계정 값으로 바꿔서 실행할 것. userId 찾기:
--   SELECT id, user_id, username FROM user ORDER BY id DESC LIMIT 20;
-- 그대로 실행하면 글은 정상적으로 보이지만 화면에서 수정/삭제는 못 한다.

SET @author_id   = 'local@1';
SET @author_name = '운영자';

INSERT INTO board_post (title, content, author_id, author_name, insert_dts, update_dts) VALUES
(
  '자유게시판을 열었습니다',
  '취업·이직 준비하면서 알게 된 것들을 편하게 남겨 주세요.\n\n'
  '- 면접 후기, 코딩테스트 유형, 처우 협상 경험 같은 것들이 특히 도움이 됩니다.\n'
  '- 회사·사람 실명 비방이나 특정인을 알아볼 수 있는 내용은 삼가 주세요.\n'
  '- 글과 댓글은 작성자 본인만 수정/삭제할 수 있습니다.',
  @author_id, @author_name, NOW(), NOW()
),
(
  '채용 캘린더 쓰는 법',
  '상단 "채용 캘린더" 는 공고 마감일 기준으로 달력에 뿌려 줍니다.\n\n'
  '마감이 몰리는 주를 미리 보고 지원 순서를 정하는 데 쓰면 좋습니다. '
  '날짜를 누르면 그날 마감인 공고만 모아 볼 수 있고, 공고를 누르면 원본 채용 페이지로 넘어갑니다.\n\n'
  '이미 닫힌 공고가 남아 있으면 공고 상세에서 삭제 요청을 보내 주세요. 확인 후 목록에서 내립니다.',
  @author_id, @author_name, NOW(), NOW()
),
(
  '구독 메일은 어떻게 오나요',
  '마이페이지에서 관심 회사·직군을 등록해 두면 새 공고가 올라올 때 메일로 보내 드립니다.\n\n'
  '- 크롤링은 매일 아침에 한 번 돌고, 그때 새로 확인된 공고만 담습니다.\n'
  '- 같은 공고가 두 번 실리는 문제는 고쳤습니다. 그래도 중복이 보이면 문의 및 건의사항으로 알려 주세요.\n'
  '- 메일이 너무 자주 온다고 느껴지면 관심 조건을 좁히는 편이 낫습니다.',
  @author_id, @author_name, NOW(), NOW()
);

-- 확인 — 3행이 나와야 한다
-- SELECT id, title, author_id, author_name, insert_dts FROM board_post ORDER BY id DESC;
