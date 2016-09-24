package com.ldaptools.server;

public interface EmbeddedLdapServer extends AutoCloseable {

	void startServer() throws Exception;
	
	void stopServer() throws Exception;
}
