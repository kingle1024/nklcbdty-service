-- 게시판(board_post / board_comment) 스키마 — 운영 DB 적용 기준. (적용 완료: 2026-08-13)
--
-- 이 프로젝트에는 Flyway 가 없다. db/migration 의 SQL 은 "운영 DB 에 손으로 적용해야 하는 DDL"
-- 의 기록이고, 게시판 두 테이블은 BoardSchemaInitializer 가 기동 때 같은 내용을 다시 적용한다.
-- 여기 SQL 은 초기화기가 일부러 하지 않는 것(컬럼 타입 확장)까지 포함한 완전한 형태다.
--
-- 왜 필요했나:
--   운영 MariaDB 의 스키마 이름은 travel 이고 다른 프로젝트와 공유한다. board_post 는 이미
--   다른 게시판이 쓰고 있던 이름이었다. CREATE TABLE IF NOT EXISTS 는 아무 일도 하지 않고
--   넘어갔고, 초기화기가 예외를 삼키기만 해서 어긋난 채로 배포됐다. 그 결과 2026-08-13 운영에서
--     - 목록/상세: Unknown column 'comment_count'                      -> 500
--     - 글 작성  : Field 'board_type' doesn't have a default value      -> 500
--   sql_mode 에 STRICT_TRANS_TABLES 가 걸려 있어 NOT NULL 컬럼 누락은 INSERT 실패가 된다.

-- 1) 적용 전 확인 — 현재 컬럼 모양
-- SELECT table_name, column_name, column_type, is_nullable, column_default
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name IN ('board_post', 'board_comment')
--  ORDER BY table_name, ordinal_position;

-- 2) 없으면 만든다.
CREATE TABLE IF NOT EXISTS board_post (
  id BIGINT NOT NULL AUTO_INCREMENT,
  board_type VARCHAR(20) NOT NULL,
  title VARCHAR(300) NOT NULL,
  content LONGTEXT NOT NULL,
  author_id VARCHAR(255) NULL,
  author_name VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NULL,
  admin_author VARCHAR(100) NULL,
  author_ip VARCHAR(64) NULL,
  view_count INT NOT NULL DEFAULT 0,
  pinned TINYINT(1) NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  insert_dts DATETIME NULL,
  update_dts DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_board_post_list (board_type, deleted, pinned, insert_dts),
  KEY idx_board_post_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS board_comment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  author_id VARCHAR(255) NULL,
  author_name VARCHAR(50) NOT NULL,
  password_hash VARCHAR(255) NULL,
  admin_author VARCHAR(100) NULL,
  author_ip VARCHAR(64) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  insert_dts DATETIME NULL,
  update_dts DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_board_comment_post (post_id, deleted, insert_dts)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 이미 다른 모양으로 있던 테이블에 빠진 컬럼을 채운다. 위 2) 가 아무 일도 하지 않은 경우다.
--    ADD COLUMN IF NOT EXISTS 는 MariaDB 10.0+ 기능이라 여러 번 실행해도 안전하다.
--    NOT NULL 컬럼에 DEFAULT 를 붙인 이유: 기존 행을 채울 값이 있어야 ALTER 가 통과한다.
ALTER TABLE board_post
  ADD COLUMN IF NOT EXISTS board_type    VARCHAR(20)  NOT NULL DEFAULT 'FREE',
  ADD COLUMN IF NOT EXISTS title         VARCHAR(300) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS content       LONGTEXT     NULL,
  ADD COLUMN IF NOT EXISTS author_id     VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS author_name   VARCHAR(50)  NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) NULL,
  ADD COLUMN IF NOT EXISTS admin_author  VARCHAR(100) NULL,
  ADD COLUMN IF NOT EXISTS author_ip     VARCHAR(64)  NULL,
  ADD COLUMN IF NOT EXISTS view_count    INT          NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS pinned        TINYINT(1)   NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS insert_dts    DATETIME     NULL,
  ADD COLUMN IF NOT EXISTS update_dts    DATETIME     NULL;

ALTER TABLE board_comment
  ADD COLUMN IF NOT EXISTS post_id       BIGINT        NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS content       VARCHAR(1000) NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS author_id     VARCHAR(255)  NULL,
  ADD COLUMN IF NOT EXISTS author_name   VARCHAR(50)   NOT NULL DEFAULT '',
  ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255)  NULL,
  ADD COLUMN IF NOT EXISTS admin_author  VARCHAR(100)  NULL,
  ADD COLUMN IF NOT EXISTS author_ip     VARCHAR(64)   NULL,
  ADD COLUMN IF NOT EXISTS deleted       TINYINT(1)    NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS insert_dts    DATETIME      NULL,
  ADD COLUMN IF NOT EXISTS update_dts    DATETIME      NULL;

-- 4) 예전 스키마는 author_id 가 NOT NULL 이었다. 지금은 익명 글·관리자 글을 author_id = NULL 로
--    넣으므로 그대로 두면 작성이 INSERT 단계에서 실패한다.
ALTER TABLE board_post    MODIFY COLUMN author_id VARCHAR(255) NULL;
ALTER TABLE board_comment MODIFY COLUMN author_id VARCHAR(255) NULL;

-- 5) 컬럼 타입 확장. 3) 의 ADD COLUMN 은 이미 있는 컬럼의 타입은 건드리지 않으므로,
--    예전 자유게시판의 title VARCHAR(200) / content TEXT 가 그대로 남는다. 좁은 채로 두면
--    긴 글에서만 "Data too long" 으로 저장이 실패한다. 초기화기는 공유 테이블의 타입을 함부로
--    바꾸지 않으므로(다른 프로젝트가 쓰는 컬럼일 수 있다) 이 확장은 손으로만 적용한다.
--    넓히기만 하는 변경이라 기존 데이터는 잘리지 않는다.
ALTER TABLE board_post    MODIFY COLUMN title   VARCHAR(300)  NOT NULL;
ALTER TABLE board_post    MODIFY COLUMN content LONGTEXT      NOT NULL;
ALTER TABLE board_comment MODIFY COLUMN content VARCHAR(1000) NOT NULL;

-- 6) 적용 후 확인
--    (a) 엔티티가 읽는 컬럼으로 조회가 되는가 — 실패하면 목록/상세가 500 이다
-- SELECT id, board_type, title, content, author_id, author_name, password_hash, admin_author,
--        author_ip, view_count, pinned, deleted, insert_dts, update_dts
--   FROM board_post LIMIT 1;
--
--    (b) 이 코드가 모르는 "NOT NULL + 기본값 없음" 컬럼이 남아 있는가 — 있으면 글 작성이 500 이다
--        (2026-08-13 장애 때의 comment_count 가 이 경우였다. 지금은 DEFAULT 0 이라 괜찮다)
-- SELECT table_name, column_name, column_type
--   FROM information_schema.columns
--  WHERE table_schema = DATABASE() AND table_name IN ('board_post', 'board_comment')
--    AND is_nullable = 'NO' AND column_default IS NULL AND extra NOT LIKE '%auto_increment%'
--    AND column_name <> 'id';
