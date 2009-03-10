package edu.washington.cs.cse490h.donut.service;

import static org.easymock.EasyMock.aryEq;
import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.washington.cs.cse490h.donut.business.Node;
import edu.washington.cs.cse490h.donut.service.application.DonutHashTableService;
import edu.washington.cs.cse490h.donut.service.thrift.Constants;
import edu.washington.cs.cse490h.donut.service.thrift.DataNotFoundException;
import edu.washington.cs.cse490h.donut.service.thrift.DataPair;
import edu.washington.cs.cse490h.donut.service.thrift.EntryKey;
import edu.washington.cs.cse490h.donut.service.thrift.KeyId;
import edu.washington.cs.cse490h.donut.service.thrift.KeyLocator;
import edu.washington.cs.cse490h.donut.service.thrift.NotResponsibleForId;
import edu.washington.cs.cse490h.donut.service.thrift.TNode;

public class NodeLocatorTest {

    private static final EntryKey ENTRY_KEY = new EntryKey(new KeyId(1), "key");
    LocatorClientFactory          clientFactoryMock;
    KeyLocator.Iface              nextLocatorMock;
    DonutHashTableService         service;

    @Before
    public void setUp() throws Exception {
        clientFactoryMock = createMock(LocatorClientFactory.class);
        nextLocatorMock = createMock(KeyLocator.Iface.class);
        service = createMock(DonutHashTableService.class);
    }

    @After
    public void tearDown() throws Exception {
        verify(clientFactoryMock);
        verify(nextLocatorMock);
        verify(service);
    }

    @Test
    public void testFindSuccessor_ImmediateSuccessor() throws Exception {
        Node node1 = new Node("node1", 8080, new KeyId(100));
        TNode node2 = new TNode("node2", 8080, new KeyId(900));

        node1.setSuccessor(node2);
        NodeLocator nodeLocator = new NodeLocator(node1, null, null);

        replay(clientFactoryMock, nextLocatorMock, service);

        assertEquals(node2, nodeLocator.findSuccessor(new KeyId(456)));

    }

    @Test
    public void testFindSuccessor_NotImmediateSuccessor() throws Exception {
        KeyId entryId = new KeyId(1024);

        Node node1 = new Node("node1", 8080, new KeyId(100));
        TNode node2 = new TNode("node2", 8080, new KeyId(900));

        TNode resultNode = new TNode("resultNode", 8080, null);

        node1.setSuccessor(node2);
        NodeLocator nodeLocator = new NodeLocator(node1, null, clientFactoryMock);

        // ClientFactory Expectations:
        expect(clientFactoryMock.get(node2)).andReturn(nextLocatorMock);
        clientFactoryMock.release(node2);

        // NextLocatorMock Expectations:
        expect(nextLocatorMock.findSuccessor(entryId)).andReturn(resultNode);
        replay(clientFactoryMock, nextLocatorMock, service);

        assertEquals(resultNode, nodeLocator.findSuccessor(entryId));
    }

    @Test(expected = DataNotFoundException.class)
    public void testGet_Dne() throws Exception {
        NodeLocator nodeLocator = new NodeLocator(null, service, null);

        expect(service.get(ENTRY_KEY)).andReturn(null);
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.get(ENTRY_KEY);
    }

    @Test
    public void testGet_Exists() throws Exception {
        NodeLocator nodeLocator = new NodeLocator(null, service, null);
        String value = "Hello World";

        expect(service.get(ENTRY_KEY)).andReturn(new DataPair(value.getBytes(), 0));
        replay(clientFactoryMock, nextLocatorMock, service);

        assertArrayEquals(value.getBytes(), nodeLocator.get(ENTRY_KEY));
    }

    @Test
    public void testReplicatePut_NoReplica() throws Exception {
        NodeLocator nodeLocator = new NodeLocator(null, service, null);
        String value = "Hello World";

        service.put(eq(ENTRY_KEY), aryEq(value.getBytes()), eq(0));
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.replicatePut(ENTRY_KEY, value.getBytes(), 0);
    }

    @Test
    public void testReplicatePut_WithReplica() throws Exception {
        Node node = new Node(null);
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);
        String value = "Hello World";

        expect(clientFactoryMock.get(node.getSuccessor())).andReturn(nextLocatorMock);
        clientFactoryMock.release(node.getSuccessor());
        nextLocatorMock.replicatePut(eq(ENTRY_KEY), aryEq(value.getBytes()), eq(1));
        service.put(eq(ENTRY_KEY), aryEq(value.getBytes()), eq(2));
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.replicatePut(ENTRY_KEY, value.getBytes(), 2);
    }

    @Test
    public void testReplicateRemove_NoReplica() throws Exception {
        NodeLocator nodeLocator = new NodeLocator(null, service, null);

        service.remove(ENTRY_KEY);
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.replicateRemove(ENTRY_KEY, 0);
    }

    @Test
    public void testReplicateRemove_WithReplica() throws Exception {
        Node node = new Node(null);
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);

        expect(clientFactoryMock.get(node.getSuccessor())).andReturn(nextLocatorMock);
        clientFactoryMock.release(node.getSuccessor());
        nextLocatorMock.replicateRemove(ENTRY_KEY, 1);
        service.remove(ENTRY_KEY);
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.replicateRemove(ENTRY_KEY, 2);
    }

    @Test
    public void testPut() throws Exception {
        Node node = new Node(null, 8080, new KeyId(1000));
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        node.setPredecessor(new TNode("predecessor", 1234, new KeyId(0)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);

        expect(clientFactoryMock.get(node.getSuccessor())).andReturn(nextLocatorMock);
        clientFactoryMock.release(node.getSuccessor());
        nextLocatorMock.replicatePut(eq(ENTRY_KEY), aryEq("data".getBytes()),
                eq(Constants.SUCCESSOR_LIST_SIZE - 1));
        service.put(eq(ENTRY_KEY), aryEq("data".getBytes()), eq(Constants.SUCCESSOR_LIST_SIZE));
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.put(ENTRY_KEY, "data".getBytes());
    }
    
    @Test(expected=NotResponsibleForId.class)
    public void testPut_NotResponsible() throws Exception {
        Node node = new Node(null, 8080, new KeyId(1000));
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        node.setPredecessor(new TNode("predecessor", 1234, new KeyId(0)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);

        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.put(new EntryKey(new KeyId(-1), "key"), "data".getBytes());
    }

    @Test
    public void testRemove() throws Exception {
        Node node = new Node(null, 8080, new KeyId(1000));
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        node.setPredecessor(new TNode("predecessor", 1234, new KeyId(0)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);

        expect(clientFactoryMock.get(node.getSuccessor())).andReturn(nextLocatorMock);
        clientFactoryMock.release(node.getSuccessor());
        nextLocatorMock.replicateRemove(eq(ENTRY_KEY), eq(Constants.SUCCESSOR_LIST_SIZE - 1));
        service.remove(eq(ENTRY_KEY));
        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.remove(ENTRY_KEY);
    }
    
    @Test(expected=NotResponsibleForId.class)
    public void testRemove_NotResponsible() throws Exception {
        Node node = new Node(null, 8080, new KeyId(1000));
        node.setSuccessor(new TNode("successor", 1234, new KeyId(123)));
        node.setPredecessor(new TNode("predecessor", 1234, new KeyId(0)));
        NodeLocator nodeLocator = new NodeLocator(node, service, clientFactoryMock);

        replay(clientFactoryMock, nextLocatorMock, service);

        nodeLocator.remove(new EntryKey(new KeyId(-1), "key"));
    }

}
>>>>>>> Added string parameter to get/put/remove and encapsulated in EntryKey. Separated remove/put from replication methods.:test/unit/edu/washington/cs/cse490h/donut/service/NodeLocatorTest.java
