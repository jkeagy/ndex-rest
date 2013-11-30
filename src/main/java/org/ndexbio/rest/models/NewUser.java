package org.ndexbio.rest.models;

import org.codehaus.jackson.annotate.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(Include.NON_NULL)
public class NewUser
{
    private String _emailAddress;
    private String _password;
    private String _username;

    
    
    public String getEmailAddress()
    {
        return _emailAddress;
    }
    
    public void setEmailAddress(String emailAddress)
    {
        _emailAddress = emailAddress;
    }
    
    public String getPassword()
    {
        return _password;
    }
    
    public void setPassword(String password)
    {
        _password = password;
    }
    
    public String getUsername()
    {
        return _username;
    }
    
    public void setUsername(String username)
    {
        _username = username;
    }
}