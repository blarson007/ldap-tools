package com.ldaptools.examples;

import com.ldaptools.server.EmbeddedLdapServer;
import com.ldaptools.server.unboundid.UnboundIDLdapServer;

public class UnboundIDServerExample {

	public static void main(String[] args) throws Exception {
		System.setProperty(org.slf4j.impl.SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "DEBUG"); // Turn on debug mode
		
		try (EmbeddedLdapServer ldapServer = new UnboundIDLdapServer(10389)) { // Construct an LDAP server that listens on port 10389
			
			ldapServer.startServer();
			
			Thread.sleep(120000);
		}
	}

}
