package com.vidkeep.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 站点访问口令单元测试（P1 F-18）。
 */
class SiteAccessServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";

    @Test
    void shouldBeDisabledWhenPasswordBlank() {
        SiteAccessService service = new SiteAccessService("  ", SECRET, 3600, "vidkeep_site_token");

        assertFalse(service.isEnabled());
        assertFalse(service.passwordMatches("anything"));
        assertFalse(service.verifyToken("1.abc"));
        assertTrue(service.issueToken().isEmpty());
    }

    @Test
    void shouldMatchPasswordAndIssueVerifiableToken() {
        SiteAccessService service = new SiteAccessService("s3cret", SECRET, 3600, "vidkeep_site_token");

        assertTrue(service.isEnabled());
        assertTrue(service.passwordMatches("s3cret"));
        assertFalse(service.passwordMatches("wrong"));

        String token = service.issueToken();
        assertTrue(service.verifyToken(token));
        assertFalse(service.verifyToken(token + "x"));
        assertFalse(service.verifyToken(null));
    }

    @Test
    void shouldRejectExpiredToken() {
        SiteAccessService service = new SiteAccessService("s3cret", SECRET, -1, "vidkeep_site_token");

        assertFalse(service.verifyToken(service.issueToken()));
    }

    @Test
    void shouldRequireStrongSecretWhenEnabled() {
        assertThrows(IllegalStateException.class,
                () -> new SiteAccessService("s3cret", "short", 3600, "vidkeep_site_token"));
    }
}
