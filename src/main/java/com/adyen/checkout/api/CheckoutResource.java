package com.adyen.checkout.api;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;
import com.adyen.Client;
import com.adyen.enums.Environment;
import com.adyen.model.checkout.*;
import com.adyen.service.checkout.PaymentsApi;
import com.adyen.service.exception.ApiException;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * REST controller for using Adyen checkout API
 */
@RestController
@RequestMapping("/api")
public class CheckoutResource {
    private final Logger log = LoggerFactory.getLogger(CheckoutResource.class);

    @Value("${ADYEN_MERCHANT_ACCOUNT}")
    private String merchantAccount;

    private final PaymentsApi paymentsApi;

    public CheckoutResource(@Value("${ADYEN_API_KEY}") String apiKey) {
        var client = new Client(apiKey, Environment.TEST);
        this.paymentsApi = new PaymentsApi(client);
    }

    /**
     * {@code POST  /getPaymentMethods} : Get valid payment methods.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the paymentMethods response.
     * @throws IOException  from Adyen API.
     * @throws ApiException from Adyen API.
     */
    @PostMapping("/getPaymentMethods")
    public ResponseEntity<PaymentMethodsResponse> paymentMethods() throws IOException, ApiException {
        var paymentMethodsRequest = new PaymentMethodsRequest();
        paymentMethodsRequest.setMerchantAccount(merchantAccount);
        paymentMethodsRequest.setChannel(PaymentMethodsRequest.ChannelEnum.WEB);

        log.info("REST request to get Adyen payment methods {}", paymentMethodsRequest);
        PaymentMethodsResponse response = paymentsApi.paymentMethods(paymentMethodsRequest);
        log.info(response.toJson());
        return ResponseEntity.ok()
            .body(response);
    }

    /**
     * {@code POST  /initiatePayment} : Make a payment.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the paymentMethods response.
     * @throws IOException  from Adyen API.
     * @throws ApiException from Adyen API.
     */
    @PostMapping("/initiatePayment")
    public ResponseEntity<PaymentResponse> payments(@RequestBody String bodyRequest, HttpServletRequest request) throws IOException, ApiException {
        log.info(bodyRequest);
        // gson 
        Gson gson = new Gson();
        JsonObject obj = gson.fromJson(bodyRequest, JsonObject.class);
        com.google.gson.JsonObject method = obj.getAsJsonObject("paymentMethod");
        CheckoutPaymentMethod paymentMethod = CheckoutPaymentMethod.fromJson(method.toString());
        log.info(paymentMethod.toString());

        String type = method.getAsJsonPrimitive("type").toString();
        log.info(type);

        BrowserInfo browserInfo;
        try {
            JsonObject obj2 = gson.fromJson(bodyRequest, JsonObject.class);
            com.google.gson.JsonObject browser = obj2.getAsJsonObject("browserInfo");
            browserInfo = BrowserInfo.fromJson(browser.toString());
        } catch (Exception exception){
            browserInfo = new BrowserInfo();
        }

        var paymentRequest = new PaymentRequest();
        paymentRequest.setMerchantAccount(merchantAccount); // required
        paymentRequest.setChannel(PaymentRequest.ChannelEnum.WEB); // required

        var amount = new Amount()
            .currency(findCurrency(type))
            .value(1000L); // value is 10€ in minor units
        paymentRequest.setAmount(amount);

        var orderRef = UUID.randomUUID().toString();
        paymentRequest.setReference(orderRef); // required
        // required for 3ds2 redirect flow
        paymentRequest.setReturnUrl("http://localhost:8080/api/handleShopperRedirect?orderRef=" + orderRef);

        // required for 3ds2 native flow
        paymentRequest.setAdditionalData(Collections.singletonMap("allow3DS2", "true"));
        // required for 3ds2 native flow
        paymentRequest.setOrigin("http://localhost:8080");
        // required for 3ds2
        paymentRequest.setBrowserInfo(browserInfo);
        // required by some issuers for 3ds2
        paymentRequest.setShopperIP(request.getRemoteAddr());

        paymentRequest.setPaymentMethod(paymentMethod);

        //var type2 = body.getPaymentMethod().getActualInstance().getClass().getTypeName();
        // required for Klarna
        if (type.contains("klarna")) {
            paymentRequest.setCountryCode("DE");
            paymentRequest.setShopperReference("1234");
            paymentRequest.setShopperEmail("youremail@email.com");
            paymentRequest.setShopperLocale("en_US");
            var lineItems = new ArrayList<LineItem>();
            lineItems.add(
                new LineItem().quantity(1L).amountExcludingTax(331L).taxPercentage(2100L).description("Sunglasses").id("Item 1").taxAmount(69L).amountIncludingTax(400L)
            );
            lineItems.add(
                new LineItem().quantity(2L).amountExcludingTax(248L).taxPercentage(2100L).description("Headphones").id("Item 2").taxAmount(52L).amountIncludingTax(300L)
            );
            paymentRequest.setLineItems(lineItems);
        } else if (type.contains("paypal")) {
            paymentRequest.setCountryCode("US");
        }

        log.info("REST request to make Adyen payment {}", paymentRequest);
        var response = paymentsApi.payments(paymentRequest);
        log.info(response.toJson());
        return ResponseEntity.ok()
            .body(response);
    }

    /**
     * {@code POST  /submitAdditionalDetails} : Make a payment.
     *
     * @return the {@link ResponseEntity} with status {@code 200 (Ok)} and with body the paymentMethods response.
     * @throws IOException  from Adyen API.
     * @throws ApiException from Adyen API.
     */
    @PostMapping("/submitAdditionalDetails")
    public ResponseEntity<PaymentDetailsResponse> payments(@RequestBody DetailsRequest detailsRequest) throws IOException, ApiException {
        log.info("REST request to make Adyen payment details {}", detailsRequest);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        return ResponseEntity.ok()
            .body(response);
    }

    /**
     * {@code GET  /handleShopperRedirect} : Handle redirect during payment.
     *
     * @return the {@link RedirectView} with status {@code 302}
     * @throws IOException  from Adyen API.
     * @throws ApiException from Adyen API.
     */
    @GetMapping("/handleShopperRedirect")
    public RedirectView redirect(@RequestParam(required = false) String payload, @RequestParam(required = false) String redirectResult, @RequestParam String orderRef) throws IOException, ApiException {
        log.info(redirectResult);
        log.info(payload);
        log.info(orderRef);

        var detailsRequest = new DetailsRequest();
        if (redirectResult != null && !redirectResult.isEmpty()) {
            PaymentCompletionDetails paymentCompletionDetails = new PaymentCompletionDetails();
            paymentCompletionDetails.setRedirectResult(redirectResult);
            detailsRequest.setDetails(paymentCompletionDetails);
        } else if (payload != null && !payload.isEmpty()) {
            PaymentCompletionDetails paymentCompletionDetails2 = new PaymentCompletionDetails();
            paymentCompletionDetails2.setPayload(payload);
            detailsRequest.setDetails(paymentCompletionDetails2);
        }

        return getRedirectView(detailsRequest);
    }

    private RedirectView getRedirectView(final DetailsRequest detailsRequest) throws ApiException, IOException {
        log.info("REST request to handle payment redirect {}", detailsRequest);
        var response = paymentsApi.paymentsDetails(detailsRequest);
        var redirectURL = "/result/";
        switch (response.getResultCode()) {
            case AUTHORISED:
                redirectURL += "success";
                break;
            case PENDING:
            case RECEIVED:
                redirectURL += "pending";
                break;
            case REFUSED:
                redirectURL += "failed";
                break;
            default:
                redirectURL += "error";
                break;
        }
        return new RedirectView(redirectURL + "?reason=" + response.getResultCode());
    }

    /* ################# UTILS ###################### */
    private String findCurrency(String type) {
        switch (type) {
            case "paypal":
            case "ach":
                return "USD";
            case "wechatpayqr":
            case "alipay":
                return "CNY";
            case "dotpay":
                return "PLN";
            case "boletobancario":
            case "boletobancario_santander":
                return "BRL";
            default:
                return "EUR";
        }
    }
    /* ################# end UTILS ###################### */
}
