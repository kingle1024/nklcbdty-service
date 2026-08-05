-- 자유게시판 테이블 생성
-- ddl-auto=none 이므로 운영 DB(MariaDB)에 수동 적용 필요. (미적용)
--
-- 왜 수동 적용이 필요한가:
--   BoardSchemaInitializer 가 기동 시 같은 DDL 을 실행하지만, 예외를 로그만 남기고
--   삼켰다. hibernate.dialect 를 명시해 둔 덕에 앱은 DB 없이도 기동하므로,
--   컨테이너가 DB 보다 먼저 뜬 기동에서는 이 DDL 만 조용히 실패하고 앱은 정상으로
--   보인다. 그 결과 board_post 가 없는 채로 서비스되어 /api/board/** 전체가 500 을
--   냈다(2026-08-05 확인). 초기화기에 재시도/검증을 넣었지만, 이미 그 상태로 떠 있는
--   운영 DB 는 이 파일로 한 번 맞춰 준다.
--
-- 아래 내용은 BoardSchemaInitializer 의 DDL 과 같아야 한다. 한쪽만 바꾸지 말 것.

CREATE TABLE IF NOT EXISTS board_post (
  id BIGINT NOT NULL AUTO_INCREMENT,
  title VARCHAR(200) NOT NULL,
  content TEXT NOT NULL,
  author_id VARCHAR(100) NOT NULL,
  author_name VARCHAR(100) NULL,
  view_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  insert_dts DATETIME NULL,
  update_dts DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_bp_deleted_id (deleted, id),
  KEY idx_bp_author (author_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS board_comment (
  id BIGINT NOT NULL AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  content VARCHAR(1000) NOT NULL,
  author_id VARCHAR(100) NOT NULL,
  author_name VARCHAR(100) NULL,
  deleted TINYINT(1) NOT NULL DEFAULT 0,
  insert_dts DATETIME NULL,
  update_dts DATETIME NULL,
  PRIMARY KEY (id),
  KEY idx_bc_post (post_id, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 적용 후 확인 — 두 행이 나와야 한다
-- SELECT table_name FROM information_schema.tables
--  WHERE table_schema = DATABASE() AND table_name IN ('board_post', 'board_comment');
