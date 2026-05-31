package com.nklcbdty.api.email.service;

import static com.nklcbdty.common.vo.QUserInterestVo.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.nklcbdty.api.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;
import com.nklcbdty.common.dto.JobPosting;
import com.nklcbdty.common.email.JobEmailContentBuilder;
import com.nklcbdty.api.user.repository.UserIdAndEmailDto;
import com.nklcbdty.api.user.repository.UserInterestRepository;
import com.nklcbdty.api.user.repository.UserInterestRepositoryImpl;
import com.nklcbdty.api.user.service.UserService;
import com.nklcbdty.common.vo.UserInterestVo;
import com.querydsl.core.Tuple;

import jakarta.mail.AuthenticationFailedException;
import jakarta.mail.MessagingException;
import jakarta.mail.SendFailedException;
import jakarta.mail.internet.AddressException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailService {
    private final UserService userService;
    private final JavaMailSender mailSender;
    private final UserInterestRepository userInterestRepository;
    private final UserInterestRepositoryImpl userInterestRepositoryImpl;
    private final JobRepository jobRepository;

    // 구독자에게 오늘자 맞춤 채용 공고 메일을 일괄 발송. 수동/자동 경로 공통 진입점.
    public int sendJobDailyEmails(List<String> userIds) {
        Map<String, String> mailMap = sendEmail(userIds);
        if (mailMap.isEmpty()) {
            log.info("맞춤 공고 메일 발송 건너뜀 (대상 없음): userIds={}", userIds);
            return 0;
        }
        String title = JobEmailContentBuilder.buildDailyTitle();
        int sent = 0;
        for (Map.Entry<String, String> entry : mailMap.entrySet()) {
            String email = entry.getKey();
            sendEmail(email, title, entry.getValue());
            log.info("맞춤 공고 메일 발송 완료: email={}", email);
            sent++;
        }
        return sent;
    }

    public void sendEmail(String to, String subject, String body) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helpder = new MimeMessageHelper(message, true, "UTF-8");
            helpder.setTo(to);
            helpder.setSubject(subject);
            helpder.setText(body, true);
            mailSender.send(message);
        } catch (AddressException e) {
            log.error("메일 발송 실패 - 잘못된 이메일 주소 형식: {}", to, e);
        } catch (AuthenticationFailedException e) {
            log.error("메일 발송 실패 - 인증 오류: {}", e.getMessage(), e);
        } catch (SendFailedException e) {
            log.error("메일 발송 실패 - 메시징 오류: {}", e.getMessage(), e);
        } catch (MessagingException e) {
            log.error("메일 발송 실패 - 스프링 메일 오류: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("메일 발송 중 예상치 못한 오류 발생", e);
        }
    }

    public String generateJobPostingEmailHtml(String keyword, List<JobPosting> jobPostings) {
        return JobEmailContentBuilder.generateHtml(keyword, jobPostings);
    }

    public Map<String, List<String>> getUserCategoryMap() {
        List<Tuple> userCategories = userInterestRepositoryImpl.findUserCategories();
        // userId 추출
        Map<String, List<String>> userCategoryMap = new HashMap<>();

        for (Tuple tuple : userCategories) {
            String userId = tuple.get(userInterestVo.userId);
            String category = tuple.get(1, String.class);
            // 필요한 작업 수행
            List<String> usrIds = userCategoryMap.getOrDefault(category, new ArrayList<>());
            usrIds.add(userId);
            userCategoryMap.put(category, usrIds);
        }
        return userCategoryMap;
    }

    /**
     * 정렬된 공고 리스트에서 종료일이 현재 시점 + 1년을 넘는 항목은 뒤로 보낸다.
     * 1년 이내 그룹은 원본 정렬(endDate DESC)을 유지하고, 초과 그룹도 자체 정렬을 유지한다.
     * 종료일 파싱이 실패하는 값(예: "영입종료시")은 1년 이내 그룹으로 둔다.
     */
    List<Job_mst> pushFarFutureEndDateToBottom(List<Job_mst> jobs) {
        LocalDate threshold = LocalDate.now().plusYears(1);
        List<Job_mst> within = new ArrayList<>();
        List<Job_mst> beyond = new ArrayList<>();
        for (Job_mst job : jobs) {
            LocalDate endDate = parseEndDate(job.getEndDate());
            if (endDate != null && endDate.isAfter(threshold)) {
                beyond.add(job);
            } else {
                within.add(job);
            }
        }
        List<Job_mst> ordered = new ArrayList<>(within.size() + beyond.size());
        ordered.addAll(within);
        ordered.addAll(beyond);
        return ordered;
    }

    private LocalDate parseEndDate(String endDate) {
        if (endDate == null || endDate.length() < 10) {
            return null;
        }
        try {
            return LocalDate.parse(endDate.substring(0, 10));
        } catch (Exception e) {
            return null;
        }
    }

    // null/"영입종료시" 만 살아있음으로 간주. 파싱불가("error" 등) 는 손상 데이터로 보고 제외.
    // reconciliation 으로 종료된 공고는 endDate=어제 가 박혀 여기서 걸러진다.
    private boolean isLive(Job_mst job, LocalDate today) {
        String endDateStr = job.getEndDate();
        if (endDateStr == null || "영입종료시".equals(endDateStr)) {
            return true;
        }
        LocalDate endDate = parseEndDate(endDateStr);
        if (endDate == null) {
            return false;
        }
        return !endDate.isBefore(today);
    }

    public Map<String, String> sendEmail(List<String> userIds) {
        List<UserIdAndEmailDto> userEmailItems = userService.findByUserIdIn(userIds);
        Map<String, String> userEmailMap = new HashMap<>();
        for (UserIdAndEmailDto item : userEmailItems) {
            userEmailMap.put(item.getUserId(), item.getEmail());
        }

        Map<String, String> result = new HashMap<>();
        for (String userId : userIds) {
            String email = userEmailMap.get(userId);
            if (email == null) {
                log.error("User ID: {} does not have an associated email.", userId);
                continue;
            }

            List<UserInterestVo> companys = userInterestRepository.findItemValueByUserIdAndItemType(userId, "company");
            List<String> companysStr = new ArrayList<>();
            for (UserInterestVo company : companys) {
                companysStr.add(company.getItemValue());
            }
            List<UserInterestVo> jobs = userInterestRepository.findItemValueByUserIdAndItemType(userId, "job");
            List<String> jobStr = new ArrayList<>();
            for (UserInterestVo job : jobs) {
                jobStr.add(job.getItemValue());
            }
            List<Job_mst> allByCompanyCdInAndSubJobCdNmIn
                = jobRepository.findAllByCompanyCdInAndSubJobCdNmInOrderByEndDateDesc(companysStr, jobStr);
            LocalDate today = LocalDate.now();
            allByCompanyCdInAndSubJobCdNmIn = allByCompanyCdInAndSubJobCdNmIn.stream()
                .filter(job -> isLive(job, today))
                .collect(java.util.stream.Collectors.toList());
            // 종료일이 현재 시점 기준 1년 초과인 공고(예: 2999-12-31 등 사실상 무기한)는 뒤로 밀어 노이즈를 줄임
            allByCompanyCdInAndSubJobCdNmIn = pushFarFutureEndDateToBottom(allByCompanyCdInAndSubJobCdNmIn);
            log.info("userId: {}, companys: {}, jobs: {}, allByCompanyCdInAndSubJobCdNmIn: {}", userId, companys, jobs, allByCompanyCdInAndSubJobCdNmIn.size());
            List<JobPosting> jobPostings = new ArrayList<>();
            for (Job_mst job : allByCompanyCdInAndSubJobCdNmIn) {
                JobPosting jobPosting = new JobPosting();
                jobPosting.setTitle(job.getAnnoSubject());
                jobPosting.setCompany(job.getCompanyCd());
                jobPosting.setUrl(job.getJobDetailLink());
                jobPosting.setJobType(job.getSubJobCdNm());
                jobPosting.setStartDate(job.getStartDate());
                jobPosting.setEndDate(job.getEndDate());
                jobPosting.setPersonalHistory(job.getPersonalHistory());
                jobPosting.setPersonalHistoryEnd(job.getPersonalHistoryEnd());
                jobPostings.add(jobPosting);
            }

            String html = this.generateJobPostingEmailHtml("backend", jobPostings);
            result.put(email, html);
        }

        return result;
    }
}
