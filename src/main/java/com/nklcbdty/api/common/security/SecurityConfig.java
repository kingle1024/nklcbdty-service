package com.nklcbdty.api.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer; // SS6에서도 사용될 수 있습니다.
import org.springframework.security.web.SecurityFilterChain; // <-- SecurityFilterChain 임포트 추가
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource; // <-- CorsConfigurationSource 임포트 추가
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.nklcbdty.api.common.filter.AuthFilter;

@Configuration // Spring 설정을 정의하는 클래스임을 명시합니다.
@EnableWebSecurity // 웹 보안 설정을 활성화합니다.
public class SecurityConfig {
    private final AuthFilter authFilter;

    @Autowired
    public SecurityConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    // Spring Security 6.x에서는 SecurityFilterChain 타입의 Bean을 정의하여 보안 설정을 구성합니다.
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // CSRF 보호 비활성화 (.csrf().disable() -> .csrf(AbstractHttpConfigurer::disable))
            .csrf(AbstractHttpConfigurer::disable)

            // 요청 경로별 권한 설정 (.authorizeRequests() -> .authorizeHttpRequests(), .antMatchers() -> .requestMatchers())
            // 설정 순서가 중요합니다. 먼저 매칭되는 규칙이 적용됩니다.
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers(AllowedPaths.getAllowedPaths()).permitAll() // AllowedPaths에 해당하는 경로는 모두 허용
                .requestMatchers("/mypage/**").authenticated() // /mypage/** 경로는 인증된 사용자만 접근 허용
                .anyRequest().permitAll() // 위에서 명시적으로 처리되지 않은 나머지 모든 요청은 허용 (주의: /mypage** rule이 먼저 오므로 /mypage는 authenticated 적용)
            )
            // .and() 제거

            // OAuth2 Login 설정
            .oauth2Login(oauth2 -> oauth2
                .loginPage("/login")
                .defaultSuccessUrl("/home", true)
                .failureUrl("/login?error=true")
            )
            // .and() 제거

            // 커스텀 필터(AuthFilter) 등록
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class) // AuthFilter를 UsernamePasswordAuthenticationFilter 이전에 등록

            // CORS 설정 (CorsConfigurationCustomizer 대신 CorsConfigurationSource Bean 사용)
            // .apply(new CorsConfigurationCustomizer()) // <-- 제거
            .cors(cors -> cors.configurationSource(corsConfigurationSource())) // 정의한 CorsConfigurationSource Bean을 사용하도록 설정
        ; // 설정 메서드 체인이 끝나면 세미콜론으로 마무리

        return http.build(); // 구성된 HttpSecurity 객체를 SecurityFilterChain으로 빌드하여 반환합니다.
    }

    // CORS 설정을 위한 CorsConfigurationSource Bean을 정의합니다.
    // Spring Security는 이 Bean이 존재하면 자동으로 CorsFilter를 추가합니다.
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowCredentials(true);
        // setAllowedOriginPattern 대신 setAllowedOriginPatterns 사용 (SS6 변경)
        config.setAllowedOriginPatterns(java.util.Collections.singletonList("*")); // 모든 오리진 허용
        config.addAllowedHeader("*"); // 모든 헤더 허용
        config.addAllowedMethod("*"); // 모든 HTTP 메서드 허용
        source.registerCorsConfiguration("/**", config); // 모든 경로에 대해 CORS 설정 적용
        return source; // 구성된 소스를 반환
    }
}
