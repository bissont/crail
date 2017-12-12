/*
 * Crail: A Multi-tiered Distributed Direct Access File System
 *
 * Author: Patrick Stuedi <stu@zurich.ibm.com>
 *
 * Copyright (C) 2016, IBM Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.ibm.crail.metadata;

import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import org.slf4j.Logger;

import com.ibm.crail.utils.CrailUtils;

public class DataNodeInfo {
	private static final Logger LOG = CrailUtils.getLogger();
	public static final int CSIZE = 20;
	
	private int storageType;
	private int storageClass;
	private int locationClass;
	private byte[] ipAddress;
	private int port;	
	
	private long key;
	
	public DataNodeInfo(){
		this.storageType = 0;
		this.storageClass = 0;
		this.locationClass = 0;
		this.ipAddress = new byte[4];
		this.port = 0;		
		this.key = 0;
	}
	
	public DataNodeInfo(int storageType, int storageClass, int locationClass, byte[] ipAddress, int port){
		this();
		
		this.storageType = storageType;
		this.storageClass = storageClass;
		this.locationClass = locationClass;
		for (int i = 0; i < ipAddress.length; i++){
			this.ipAddress[i] = ipAddress[i];
		}
		this.port = port;
	}	
	
	void setDataNodeInfo(DataNodeInfo info) {
		this.storageType = info.getStorageType();
		this.storageClass = info.getStorageClass();
		this.locationClass = info.getLocationClass();
		for (int i = 0; i < ipAddress.length; i++){
			this.ipAddress[i] = info.getIpAddress()[i];
		}		
		this.port = info.getPort();
	}

	public int write(ByteBuffer buffer){
		buffer.putInt(storageType);
		buffer.putInt(storageClass);
		buffer.putInt(locationClass);
		buffer.put(ipAddress);
		buffer.putInt(port);		
		return CSIZE;
	}
	
	public void update(ByteBuffer buffer) throws UnknownHostException {
		this.storageType = buffer.getInt();
		this.storageClass = buffer.getInt();
		this.locationClass = buffer.getInt();
		buffer.get(ipAddress);
		this.port = buffer.getInt();
	}	
	
	public byte[] getIpAddress() {
		return ipAddress;
	}

	public int getPort() {
		return port;
	}

	public int getLocationClass() {
		return locationClass;
	}

	public int getStorageType() {
		return storageType;
	}
	
	public int getStorageClass() {
		return storageClass;
	}	

	public static long calcKey (byte[] ip, int portNum) {
		int a = java.util.Arrays.hashCode(ip);
		long localKey = (((long)a) << 32) | (portNum & 0xffffffffL);
		return localKey;
	}
	
	public long key() {
		if (key == 0) {
			key = calcKey(ipAddress, port);
		}
		return key;
	}

	@Override
	public String toString() {
		return "DataNodeInfo [storageType=" + storageType + ", storageClass="
				+ storageClass + ", locationClass=" + locationClass
				+ ", ipAddress=" + Arrays.toString(ipAddress) + ", port="
				+ port + ", key=" + key + "]";
	}
}
