package com.camping.tests.steps;

import com.camping.tests.clients.KioskClient;
import com.camping.tests.context.ScenarioContext;
import com.camping.tests.dto.PaymentConfirmRequest;
import com.camping.tests.dto.PaymentCreateRequest;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;

import java.io.IOException;
import java.util.List;

import static com.camping.tests.common.TestConstants.DEFAULT_PAYMENT_KEY;
import static org.assertj.core.api.Assertions.assertThat;

public class KioskSteps {

    private final ScenarioContext context;
    private final KioskClient kioskClient;

    public KioskSteps(ScenarioContext context) {
        this.context = context;
        this.kioskClient = new KioskClient();
    }

    @만약("키오스크 서비스의 {string}에 GET 요청을 보낸다")
    public void 키오스크_서비스에_GET요청을_보낸다(String requestUrl) {
        var response = kioskClient.get(requestUrl);
        context.setResponse(response);
    }

    @만약("키오스크로 상품 목록을 조회하면")
    public void 키오스크로_상품_목록을_조회하면() {
        var response = kioskClient.getProducts();
        context.setResponse(response);
    }

    @만약("키오스크에 결제 생성을 요청한다")
    public void 키오스크에_결제_생성을_요청한다() throws IOException {
        var request = PaymentCreateRequest.forCardPaymentWithDefaultItem();
        var response = kioskClient.createPayment(request);
        context.setResponse(response);
    }

    @그리고("키오스크에 결제 확정을 요청한다")
    public void 키오스크에_결제_확정을_요청한다() throws IOException {
        var request = PaymentConfirmRequest.defaultConfirm();
        var response = kioskClient.confirmPayment(request);
        context.setResponse(response);
    }

    @그리고("키오스크에 금액 {int}원으로 결제 확정을 요청한다")
    public void 키오스크에_금액_원으로_결제_확정을_요청한다(int amount) throws IOException {
        var request = PaymentConfirmRequest.withAmount(amount);
        var response = kioskClient.confirmPayment(request);
        context.setResponse(response);
    }

    @그러면("결제가 성공이어야 한다")
    public void 결제가_성공이어야_한다() {
        var response = context.getResponse();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getBoolean("success")).isTrue();
        assertThat(response.jsonPath().getString("message")).isEqualTo("결제 성공");
        assertThat(response.jsonPath().getString("transactionId")).isEqualTo(DEFAULT_PAYMENT_KEY);
        assertThat(response.jsonPath().getInt("paidAmount")).isEqualTo(10000);
    }

    @그러면("결제가 실패이어야 한다")
    public void 결제가_실패이어야_한다() {
        var response = context.getResponse();
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.jsonPath().getBoolean("success")).isFalse();
        assertThat(response.jsonPath().getString("transactionId")).isNull();
        assertThat(response.jsonPath().getString("message")).isEqualTo("결제 또는 확정 실패");
        assertThat(response.jsonPath().getInt("paidAmount")).isZero();
    }

    @그러면("상품 개수는 1개 이상이다")
    public void 상품_개수는_1개_이상이다() {
        List<Object> list = context.getResponse().jsonPath().getList(".");
        assertThat(list).isNotEmpty();
    }
}



