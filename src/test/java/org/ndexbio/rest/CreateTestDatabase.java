/**
 * Copyright (c) 2013, 2015, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio.rest;

import java.io.File;
import java.io.InputStream;
import java.net.URL;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import javax.servlet.http.HttpServletRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.orientechnologies.orient.client.remote.OServerAdmin;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentPool;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientBaseGraph;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;
import org.easymock.EasyMock;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.ndexbio.orientdb.NdexSchemaManager;
import org.ndexbio.rest.services.*;

/******************************************************************************
* This class creates the bare minimum for a test database needed to develop
* and test the web site. Its tests are run first before all other unit tests.
******************************************************************************/
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CreateTestDatabase
{
    private static OServerAdmin _orientDbAdmin;
//    private static FramedGraphFactory _graphFactory = null;
    private static ODatabaseDocumentTx _ndexDatabase = null;
//    private static FramedGraph<OrientBaseGraph> _orientDbGraph = null;

    private static final HttpServletRequest _mockRequest = EasyMock.createMock(HttpServletRequest.class);
    private static final ObjectMapper _jsonMapper = new ObjectMapper();
    private static final Properties _testProperties = new Properties();

    
    
    @BeforeClass
    public static void initializeTests() throws Exception
    {
    	
    	
        final InputStream propertiesStream = Thread.currentThread().getContextClassLoader().getResourceAsStream("ndex.properties");
        _testProperties.load(propertiesStream);
    }
    
    
    
    @Test
    public void deleteExistingDatabase()
    {
        try
        {
            //Can't use 'admin' as the username or password here, OrientDB
            //seems to have a hard-coded failure if either is 'admin'
        	_orientDbAdmin = new OServerAdmin(_testProperties.getProperty("OrientDB-URL"))
        	    .connect(_testProperties.getProperty("OrientDB-Admin-Username"),
        	        _testProperties.getProperty("OrientDB-Admin-Password"));

            if (_orientDbAdmin.existsDatabase("local"))
            {
                _orientDbAdmin.dropDatabase("ndex");
                Assert.assertFalse(_orientDbAdmin.existsDatabase("local"));
            }
        }
        catch (Exception e)
        {
            Assert.fail("Failed to delete existing database. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void generateTestDatabase()
    {
        try
        {
            _orientDbAdmin.createDatabase("ndex", "document", "local");
            Assert.assertTrue(_orientDbAdmin.existsDatabase("local"));
   /*         
            _graphFactory = new FramedGraphFactory(new GremlinGroovyModule(),
                new TypedGraphModuleBuilder()
                    .withClass(IGroup.class)
                    .withClass(IUser.class)
                    .withClass(IGroupMembership.class)
                    .withClass(INetworkMembership.class)
                    .withClass(IGroupInvitationRequest.class)
                    .withClass(IJoinGroupRequest.class)
                    .withClass(INetworkAccessRequest.class)
                    .withClass(IBaseTerm.class)
                    .withClass(IFunctionTerm.class)
                    .build());
     */       
            _ndexDatabase = ODatabaseDocumentPool.global().acquire("remote:localhost/ndex", "admin", "admin");
            Assert.assertNotNull(_ndexDatabase);
            
//            _orientDbGraph = _graphFactory.create((OrientBaseGraph)new OrientGraph(_ndexDatabase));
            NdexSchemaManager.INSTANCE.init(_ndexDatabase);
        }
        catch (Exception e)
        {
            Assert.fail("Failed to initialize database. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
/*
    @Test
    public void generateTestUsers()
    {
        final URL testUsersUrl = getClass().getResource("/resources/test-users.json");
        final UserService userService = new UserService(_mockRequest);

        try
        {
            final JsonNode serializedUsers = _jsonMapper.readTree(new File(testUsersUrl.toURI()));
            final Iterator<JsonNode> usersIterator = serializedUsers.elements();
            
            while (usersIterator.hasNext())
            {
                final JsonNode serializedUser = usersIterator.next();
                
                final NewUser newUser = _jsonMapper.readValue(serializedUser.toString(), NewUser.class);

                final User loggedInUser = userService.createUser(newUser);
                Assert.assertNotNull(loggedInUser);
                setLoggedInUser(loggedInUser);

                final User updatedUser = _jsonMapper.readValue(serializedUser.toString(), User.class);
                updatedUser.setId(loggedInUser.getId());
                
                userService.updateUser(updatedUser);
                
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
            }
        }
        catch (Exception e)
        {
            Assert.fail("Failed to deserialize/create test users. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void insertTestGroups()
    {
        final URL testGroupsUrl = getClass().getResource("/resources/test-groups.json");
        final GroupService groupService = new GroupService(_mockRequest);

        try
        {
            final JsonNode serializedGroups = _jsonMapper.readTree(new File(testGroupsUrl.toURI()));
            final Iterator<JsonNode> groupsIterator = serializedGroups.elements();
            
            while (groupsIterator.hasNext())
            {
                final JsonNode serializedGroup = groupsIterator.next();
                final Group newGroup = _jsonMapper.readValue(serializedGroup.toString(), Group.class);

                //Get the group owner name from the members, then clear the
                //members since we don't have the member ID
                final User loggedInUser = getUser(newGroup.getMembers().get(0).getResourceName());
                setLoggedInUser(loggedInUser);
                newGroup.getMembers().clear();
                
                groupService.createGroup(newGroup);
                
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
            }
        }
        catch (Exception e)
        {
            Assert.fail("Failed to deserialize/create test groups. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    @Test
    public void insertTestNetworks()
    {
        final NetworkService networkService = new NetworkService(_mockRequest);
        final String networkFilenames[] =
        {
            "NCI_NATURE.FoxO family signaling.517135.jdex",
            "reactome-test.jdex",
            "REACTOME.Cyclin D associated events in G1.485628.jdex",
            "REACTOME.G0 and Early G1.485619.jdex",
            "REACTOME.G1 Phase.485618.jdex"
        };

        for (String networkFilename : networkFilenames)
        {
            try
            {
                final URL testNetworkUrl = getClass().getResource("/resources/" + networkFilename);
                final Network newNetwork = _jsonMapper.readValue(new File(testNetworkUrl.toURI()), Network.class);
     
                //Get the network owner name from the members, then clear the
                //members since we don't have the member ID
                final User loggedInUser;
                if (newNetwork.getMembers().get(0).getResourceName().equals("triptychjs"))
                    loggedInUser = getUser("dexterpratt");
                else
                    loggedInUser = getUser(newNetwork.getMembers().get(0).getResourceName());
                
                setLoggedInUser(loggedInUser);

                newNetwork.getMembers().clear();
                
                networkService.createNetwork(newNetwork);
                
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
            }
            catch (Exception e)
            {
                Assert.fail("Failed to deserialize/create test network: " + networkFilename + ". Cause: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }
    
    @Test
    public void insertTestRequests()
    {
        final URL testRequestsUrl = getClass().getResource("/resources/test-requests.json");
        final RequestService requestService = new RequestService(_mockRequest);

        try
        {
            final JsonNode serializedRequests = _jsonMapper.readTree(new File(testRequestsUrl.toURI()));
            final Iterator<JsonNode> requestsIterator = serializedRequests.elements();
            
            while (requestsIterator.hasNext())
            {
                final JsonNode serializedRequest = requestsIterator.next();
                final Request newRequest = _jsonMapper.readValue(serializedRequest.toString(), Request.class);

                final ORID fromRid = getRid(newRequest.getFrom());
                newRequest.setFromId(IdConverter.toJid(fromRid));

                final ORID toRid = getRid(newRequest.getTo());
                newRequest.setToId(IdConverter.toJid(toRid));

                //Get the group owner name from the members, then clear the
                //members since we don't have the member ID
                final User loggedInUser;
                if (newRequest.getFrom().equals("triptychjs"))
                    loggedInUser = getUser("dexterpratt");
                else
                    loggedInUser = getUser(newRequest.getFrom());
                    
                setLoggedInUser(loggedInUser);
                requestService.createRequest(newRequest);
                
                //Mocking the HTTP request inside a loop, so reset it
                EasyMock.reset(_mockRequest);
            }
        }
        catch (Exception e)
        {
            Assert.fail("Failed to deserialize/create test requests. Cause: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ORID getRid(String objectName) throws IllegalArgumentException
    {
        objectName = objectName.replace("'", "''");
        
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + objectName + "'"));
        if (!matchingUsers.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingUsers.get(0)).getId();
        
        final List<ODocument> matchingGroups = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Group where name = '" + objectName + "'"));
        if (!matchingGroups.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingGroups.get(0)).getId();

        final List<ODocument> matchingNetworks = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Network where name = '" + objectName + "'"));
        if (!matchingNetworks.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingNetworks.get(0)).getId();

        final List<ODocument> matchingRequests = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Request where message = '" + objectName + "'"));
        if (!matchingRequests.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingRequests.get(0)).getId();

        final List<ODocument> matchingTasks = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from Task where description = '" + objectName + "'"));
        if (!matchingTasks.isEmpty())
            return (ORID)_orientDbGraph.getVertex(matchingTasks.get(0)).getId();
        
        throw new NdexException(objectName + " is not a user, group, network, request, or task.");
    }

    private User getUser(final String username)
    {
        final List<ODocument> matchingUsers = _ndexDatabase.query(new OSQLSynchQuery<Object>("select from User where username = '" + username + "'"));
        if (!matchingUsers.isEmpty())
            return new User(_orientDbGraph.getVertex(matchingUsers.get(0), IUser.class), true);
        else
            return null;
    }
    
    private void setLoggedInUser(final User loggedInUser)
    {
        EasyMock.expect(_mockRequest.getAttribute("User"))
        .andReturn(loggedInUser)
        .anyTimes();

        EasyMock.replay(_mockRequest);
    } */
}
