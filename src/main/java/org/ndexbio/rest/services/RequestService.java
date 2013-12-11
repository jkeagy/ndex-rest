package org.ndexbio.rest.services;

import java.util.Date;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.IGroupInvitationRequest;
import org.ndexbio.rest.domain.IGroupMembership;
import org.ndexbio.rest.domain.IJoinGroupRequest;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.INetworkAccessRequest;
import org.ndexbio.rest.domain.INetworkMembership;
import org.ndexbio.rest.domain.IRequest;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.domain.Permissions;
import org.ndexbio.rest.exceptions.NdexException;
import org.ndexbio.rest.exceptions.ObjectNotFoundException;
import org.ndexbio.rest.helpers.RidConverter;
import org.ndexbio.rest.models.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.orientechnologies.orient.core.id.ORID;
import com.orientechnologies.orient.core.metadata.schema.OType;
import com.orientechnologies.orient.core.record.impl.ODocument;
import com.orientechnologies.orient.core.sql.query.OSQLSynchQuery;
import com.tinkerpop.blueprints.impls.orient.OrientElement;

@Path("/requests")
public class RequestService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(RequestService.class);
    
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public RequestService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    
    
    /**************************************************************************
    * Creates a request. 
    * 
    * @param newRequest
    *            The request to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Duplicate requests or failed to create the request in the
    *            database.
    * @return The newly created request.
    **************************************************************************/
    @PUT
    @Produces("application/json")
    public Request createRequest(final Request newRequest) throws IllegalArgumentException, NdexException
    {
        if (newRequest == null)
            throw new IllegalArgumentException("The request to create is empty.");
        
        final ORID fromRid = RidConverter.convertToRid(newRequest.getFromId());
        final ORID toRid = RidConverter.convertToRid(newRequest.getToId());
        
        if (fromRid.equals(toRid))
            throw new IllegalArgumentException("Nice try, but you cannot make a request to yourself. You may want to consider schizophrenia counseling.");
        
        try
        {
            setupDatabase();
            
            final List<ODocument> existingRequests = _ndexDatabase.query(new OSQLSynchQuery<Integer>("select count(*) from Request where out_fromUser = " + fromRid.toString() + " and (out_toNetwork = " + toRid.toString() + " or out_toGroup = " + toRid.toString() + ")"));
            if (existingRequests == null || existingRequests.isEmpty())
                throw new NdexException("Unable to get request count.");
            else if ((long)existingRequests.get(0).field("count") > 0)
                throw new NdexException("You have already made that request and cannot make another.");
            
            if (newRequest.getRequestType().equals("Group Invitation"))
                createGroupInvitationRequest(fromRid, toRid, newRequest);
            else if (newRequest.getRequestType().equals("Join Group"))
                createJoinGroupRequest(fromRid, toRid, newRequest);
            else if (newRequest.getRequestType().equals("Network Access"))
                createNetworkAccessRequest(fromRid, toRid, newRequest);
            else
                throw new IllegalArgumentException("That request type isn't supported: " + newRequest.getRequestType() + ".");
            
            return newRequest;
        }
        catch (Exception e)
        {
            _logger.error("Failed to create a request.", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to create your request.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Deletes a request.
    * 
    * @param requestId
    *            The request ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The request doesn't exist.
    * @throws NdexException
    *            Failed to delete the request from the database.
    **************************************************************************/
    @DELETE
    @Path("/{requestId}")
    @Produces("application/json")
    public void deleteRequest(@PathParam("requestId")final String requestId) throws IllegalArgumentException, ObjectNotFoundException, NdexException
    {
        if (requestId == null || requestId.isEmpty())
            throw new IllegalArgumentException("No request ID was specified.");
        
        final ORID requestRid = RidConverter.convertToRid(requestId);

        try
        {
            final IRequest requestToDelete = _orientDbGraph.getVertex(requestRid, IRequest.class);
            if (requestToDelete == null)
                throw new ObjectNotFoundException("Request", requestId);
            
            final List<ODocument> requestChildren = _ndexDatabase.query(new OSQLSynchQuery<Object>("select @rid from (traverse * from " + requestRid + " while @class <> 'Account')"));
            for (ODocument networkChild : requestChildren)
            {
                final ORID childId = networkChild.field("rid", OType.LINK);

                final OrientElement element = _orientDbGraph.getBaseGraph().getElement(childId);
                if (element != null)
                    element.remove();
            }

            _orientDbGraph.removeVertex(requestToDelete.asVertex());
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to delete request: " + requestId + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to delete the request.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Gets a request by ID.
    * 
    * @param requestId
    *           The request ID.
    * @throws IllegalArgumentException
    *           Bad input.
    * @throws NdexException
    *           Failed to query the database.
    * @return The request.
    **************************************************************************/
    @GET
    @Path("/{requestId}")
    @Produces("application/json")
    public Request getRequest(@PathParam("requestId")final String requestId) throws IllegalArgumentException, NdexException
    {
        if (requestId == null || requestId.isEmpty())
            throw new IllegalArgumentException("No request ID was specified.");
        
        final ORID requestRid = RidConverter.convertToRid(requestId);

        try
        {
            setupDatabase();

            final IRequest request = _orientDbGraph.getVertex(requestRid, IRequest.class);
            if (request == null)
                throw new ObjectNotFoundException("Request", requestId);
    
            return new Request(request);
        }
        catch (Exception e)
        {
            _logger.error("Failed to get request: " + requestId + ".", e);
            throw new NdexException("Failed to get the request.");
        }
        finally
        {
            teardownDatabase();
        }
    }

    /**************************************************************************
    * Updates a request.
    * 
    * @param updatedRequest
    *            The updated request information.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to update the request in the database.
    **************************************************************************/
    @POST
    @Produces("application/json")
    public void updateRequest(final Request updatedRequest) throws IllegalArgumentException, NdexException
    {
        if (updatedRequest == null)
            throw new IllegalArgumentException("The updated request is empty.");
        
        final ORID requestRid = RidConverter.convertToRid(updatedRequest.getId());

        try
        {
            setupDatabase();
            
            final IRequest requestToUpdate = _orientDbGraph.getVertex(requestRid, IRequest.class);
            if (requestToUpdate == null)
                throw new ObjectNotFoundException("Request", updatedRequest.getId());

            requestToUpdate.setResponder(updatedRequest.getResponder());
            requestToUpdate.setResponse(updatedRequest.getResponse());
            
            if (updatedRequest.getResponse() != "DECLINED")
            {
                if (updatedRequest.getRequestType() == "Group Invitation")
                    processGroupInvitation(updatedRequest);
                else if (updatedRequest.getRequestType() == "Join Group")
                    processJoinGroup(updatedRequest);
                else if (updatedRequest.getRequestType() == "Network Access")
                    processNetworkAccess(updatedRequest);
            }
            
            _orientDbGraph.getBaseGraph().commit();
        }
        catch (Exception e)
        {
            _logger.error("Failed to update request: " + updatedRequest.getId() + ".", e);
            _orientDbGraph.getBaseGraph().rollback(); 
            throw new NdexException("Failed to update the request.");
        }
        finally
        {
            teardownDatabase();
        }
    }
    
    
    
    
    /**************************************************************************
    * Creates a group invitation request. 
    * 
    * @param fromRid
    *            The JID of the requesting group.
    * @param toRid
    *            The JID of the invited user.
    * @param requestToCreate
    *            The request data.
    * @throws ObjectNotFoundException
    *            The group or user wasn't found.
    * @throws NdexException
    *            Failed to create the request in the database.
    **************************************************************************/
    private void createGroupInvitationRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
    {
        final IGroup requestingGroup = _orientDbGraph.getVertex(fromRid, IGroup.class);
        if (requestingGroup == null)
            throw new ObjectNotFoundException("Group", fromRid);
        
        final IUser requestedUser = _orientDbGraph.getVertex(toRid, IUser.class);
        if (requestedUser == null)
            throw new ObjectNotFoundException("User", toRid);

        final IGroupInvitationRequest newRequest = _orientDbGraph.addVertex("class:groupInvite", IGroupInvitationRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromGroup(requestingGroup);
        newRequest.setToUser(requestedUser);
        requestingGroup.addRequest(newRequest);
        requestedUser.addRequest(newRequest);
        
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
    
    /**************************************************************************
    * Creates a join group request. 
    * 
    * @param fromRid
    *            The JID of the requesting group.
    * @param toRid
    *            The JID of the invited user.
    * @param requestToCreate
    *            The request data.
    * @throws ObjectNotFoundException
    *            The group or user wasn't found.
    * @throws NdexException
    *            Failed to create the request in the database.
    **************************************************************************/
    private void createJoinGroupRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
    {
        final IUser requestOwner = _orientDbGraph.getVertex(fromRid, IUser.class);
        if (requestOwner == null)
            throw new ObjectNotFoundException("User", fromRid);
        
        final IGroup requestedGroup = _orientDbGraph.getVertex(toRid, IGroup.class);
        if (requestedGroup == null)
            throw new ObjectNotFoundException("Group", toRid);

        final IJoinGroupRequest newRequest = _orientDbGraph.addVertex("class:joinGroup", IJoinGroupRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromUser(requestOwner);
        newRequest.setToGroup(requestedGroup);
        
        requestOwner.addRequest(newRequest);
        requestedGroup.addRequest(newRequest);
        
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }
    
    /**************************************************************************
    * Creates a network access request. 
    * 
    * @param fromRid
    *            The JID of the requesting group.
    * @param toRid
    *            The JID of the invited user.
    * @param requestToCreate
    *            The request data.
    * @throws ObjectNotFoundException
    *            The group or user wasn't found.
    * @throws NdexException
    *            Failed to create the request in the database.
    **************************************************************************/
    private void createNetworkAccessRequest(final ORID fromRid, final ORID toRid, final Request requestToCreate) throws ObjectNotFoundException, NdexException
    {
        final IUser requestOwner = _orientDbGraph.getVertex(fromRid, IUser.class);
        if (requestOwner == null)
            throw new ObjectNotFoundException("User", fromRid);
        
        final INetwork requestedNetwork = _orientDbGraph.getVertex(toRid, INetwork.class);
        if (requestedNetwork == null)
            throw new ObjectNotFoundException("Network", toRid);

        final INetworkAccessRequest newRequest = _orientDbGraph.addVertex("class:networkAccess", INetworkAccessRequest.class);
        newRequest.setMessage(requestToCreate.getMessage());
        newRequest.setRequestTime(new Date());
        newRequest.setFromUser(requestOwner);
        newRequest.setToNetwork(requestedNetwork);
        
        requestOwner.addRequest(newRequest);
        requestedNetwork.addRequest(newRequest);
        
        _orientDbGraph.getBaseGraph().commit();

        requestToCreate.setId(RidConverter.convertToJid((ORID)newRequest.asVertex().getId()));
    }

    /**************************************************************************
    * Adds a user to the group that invited them with read-only permissions.
    * 
    * @param requestToProcess
    *            The request.
    **************************************************************************/
    private void processGroupInvitation(final Request requestToProcess) throws Exception
    {
        final ORID groupId = RidConverter.convertToRid(requestToProcess.getFromId());
        final ORID userId = RidConverter.convertToRid(requestToProcess.getToId());
        
        IGroup group = _orientDbGraph.getVertex(groupId, IGroup.class);
        IUser user = _orientDbGraph.getVertex(userId, IUser.class);
        
        IGroupMembership newMember = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
        newMember.setGroup(group);
        newMember.setMember(user);
        newMember.setPermissions(Permissions.valueOf(requestToProcess.getResponse()));
        
        group.addMember(newMember);
    }

    /**************************************************************************
    * Adds a user to their requested group with read-only permissions.
    * 
    * @param requestToProcess
    *            The request.
    **************************************************************************/
    private void processJoinGroup(final Request requestToProcess) throws Exception
    {
        final ORID groupId = RidConverter.convertToRid(requestToProcess.getToId());
        final ORID userId = RidConverter.convertToRid(requestToProcess.getFromId());
        
        IGroup group = _orientDbGraph.getVertex(groupId, IGroup.class);
        IUser user = _orientDbGraph.getVertex(userId, IUser.class);
        
        IGroupMembership newMember = _orientDbGraph.addVertex("class:groupMembership", IGroupMembership.class);
        newMember.setGroup(group);
        newMember.setMember(user);
        newMember.setPermissions(Permissions.valueOf(requestToProcess.getResponse()));
        
        group.addMember(newMember);
    }

    /**************************************************************************
    * Adds a user to a network's membership with read-only permissions.
    * 
    * @param requestToProcess
    *            The request.
    **************************************************************************/
    private void processNetworkAccess(final Request requestToProcess) throws Exception
    {
        final ORID networkId = RidConverter.convertToRid(requestToProcess.getToId());
        final ORID userId = RidConverter.convertToRid(requestToProcess.getFromId());
        
        INetwork network = _orientDbGraph.getVertex(networkId, INetwork.class);
        IUser user = _orientDbGraph.getVertex(userId, IUser.class);
        
        INetworkMembership newMember = _orientDbGraph.addVertex("class:networkMembership", INetworkMembership.class);
        newMember.setNetwork(network);
        newMember.setMember(user);
        newMember.setPermissions(Permissions.valueOf(requestToProcess.getResponse()));
        
        network.addMember(newMember);
    }
}
