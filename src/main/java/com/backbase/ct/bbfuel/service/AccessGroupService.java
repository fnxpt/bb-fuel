package com.backbase.ct.bbfuel.service;

import static com.backbase.ct.bbfuel.data.AccessGroupsDataGenerator.generateDataGroupPostRequestBody;
import static com.backbase.ct.bbfuel.data.AccessGroupsDataGenerator.generateFunctionGroupPostRequestBody;
import static com.backbase.ct.bbfuel.util.ResponseUtils.isBadRequestException;
import static org.apache.http.HttpStatus.SC_CREATED;
import static org.apache.http.HttpStatus.SC_MULTI_STATUS;

import com.backbase.ct.bbfuel.client.accessgroup.AccessGroupIntegrationRestClient;
import com.backbase.ct.bbfuel.client.accessgroup.AccessGroupPresentationRestClient;
import com.backbase.ct.bbfuel.client.accessgroup.ServiceAgreementsIntegrationRestClient;
import com.backbase.dbs.accesscontrol.client.v2.model.FunctionGroupItem;
import com.backbase.dbs.accesscontrol.client.v2.model.DataGroupItem;
import com.backbase.integration.accessgroup.rest.spec.v2.accessgroups.BatchResponseItem;
import com.backbase.integration.accessgroup.rest.spec.v2.accessgroups.function.Permission;
import com.backbase.integration.accessgroup.rest.spec.v2.accessgroups.functiongroups.FunctionGroupPostResponseBody;
import io.restassured.response.Response;
import java.util.List;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AccessGroupService {

    private final AccessGroupPresentationRestClient accessGroupPresentationRestClient;

    private final AccessGroupIntegrationRestClient accessGroupIntegrationRestClient;

    private final ServiceAgreementsIntegrationRestClient serviceAgreementsIntegrationRestClient;

    public String ingestFunctionGroup(String externalServiceAgreementId, String functionGroupName, String functionGroupType,
        List<Permission> permissions) {
        Response response = accessGroupIntegrationRestClient.ingestFunctionGroup(
            generateFunctionGroupPostRequestBody(externalServiceAgreementId, functionGroupName, functionGroupType, permissions));

        if (isBadRequestException(response, "Function Group with given name already exists")) {

            String internalServiceAgreementId = serviceAgreementsIntegrationRestClient
                .retrieveServiceAgreementByExternalId(externalServiceAgreementId)
                .getId();

            // Combination of function group name and service agreement is unique in the system
            FunctionGroupItem existingFunctionGroup = accessGroupPresentationRestClient
                .retrieveFunctionGroupsByServiceAgreement(internalServiceAgreementId)
                .stream()
                .filter(functionGroupsGetResponseBody -> functionGroupName.equals(functionGroupsGetResponseBody.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("No existing function group found by service agreement [%s] and name [%s]", externalServiceAgreementId, functionGroupName)));

            log.info("Function group \"{}\" [{}] already exists, skipped ingesting this function group",
                existingFunctionGroup.getName(), existingFunctionGroup.getId());

            return existingFunctionGroup.getId();

        } else {
            String functionGroupId = response.then()
                .statusCode(SC_CREATED)
                .extract()
                .as(FunctionGroupPostResponseBody.class)
                .getId();

            log.info("Function group \"{}\" [{}] ingested under service agreement [{}])",
                functionGroupName, functionGroupId, externalServiceAgreementId);

            return functionGroupId;
        }
    }

    public String ingestDataGroup(String externalServiceAgreementId, String dataGroupName,
        String type, List<String> internalArrangementIds) {
        Response response = accessGroupIntegrationRestClient.ingestDataGroup(
            generateDataGroupPostRequestBody(externalServiceAgreementId, dataGroupName, type, internalArrangementIds));

        if (isBadRequestException(response, "Function Group with given name already exists")) {

            String internalServiceAgreementId = serviceAgreementsIntegrationRestClient
                .retrieveServiceAgreementByExternalId(externalServiceAgreementId)
                .getId();

            // Combination of data group name and service agreement is unique in the system
            DataGroupItem existingDataGroup = accessGroupPresentationRestClient
                .retrieveDataGroupsByServiceAgreement(internalServiceAgreementId)
                .stream()
                .filter(dataGroupsGetResponseBody -> dataGroupName.equals(dataGroupsGetResponseBody.getName()))
                .findFirst()
                .orElseThrow(() -> new RuntimeException(String.format("No existing data group found by service agreement [%s] and name [%s]", externalServiceAgreementId, dataGroupName)));

            return existingDataGroup.getId();

        } else {
            String dataGroupId = Stream.of(response.then()
                .statusCode(SC_MULTI_STATUS)
                .extract()
                .as(BatchResponseItem[].class))
                    .filter(batchResponseItem -> StringUtils.isNotEmpty(batchResponseItem.getResourceId()))
                    .findFirst().get().getResourceId();

            log.info("Data group \"{}\" [{}] ingested under service agreement [{}]",
                dataGroupName, dataGroupId, externalServiceAgreementId);

            return dataGroupId;
        }
    }
}
