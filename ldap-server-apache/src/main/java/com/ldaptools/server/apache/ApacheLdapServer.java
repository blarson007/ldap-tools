package com.ldaptools.server.apache;

import java.io.File;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.name.Dn;
import org.apache.directory.api.ldap.model.schema.SchemaManager;
import org.apache.directory.api.ldap.model.schema.registries.SchemaLoader;
import org.apache.directory.api.ldap.schema.extractor.SchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.extractor.impl.DefaultSchemaLdifExtractor;
import org.apache.directory.api.ldap.schema.loader.LdifSchemaLoader;
import org.apache.directory.api.ldap.schema.manager.impl.DefaultSchemaManager;
import org.apache.directory.api.util.exception.Exceptions;
import org.apache.directory.server.constants.ServerDNConstants;
import org.apache.directory.server.core.DefaultDirectoryService;
import org.apache.directory.server.core.api.CacheService;
import org.apache.directory.server.core.api.DirectoryService;
import org.apache.directory.server.core.api.DnFactory;
import org.apache.directory.server.core.api.InstanceLayout;
import org.apache.directory.server.core.api.partition.Partition;
import org.apache.directory.server.core.api.schema.SchemaPartition;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmIndex;
import org.apache.directory.server.core.partition.impl.btree.jdbm.JdbmPartition;
import org.apache.directory.server.core.partition.ldif.LdifPartition;
import org.apache.directory.server.ldap.LdapServer;
import org.apache.directory.server.protocol.shared.transport.TcpTransport;

import com.ldaptools.server.EmbeddedLdapServer;

public class ApacheLdapServer implements EmbeddedLdapServer {
	private static final String SYSTEM_PARTITION_ID = "system";

	private DirectoryService directoryService;
	private LdapServer server;
	
	public ApacheLdapServer() throws Exception {
		this(getTempWorkDir());
	}
	
	public ApacheLdapServer(File workDir) throws Exception {
		System.out.println("Setting work directory to " + workDir.getAbsolutePath());
		initDirectoryService(workDir);
	}
	
	@Override
	public void startServer() throws Exception {
        server = new LdapServer();
        server.setTransports(new TcpTransport(10389));
        server.setDirectoryService(directoryService);
        
        server.start();
    }
	
	@Override
	public void stopServer() throws Exception {
		server.stop();
		directoryService.shutdown();
	}
	
	@Override
	public void close() throws Exception {
		stopServer();
	}
	
	private void initDirectoryService(File workDir) throws Exception {
		// Initialize the LDAP service
		directoryService = new DefaultDirectoryService();
		directoryService.setInstanceLayout(new InstanceLayout(workDir));

		CacheService cacheService = new CacheService();
		cacheService.initialize(directoryService.getInstanceLayout());

		directoryService.setCacheService(cacheService);

		// first load the schema
		initSchemaPartition();

		// then the system partition
		// this is a MANDATORY partition
		// DO NOT add this via addPartition() method, trunk code complains about duplicate partition while initializing
		JdbmPartition systemPartition = new JdbmPartition(directoryService.getSchemaManager(), directoryService.getDnFactory());
		systemPartition.setId(SYSTEM_PARTITION_ID);
		systemPartition.setPartitionPath(new File(directoryService.getInstanceLayout().getPartitionsDirectory(), systemPartition.getId()).toURI());
		systemPartition.setSuffixDn(new Dn(ServerDNConstants.SYSTEM_DN));
		systemPartition.setSchemaManager(directoryService.getSchemaManager());

		// mandatory to call this method to set the system partition
		// Note: this system partition might be removed from trunk
		directoryService.setSystemPartition(systemPartition);

		// Disable the ChangeLog system
		directoryService.getChangeLog().setEnabled(false);
		directoryService.setDenormalizeOpAttrsEnabled(true);

		// Now we can create as many partitions as we need
		// TOOD: This is temporary.  The partitions will be specified by the user.  If none are specified, we'll just use the system partition.
		Partition zephyrPartition = addPartition("zephyr", "dc=zephyr,dc=jamfsw,dc=corp", directoryService.getDnFactory());

		// Index some attributes on the apache partition
		addIndex(systemPartition, "objectClass", "ou", "uid");
		addIndex(zephyrPartition, "objectClass", "ou", "uid");

		// And start the service
		directoryService.startup();
		
		insertDomains();
	}
	
	private void insertDomains() throws LdapException {
		for (Partition partition : directoryService.getPartitions()) {
			if (partition.getId().equals(SYSTEM_PARTITION_ID)) {
				continue;
			}
			
			try {
				directoryService.getAdminSession().lookup(partition.getSuffixDn());
			} catch (LdapException lnnfe) {
				Dn dnFoo = partition.getSuffixDn();
				Entry entryFoo = directoryService.newEntry(dnFoo);
				// TODO: These will not necessarily be our object classes 
				entryFoo.add("objectClass", "top", "domain", "extensibleObject");
				entryFoo.add("dc", partition.getId());
				directoryService.getAdminSession().add(entryFoo);
			}
		}	
	}
	
	private void initSchemaPartition() throws Exception {
		InstanceLayout instanceLayout = directoryService.getInstanceLayout();

		File schemaPartitionDirectory = new File(instanceLayout.getPartitionsDirectory(), "schema");

		// Extract the schema on disk (a brand new one) and load the registries
		if (schemaPartitionDirectory.exists()) {
			System.out.println("schema partition already exists, skipping schema extraction");
		} else {
			SchemaLdifExtractor extractor = new DefaultSchemaLdifExtractor(instanceLayout.getPartitionsDirectory());
			extractor.extractOrCopy();
		}

		SchemaLoader loader = new LdifSchemaLoader(schemaPartitionDirectory);
		SchemaManager schemaManager = new DefaultSchemaManager(loader);

		// We have to load the schema now, otherwise we won't be able
		// to initialize the Partitions, as we won't be able to parse
		// and normalize their suffix Dn
		schemaManager.loadAllEnabled();

		List<Throwable> errors = schemaManager.getErrors();

		if (errors.size() != 0) {
			throw new Exception(Exceptions.printErrors(errors));
		}

		directoryService.setSchemaManager(schemaManager);

		// Init the LdifPartition with schema
		LdifPartition schemaLdifPartition = new LdifPartition(schemaManager, directoryService.getDnFactory());
		schemaLdifPartition.setPartitionPath(schemaPartitionDirectory.toURI());

		// The schema partition
		SchemaPartition schemaPartition = new SchemaPartition(schemaManager);
		schemaPartition.setWrappedPartition(schemaLdifPartition);
		directoryService.setSchemaPartition(schemaPartition);
	}
	
	public Partition addPartition(String partitionId, String partitionDn) throws Exception {
	    return addPartition(partitionId, partitionDn, directoryService.getDnFactory());
	}
	
	private Partition addPartition(String partitionId, String partitionDn, DnFactory dnFactory) throws Exception {
		// Create a new partition with the given partition id 
		JdbmPartition partition = new JdbmPartition(directoryService.getSchemaManager(), dnFactory);
		partition.setId(partitionId);
		partition.setPartitionPath(new File(directoryService.getInstanceLayout().getPartitionsDirectory(), partitionId).toURI());
		partition.setSuffixDn(new Dn(partitionDn));
		directoryService.addPartition(partition);
		
		return partition;
    }
	
	@SuppressWarnings({ "rawtypes", "unchecked" })
	private void addIndex(Partition partition, String... attrs) {
		// Index some attributes on the partition
		Set indexedAttributes = new HashSet();

		for (String attribute : attrs) {
			indexedAttributes.add(new JdbmIndex(attribute, false));
		}

		((JdbmPartition) partition).setIndexedAttributes(indexedAttributes);
	}
	
	private static File getTempWorkDir() {
		File workDir = new File(System.getProperty("java.io.tmpdir") + "/embedded-server-work");
        boolean success = workDir.mkdirs();
        
        if (success == false) {
        	System.err.println("Failed to create temporary directory at " + workDir.getAbsolutePath());
        }
        
        return workDir;
	}
}
