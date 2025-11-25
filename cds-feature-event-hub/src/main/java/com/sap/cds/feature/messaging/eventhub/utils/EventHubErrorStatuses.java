package com.sap.cds.feature.messaging.eventhub.utils;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ErrorStatuses;

public enum EventHubErrorStatuses implements ErrorStatus {

	MULTIPLE_EVENT_HUB_BINDINGS(50007027, "Multiple event-hub service bindings found: Only a single service binding for Event Hub is supported.", ErrorStatuses.SERVER_ERROR),
	EVENT_HUB_TENANT_CONTEXT_MISSING(50007028, "Missing tenant context to emit a message to Event Hub.", ErrorStatuses.SERVER_ERROR),
	EVENT_HUB_EMIT_MISSING_CE_SOURCE(50007029, "Event Hub service failed to emit, due to ceSource missing in the service binding.", ErrorStatuses.SERVER_ERROR),
	EVENT_HUB_EMIT_MISSING_SYSTEM_ID(50007030, "Event Hub service failed to emit, due to systemId missing in the service binding.", ErrorStatuses.SERVER_ERROR);

	private final int code;
	private final String description;
	private final ErrorStatus httpError;

	private EventHubErrorStatuses(int code, String description, ErrorStatus httpError) {
		this.code = code;
		this.description = description;
		this.httpError = httpError;
	}

	@Override
	public String getCodeString() {
		return String.valueOf(code);
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public int getHttpStatus() {
		return httpError.getHttpStatus();
	}
}
