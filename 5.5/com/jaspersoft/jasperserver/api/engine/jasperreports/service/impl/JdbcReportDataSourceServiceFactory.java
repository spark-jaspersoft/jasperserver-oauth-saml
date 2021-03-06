package com.jaspersoft.jasperserver.api.engine.jasperreports.service.impl;




import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;
import javax.sql.DataSource;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import com.jaspersoft.jasperserver.api.engine.jasperreports.service.impl.JdbcDataSourceService;
import com.jaspersoft.jasperserver.api.engine.jasperreports.service.impl.PooledDataSource;
import com.jaspersoft.jasperserver.api.engine.jasperreports.service.impl.PooledJdbcDataSourceFactory;
import com.jaspersoft.jasperserver.api.engine.jasperreports.util.PooledObjectCache;
import com.jaspersoft.jasperserver.api.engine.jasperreports.util.PooledObjectEntry;

import org.apache.log4j.LogManager;
import org.springframework.security.Authentication;
import org.springframework.security.context.SecurityContextHolder;

import com.jaspersoft.jasperserver.api.JSException;
import com.jaspersoft.jasperserver.api.JSExceptionWrapper;
import com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.JdbcReportDataSource;
import com.jaspersoft.jasperserver.api.metadata.jasperreports.domain.ReportDataSource;
import com.jaspersoft.jasperserver.api.metadata.jasperreports.service.ReportDataSourceService;
import com.jaspersoft.jasperserver.api.metadata.jasperreports.service.ReportDataSourceServiceFactory;
import com.jaspersoft.jasperserver.api.metadata.user.domain.impl.client.MetadataUserDetails;

public class JdbcReportDataSourceServiceFactory  implements ReportDataSourceServiceFactory {

		private static final org.apache.log4j.Logger log = LogManager.getLogger(JdbcReportDataSourceServiceFactory.class);
private String query="SELECT * from Organization_Lookup where Organization=?";







protected static class PooledDataSourcesCache {

	        protected static class DataSourceEntry extends PooledObjectEntry {
				final PooledDataSource ds;

				public DataSourceEntry(Object key, PooledDataSource ds) {
					super(key);
					this.ds = ds;
				}

	            public boolean isActive() {
	                return ds.isActive();
	            }

			}

	        static class PooledDataSourcesCacheLog implements PooledObjectCache.PooledObjectCacheLog {

	            public void debug(Object key, DebugCode code) {
	                if (!log.isDebugEnabled()) return;
	                switch (code) {
	                    case STILL_ACTIVE:
							log.debug("Connection pool for " + key + " is still active, not expiring");
	                        break;
	                    case EXPIRING:
	                        log.debug("Expiring connection pool for " + key);
	                        break;
	                }

	            }
	        }

	        protected PooledObjectCache  pooledObjectCache;

			public PooledDataSourcesCache() {
			    pooledObjectCache = new PooledObjectCache();
	            pooledObjectCache.setLog(new PooledDataSourcesCacheLog());
			}

			public PooledDataSource get(Object key, long now) {
	            PooledObjectEntry entry = pooledObjectCache.get(key, now);
				if ((entry != null) && (entry instanceof DataSourceEntry)) return ((DataSourceEntry)entry).ds;
			    return null;
			}

			public void put(Object key, PooledDataSource ds, long now) {
				DataSourceEntry entry = new DataSourceEntry(key, ds);
				pooledObjectCache.put(key, entry, now);
			}
			
			public List removeExpired(long now, int timeout) {
	            List expired = new ArrayList();
	            List<PooledObjectEntry> expiredEntries = pooledObjectCache.removeExpired(now, timeout);
	            for (PooledObjectEntry objectEntry : expiredEntries) expired.add(((DataSourceEntry)objectEntry).ds);
				
	            return expired;
			}
        
		}

		private PooledJdbcDataSourceFactory pooledJdbcDataSourceFactory;
		private PooledDataSourcesCache poolDataSources;
		private int poolTimeout;
		
		private boolean defaultReadOnly = true;
		private boolean defaultAutoCommit = false;

		public JdbcReportDataSourceServiceFactory () {
			poolDataSources = new PooledDataSourcesCache();
		}

	    protected TimeZone getTimeZoneByDataSourceTimeZone(String dataSourceTimeZone) {
	        String timezoneId = dataSourceTimeZone == null ? "" : dataSourceTimeZone;
	        return timezoneId.isEmpty() ? TimeZone.getDefault() : TimeZone.getTimeZone(timezoneId);
	    }

		public ReportDataSourceService createService(ReportDataSource reportDataSource) {
			if (!(reportDataSource instanceof JdbcReportDataSource)) {
				throw new JSException("jsexception.invalid.jdbc.datasource", new Object[] {reportDataSource.getClass()});
			}
			JdbcReportDataSource jdbcDataSource = (JdbcReportDataSource) reportDataSource;
			 poolDataSources.removeExpired(System.currentTimeMillis(), 0);
			 DataSource dataSource = null;
			
			//check auth object on server to get accesstoken if you are on the server
			Authentication auth = SecurityContextHolder.getContext().getAuthentication();
			
			
            log.info("Authentication object: " + auth);
			// the authentication synchronizer creates a new MetadataUserDetails
			// object which holds all user information
			if(auth!=null){
			MetadataUserDetails user = (MetadataUserDetails) auth.getPrincipal();
			log.info("Metadata user:  " + user);
		//	log.info("Original auth object  : " + user.getOriginalAuthentication());
			
			 
			if(user.getTenantId()!=null){  
					
			
				
			String  fullquery=null;
		
					  fullquery=query.replaceFirst("\\?", "'" + user.getTenantId()+ "'");
					 Connection conn=null;
					 String jndiName="jdbc/jasperserver";
					  log.debug("Using query to pull new connection information:  " + fullquery);
					  try
						{
				            Context ctx = new InitialContext();
							DataSource ds = (DataSource) ctx.lookup("java:comp/env/" + jndiName);
				             conn = ds.getConnection();
				            if (log.isDebugEnabled()) {
				                log.debug("CreateConnection successful at for jndi jdbc: " + jndiName);
				            }
				                
				            }
						catch (NamingException e)
						{
							try {
				                //Added as short time solution due of http://bugzilla.jaspersoft.com/show_bug.cgi?id=26570.
				                //The main problem - this code executes in separate tread (non http).
				                //Jboss 7 support team recommend that you use the non-component environment namespace for such situations.
				                Context ctx = new InitialContext();
				                DataSource ds = (DataSource) ctx.lookup(jndiName);
				                 conn = ds.getConnection();
				                if (log.isDebugEnabled()) {
				                    log.debug("CreateConnection successful at for jndi jdbc: " + jndiName);
				                }
				              

				            }  catch (NamingException ex) {
				                
				                    log.error(e, e);
				                    throw new JSExceptionWrapper(e);
				                
				            } catch (SQLException ex) {
				               
				                    log.error(e, e);
				                    throw new JSExceptionWrapper(e);
				            }

						}
						catch (SQLException e)
						{
							
								log.error(e, e);
								throw new JSExceptionWrapper(e);
						}
					
					  if(conn!=null){
					  
					  
					  
					  String dbuname=null;
						 String dburl=null;
						 String driverclass=null;
						 String dbpw=null;
					  
							try {
						  PreparedStatement pst = (PreparedStatement) conn.prepareStatement(fullquery);
							ResultSet myres=null;
							 myres=pst.executeQuery();
							
							 
							 if(myres.next()){
							 log.debug("Pulling connection information from database using org: " +  user.getTenantId());
							
							 dbuname= (String)myres.getString("db_username");
							
							   dburl=(String)myres.getString("db_url");
							  
				 				dbpw=(String)myres.getString("db_password");
				 				driverclass=(String)myres.getString("jdbc_driver_classname");
							  
							 }
							  
							  log.debug("New Connection Username: " + dbuname);
							  log.debug("New Connection password: " + dbpw);
							  log.debug("New Connection url: " + dburl);
							 
							  myres.close();
							
							 conn.close();
					       poolDataSources.removeExpired(System.currentTimeMillis(), 0);
							} catch (Exception e) {
								
								log.error("Exception running query: " + fullquery + " to retrieve client db connection information.");
								log.error(e.getMessage());
								throw new JSExceptionWrapper("Error retrieving connection information from the db connection pool table for username= " + jdbcDataSource.getUsername() + ".  Report will not be filled correctly due to no connection information for this client.  Please check your connection table in your db.", e);
								//return new JdbcDataSourceService(dataSource, getTimeZoneByDataSourceTimeZone(jdbcDataSource.getTimezone()));
							}
						  
									if(dburl.equalsIgnoreCase("no_proxy")){
										 dataSource = getPoolDataSource(jdbcDataSource.getDriverClass(), jdbcDataSource.getConnectionUrl(), jdbcDataSource.getUsername(), jdbcDataSource.getPassword());
									}
									else if(driverclass!=null&&dburl!=null&&dbuname!=null&&dbpw!=null )
									
										{
											//create new proxy datasourc
											dataSource = getPoolDataSource(driverclass, dburl, dbuname, dbpw);
									  	}else{
											//normal datasource creation
												 dataSource = getPoolDataSource(jdbcDataSource.getDriverClass(), jdbcDataSource.getConnectionUrl(), jdbcDataSource.getUsername(), jdbcDataSource.getPassword());
											
											
										}
						
					  } else{
						  log.error("Connection to proxy table on jasperserver could not be created.");
						   throw new JSExceptionWrapper("Connection to proxy table on jasperserver could not be created.", null);
					  }
							
					

			}
			else{
				//normal datasource creation for superuser (orgid=null)
				 dataSource = getPoolDataSource(jdbcDataSource.getDriverClass(), jdbcDataSource.getConnectionUrl(), jdbcDataSource.getUsername(), jdbcDataSource.getPassword());
				}
			}
			return new JdbcDataSourceService(dataSource, getTimeZoneByDataSourceTimeZone(jdbcDataSource.getTimezone()));
		}

		protected DataSource getPoolDataSource(String driverClass, String url, String username, String password) {
			long now = System.currentTimeMillis();
			releaseExpiredPools(now);
				
			Object poolKey = createJdbcPoolKey(driverClass, url, username, password);
			PooledDataSource dataSource;
			synchronized (poolDataSources.pooledObjectCache) {
				dataSource = poolDataSources.get(poolKey, now);
				if (dataSource == null) {
					if (log.isDebugEnabled()) {
						log.debug("Creating connection pool for " + poolKey + ".");
					}
					dataSource = pooledJdbcDataSourceFactory.createPooledDataSource(
							driverClass, url, username, password,
							defaultReadOnly, defaultAutoCommit);
					poolDataSources.put(poolKey, dataSource, now);
				} else {
					if (log.isDebugEnabled()) {
						log.debug("Using cached connection pool for " + poolKey + ".");
					}
				}
			}

			return dataSource.getDataSource();
		}

		protected void releaseExpiredPools(long now) {
			List expired = null;
			synchronized (poolDataSources.pooledObjectCache) {
				if (getPoolTimeout() > 0) {
					expired = poolDataSources.removeExpired(now, getPoolTimeout());
				}
			}

			if (expired != null && !expired.isEmpty()) {
				for (Iterator it = expired.iterator(); it.hasNext();) {
					PooledDataSource ds = (PooledDataSource) it.next();
					try {
						ds.release();
					} catch (Exception e) {
						log.error("Error while releasing connection pool.", e);
						// ignore
					}
				}
			}
		}
		
		
		public PooledJdbcDataSourceFactory getPooledJdbcDataSourceFactory() {
			return pooledJdbcDataSourceFactory;
		}

		public void setPooledJdbcDataSourceFactory(
				PooledJdbcDataSourceFactory jdbcDataSourceFactory) {
			this.pooledJdbcDataSourceFactory = jdbcDataSourceFactory;
		}

		protected Object createJdbcPoolKey(String driverClass,
				String url, String username, String password) {
			return new JdbcPoolKey(driverClass, url, username, password);
		}
		
		protected static class JdbcPoolKey {
			private final String driverClass;
			private final String url;
			private final String username;
			private final String password;
			private final int hash;

			public JdbcPoolKey(String driverClass, String url, String username,
					String password) {
				this.driverClass = driverClass;
				this.url = url;
				this.username = username;
				this.password = password;

				int hashCode = 559;
				if (driverClass != null) {
					hashCode += driverClass.hashCode();
				}
				hashCode *= 43;
				if (url != null) {
					hashCode += url.hashCode();
				}
				hashCode *= 43;
				if (username != null) {
					hashCode += username.hashCode();
				}
				hashCode *= 43;
				if (password != null) {
					hashCode += password.hashCode();
				}
				
				hash = hashCode;
			}

			public boolean equals(Object obj) {
				if (!(obj instanceof JdbcPoolKey)) {
					return false;
				}
				if (this == obj) {
					return true;
				}
				
				JdbcPoolKey key = (JdbcPoolKey) obj;
				return
					(driverClass == null ? key.driverClass == null : (key.driverClass != null && driverClass.equals(key.driverClass))) &&
					(url == null ? key.url == null : (key.url != null && url.equals(key.url))) &&
					(username == null ? key.username == null : (key.username != null && username.equals(key.username))) &&
					(password == null ? key.password == null : (key.password != null && password.equals(key.password)));
			}

			public int hashCode() {
				return hash;
			}

			public String toString() {
				return "driver=\"" + driverClass + "\", url=\"" 
						+ url + "\", username=\"" + username + "\"";
			}
		}

		public int getPoolTimeout() {
			return poolTimeout;
		}

		public void setPoolTimeout(int poolTimeout) {
			this.poolTimeout = poolTimeout;
		}
		
		public boolean getDefaultReadOnly() {
			return defaultReadOnly;
		}

		public void setDefaultReadOnly(boolean defaultReadOnly) {
			this.defaultReadOnly = defaultReadOnly;
		}

		public boolean getDefaultAutoCommit() {
			return defaultAutoCommit;
		}

		public void setDefaultAutoCommit(boolean defaultAutoCommit) {
			this.defaultAutoCommit = defaultAutoCommit;
		}
		private String getJDBCConnectionString(String address, String org) {
			  return String.format("jdbc:sqlserver://%s:1433;databaseName=%s", address,org);
			 }
	
}
