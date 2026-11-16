package com.vidkeep.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 校验 deploy/sql/init.sql 中的种子管理员密码哈希确实对应 admin123。
 *
 * <p>该哈希一旦与注释不一致，全新环境将无法登录，因此纳入单元测试兜底。
 * 在非仓库工作目录运行时自动跳过。</p>
 */
class SeedPasswordTest {

    private static final Path INIT_SQL = Path.of("..", "deploy", "sql", "init.sql");
    private static final String EXPECTED_PASSWORD = "admin123";
    private static final Pattern BCRYPT_PATTERN = Pattern.compile("\\$2[aby]\\$\\d{2}\\$[./A-Za-z0-9]{53}");

    @Test
    void seedAdminHashShouldMatchAdmin123() throws IOException {
        assumeTrue(Files.exists(INIT_SQL), "当前工作目录不在仓库内，跳过种子密码校验");

        String sql = Files.readString(INIT_SQL, StandardCharsets.UTF_8);
        Matcher matcher = BCRYPT_PATTERN.matcher(sql);
        assertTrue(matcher.find(), "deploy/sql/init.sql 中未找到 BCrypt 密码哈希");

        String hash = matcher.group();
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        assertTrue(encoder.matches(EXPECTED_PASSWORD, hash),
                "种子管理员密码哈希必须对应明文 " + EXPECTED_PASSWORD);
    }
}
