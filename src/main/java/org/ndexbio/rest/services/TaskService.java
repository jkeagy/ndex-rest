package org.ndexbio.rest.services;

import java.util.Date;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;

import org.jboss.resteasy.annotations.providers.multipart.MultipartForm;
import org.ndexbio.common.access.NdexAOrientDBConnectionPool;
import org.ndexbio.common.access.NdexDatabase;
import org.ndexbio.common.exceptions.NdexException;
import org.ndexbio.common.exceptions.ObjectNotFoundException;
import org.ndexbio.common.models.dao.orientdb.RequestDAO;
import org.ndexbio.common.models.dao.orientdb.TaskDAO;
import org.ndexbio.model.object.Request;
import org.ndexbio.model.object.Status;
import org.ndexbio.model.object.TaskType;
import org.ndexbio.model.object.Task;
import org.ndexbio.rest.annotations.ApiDoc;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx;
import com.orientechnologies.orient.core.id.ORID;
import com.tinkerpop.blueprints.impls.orient.OrientGraph;

@Path("/task")
public class TaskService extends NdexService
{
    private static final Logger _logger = LoggerFactory.getLogger(TaskService.class);
	private  TaskDAO dao;
	private  NdexDatabase database;
	private  ODatabaseDocumentTx  localConnection;  //all DML will be in this connection, in one transaction.
	private  OrientGraph graph;
    
    
    /**************************************************************************
    * Injects the HTTP request into the base class to be used by
    * getLoggedInUser(). 
    * 
    * @param httpRequest
    *            The HTTP request injected by RESTEasy's context.
    **************************************************************************/
    public TaskService(@Context HttpServletRequest httpRequest)
    {
        super(httpRequest);
    }
    
    /**************************************************************************
    * Creates a task. 
    * 
    * @param newTask
    *            The task to create.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws NdexException
    *            Failed to create the task in the database.
    * @return The newly created task.
    **************************************************************************/
    /*
     * refactored for non-transactional database operation
     */
    @POST
    @Produces("application/json")
	@ApiDoc("Create a new task owned by the authenticated user based on the supplied JSON task object.")
    public UUID createTask(final Task newTask) throws IllegalArgumentException, NdexException
    {
    	Preconditions.checkArgument(null != newTask, 
    			" A task object is required");
        
        
        final String userAccount = this.getLoggedInUser().getAccountName();

        try
        {
        	this.openDatabase();
            //TaskDAO dao = new TaskDAO(this._ndexDatabase);
            
            return dao.createTask(userAccount, newTask);
        }
        catch (Exception e)
        {
            _logger.error("Error creating task for: " + userAccount + ".", e);
            throw new NdexException("Error creating a task.");
        }
        finally
        {
        	this.closeDatabase();
        }
    }
    

    
	@PUT
	@Path("/{taskId}/status/{status}")
	@Produces("application/json")
	@ApiDoc("Sets the status of the task, throws exception if status is not recognized.")
	public Task updateTaskStatus(@PathParam("status") final String status,
			@PathParam("taskId") final String taskId) throws NdexException {
		
		Preconditions.checkArgument(!Strings.isNullOrEmpty(taskId),
				"A task ID is required");

		this.openDatabase();
		
		try {
			
			Status s = Status.valueOf(status);

			return dao.updateTaskStatus(s,taskId, this.getLoggedInUser());

			
		} catch (Exception e) {
			_logger.error("Error changing task status for: "
					+ this.getLoggedInUser().getAccountName() + ".", e);
			throw new NdexException("Error changing task status." + e.getMessage());
			
		} finally {
			this.closeDatabase();

		}

	}
	
    private void openDatabase() throws NdexException {
		database = new NdexDatabase();
		localConnection = database.getAConnection();
		graph = new OrientGraph(localConnection);
		dao = new TaskDAO(localConnection);
	}
	private void closeDatabase() {
		localConnection.close();
		database.close();
	}

    /**************************************************************************
    * Deletes a task. 
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The task doesn't exist.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to delete the task from the database.
    **************************************************************************/
    /*
     * refactored for non-transactional database operations
     */
    @DELETE
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Delete the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public void deleteTask(@PathParam("taskId")final String taskUUID) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
    	Preconditions.checkArgument(!Strings.isNullOrEmpty(taskUUID), 
    			"A task id is required");
       
		ODatabaseDocumentTx db = null;
		try {
			_logger.info("Starting delete for task " + taskUUID);
			db = NdexAOrientDBConnectionPool.getInstance().acquire();
            
			TaskDAO tdao= new TaskDAO(db);
            final Task taskToDelete = tdao.getTaskByUUID(taskUUID);
            
            if (taskToDelete == null)
                throw new ObjectNotFoundException("Task", taskUUID);
            else if (!taskToDelete.getTaskOwnerId().equals(this.getLoggedInUser().getExternalId()))
                throw new SecurityException("You cannot delete a task you don't own.");
    
            tdao.deleteTask(taskToDelete.getExternalId());
            
            db.commit();
            _logger.info("Completed commit of delete for task " + taskUUID);
           
        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1){
                throw new ObjectNotFoundException("Task", taskUUID);
            }
            
            _logger.error("Failed to delete task: " + taskUUID + ".", e);
        
            throw new NdexException("Failed to delete a task.");
        }
        finally
        {
        	if ( db!=null) db.close();
        }
    }

    /**************************************************************************
    * Gets a task by ID.
    * 
    * @param taskId
    *            The task ID.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to query the database.
    **************************************************************************/
/*    @GET
    @Path("/{taskId}")
    @Produces("application/json")
	@ApiDoc("Return a JSON task object for the task specified by taskId. Errors if no task found or if authenticated user does not own task.")
    public Task getTask(@PathParam("taskId")final String taskId) throws IllegalArgumentException, SecurityException, NdexException
    {
        if (taskId == null || taskId.isEmpty())
            throw new IllegalArgumentException("No task ID was specified.");

        try
        {
            final ORID taskRid = IdConverter.toRid(taskId);
            
            setupDatabase();
            
            final ITask task = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (task != null)
            {
                if (!task.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                    throw new SecurityException("Access denied.");
                else
                    return new Task(task);
            }
        }
        catch (SecurityException se)
        {
            throw se;
        }
        catch (Exception e)
        {
            _logger.error("Failed to get task: " + taskId + ".", e);
            throw new NdexException("Failed to retrieve the task.");
        }
        finally
        {
            teardownDatabase();
        }
        
        return null;
    }
*/
    /**************************************************************************
    * Updates a task.
    * 
    * @param updatedTask
    *            The updated request.
    * @throws IllegalArgumentException
    *            Bad input.
    * @throws ObjectNotFoundException
    *            The task doesn't exist.
    * @throws SecurityException
    *            The user doesn't own the task.
    * @throws NdexException
    *            Failed to update the task in the database.
    **************************************************************************/
/*    @POST
    @Produces("application/json")
	@ApiDoc("Updates the task specified by taskId in the POSTed task JSON structure. Properties of the task are changed to match the properties in the JSON structure. Errors if no task found or if authenticated user does not own task.")
    public void updateTask(final Task updatedTask) throws IllegalArgumentException, ObjectNotFoundException, SecurityException, NdexException
    {
       Preconditions.checkArgument(null != updatedTask, 
    		   "A task is required");
    	
        ORID taskRid = IdConverter.toRid(updatedTask.getId());

        try
        {
            setupDatabase();
            
            final ITask taskToUpdate = _orientDbGraph.getVertex(taskRid, ITask.class);
            if (taskToUpdate == null)
                throw new ObjectNotFoundException("Task", updatedTask.getId());
            else if (!taskToUpdate.getOwner().getUsername().equals(this.getLoggedInUser().getUsername()))
                throw new SecurityException("Access denied.");

            taskToUpdate.setDescription(updatedTask.getDescription());
            taskToUpdate.setPriority(updatedTask.getPriority());
            taskToUpdate.setProgress(updatedTask.getProgress());
            taskToUpdate.setStatus(updatedTask.getStatus());
            taskToUpdate.setType(updatedTask.getType());

        }
        catch (SecurityException | ObjectNotFoundException onfe)
        {
            throw onfe;
        }
        catch (Exception e)
        {
            if (e.getMessage().indexOf("cluster: null") > -1){
                throw new ObjectNotFoundException("Task", updatedTask.getId());
            }
            
            _logger.error("Failed to update task: " + updatedTask.getId() + ".", e);
           
            throw new NdexException("Failed to update task: " + updatedTask.getId() + ".");
        }
        finally
        {
            teardownDatabase();
        }
    }
    

	
  */  
    
	/**************************************************************************
	 * Exports a network to an xbel-formatted file. Creates a network upload task
	 * 
	 * @param networkId
	 *            The id of the network to export
	 * @throws IllegalArgumentException
	 *             Bad input.
	 * @throws NdexException
	 *             Failed to create a network export task
	 **************************************************************************/
   /* @PUT
    @Path("/exportNetwork/xbel/{networkId}")
    @Produces("application/json")
	@ApiDoc("Creates a queued task  for asynchronous exporting of a NDEx network to an external "
			+ "XML file meeting the XBEL validation rules. An Exception is thrown if an invalid "
			+ "network id is specified")
	public Task createXBELExportNetworkTask(@PathParam("networkId")final String networkId)
			throws IllegalArgumentException, SecurityException, NdexException {

		
			Preconditions
					.checkArgument(!Strings.isNullOrEmpty(networkId), "A network ID is required");
		
			setupDatabase();




				try {
					final IUser taskOwner = _orientDbGraph.getVertex(
							IdConverter.toRid(this.getLoggedInUser().getId()),
							IUser.class);
					
					final INetwork network = _orientDbGraph.getVertex(
							IdConverter.toRid(networkId), INetwork.class);
					if (network == null)
						throw new ObjectNotFoundException("Network", networkId);
					
					
					ITask processNetworkTask = _orientDbGraph.addVertex(
							"class:task", ITask.class);
					processNetworkTask.setDescription(network.getName() + ".xbel");
					processNetworkTask.setType(TaskType.EXPORT_NETWORK_TO_FILE);
					processNetworkTask.setOwner(taskOwner);
					processNetworkTask.setPriority(Priority.LOW);
					processNetworkTask.setProgress(0);
					processNetworkTask.setResource(networkId);
					processNetworkTask.setStartTime(new Date());
					processNetworkTask.setStatus(Status.QUEUED);
					// retain commit statement for planned return to transaction-based operation
					_orientDbGraph.getBaseGraph().commit();
					Task newTask = new Task(processNetworkTask);
					return newTask;
				} 
				catch (Exception e)
		        {
		            _logger.error("Error creating task for: " + this.getLoggedInUser().getUsername() + ".", e);
		            throw new NdexException("Error creating a task.");
		        } 
				finally {
					teardownDatabase();
				}
			
		
	}  */
}
