package com.sap.cds.feature.messaging.eventhub.client;

import com.sap.cds.services.runtime.CdsRuntime;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;
import com.sap.cds.services.utils.environment.ServiceBindingUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import org.junit.jupiter.api.Test;
import org.mockserver.client.MockServerClient;
import org.mockserver.integration.ClientAndServer;

import java.util.HashMap;
import java.util.Map;

import static org.mockserver.model.HttpRequest.request;
import static org.mockserver.model.HttpResponse.response;

class EventHubClientTest {
	private static final int port = 8031;
	final String authPath = "/oauth2/token";

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
		try (MockServerClient mockServer = ClientAndServer.startClientAndServer(port)) {
			mockServer.when(request().withMethod("POST").withPath(authPath)).respond(response().withBody(getAuthResponse()).withStatusCode(200));

			mockServer.when(request().withMethod("POST").
							withPath("/").withBody("{\"attr1\":\"value1\"}").
							withHeader("header1", "value1").
							withHeader("ce-source", "sourceValue")).
					respond(response().withStatusCode(200).withBody("{}"));

			EventHubClient client = new EventHubClient(getClientManagementConfig());

			Map<String, Object> data = new HashMap<>();
			data.put("attr1", "value1");
			Map<String, Object> headers = new HashMap<>();
			headers.put("header1", "value1");
			headers.put("source", "sourceValue"); //will be converted to "ce-source" in the outgoing http call
			client.sendMessage(data, headers);
		}
	}
}
