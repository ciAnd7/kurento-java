/*
 * (C) Copyright 2013 Kurento (http://kurento.org/)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl-2.1.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 */
package com.kurento.kmf.media.events.internal;

import static com.kurento.kms.thrift.api.KmsMediaPlayerEndPointTypeConstants.EVENT_EOS;

import com.kurento.kmf.media.events.EndOfStreamEvent;
import com.kurento.kmf.media.internal.ProvidesMediaEvent;
import com.kurento.kms.thrift.api.KmsMediaEvent;

/**
 * Default implementation of {@link EndOfStreamEvent}
 * 
 * @author Ivan Gracia (igracia@gsyc.es)
 * 
 */
@ProvidesMediaEvent(type = EVENT_EOS)
public class EndOfStreamEventImpl extends VoidMediaEvent implements
		EndOfStreamEvent {

	/**
	 * @param event
	 *            The Thrift event received form the server.
	 */
	public EndOfStreamEventImpl(KmsMediaEvent event) {
		super(event);
	}

}