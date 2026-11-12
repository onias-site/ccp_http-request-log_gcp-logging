package com.ccp.implementations.http.request.log.gcp.logging;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.ccp.constants.CcpOtherConstants;
import com.ccp.decorators.CcpJsonFieldName;
import com.ccp.decorators.CcpJsonRepresentation;
import com.ccp.especifications.http.CcpHttpMethods;
import com.ccp.especifications.http.CcpHttpRequestLog;
import com.google.api.gax.paging.Page;
import com.google.cloud.logging.HttpRequest;
import com.google.cloud.logging.LogEntry;
import com.google.cloud.logging.Logging;
import com.google.cloud.logging.Logging.EntryListOption;
import com.google.cloud.logging.Logging.SortingField;
import com.google.cloud.logging.Logging.SortingOrder;
import com.google.cloud.logging.LoggingOptions;

/**
 * {@code CcpHttpRequestLog} implementation over the request log that Cloud Run writes for each service
 * ({@code run.googleapis.com/requests}), read with the Cloud Logging SDK. The credentials and the GCP project are the
 * application default ones: the service account of the environment, or {@code GOOGLE_APPLICATION_CREDENTIALS} on a
 * developer machine. The account needs {@code roles/logging.viewer} on the project.
 */
class GcpLoggingHttpRequestLog implements CcpHttpRequestLog {

	/** The single instance: the class keeps no state. */
	static final GcpLoggingHttpRequestLog INSTANCE = new GcpLoggingHttpRequestLog();

	private GcpLoggingHttpRequestLog() {}

	/** The texts of the filter of Cloud Logging. */
	enum Filter implements CcpJsonFieldName {
		/** The filter: service, method, status, URL and moment, in that order. */
		template {
			public String getValue() {
				return "resource.type=\"cloud_run_revision\" AND log_id(\"run.googleapis.com/requests\")"
						+ " AND resource.labels.service_name=\"%s\" AND httpRequest.requestMethod=\"%s\""
						+ " AND httpRequest.status=%d AND httpRequest.requestUrl=~\"%s\" AND timestamp>=\"%s\"";
			}
		}
	}

	/** How many entries each page of Cloud Logging brings. */
	private static final int PAGE_SIZE = 1000;

	/**
	 * Reads every page of the matching entries of the request log.
	 * @param serviceName the Cloud Run service
	 * @param method the HTTP method of the requests
	 * @param pathRegex a regular expression that the URL must contain
	 * @param httpStatus the status answered
	 * @param startTimeInMillis the moment, inclusive
	 * @return one JSON for each request, oldest first
	 */
	public List<CcpJsonRepresentation> getRequestsSince(String serviceName, CcpHttpMethods method, String pathRegex, int httpStatus, long startTimeInMillis) {
		String filter = this.getFilter(serviceName, method, pathRegex, httpStatus, startTimeInMillis);

		EntryListOption filterOption = EntryListOption.filter(filter);
		EntryListOption sortOption = EntryListOption.sortOrder(SortingField.TIMESTAMP, SortingOrder.ASCENDING);
		EntryListOption pageSizeOption = EntryListOption.pageSize(PAGE_SIZE);

		LoggingOptions loggingOptions = LoggingOptions.getDefaultInstance();
		List<CcpJsonRepresentation> requests = new ArrayList<>();
		try (Logging logging = loggingOptions.getService()) {
			Page<LogEntry> firstPage = logging.listLogEntries(filterOption, sortOption, pageSizeOption);
			Iterable<LogEntry> entries = firstPage.iterateAll();
			for (LogEntry entry : entries) {
				CcpJsonRepresentation request = this.toJson(entry);
				requests.add(request);
			}
		} catch (Exception e) {
			CcpErrorGcpLoggingRead error = new CcpErrorGcpLoggingRead(e);
			throw error;
		}
		return requests;
	}

	/**
	 * The filter of Cloud Logging: the request log of the Cloud Run service, by method, status, URL and moment.
	 * @param serviceName the Cloud Run service
	 * @param method the HTTP method of the requests
	 * @param pathRegex a regular expression that the URL must contain
	 * @param httpStatus the status answered
	 * @param startTimeInMillis the moment, inclusive
	 * @return the filter
	 */
	String getFilter(String serviceName, CcpHttpMethods method, String pathRegex, int httpStatus, long startTimeInMillis) {
		Instant startTime = Instant.ofEpochMilli(startTimeInMillis);
		String startTimeText = startTime.toString();
		String methodName = method.name();
		String filterTemplate = Filter.template.getValue();
		String filter = String.format(filterTemplate, serviceName, methodName, httpStatus, pathRegex, startTimeText);
		return filter;
	}

	/**
	 * Copies the fields of the request kept by the entry.
	 * @param entry the entry of the request log
	 * @return the request, without the fields the entry did not keep
	 */
	CcpJsonRepresentation toJson(LogEntry entry) {
		Instant timestamp = entry.getInstantTimestamp();
		long timestampInMillis = timestamp.toEpochMilli();
		CcpJsonRepresentation request = CcpOtherConstants.EMPTY_JSON.put(Fields.timestamp, timestampInMillis);
		HttpRequest httpRequest = entry.getHttpRequest();
		// third party: an entry of the request log always carries the request, but the SDK does not promise it
		if(httpRequest == null) {
			return request;
		}
		String url = httpRequest.getRequestUrl();
		String userAgent = httpRequest.getUserAgent();
		String ip = httpRequest.getRemoteIp();
		CcpJsonRepresentation withUrl = url == null ? request : request.put(Fields.url, url);
		CcpJsonRepresentation withUserAgent = userAgent == null ? withUrl : withUrl.put(Fields.userAgent, userAgent);
		CcpJsonRepresentation withIp = ip == null ? withUserAgent : withUserAgent.put(Fields.ip, ip);
		return withIp;
	}

	/** Raised when the request log cannot be read: a partial list is never returned. */
	@SuppressWarnings("serial")
	private static class CcpErrorGcpLoggingRead extends RuntimeException {
		/**
		 * Wraps the cause.
		 * @param cause the original failure
		 */
		private CcpErrorGcpLoggingRead(Throwable cause) {
			super(cause);
		}
	}
}
