package com.camping.tests.steps;

import com.camping.tests.config.TestConfig;
import com.camping.tests.dto.ConfirmResponse;
import com.camping.tests.dto.CreateResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import io.cucumber.java.After;
import io.cucumber.java.Before;
import io.cucumber.java.ko.먼저;

import static com.camping.tests.common.TestConstants.DEFAULT_ORDER_ID;
import static com.camping.tests.common.TestConstants.DEFAULT_PAYMENT_KEY;
import static com.camping.tests.common.TestConstants.Payment.PAYMENTS_CONFIRM_ENDPOINT;
import static com.camping.tests.common.TestConstants.Payment.PAYMENTS_ENDPOINT;
import static com.github.tomakehurst.wiremock.client.WireMock.*;

public class MockPaymentSteps {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setupWireMock() {
        WireMock.configureFor(TestConfig.getPaymentMockHost(), TestConfig.getPaymentMockPort());
        WireMock.reset();
    }

    @After
    public void teardownWireMock() {
        WireMock.reset();
    }

    @먼저("결제 서버가 정상적으로 응답 가능한 상태이다")
    public void 결제_서버가_정상적으로_응답_가능한_상태이다() throws JsonProcessingException {
        var createResponse = new CreateResponse(DEFAULT_PAYMENT_KEY, DEFAULT_ORDER_ID, "CREATED");
        stubFor(post(urlEqualTo(PAYMENTS_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(createResponse))));

        var confirmResponse = new ConfirmResponse(DEFAULT_PAYMENT_KEY, DEFAULT_ORDER_ID, "CARD", "2026-02-02T10:00:00Z", 10000, "APPROVED", new ConfirmResponse.Receipt("http://receipt.url/123"));
        stubFor(post(urlEqualTo(PAYMENTS_CONFIRM_ENDPOINT))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(confirmResponse))));
    }

    @먼저("결제 서버가 {string} 에러를 반환하도록 설정한다")
    public void 결제_서버가_에러를_반환하도록_설정한다(String errorType) throws JsonProcessingException {
        if ("잔액부족".equals(errorType)) {
            var confirmResponse = new ConfirmResponse(DEFAULT_PAYMENT_KEY, DEFAULT_ORDER_ID, "CARD", null, 0, "REJECTED", null);
            stubFor(post(urlEqualTo(PAYMENTS_CONFIRM_ENDPOINT))
                    .willReturn(aResponse()
                            .withStatus(400)
                            .withHeader("Content-Type", "application/json")
                            .withBody(objectMapper.writeValueAsString(confirmResponse))));
        }
    }
}
