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

package com.ibm.crail.core;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import com.ibm.crail.CrailFile;
import com.ibm.crail.conf.CrailConstants;
import com.ibm.crail.namenode.rpc.NameNodeProtocol;
import com.ibm.crail.namenode.rpc.RpcResponseMessage;

public abstract class CoreMetaDataOperation<R,T> implements Future<T> {
	protected static int RPC_PENDING = 0;
	protected static int RPC_DONE = 1;
	protected static int RPC_ERROR = 2;		
	
	private AtomicInteger status;
	private Future<R> rpcResult;
	private T finalResult;
	private Exception exception;
	
	abstract T process(R tmp) throws Exception;
	
	public CoreMetaDataOperation(Future<R> result){
		this.rpcResult = result;
		this.finalResult = null;
		this.status = new AtomicInteger(RPC_PENDING);
		this.exception = null;
	}

	@Override
	public boolean isDone() {
		if (status.get() == RPC_PENDING){
			try {
				if (rpcResult.isDone()){
					R tmp = rpcResult.get();
					finalResult = process(tmp);
					status.set(RPC_DONE);
				}
			} catch (Exception e) {
				status.set(RPC_ERROR);
				this.exception = e;
			}
		}
		
		return status.get() > 0;
	}

	@Override
	public T get() throws InterruptedException, ExecutionException {
		if (this.exception != null){
			throw new ExecutionException(exception);
		}
		
		if (status.get() == RPC_PENDING){
			try {
				R tmp = rpcResult.get();
				finalResult = process(tmp);
				status.set(RPC_DONE);
			} catch (Exception e) {
				status.set(RPC_ERROR);
				this.exception = e;
			}
		}	
		
		if (status.get() == RPC_DONE){
			return finalResult;
		} else if (status.get() == RPC_PENDING){
			throw new InterruptedException("RPC timeout");
		} else if (exception != null) {
			throw new ExecutionException(exception);
		} else {
			throw new InterruptedException("RPC error");
		}	
	}

	@Override
	public T get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		if (this.exception != null){
			throw new ExecutionException(exception);
		}		
		
		if (status.get() == RPC_PENDING){
			try {
				R tmp = rpcResult.get(CrailConstants.DATA_TIMEOUT, TimeUnit.MILLISECONDS);
				finalResult = process(tmp);
				status.set(RPC_DONE);
			} catch (Exception e) {
				status.set(RPC_ERROR);
				this.exception = e;
			}
		}	
		
		if (status.get() == RPC_DONE){
			return finalResult;
		} else if (status.get() == RPC_PENDING){
			throw new InterruptedException("RPC timeout");
		} else if (exception != null) {
			throw new ExecutionException(exception);
		} else {
			throw new InterruptedException("RPC error");
		}
	}
	
	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}	
}

class CreateFileFuture extends CoreMetaDataOperation<RpcResponseMessage.CreateFileRes, CrailFile> {
	private String path;
	private CoreFileSystem fs;
	private int storageAffinity;
	private int locationAffinity;

	public CreateFileFuture(CoreFileSystem fs, String path, Future<RpcResponseMessage.CreateFileRes> fileRes, int storageAffinity, int locationAffinity) {
		super(fileRes);
		this.fs = fs;
		this.path = path;
		this.storageAffinity = storageAffinity;
		this.locationAffinity = locationAffinity;
	}

	@Override
	CrailFile process(RpcResponseMessage.CreateFileRes tmp) throws Exception {
		return fs._create(tmp, path, storageAffinity, locationAffinity);
	}

}

class DeleteFileFuture extends CoreMetaDataOperation<RpcResponseMessage.DeleteFileRes, CrailFile> {
	private String path;
	private boolean recursive;
	private CoreFileSystem fs;

	public DeleteFileFuture(CoreFileSystem fs, String path, boolean recursive, Future<RpcResponseMessage.DeleteFileRes> fileRes) {
		super(fileRes);
		this.fs = fs;
		this.path = path;
		this.recursive = recursive;
	}

	@Override
	CrailFile process(RpcResponseMessage.DeleteFileRes tmp) throws Exception {
		return fs._delete(tmp, path, recursive);
	}
}

class LookupFileFuture extends CoreMetaDataOperation<RpcResponseMessage.GetFileRes, CrailFile> {
	private String path;
	private CoreFileSystem fs;	

	public LookupFileFuture(CoreFileSystem fs, String path, Future<RpcResponseMessage.GetFileRes> fileRes) {
		super(fileRes);
		this.fs = fs;
		this.path = path;
	}

	@Override
	CrailFile process(RpcResponseMessage.GetFileRes tmp) throws Exception {
		return fs._lookup(tmp, path);
	}

}

class RenameFileFuture extends CoreMetaDataOperation<RpcResponseMessage.RenameRes, CrailFile> {
	private String src;
	private String dst;
	private CoreFileSystem fs;

	public RenameFileFuture(CoreFileSystem fs, String src, String dst, Future<RpcResponseMessage.RenameRes> fileRes) {
		super(fileRes);
		this.fs = fs;
		this.src = src;
		this.dst = dst;
	}

	@Override
	CrailFile process(RpcResponseMessage.RenameRes tmp) throws Exception {
		return fs._rename(tmp, src, dst);
	}
}

class SyncFileFuture extends CoreMetaDataOperation<RpcResponseMessage.VoidRes, Void> {

	public SyncFileFuture(Future<RpcResponseMessage.VoidRes> fileRes) {
		super(fileRes);
	}
	
	@Override
	Void process(RpcResponseMessage.VoidRes tmp) throws Exception {
		if (tmp.getError() != NameNodeProtocol.ERR_OK){
			throw new Exception("sync: " + NameNodeProtocol.messages[tmp.getError()]);
		}
		return null;
	}
}

class NoOperation implements Future<Void> {

	@Override
	public boolean cancel(boolean mayInterruptIfRunning) {
		return false;
	}

	@Override
	public boolean isCancelled() {
		return false;
	}

	@Override
	public boolean isDone() {
		return true;
	}

	@Override
	public Void get() throws InterruptedException, ExecutionException {
		return null;
	}

	@Override
	public Void get(long timeout, TimeUnit unit) throws InterruptedException,
			ExecutionException, TimeoutException {
		return null;
	}
}