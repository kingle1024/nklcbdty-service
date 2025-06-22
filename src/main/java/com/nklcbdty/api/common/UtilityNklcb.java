package com.nklcbdty.api.common;

import java.util.Date;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import io.jsonwebtoken.io.Decoders; // Base64 인코딩된 키를 사용하는 경우
import javax.crypto.SecretKey; // 추가해야 할 import

@Component
public class UtilityNklcb {
    @Value("${jwt.secret}")
    private String SECRET_KEY;

    public static String maskAuthor(String item) {
        if (item == null || item.length() <= 1) {
            return item; // null이거나 길이가 1 이하인 경우 그대로 반환
        }

        // 첫 글자를 제외한 나머지 부분을 '*'로 변환
        StringBuilder maskedAuthor = new StringBuilder();
        maskedAuthor.append(item.charAt(0));

        for (int i = 1; i < item.length(); i++) {
            maskedAuthor.append('*');
        }

        return maskedAuthor.toString();
    }


    public String generateToken(String userId, boolean isRefreshToken) {
        Date now = new Date();
        Date expiryDate;

        if (isRefreshToken) {
            expiryDate = new Date(now.getTime() + 604_800_000); // 7일 후 만료
        } else {
            expiryDate = new Date(now.getTime() + 3_600_000); // 1시간 후 만료
        }
        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY));

        return Jwts.builder()
            .setSubject(userId)
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .signWith(secretKey) // deprecated 되지 않은 signWith(SecretKey) 사용
            .compact();
    }

    public void validToken(String token) {
        SecretKey secretKey = Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET_KEY));
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey) // verifyWith 대신 setSigningKey를 사용해!
                .build()
                .parseClaimsJws(token)
                .getBody();

        try {
            if (claims.getExpiration().before(new Date())) {
                throw new RuntimeException("Refresh Token expired");
            }
        } catch (SignatureException e) {
            throw new RuntimeException("Invalid Refresh Token signature");
        } catch (Exception e) {
            throw new RuntimeException("Invalid Refresh Token");
        }

    }
}
