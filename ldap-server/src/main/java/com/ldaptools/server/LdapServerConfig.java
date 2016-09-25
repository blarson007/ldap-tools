package com.ldaptools.server;

public class LdapServerConfig {

	private String host = "localhost";
	private int port = 389;
	private String user = "CN=Administrator,CN=Users,DC=ad,DC=example,DC=com";
	private String password = "p@ssw0rd";
	private String baseDN = "DC=ad,DC=example,DC=com";
	private String userSearchBase = "CN=Users,DC=ad,DC=example,DC=com";
	
	private int numUsers = 100;
	private int numGroups = 5;
	
	private boolean enforceSchema = true;
	
	public LdapServerConfig() {  }
	
	public LdapServerConfig(int port) {
		this.port = port;
	}

	public String getHost() {
		return host;
	}

	public void setHost(String host) {
		this.host = host;
	}

	public int getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = port;
	}

	public String getUser() {
		return user;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getBaseDN() {
		return baseDN;
	}

	public String getUserSearchBase() {
		return userSearchBase;
	}

	public int getNumUsers() {
		return numUsers;
	}

	public void setNumUsers(int numUsers) {
		this.numUsers = numUsers;
	}

	public int getNumGroups() {
		return numGroups;
	}

	public void setNumGroups(int numGroups) {
		this.numGroups = numGroups;
	}

	public boolean isEnforceSchema() {
		return enforceSchema;
	}

	public void setEnforceSchema(boolean enforceSchema) {
		this.enforceSchema = enforceSchema;
	}
}
