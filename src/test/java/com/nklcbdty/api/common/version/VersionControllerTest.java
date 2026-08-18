package com.nklcbdty.api.common.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import com.nklcbdty.api.common.security.AllowedPaths;

class VersionControllerTest {

    /** BuildProperties 가 없을 때도 동작해야 한다(로컬 bootRun). */
    private ObjectProvider<BuildProperties> absent() {
        return new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                throw new UnsupportedOperationException();
            }

            @Override
            public BuildProperties getObject(Object... args) {
                throw new UnsupportedOperationException();
            }

            @Override
            public BuildProperties getIfAvailable() {
                return null;
            }

            @Override
            public BuildProperties getIfUnique() {
                return null;
            }
        };
    }

    private ObjectProvider<BuildProperties> present(BuildProperties props) {
        return new ObjectProvider<>() {
            @Override
            public BuildProperties getObject() {
                return props;
            }

            @Override
            public BuildProperties getObject(Object... args) {
                return props;
            }

            @Override
            public BuildProperties getIfAvailable() {
                return props;
            }

            @Override
            public BuildProperties getIfUnique() {
                return props;
            }
        };
    }

    @Test
    void 빌드정보가_있으면_빌드시각과_버전을_알려준다() {
        Properties p = new Properties();
        p.setProperty("version", "0.0.1-SNAPSHOT");
        p.setProperty("time", "2026-08-18T09:30:00Z");

        Map<String, Object> body = new VersionController(present(new BuildProperties(p))).version();

        assertEquals("0.0.1-SNAPSHOT", body.get("version"));
        assertNotNull(body.get("buildTime"));
        // KST 로 환산해 보여준다. 09:30Z → 18:30+09:00
        assertTrue(String.valueOf(body.get("buildTime")).contains("18:30"),
            "KST 로 보여야 한다: " + body.get("buildTime"));
    }

    @Test
    void 빌드정보가_없어도_기동시각은_알려준다() {
        Map<String, Object> body = new VersionController(absent()).version();

        assertNull(body.get("buildTime"));
        assertNull(body.get("version"));
        assertNotNull(body.get("startedAt"), "배포 반영 판단의 핵심 값이라 항상 있어야 한다");
        assertNotNull(body.get("uptime"));
    }

    @Test
    void 기동시각은_현재보다_과거다() {
        Map<String, Object> body = new VersionController(absent()).version();

        String startedAt = String.valueOf(body.get("startedAt"));
        assertTrue(ZonedDateTime.parse(startedAt).toInstant().isBefore(Instant.now().plusSeconds(1)),
            "startedAt=" + startedAt);
    }

    @Test
    void 인증_없이_열려_있어야_한다() {
        // 필터에 막히면 배포 확인 수단이 사라진다.
        assertTrue(java.util.Arrays.asList(AllowedPaths.getAllowedPaths()).contains("/api/version"));
    }
}
