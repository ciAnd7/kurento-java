/**
 * This file is generated with Kurento ktool-rom-processor.
 * Please don't edit. Changes should go to kms-interface-rom and
 * ktool-rom-processor templates.
 */
package org.kurento.client.events;

import org.kurento.client.MediaObject;
import org.kurento.client.internal.server.Param;

/**
 * 
 * Event generated when an object exits a window.
 * 
 **/
public class WindowOutEvent extends MediaEvent {

	/**
	 * 
	 * Opaque String indicating the id of the window entered
	 * 
	 **/
	private String windowId;

	/**
	 * 
	 * Event generated when an object exits a window.
	 * 
	 * @param source
	 *            Object that raised the event
	 * @param type
	 *            Type of event that was raised
	 * @param windowId
	 *            Opaque String indicating the id of the window entered
	 * 
	 **/
	public WindowOutEvent(@Param("source") MediaObject source,
			@Param("type") String type, @Param("windowId") String windowId) {
		super(source, type);
		this.windowId = windowId;
	}

	/**
	 * 
	 * Getter for the windowId property
	 * 
	 * @return Opaque String indicating the id of the window entered *
	 **/
	public String getWindowId() {
		return windowId;
	}

	/**
	 * 
	 * Setter for the windowId property
	 * 
	 * @param windowId
	 *            Opaque String indicating the id of the window entered
	 * 
	 **/
	public void setWindowId(String windowId) {
		this.windowId = windowId;
	}

}