package org.ndexbio.rest.models;

import java.util.ArrayList;
import java.util.List;
import org.ndexbio.rest.domain.IGroup;
import org.ndexbio.rest.domain.INetwork;
import org.ndexbio.rest.domain.IUser;
import org.ndexbio.rest.helpers.RidConverter;
import com.orientechnologies.orient.core.id.ORID;

public class User
{
    private String _backgroundImage;
    private String _description;
    private String _firstName;
    private String _foregroundImage;
    private String _id;
    private String _lastName;
    private List<Group> _ownedGroups;
    private List<Network> _ownedNetworks;
    private String _username;
    private String _website;
    private List<Network> _workSurface;

    
    
    /**************************************************************************
    * Default constructor.
    **************************************************************************/
    public User()
    {
        _ownedGroups = new ArrayList<Group>();
        _ownedNetworks = new ArrayList<Network>();
        _workSurface = new ArrayList<Network>();
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * Doesn't load owned Groups or Networks.
    * 
    * @param user The User with source data.
    **************************************************************************/
    public User(IUser user)
    {
        this(user, false);
    }
    
    /**************************************************************************
    * Populates the class (from the database) and removes circular references.
    * 
    * @param user The User with source data.
    * @param loadEverything True to load owned Groups and Networks, false to
    *                       exclude them.
    **************************************************************************/
    public User(IUser user, boolean loadEverything)
    {
        this();
        
        _backgroundImage = user.getBackgroundImg();
        _description = user.getDescription();
        _firstName = user.getFirstName();
        _foregroundImage = user.getForegroundImg();
        _id = RidConverter.convertToJid((ORID)user.asVertex().getId());
        _lastName = user.getLastName();
        _username = user.getUsername();
        _website = user.getWebsite();
        
        for (INetwork onWorkSurface : user.getWorkspace())
            _workSurface.add(new Network(onWorkSurface));

        if (loadEverything)
        {
            for (IGroup ownedGroup : user.getOwnedGroups())
                _ownedGroups.add(new Group(ownedGroup));
            
            for (INetwork ownedNetwork : user.getOwnedNetworks())
                _ownedNetworks.add(new Network(ownedNetwork));
        }
    }
    
    
    
    public String getBackgroundImage()
    {
        return _backgroundImage;
    }

    public void setBackgroundImage(String backgroundImage)
    {
        _backgroundImage = backgroundImage;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public String getFirstName()
    {
        return _firstName;
    }

    public void setFirstName(String firstName)
    {
        _firstName = firstName;
    }

    public String getForegroundImage()
    {
        return _foregroundImage;
    }

    public void setForegroundImg(String foregroundImage)
    {
        _foregroundImage = foregroundImage;
    }
    
    public String getId()
    {
        return _id;
    }
    
    public void setId(String id)
    {
        _id = id;
    }

    public String getLastName()
    {
        return _lastName;
    }

    public void setLastName(String lastName)
    {
        _lastName = lastName;
    }

    public List<Group> getOwnedGroups()
    {
        return _ownedGroups;
    }

    public void setOwnedGroups(List<Group> ownedGroups)
    {
        _ownedGroups = ownedGroups;
    }

    public List<Network> getOwnedNetworks()
    {
        return _ownedNetworks;
    }

    public void setOwnedNetworks(List<Network> ownedNetworks)
    {
        _ownedNetworks = ownedNetworks;
    }

    public String getUsername()
    {
        return _username;
    }

    public void setUsername(String username)
    {
        _username = username;
    }

    public String getWebsite()
    {
        return _website;
    }

    public void setWebsite(String website)
    {
        _website = website;
    }

    public List<Network> getWorkSurface()
    {
        return _workSurface;
    }
    
    public void setWorkSurface(List<Network> workSurface)
    {
        _workSurface = workSurface;
    }
}
