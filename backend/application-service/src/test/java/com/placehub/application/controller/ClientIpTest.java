package com.placehub.application.controller;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Security review F3: the consent IP comes from X-Forwarded-For only when the gateway attests the request. */
class ClientIpTest {

    static final String TOKEN = "gateway-attest-token";

    static MockHttpServletRequest request(String xff, String attest) {
        MockHttpServletRequest r = new MockHttpServletRequest();
        r.setRemoteAddr("172.18.0.9");
        if (xff != null) r.addHeader("X-Forwarded-For", xff);
        if (attest != null) r.addHeader(ApplicationController.GATEWAY_HEADER, attest);
        return r;
    }

    @Test
    void attestedRequestsUseTheAddressTheGatewayAppended() {
        assertThat(ApplicationController.clientIp(request("6.6.6.6, 203.0.113.7", TOKEN), TOKEN)).isEqualTo("203.0.113.7");
    }

    @Test
    void unattestedOrForgedRequestsIgnoreXForwardedFor() {
        assertThat(ApplicationController.clientIp(request("6.6.6.6", null), TOKEN)).isEqualTo("172.18.0.9");
        assertThat(ApplicationController.clientIp(request("6.6.6.6", "guess"), TOKEN)).isEqualTo("172.18.0.9");
        assertThat(ApplicationController.clientIp(request("6.6.6.6", TOKEN), "")).isEqualTo("172.18.0.9");
    }
}
