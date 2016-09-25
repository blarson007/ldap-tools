package com.ldaptools.servlet.apache;

import javax.servlet.http.HttpServlet;

import com.ldaptools.server.apache.ApacheLdapServer;


public class ApacheLdapServlet extends HttpServlet {

	private static final long serialVersionUID = 430363832679398558L;
	
	private ApacheLdapServer apacheLdapServer;

	public void init() {
		try {
			apacheLdapServer = new ApacheLdapServer();
			apacheLdapServer.startServer();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void destroy() {
		try {
			apacheLdapServer.stopServer();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
