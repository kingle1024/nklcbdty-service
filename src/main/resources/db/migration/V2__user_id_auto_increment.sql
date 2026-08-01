-- user 테이블 id 에 PK + AUTO_INCREMENT 추가
-- ddl-auto=none 이므로 운영 DB에 수동 적용 필요. (2026-08-01 적용 완료)
--
-- UserVo 는 @Id @GeneratedValue(IDENTITY) 인데 실제 테이블에는 PK/auto_increment 가
-- 없어서 신규 사용자 INSERT 가 항상 아래 오류로 실패했다.
--   HibernateException: The database returned no natively generated identity value : UserVo
-- 자체 회원가입뿐 아니라 신규 카카오 사용자의 첫 로그인(UserService.loadUserById)도 같이 막혀 있었다.
ALTER TABLE user MODIFY id BIGINT NOT NULL AUTO_INCREMENT, ADD PRIMARY KEY (id);
