package com.sap.cds.feature.messaging.eventhub.utils;

import com.sap.cds.services.ErrorStatus;
import com.sap.cds.services.ErrorStatuses;

public enum EventHubErrorStatuses implements ErrorStatus {

	EVENT_HUB_EMIT_FAILED(50007026, "Event Hub service in single tenant plan does not support to emit events.", ErrorStatuses.SERVER_ERROR),
	MULTIPLE_EVENT_HUB_BINDINGS(50007027, "Multiple event-hub service bindings found: Only a single service binding for Event Hub is supported.", ErrorStatuses.SERVER_ERROR),
	EVENT_HUB_TENANT_CONTEXT_MISSING(50007028, "Missing tenant context to emit a message to Event Hub.", ErrorStatuses.SERVER_ERROR);

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
