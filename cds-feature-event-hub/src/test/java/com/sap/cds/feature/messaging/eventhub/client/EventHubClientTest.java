package com.sap.cds.feature.messaging.eventhub.client;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.sap.cds.services.environment.CdsProperties;
import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;

class EventHubClientTest {
	final String authPath = "/oauth2/token";

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance().options(WireMockConfiguration.wireMockConfig().port(8031)).build();

	private ServiceBinding getClientManagementConfig() {
		CdsRuntime runtime = CdsRuntimeConfigurer.create().complete();
		return runtime.getEnvironment().getServiceBindings().filter(b -> ServiceBindingUtils.matches(b, null, "eventmesh-sap2sap-internal")).findFirst().orElse(null);
	}

	private String getAuthResponse() {
		return """
			{
			  "access_token": "",
			  "token_type": "bearer",
			  "expires_in": 43199,
			  "scope": "uaa.resource",
			  "jti": "ad2e0f52cba04531811a16a945639588"
			}\
			""";
	}

	@Test
	void testSendMessage() throws Exception {
		server.givenThat(WireMock.post(authPath).willReturn(WireMock.okJson(getAuthResponse())));
		server.givenThat(WireMock.post("/").withRequestBody(WireMock
			.equalToJson("{\"attr1\":\"value1\"}"))
				.withHeader("header1", WireMock.equalTo("value1"))
				.withHeader("ce-source", WireMock.equalTo("sourceValue")).willReturn(WireMock.okJson("{}")));

		EventHubClient client = new EventHubClient(getClientManagementConfig(), new CdsProperties.ConnectionPool());

		Map<String, Object> data = new HashMap<>();
		data.put("attr1", "value1");
		Map<String, Object> headers = new HashMap<>();
		headers.put("header1", "value1");
		headers.put("source", "sourceValue"); //will be converted to "ce-source" in the outgoing http call
		client.sendMessage(data, headers);
	}
}
