package com.nklcbdty.api.email.service;

import static com.nklcbdty.api.user.vo.QUserInterestVo.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import com.nklcbdty.api.crawler.repository.JobRepository;
import com.nklcbdty.api.crawler.vo.Job_mst;
import com.nklcbdty.api.email.dto.JobPosting;
import com.nklcbdty.api.user.repository.UserIdAndEmailDto;
import com.nklcbdty.api.user.repository.UserInterestRepository;
import com.nklcbdty.api.user.repository.UserInterestRepositoryImpl;
import com.nklcbdty.api.user.service.UserService;
import com.nklcbdty.api.user.vo.UserInterestVo;
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
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy년 MM월 dd일");
        String today = LocalDateTime.now().format(dateFormatter);

        StringBuilder htmlBuilder = new StringBuilder();

        // HTML 메일의 시작 부분 (헤더, 키워드 영역)
        htmlBuilder.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"padding: 20px 0\">");
        htmlBuilder.append("<tbody><tr><td align=\"center\">");
        htmlBuilder.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" style=\"background: #ffffff; border-radius: 8px; overflow: hidden\">");
        htmlBuilder.append("<tbody><tr><td style=\"padding: 24px; background-color: #222; color: #fff; text-align: center; font-size: 20px;\">");
        htmlBuilder.append("🎯 <strong>").append(today).append(" 맞춤 채용 공고가 도착했어요!</strong>");
        htmlBuilder.append("</td></tr>");
        htmlBuilder.append("<tr><td style=\"padding: 16px 24px; font-size: 16px; color: #333\">");
        htmlBuilder.append("<p>");
        htmlBuilder.append("네카라쿠배 채용 공고 모음 서비스에서 구독해주신 키워드 ");
        htmlBuilder.append("<strong style=\"color: #007bff\">").append(keyword).append("</strong>에 해당하는 새로운 채용 소식을 알려드려요.");
        htmlBuilder.append("</p>");
        htmlBuilder.append("<p>");
        htmlBuilder.append("더 많은 채용 공고를 보시려면 ");
        htmlBuilder.append("<a href=\"https://www.nklcb.co.kr\" style=\"color: #007bff; text-decoration: underline\" rel=\"noreferrer noopener\" target=\"_blank\"><strong>nklcb.co.kr</strong></a>");
        htmlBuilder.append("에서 확인하실 수 있어요.");
        htmlBuilder.append("</p>");
        htmlBuilder.append("</td></tr>");

        for (JobPosting job : jobPostings) {
            htmlBuilder.append("<tr><td style=\"padding: 16px; border-bottom: 1px solid #eee\">");
            htmlBuilder.append("<a href=\"").append(job.getUrl()).append("\" style=\"font-size: 16px; font-weight: bold; color: #222; text-decoration: none;\" rel=\"noreferrer noopener\" target=\"_blank\">");
            htmlBuilder.append(job.getTitle());
            htmlBuilder.append("</a>");
            htmlBuilder.append("<div style=\"font-size: 14px; color: #666; margin-top: 4px\">");
            htmlBuilder.append(job.getCompany()).append(" | ").append(job.getJobType());

            String deadline = job.getEndDate();
            if (deadline != null && !deadline.isEmpty()) {
                htmlBuilder.append(" | ").append(deadline);
            }

            htmlBuilder.append("</div>");
            htmlBuilder.append("</td></tr>");
        }

        // HTML 메일의 끝 부분
        htmlBuilder.append("</tbody></table>");
        htmlBuilder.append("</td></tr></tbody></table>");

        return htmlBuilder.toString();
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
            List<Job_mst> allByCompanyCdInAndSubJobCdNmIn
                = jobRepository.findAllByCompanyCdInAndSubJobCdNmInOrderByEndDateDesc(companysStr, jobStr);
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
                jobPostings.add(jobPosting);
            }

            String html = this.generateJobPostingEmailHtml("backend", jobPostings);
            result.put(email, html);
        }

        return result;
    }
}
