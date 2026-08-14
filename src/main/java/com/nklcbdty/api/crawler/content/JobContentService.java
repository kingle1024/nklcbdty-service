package com.nklcbdty.api.crawler.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nklcbdty.common.vo.Job_mst;

import lombok.extern.slf4j.Slf4j;

/**
 * 공고 하나의 본문을 받아 {@code job_content} 에 넣는다.
 *
 * <p>실패도 기록한다. 실패를 안 남기면 다음 주기에 같은 공고가 다시 대상으로 잡혀,
 * 죽은 링크 몇 건이 큐를 계속 차지하고 뒤에 있는 공고가 영영 수집되지 않는다.</p>
 */
@Slf4j
@Service
public class JobContentService {

    /** 본문이 이보다 짧으면 실패로 본다. 껍데기 응답·SPA 셸을 성공으로 저장하지 않기 위함. */
    private static final int MIN_LENGTH = ContentText.MIN_MEANINGFUL_LENGTH;

    /**
     * 저장 상한. 전용 수집기는 1~7천 자인데, 범용 수집기가 본문 컨테이너를 못 찾고 페이지를 통째로
     * 집으면 2만 자가 넘는다(카카오뱅크 실측 22,882자). 뒤쪽은 대개 푸터·추천공고라 잘라도 된다.
     */
    private static final int MAX_LENGTH = 20_000;

    private final JobContentRepository repository;
    private final List<JobContentFetcher> fetchers;

    public JobContentService(JobContentRepository repository, List<JobContentFetcher> fetchers) {
        this.repository = repository;
        this.fetchers = fetchers;
    }

    /**
     * 공고 하나를 수집해 저장한다.
     *
     * @return 본문을 새로 채웠거나 갱신했으면 true. 실패했거나 내용이 그대로면 false.
     */
    @Transactional
    public boolean collect(Job_mst job) {
        JobContent row = repository.findById(job.getId())
            .orElseGet(() -> new JobContent(job.getId(), job.getCompanyCd()));
        row.setCompanyCd(job.getCompanyCd());
        row.setFetchedAt(LocalDateTime.now());

        JobContentFetcher fetcher = fetcherFor(job);
        if (fetcher == null) {
            return saveFailure(row, "수집기 없음");
        }
        row.setSource(fetcher.sourceName());

        JobContentFetcher.Fetched fetched;
        try {
            fetched = fetcher.fetch(job);
        } catch (Exception e) {
            log.warn("본문 수집 실패 jobId={} source={} - {}", job.getId(), fetcher.sourceName(), e.getMessage());
            return saveFailure(row, abbreviate(e.getMessage()));
        }

        if (fetched == null || fetched.isEmpty()) {
            return saveFailure(row, "본문 없음");
        }
        String text = fetched.text().strip();
        if (text.length() < MIN_LENGTH) {
            return saveFailure(row, "본문이 너무 짧음(" + text.length() + "자)");
        }
        if (text.length() > MAX_LENGTH) {
            log.info("본문이 길어 잘라 저장 jobId={} source={} {}자 → {}자",
                job.getId(), fetcher.sourceName(), text.length(), MAX_LENGTH);
            text = text.substring(0, MAX_LENGTH);
        }

        String hash = sha256(text);
        if (hash.equals(row.getContentHash()) && row.hasContent()) {
            // 내용이 그대로다. fetchedAt 만 갱신해 재시도 큐에서 빠지게 한다.
            row.setFailReason(null);
            repository.save(row);
            return false;
        }

        row.setContent(text);
        row.setContentHtml(fetched.html());
        row.setContentHash(hash);
        row.setFailReason(null);
        row.setUpdatedAt(LocalDateTime.now());
        repository.save(row);
        log.info("본문 수집 jobId={} source={} {}자", job.getId(), fetcher.sourceName(), text.length());
        return true;
    }

    public Optional<JobContent> find(Long jobId) {
        return repository.findById(jobId);
    }

    private JobContentFetcher fetcherFor(Job_mst job) {
        return fetchers.stream()
            .filter(f -> f.supports(job))
            .findFirst()
            .orElse(null);
    }

    private boolean saveFailure(JobContent row, String reason) {
        row.setFailReason(reason);
        repository.save(row);
        return false;
    }

    private String abbreviate(String message) {
        if (message == null) {
            return "알 수 없는 오류";
        }
        return message.length() <= 300 ? message : message.substring(0, 300);
    }

    private String sha256(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 미지원", e);
        }
    }
}
