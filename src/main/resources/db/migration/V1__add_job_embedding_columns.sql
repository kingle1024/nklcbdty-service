-- RAG: 의미 검색용 임베딩 컬럼 추가
-- ddl-auto=none 이므로 운영 DB에 수동 적용 필요.
-- 모델: paraphrase-multilingual-MiniLM-L12-v2 (384차원 × 4byte = 1536 byte)
ALTER TABLE job_mst ADD COLUMN embedding MEDIUMBLOB NULL;
ALTER TABLE job_mst ADD COLUMN embedding_version VARCHAR(64) NULL;
CREATE INDEX idx_job_mst_embedding_null ON job_mst ((embedding IS NULL));
