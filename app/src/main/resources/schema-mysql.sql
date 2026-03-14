-- MySQL FULLTEXT ngram 인덱스 설정
-- 사전 조건: my.cnf에 ngram_token_size=2 설정 필요

-- 빈 stopword 테이블 (한글 검색 시 기본 영어 stopword 간섭 방지)
CREATE TABLE IF NOT EXISTS mysql.ngram_stopwords (value VARCHAR(18)) ENGINE = InnoDB;
-- SET GLOBAL innodb_ft_server_stopword_table = 'mysql/ngram_stopwords';

-- 상품 테이블 FULLTEXT 인덱스 (name + description)
ALTER TABLE products ADD FULLTEXT INDEX ft_product_search (name, description) WITH PARSER ngram;
