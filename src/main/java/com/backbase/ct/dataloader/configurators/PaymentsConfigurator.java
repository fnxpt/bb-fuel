package com.backbase.ct.dataloader.configurators;

import static com.backbase.ct.dataloader.data.CommonConstants.PAYMENT_TYPE_SEPA_CREDIT_TRANSFER;
import static com.backbase.ct.dataloader.data.CommonConstants.PAYMENT_TYPE_US_DOMESTIC_WIRE;
import static org.apache.http.HttpStatus.SC_ACCEPTED;

import com.backbase.ct.dataloader.clients.accessgroup.UserContextPresentationRestClient;
import com.backbase.ct.dataloader.clients.common.LoginRestClient;
import com.backbase.ct.dataloader.clients.payment.PaymentOrderPresentationRestClient;
import com.backbase.ct.dataloader.clients.productsummary.ProductSummaryPresentationRestClient;
import com.backbase.ct.dataloader.data.CommonConstants;
import com.backbase.ct.dataloader.data.PaymentsDataGenerator;
import com.backbase.ct.dataloader.utils.CommonHelpers;
import com.backbase.ct.dataloader.utils.GlobalProperties;
import com.backbase.dbs.presentation.paymentorder.rest.spec.v2.paymentorders.InitiatePaymentOrder;
import com.backbase.presentation.productsummary.rest.spec.v2.productsummary.ArrangementsByBusinessFunctionGetResponseBody;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PaymentsConfigurator {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentsConfigurator.class);
    private static GlobalProperties globalProperties = GlobalProperties.getInstance();

    private Random random = new Random();
    private final PaymentOrderPresentationRestClient paymentOrderPresentationRestClient;
    private final LoginRestClient loginRestClient;
    private final ProductSummaryPresentationRestClient productSummaryPresentationRestClient;
    private final UserContextPresentationRestClient userContextPresentationRestClient;

    public void ingestPaymentOrders(String externalUserId) {
        final List<String> PAYMENT_TYPES = Arrays
            .asList(PAYMENT_TYPE_SEPA_CREDIT_TRANSFER, PAYMENT_TYPE_US_DOMESTIC_WIRE);

        loginRestClient.login(externalUserId, externalUserId);
        userContextPresentationRestClient.selectContextBasedOnMasterServiceAgreement();
        List<ArrangementsByBusinessFunctionGetResponseBody> sepaCtArrangements = productSummaryPresentationRestClient
            .getSepaCtArrangements();
        List<ArrangementsByBusinessFunctionGetResponseBody> usDomesticWireArrangements = productSummaryPresentationRestClient
            .getUsDomesticWireArrangements();

        int randomAmount = CommonHelpers
            .generateRandomNumberInRange(globalProperties.getInt(CommonConstants.PROPERTY_PAYMENTS_MIN),
                globalProperties.getInt(CommonConstants.PROPERTY_PAYMENTS_MAX));
        IntStream.range(0, randomAmount).parallel().forEach(randomNumber -> {
            String paymentType = PAYMENT_TYPES.get(random.nextInt(PAYMENT_TYPES.size()));
            ArrangementsByBusinessFunctionGetResponseBody randomArrangement;

            if (PAYMENT_TYPE_SEPA_CREDIT_TRANSFER.equals(paymentType)) {
                randomArrangement = sepaCtArrangements.get(random.nextInt(sepaCtArrangements.size()));
            } else {
                randomArrangement = usDomesticWireArrangements.get(random.nextInt(usDomesticWireArrangements.size()));
            }

            InitiatePaymentOrder initiatePaymentOrder = PaymentsDataGenerator
                .generateInitiatePaymentOrder(randomArrangement.getId(), paymentType);
            paymentOrderPresentationRestClient.initiatePaymentOrder(initiatePaymentOrder)
                .then()
                .statusCode(SC_ACCEPTED);

            LOGGER.info("Payment order ingested for debtor account [{}] for user [{}]",
                initiatePaymentOrder.getDebtorAccount().getIdentification().getIdentification(), externalUserId);
        });
    }
}
