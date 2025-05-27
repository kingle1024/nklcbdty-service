package com.nklcbdty.api.common.security;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import com.nklcbdty.api.common.filter.AuthFilter;

@Configuration
@EnableWebSecurity
public class SecurityConfig extends WebSecurityConfigurerAdapter {
    private final AuthFilter authFilter;

    @Autowired
    public SecurityConfig(AuthFilter authFilter) {
        this.authFilter = authFilter;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeRequests()
                .antMatchers(AllowedPaths.getAllowedPaths()).permitAll()
                .antMatchers("/mypage/**").authenticated()
                .anyRequest().permitAll()
            .and()
            .oauth2Login()
                .loginPage("/login")
                .defaultSuccessUrl("/home", true)
                .failureUrl("/login?error=true")
            .and()
            .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class) // AuthFilter 등록
            .apply(new CorsConfigurationCustomizer());
    }

    public static class CorsConfigurationCustomizer extends
        AbstractHttpConfigurer<CorsConfigurationCustomizer, HttpSecurity> {
        @Override
        public void configure(HttpSecurity http) {
            UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
            CorsConfiguration config = new CorsConfiguration();
            config.setAllowCredentials(true);
            config.addAllowedOriginPattern("*");
            config.addAllowedHeader("*");
            config.addAllowedMethod("*");
            source.registerCorsConfiguration("/**", config);
            http.addFilterBefore(new CorsFilter(source), CorsFilter.class);
        }
    }
}
