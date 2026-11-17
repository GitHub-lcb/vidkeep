package com.vidkeep.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 播放地址签名单元测试（P1 F-17）。
 */
class PlayTokenServiceTest {

    private static final String SECRET = "0123456789abcdef0123456789abcdef";
    private static final String PATH = "/hls/12/master.m3u8";

    private final PlayTokenService playTokenService = new PlayTokenService(SECRET, 3600, 43200);

    @Test
    void shouldBuildAndVerifySignedUrl() {
        String url = playTokenService.buildSignedUrl(PATH);

        assertTrue(url.startsWith(PATH + "?"));
        String[] params = url.substring(url.indexOf('?') + 1).split("&");
        String expire = params[0].substring("expire=".length());
        String token = params[1].substring("token=".length());

        assertTrue(playTokenService.verify(PATH, expire, token));
    }

    @Test
    void shouldRejectTamperedToken() {
        String url = playTokenService.buildSignedUrl(PATH);
        String[] params = url.substring(url.indexOf('?') + 1).split("&");
        String expire = params[0].substring("expire=".length());
        String token = params[1].substring("token=".length());

        assertFalse(playTokenService.verify(PATH, expire, token + "00"));
        assertFalse(playTokenService.verify(PATH, expire, null));
        assertFalse(playTokenService.verify(PATH, null, token));
    }

    @Test
    void shouldRejectTokenForDifferentPath() {
        String url = playTokenService.buildSignedUrl(PATH);
        String[] params = url.substring(url.indexOf('?') + 1).split("&");
        String expire = params[0].substring("expire=".length());
        String token = params[1].substring("token=".length());

        assertFalse(playTokenService.verify("/hls/13/master.m3u8", expire, token));
    }

    @Test
    void shouldRejectExpiredUrl() {
        PlayTokenService expiredService = new PlayTokenService(SECRET, -10, 43200);
        String url = expiredService.buildSignedUrl(PATH);
        String[] params = url.substring(url.indexOf('?') + 1).split("&");
        String expire = params[0].substring("expire=".length());
        String token = params[1].substring("token=".length());

        assertFalse(playTokenService.verify(PATH, expire, token));
    }

    @Test
    void shouldRejectShortSecret() {
        assertThrows(IllegalStateException.class, () -> new PlayTokenService("too-short", 3600, 43200));
    }
}
