-- job_mst 중복 공고 행 정리 + (company_cd, anno_id) 유니크 인덱스 추가
-- ddl-auto=none 이므로 운영 DB(MariaDB)에 수동 적용 필요. (미적용)
-- ROW_NUMBER() / 다중 테이블 DELETE 를 쓰므로 MariaDB 10.2 이상에서 실행할 것.
--
-- 왜:
--   getNotSaveJobItem 이 크롤 결과를 DB 에 이미 있는 행하고만 비교해서, 한 크롤
--   결과 안에 같은 annoId 가 두 번 들어오면 둘 다 "신규"로 저장됐다. 07시 배치와
--   수동 /crawler 호출이 겹칠 때도 같은 일이 생긴다. anno_id 에 제약이 없어 한 번
--   들어간 중복 행은 계속 남고, 구독 메일에 같은 공고가 매일 반복해서 실렸다.
--   코드 쪽은 막았지만(dedupeCrawledByAnnoId) 이미 쌓인 행과 동시 실행은 DB 에서
--   막아야 한다.
--
-- 왜 anno_id 단독이 아니라 (company_cd, anno_id) 인지:
--   annoId 는 크롤 원본마다 체계가 다르다. NAVER annoId, LINE strapiId, TOSS id,
--   YANOLJA openingId, KAKAO recruitNoticeSn/jobOfferId … 전부 각자 번호라
--   회사가 다르면 같은 숫자가 나올 수 있다. anno_id 단독 유니크는 멀쩡한 공고의
--   저장을 막는다.

-- 1) 적용 전 확인 — 중복 그룹과 삭제될 행 수
-- SELECT company_cd, anno_id, COUNT(*) AS rows_
--   FROM job_mst
--  WHERE anno_id IS NOT NULL AND company_cd IS NOT NULL
--  GROUP BY company_cd, anno_id
-- HAVING COUNT(*) > 1
--  ORDER BY rows_ DESC;

-- 2) 중복 행 삭제. 그룹마다 update_dts 가 가장 최신인 행(같으면 id 가 큰 행)을
--    남긴다. endDate/personalHistory 갱신이 반영된 행을 남기기 위함이다.
--    company_cd IS NULL 인 과거 행은 건드리지 않는다 — 회사를 모르는 상태로
--    anno_id 만 묶으면 회사가 다른 별개 공고를 지울 수 있고, 3) 의 유니크 인덱스도
--    NULL 이 섞인 조합은 어차피 제약하지 않는다. 정리 범위를 제약 범위와 맞춘다.
--    derived table 로 감싸야 같은 테이블을 읽으면서 삭제할 수 있다.
DELETE dup_target
  FROM job_mst AS dup_target
  JOIN (
        SELECT id
          FROM (
                SELECT id,
                       ROW_NUMBER() OVER (
                           PARTITION BY company_cd, anno_id
                           ORDER BY update_dts DESC, id DESC
                       ) AS rn
                  FROM job_mst
                 WHERE anno_id IS NOT NULL
                   AND company_cd IS NOT NULL
               ) ranked
         WHERE ranked.rn > 1
       ) AS to_delete
    ON to_delete.id = dup_target.id;

-- 3) 재발 방지. anno_id 나 company_cd 가 NULL 인 행은 유니크 인덱스가 NULL 을
--    서로 다른 값으로 봐서 걸리지 않는다 — 기존 행을 막지 않는다.
ALTER TABLE job_mst
  ADD UNIQUE INDEX uk_job_mst_company_anno (company_cd, anno_id);

-- 4) 적용 후 확인 — 0행이어야 한다
-- SELECT company_cd, anno_id, COUNT(*)
--   FROM job_mst
--  WHERE anno_id IS NOT NULL AND company_cd IS NOT NULL
--  GROUP BY company_cd, anno_id
-- HAVING COUNT(*) > 1;
