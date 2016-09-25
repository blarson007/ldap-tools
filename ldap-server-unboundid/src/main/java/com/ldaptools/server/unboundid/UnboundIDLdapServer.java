package com.ldaptools.server.unboundid;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ldaptools.server.EmbeddedLdapServer;
import com.ldaptools.server.LdapServerConfig;
import com.ldaptools.server.LdapServerStatus;
import com.unboundid.ldap.listener.InMemoryDirectoryServer;
import com.unboundid.ldap.listener.InMemoryDirectoryServerConfig;
import com.unboundid.ldap.listener.InMemoryListenerConfig;
import com.unboundid.ldap.sdk.Entry;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.schema.Schema;
import com.unboundid.ldif.LDIFException;
import com.unboundid.ldif.LDIFReader;

public class UnboundIDLdapServer implements EmbeddedLdapServer {

	private static final Logger logger = LoggerFactory.getLogger(UnboundIDLdapServer.class);
	
	private InMemoryDirectoryServer directoryServer;
	private LdapServerConfig ldapServerConfig;
	private LdapServerStatus ldapServerStatus = LdapServerStatus.PreInit;
	
	/**
	 * Default no-arg constructor.  Uses default configurations specified in {@link LdapServerConfig}.
	 */
	public UnboundIDLdapServer() {
		ldapServerConfig = new LdapServerConfig();
	}
	
	/**
	 * Constructor that allows the specification of an {@link LdapServerConfig} object.
	 * 
	 * @param ldapServerConfig
	 */
	public UnboundIDLdapServer(LdapServerConfig ldapServerConfig) {
		this.ldapServerConfig = ldapServerConfig;
	}
	
	/**
	 * Convenience constructor that allows the specification of a port. This is likely the most commonly
	 * changed configuration, and it would be essential if the default LDAP port (389) were already in use.
	 * 
	 * @param port - The port number that the director server should listen on
	 */
	public UnboundIDLdapServer(int port) {
		ldapServerConfig = new LdapServerConfig(port);
	}

	/**
	 * Attempt to start up the in-memory directory server. The following actions are performed as a result:<br>
	 * 1. Read in a default schema that the directory server will adhere to<br>
	 * 2. Read in the domain and user bases from a file<br>
	 * 3. Generate a number of users and groups that will populate the server<br>
	 * 4. Read in the generated users and groups<br>
	 * 5. Start the in-memory server
	 */
	@Override
	public void startServer() throws Exception {
		logger.warn("Starting in-memory LDAP server");
		
		try {
			logger.debug("Constructing the in-memory server");
			
			InMemoryDirectoryServerConfig directoryServerConfig = new InMemoryDirectoryServerConfig(ldapServerConfig.getBaseDN());
			
			directoryServerConfig.addAdditionalBindCredentials(ldapServerConfig.getUser(), ldapServerConfig.getPassword());
			directoryServerConfig.setGenerateOperationalAttributes(true);
			
			// TODO: Use a schema
			if (ldapServerConfig.isEnforceSchema()) {
				logger.debug("Constructing the schema");
				
				Schema adSchema = getSchemaFromInputStream("ldif/schema.ldif");
				directoryServerConfig.setSchema(adSchema);
			} else {
				directoryServerConfig.setSchema(null);
			}
			
			InMemoryListenerConfig listenerConfig = InMemoryListenerConfig.createLDAPConfig(ldapServerConfig.getHost(), ldapServerConfig.getPort());
			directoryServerConfig.setListenerConfigs(listenerConfig);
			
			directoryServer = new InMemoryDirectoryServer(directoryServerConfig);
			
			logger.debug("Building the base DNs");
			
			insertEntry("ldif/ad_base.ldif", directoryServer);
			insertEntry("ldif/user_base.ldif", directoryServer);
			
			logger.debug("Starting the in-memory listener");
			
			directoryServer.startListening();
			
			ldapServerStatus = LdapServerStatus.SuccessfulInit;
		} catch (Exception e) {
			logger.error("Error while starting in-memory LDAP server", e);
			ldapServerStatus = LdapServerStatus.FailedInit;
		}
	}
	
	/**
	 * Attempt to shut down the in-memory directory server. Note that this is an essential
	 * step in using the in-memory server. Failure to shutdown the server could leave the
	 * resources allocated and would potentially require a kill -9 to terminate the process.
	 */
	@Override
	public void stopServer() {
		logger.warn("Shutting down in-memory LDAP server");
		
		try {
			if (ldapServerStatus == LdapServerStatus.SuccessfulInit) {
				directoryServer.shutDown(false);
			} else {
				logger.warn("Ldap Server not started.  Skipping shutdown.");
			}
		} catch (Exception e) {
			logger.error("Error while shutting down in-memory LDAP server", e);
		} finally {
			directoryServer = null;
		}
	}
	
	@Override
	public void close() throws Exception {
		stopServer();
	}
	
	// TODO: All imports, data population will need to moved else-where
	public void importLdif(String ldifString) throws LDIFException, LDAPException, IOException {
		importLdif(ldifString, directoryServer);
	}
	
	public LdapServerConfig getLdapServerConfig() {
		return ldapServerConfig;
	}
	
	public LdapServerStatus getLdapServerStatus() {
		return ldapServerStatus;
	}
	
	private void insertEntry(String resource, InMemoryDirectoryServer ds) throws LDAPException, LDIFException, IOException {
		InputStream stream = getClass().getClassLoader().getResourceAsStream(resource);
		LDIFReader reader = new LDIFReader(stream);
		
		try {
			ds.add(reader.readEntry());
		} finally {
			closeResources(reader, stream);
		}
	}
	
	private void importLdif(String resource, InMemoryDirectoryServer ds) throws LDIFException, LDAPException, IOException {
		for (Entry entry : LDIFReader.readEntries(new ByteArrayInputStream(resource.getBytes()))) {
			insertEntryWithAdditionalFields(entry, ds);
		}
	}
	
	private void insertEntryWithAdditionalFields(Entry entry, InMemoryDirectoryServer ds) throws LDAPException {
		entry.addAttribute("instanceType", "4");
		entry.addAttribute("objectSid", "blah");
		entry.addAttribute("nTSecurityDescriptor", "blah");
		
		if (isGroupEntry(entry)) {
			entry.addAttribute("sAMAccountName", entry.getAttributeValue("name"));
			entry.addAttribute("groupType", "-2147483644");
		}
	
		ds.add(entry);
	}
	
	private Schema getSchemaFromInputStream(String schemaResource) throws LDIFException, IOException {
		InputStream stream = getClass().getClassLoader().getResourceAsStream(schemaResource);
		LDIFReader reader = new LDIFReader(stream);
		
		try {
			return new Schema(reader.readEntry());
		} finally {
			closeResources(reader, stream);
		}
	}
	
	private void closeResources(LDIFReader reader, InputStream stream) {
		try {
			if (reader != null) {
				reader.close();
			}	
		} catch (Exception e) {  }	
		
		try {
			if (stream != null) {
				stream.close();
			}	
		} catch (Exception e) {  }
	}
	
	private boolean isGroupEntry(Entry entry) {
		
		if (!entry.hasAttribute("objectClass")) {
			return false;
		}
		
		for (String objectClass : entry.getAttributeValues("objectClass")) {
			if (objectClass != null && objectClass.equalsIgnoreCase("group")) {
				return true;
			}
		}
		
		return false;
	}
}
