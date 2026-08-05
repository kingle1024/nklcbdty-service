package com.nklcbdty.api.board.config;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.SQLException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BoardSchemaInitializerTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    /** 대기 없이 재시도만 확인한다. */
    private BoardSchemaInitializer initializer() {
        return new BoardSchemaInitializer(jdbcTemplate, 0L);
    }

    @Test
    void DB가_늦게_떠도_다음_시도에서_테이블을_만든다() {
        // 첫 시도는 연결 실패, 두 번째 시도는 성공
        doThrow(new DataAccessResourceFailureException("connection refused"))
            .doNothing()
            .doNothing()
            .when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class))).thenReturn(0);

        initializer().run(null);

        // 실패한 1회 + 성공한 2회 = execute 3회. 성공 후에는 더 시도하지 않는다.
        verify(jdbcTemplate, times(3)).execute(anyString());
    }

    @Test
    void 끝까지_실패하면_정해진_횟수만_시도하고_멈춘다() {
        doThrow(new DataAccessResourceFailureException("connection refused"))
            .when(jdbcTemplate).execute(anyString());

        initializer().run(null);

        verify(jdbcTemplate, times(BoardSchemaInitializer.MAX_ATTEMPTS)).execute(anyString());
        // 연결이 안 되면 확인 쿼리까지 가지도 못한다
        verify(jdbcTemplate, never()).queryForObject(anyString(), eq(Integer.class));
    }

    @Test
    void DDL은_성공했지만_읽을_수_없으면_실패로_본다() {
        // CREATE 는 통과하는데 SELECT 가 막히는 경우(권한 등)를 성공으로 봐서는 안 된다.
        doNothing().when(jdbcTemplate).execute(anyString());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class)))
            .thenThrow(new BadSqlGrammarException("select", "SELECT COUNT(*) FROM board_post",
                new SQLException("table doesn't exist")));

        initializer().run(null);

        verify(jdbcTemplate, times(BoardSchemaInitializer.MAX_ATTEMPTS))
            .queryForObject(anyString(), eq(Integer.class));
    }
}
