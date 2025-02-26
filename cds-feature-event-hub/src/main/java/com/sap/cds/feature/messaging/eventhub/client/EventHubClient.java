package com.sap.cds.feature.messaging.eventhub.client;

import java.io.IOException;
import java.util.Map;

import com.sap.cds.integration.cloudsdk.rest.client.JsonRestClient;
import com.sap.cds.services.environment.CdsProperties.ConnectionPool;
import com.sap.cds.services.messaging.utils.CloudEventUtils;
import com.sap.cloud.environment.servicebinding.api.ServiceBinding;
import com.sap.cloud.sdk.cloudplatform.connectivity.OnBehalfOf;
import com.sap.cloud.sdk.cloudplatform.connectivity.ServiceBindingDestinationOptions;

public class EventHubClient extends JsonRestClient {

	// Maps CloudEvents headers to headers expected by Event Hub
	private static final Map<String, String> HEADER_MAPPINGS = Map.of(
			CloudEventUtils.KEY_ID, "ce-id",
			CloudEventUtils.KEY_SPECVERSION, "ce-specversion",
			CloudEventUtils.KEY_DATACONTENTTYPE, "Content-Type",
			CloudEventUtils.KEY_TIME, "ce-time",
			CloudEventUtils.KEY_TYPE, "ce-type",
			CloudEventUtils.KEY_SOURCE, "ce-source"
	);

	public EventHubClient(ServiceBinding binding, ConnectionPool connectionPool) {
		super(ServiceBindingDestinationOptions
						.forService(binding)
						.onBehalfOf(OnBehalfOf.TECHNICAL_USER_PROVIDER)
						.build(),
				connectionPool);
	}

	public void sendMessage(Map<String, Object> message, Map<String, Object> headers) throws IOException {
		postRequest("/", CloudEventUtils.toJson(message), convertCloudEventHeaders(headers));
	}

	private Map<String, Object> convertCloudEventHeaders(Map<String, Object> headers) {
		HEADER_MAPPINGS.forEach((ceHeader, ebHeader) -> {
			if (headers.containsKey(ceHeader)) {
				headers.put(ebHeader, headers.remove(ceHeader));
			}
		});
		return headers;
	}

}
