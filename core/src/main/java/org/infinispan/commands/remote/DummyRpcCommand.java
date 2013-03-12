/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.commands.remote;

import org.infinispan.context.InvocationContext;
import org.infinispan.remoting.transport.Address;

public class DummyRpcCommand implements CacheRpcCommand {
	protected final String cacheName;

	private Address origin;

	public static final byte COMMAND_ID = 124;

	private Object key;

	private DummyRpcCommand( ) {
cacheName=null;
	   }
	
	public DummyRpcCommand(String cacheName) {
		this.cacheName = cacheName;
	}

	public DummyRpcCommand(String cacheName, Object key) {
		this.cacheName = cacheName;
		this.key = key;
	}

	public Object getKey() {
		return key;
	}

	public void setKey(Object key) {
		this.key = key;
	}

	@Override
	public Object[] getParameters() {
		return new Object[] { key };
	}

	@Override
	public void setParameters(int commandId, Object[] args) {
		int i = 0;
		key = args[i++];
	}

	@Override
	public byte getCommandId() {
		return COMMAND_ID;
	}

	@Override
	public Address getOrigin() {
		return origin;
	}

	@Override
	public void setOrigin(Address origin) {
		this.origin = origin;
	}

	@Override
	public Object perform(InvocationContext ctx) throws Throwable {
		return new Integer(2);
	}

	@Override
	public boolean isReturnValueExpected() {
		return true;
	}

	@Override
	public String getCacheName() {
		return this.cacheName;
	}

	public void initialize() {
		// TODO Auto-generated method stub
		
	}
}
