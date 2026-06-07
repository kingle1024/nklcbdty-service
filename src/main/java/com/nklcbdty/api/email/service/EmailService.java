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
import com.nklcbdty.common.crawler.repository.JobRepository;
import com.nklcbdty.common.vo.Job_mst;
import com.nklcbdty.common.dto.JobPosting;
import com.nklcbdty.common.email.JobEmailContentBuilder;
import com.nklcbdty.common.email.JobMailOrdering;
import com.nklcbdty.common.user.dto.UserIdAndEmailDto;
import com.nklcbdty.common.user.repository.UserInterestRepository;
import com.nklcbdty.common.user.repository.UserInterestRepositoryImpl;
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
            // 자동(batch) 메일과 동일하게 사용자 경력 필터링도 적용. 과거에는 빠져있어
            // 경력 3년 사용자에게도 7년 이상 공고가 메일로 노출됐다.
            List<UserInterestVo> careerYear = userInterestRepository.findItemValueByUserIdAndItemType(userId, "career_year");
            long careerYearNum;
            if (careerYear.isEmpty()) {
                careerYearNum = 0;
            } else {
                careerYearNum = Long.parseLong(careerYear.get(careerYear.size() - 1).getItemValue());
            }
            List<Job_mst> allByCompanyCdInAndSubJobCdNmIn
                = jobRepository.findJobsByDetailedCriteria(companysStr, jobStr, careerYearNum, 0L);
            LocalDate today = LocalDate.now();
            allByCompanyCdInAndSubJobCdNmIn = allByCompanyCdInAndSubJobCdNmIn.stream()
                .filter(job -> JobMailOrdering.isLive(job, today))
                .collect(java.util.stream.Collectors.toList());
            // 종료일이 현재 시점 기준 1년 초과인 공고(예: 2999-12-31 등 사실상 무기한)는 뒤로 밀어 노이즈를 줄임
            allByCompanyCdInAndSubJobCdNmIn = JobMailOrdering.pushFarFutureEndDateToBottom(allByCompanyCdInAndSubJobCdNmIn);
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
