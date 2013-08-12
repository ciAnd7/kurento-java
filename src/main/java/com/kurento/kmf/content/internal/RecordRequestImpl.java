package com.kurento.kmf.content.internal;

import java.io.IOException;

import javax.servlet.AsyncContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.kurento.kmf.content.ContentException;
import com.kurento.kmf.content.RecordRequest;
import com.kurento.kmf.media.HttpEndPoint;
import com.kurento.kmf.media.MediaElement;
import com.kurento.kmf.media.MediaManager;
import com.kurento.kmf.media.MediaManagerFactory;
import com.kurento.kmf.media.MediaSink;
import com.kurento.kmf.media.MediaSrc;
import com.kurento.kmf.media.RecorderEndPoint;
import com.kurento.kms.api.MediaType;

public class RecordRequestImpl implements RecordRequest, StreamingProxyListener {
	private static final Logger log = LoggerFactory
			.getLogger(RecordRequestImpl.class);

	@Autowired
	private MediaManagerFactory mediaManagerFactory;

	@Autowired
	private StreamingProxy proxy;

	private AsyncContext asyncContext;
	private String contentId;
	private boolean redirect;

	RecordRequestImpl(AsyncContext asyncContext, String contentId,
			boolean redirect) {
		this.asyncContext = asyncContext;
		this.contentId = contentId;
		this.redirect = redirect;
	}

	@Override
	public String getContentId() {
		return contentId;
	}

	@Override
	public HttpServletRequest getHttpServletRequest() {
		return (HttpServletRequest) asyncContext.getRequest();
	}

	@Override
	public void record(String contentPath) throws ContentException {

		if (!((HttpServletRequest) asyncContext.getRequest()).isAsyncStarted()) {
			return;
		}

		HttpEndPoint httpEndPoint = null;
		RecorderEndPoint recorderEndPoint = null;

		try {
			MediaManager mediaManager = mediaManagerFactory.createMediaManager(
					null, 0, null);
			httpEndPoint = mediaManager.getHttpEndPoint();
			recorderEndPoint = mediaManager.getUriEndPoint(
					RecorderEndPoint.class, contentPath);
			MediaSrc videoMediaSrc = recorderEndPoint
					.getMediaSrc(MediaType.VIDEO).iterator().next();
			MediaSink videoMediaSink = httpEndPoint
					.getMediaSink(MediaType.VIDEO).iterator().next();
			MediaSrc audioMediaSrc = recorderEndPoint
					.getMediaSrc(MediaType.AUDIO).iterator().next();
			MediaSink audioMediaSink = httpEndPoint
					.getMediaSink(MediaType.AUDIO).iterator().next();
			videoMediaSrc.connect(videoMediaSink);
			audioMediaSrc.connect(audioMediaSink);

			HttpServletResponse response = (HttpServletResponse) asyncContext
					.getResponse();
			HttpServletRequest request = (HttpServletRequest) asyncContext
					.getRequest();

			if (redirect) {
				response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
				response.setHeader("Location", httpEndPoint.getUrl());
			} else {
				proxy.tunnelTransaction(request, response,
						httpEndPoint.getUrl(), this);
			}

		} catch (Throwable t) {
			// TODO when final KMS version is ready, perhaps it will be
			// necessary to release httpEndPoint and recorderEndPoint resources.
			throw new ContentException(t.getMessage(), t);
		} finally {
			// If this call is made asynchronous complete should be in the
			// continuation
			if (redirect) {
				asyncContext.complete();
			}
		}
	}

	@Override
	public void record(MediaElement element) throws ContentException {
		if (!((HttpServletRequest) asyncContext.getRequest()).isAsyncStarted()) {
			return;
		}

		HttpEndPoint httpEndPoint = null;

		try {
			// FIXME: mediaManager must be retrieved from received MediaElement
			MediaManager mediaManager = mediaManagerFactory.createMediaManager(
					null, 0, null);
			httpEndPoint = mediaManager.getHttpEndPoint();
			MediaSrc videoMediaSrc = element.getMediaSrc(MediaType.VIDEO)
					.iterator().next();
			MediaSink videoMediaSink = httpEndPoint
					.getMediaSink(MediaType.VIDEO).iterator().next();
			MediaSrc audioMediaSrc = element.getMediaSrc(MediaType.AUDIO)
					.iterator().next();
			MediaSink audioMediaSink = httpEndPoint
					.getMediaSink(MediaType.AUDIO).iterator().next();
			videoMediaSrc.connect(videoMediaSink);
			audioMediaSrc.connect(audioMediaSink);

			HttpServletResponse response = (HttpServletResponse) asyncContext
					.getResponse();
			HttpServletRequest request = (HttpServletRequest) asyncContext
					.getRequest();
			response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
			response.setHeader("Location", httpEndPoint.getUrl());

			if (redirect) {
				response.setStatus(HttpServletResponse.SC_TEMPORARY_REDIRECT);
				response.setHeader("Location", httpEndPoint.getUrl());
			} else {
				proxy.tunnelTransaction(request, response,
						httpEndPoint.getUrl(), this);
			}

		} catch (Throwable t) {
			// TODO: when final KMS version is ready, perhaps it will be
			// necessary to release httpEndPoint resources.
			throw new ContentException(t.getMessage(), t);
		} finally {
			// If this call is made asynchronous complete should be in the
			// continuation
			if (redirect) {
				asyncContext.complete();
			}
		}

	}

	@Override
	public void reject(int statusCode, String message) {
		if (!((HttpServletRequest) asyncContext.getRequest()).isAsyncStarted()) {
			return;
		}

		try {
			((HttpServletResponse) asyncContext.getResponse()).sendError(
					statusCode, message);
		} catch (IOException e) {
			log.error("Exception rejecting RecordRequest", e);
		} finally {
			asyncContext.complete();
		}
	}

	@Override
	public void onProxySuccess() {
		asyncContext.complete();
	}

	@Override
	public void onProxyError(String message) {
		asyncContext.complete();
		// TODO: Error handling
	}
}
