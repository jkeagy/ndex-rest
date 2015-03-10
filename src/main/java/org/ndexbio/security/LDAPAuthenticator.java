package org.ndexbio.security;

import java.util.AbstractMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.naming.ldap.InitialLdapContext;
import javax.naming.ldap.LdapContext;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.UnauthorizedOperationException;
import org.ndexbio.task.Configuration;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.orientechnologies.orient.core.record.impl.ODocument;

public class LDAPAuthenticator {
	
	private final static int CACHE_SIZE = 500;
	
	private final static String PROP_LDAP_URL = "PROP_LDAP_URL";
	private final static String AD_SEARCH_BASE= "AD_SEARCH_BASE";
	private final static String AD_NDEX_GROUP_NAME="AD_NDEX";
	private String ldapAdServer;
	private String ldapSearchBase;
	private String ldapNDExGroup;
	private Hashtable <String,Object> env ;
	private Pattern pattern ;
	
	//key is the combination of 
	protected LoadingCache<java.util.Map.Entry<String,String>, Boolean>  userCredentials;
	
	public LDAPAuthenticator (Configuration config) throws NdexException, NamingException {
		
		ldapAdServer = config.getProperty(PROP_LDAP_URL);
		if ( ldapAdServer == null )
			throw new NdexException("Property " + PROP_LDAP_URL + " have to be defined in configuration file.");
		
		ldapSearchBase=config.getProperty(AD_SEARCH_BASE);
		if ( ldapSearchBase == null) 
			throw new NdexException ("Property " + AD_SEARCH_BASE + " have to be defined in configuration file.");
		
		ldapNDExGroup = config.getProperty(AD_NDEX_GROUP_NAME);
       
        env = new Hashtable<>();

		env.put(Context.SECURITY_AUTHENTICATION, "simple");
       env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
       env.put(Context.PROVIDER_URL, ldapAdServer);
       
       env.put("java.naming.ldap.attributes.binary", "objectSID");
       env.put("com.sun.jndi.ldap.trace.ber", System.err);
       
	   pattern = Pattern.compile("^CN=(.*?[^\\\\]),");

       userCredentials = CacheBuilder
				.newBuilder().maximumSize(CACHE_SIZE)
				.expireAfterAccess(240L, TimeUnit.MINUTES)
				.build(new CacheLoader<java.util.Map.Entry<String,String>, Boolean>() {
				   @Override
				   public Boolean load(java.util.Map.Entry<String,String> entry) throws NdexException, ExecutionException {
					   String userName = entry.getKey();
					   String pswd = entry.getValue();
					   Boolean userIsInGrp = userIsInNdexGroup(userName, pswd);
                   	   if ( userIsInGrp.booleanValue() ) {
                   		   userCredentials.put(new AbstractMap.SimpleImmutableEntry<String,String>(userName, pswd), Boolean.TRUE);	
                   		   return Boolean.TRUE;
                   	   }
                   	   throw new UnauthorizedOperationException("User " + userName + " is not in the required group." );
				   }
			    });;
       
	}

	
	private Boolean userIsInNdexGroup (String username, String password) throws UnauthorizedOperationException  {
      env.put(Context.SECURITY_PRINCIPAL, "NA\\" +username);
      env.put(Context.SECURITY_CREDENTIALS, password);
      try {
    	  LdapContext ctx = new InitialLdapContext(env,null);
      

	  String searchFilter = "(&(SAMAccountName="+ username + ")(objectClass=user)(objectCategory=person))";
	  SearchControls searchControls = new SearchControls();
	  searchControls.setSearchScope(SearchControls.SUBTREE_SCOPE);
	  NamingEnumeration<SearchResult> results = ctx.search(ldapSearchBase, searchFilter, searchControls);
	  
	  SearchResult searchResult = null;
		if ( results.hasMoreElements()) {
			searchResult = results.nextElement();
			Attributes attrs = searchResult.getAttributes();
//			Attribute uWWID = attrs.get("employeeID");
			if ( ldapNDExGroup != null ) {
				Attribute grp = attrs.get("memberOf");
				NamingEnumeration enu = grp.getAll();
				while ( enu.hasMore()) {
					String obj = (String)enu.next();
					//	        	System.out.println(obj);
					Matcher matcher = pattern.matcher(obj);
					if (matcher.find())
					{
						if ( matcher.group(1).equals(ldapNDExGroup) ) 
							return Boolean.TRUE;
					}
				}
				return Boolean.FALSE;
			}	
			return Boolean.TRUE;
		}
		return Boolean.FALSE;
      } catch (NamingException e) {
    	  throw new UnauthorizedOperationException(e.getMessage());
      }
	}
	
	public boolean authenticateUser(String username, String password) throws UnauthorizedOperationException {
		Boolean result;
		try {
			result = userCredentials.get(new AbstractMap.SimpleImmutableEntry<String,String>(username, password));
		} catch (ExecutionException e) {
			throw new UnauthorizedOperationException(e.getMessage());
		}
		return result.booleanValue() ;
	}
}
 