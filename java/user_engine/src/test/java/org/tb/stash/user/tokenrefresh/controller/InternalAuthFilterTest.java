package org.tb.stash.user.tokenrefresh.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.tb.stash.user.config.InternalAuthProperties;

class InternalAuthFilterTest {

    private static final String SECRET = "s3cr3t-token";
    private static final String INTERNAL_URI =
            "/internal/users/00000000-0000-0000-0000-0000000000aa/oauth/google/access-token";

    private InternalAuthFilter filter;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new InternalAuthFilter(new InternalAuthProperties(SECRET), new ObjectMapper());
        chain = mock(FilterChain.class);
    }

    @Test
    void nonInternalUri_passesThroughUntouched() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/me");
        MockHttpServletResponse response = new MockHttpServletResponse();

        // shouldNotFilter short-circuits OncePerRequestFilter before doFilterInternal.
        assertThat(filter.shouldNotFilter(request)).isTrue();
        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void correctHeader_invokesChain() throws Exception {
        MockHttpServletResponse response = invoke(request(SECRET));

        verify(chain).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
    }

    @Test
    void missingHeader_returns401JsonAndDoesNotCallChain() throws Exception {
        MockHttpServletResponse response = invoke(request(null));

        assertUnauthorized(response);
    }

    @Test
    void blankHeader_returns401JsonAndDoesNotCallChain() throws Exception {
        MockHttpServletResponse response = invoke(request("   "));

        assertUnauthorized(response);
    }

    @Test
    void wrongHeader_returns401JsonAndDoesNotCallChain() throws Exception {
        MockHttpServletResponse response = invoke(request("nope"));

        assertUnauthorized(response);
    }

    private MockHttpServletResponse invoke(MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    private static MockHttpServletRequest request(String headerValue) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", INTERNAL_URI);
        request.setRequestURI(INTERNAL_URI);
        if (headerValue != null) {
            request.addHeader("X-Internal-Auth", headerValue);
        }
        return request;
    }

    private void assertUnauthorized(MockHttpServletResponse response) throws Exception {
        verify(chain, never()).doFilter(any(), any());
        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getHeader(HttpHeaders.CACHE_CONTROL)).isEqualTo("no-store");
        assertThat(response.getContentAsString())
                .contains("\"error\":\"unauthorized\"")
                .doesNotContain(SECRET);
    }
}
