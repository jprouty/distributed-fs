/*
 * Copyright 2009 Amit Levy, Jeff Prouty, Rylan Hawkins
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *      
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package edu.washington.cs.cse490h.donut.service;

import org.apache.thrift.TException;

import com.google.inject.Inject;

import edu.washington.cs.cse490h.donut.business.EntryKey;
import edu.washington.cs.cse490h.donut.business.TNode;
import edu.washington.cs.cse490h.donut.util.KeyIdUtil;

/**
 * @author alevy
 */
public class DonutHashRequestService implements HashService.Iface {

    private final KeyLocator.Iface     keyLocator;
    private final LocatorClientFactory clientFactory;

    @Inject
    public DonutHashRequestService(KeyLocator.Iface keyLocator, LocatorClientFactory clientFactory) {
        this.keyLocator = keyLocator;
        this.clientFactory = clientFactory;
    }

    public byte[] get(String key) throws DataNotFoundException, TException {
        EntryKey entryKey = new EntryKey(KeyIdUtil.generateKeyId(key), key);
        TNode successor = keyLocator.findSuccessor(entryKey.getId());

        try {
            KeyLocator.Iface hashClient = clientFactory.get(successor);
            return hashClient.get(entryKey);
        } catch (RetryFailedException e) {
            throw new TException(e);
        } finally {
            clientFactory.release(successor);
        }
    }

    public void put(String key, byte[] value) throws TException {
        EntryKey entryKey = new EntryKey(KeyIdUtil.generateKeyId(key), key);
        TNode successor = keyLocator.findSuccessor(entryKey.getId());

        try {
            KeyLocator.Iface hashClient = clientFactory.get(successor);
            hashClient.put(entryKey, value);
        } catch (RetryFailedException e) {
            throw new TException(e);
        } catch (NotResponsibleForId e) {
            throw new TException(e);
        } finally {
            clientFactory.release(successor);
        }
    }

    public void remove(String key) throws TException {
        EntryKey entryKey = new EntryKey(KeyIdUtil.generateKeyId(key), key);
        TNode successor = keyLocator.findSuccessor(entryKey.getId());

        try {
            KeyLocator.Iface hashClient = clientFactory.get(successor);
            hashClient.remove(entryKey);
        } catch (RetryFailedException e) {
            throw new TException(e);
        } catch (NotResponsibleForId e) {
            throw new TException(e);
        } finally {
            clientFactory.release(successor);
        }
    }

}
