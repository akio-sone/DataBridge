package org.renci.databridge.engines.ingest;

import java.util.logging.Logger;
import java.util.logging.Level;
import java.util.Properties;
import java.io.FileInputStream;

import org.renci.databridge.util.AMQPMessageListener;
import org.renci.databridge.message.IngestMetadataMessage;
import org.renci.databridge.persistence.metadata.MetadataDAOFactory;

/**
 * Entry point for metadata ingester subystem. 
 * This subsystem ingests metadata (from external sources) and persists it.
 * 
 * @author mrshoffn
 */
public class IngestEngine {

  protected static Logger logger = Logger.getLogger ("org.renci.databridge.engines.ingest");

  /**
   * @param args must contain a path to a properties file that defines:
   * org.renci.databridge.primaryQueue
   * org.renci.databridge.exchange
   * org.renci.databridge.queueHost
   * org.renci.databridge.relevancedb.dbType
   * org.renci.databridge.relevancedb.dbName
   * org.renci.databridge.relevancedb.dbPort
   */
  public static void main (String [] args) throws Exception {

    if (args.length != 1) { 
      throw new RuntimeException ("Usage: IngestEngine <abs_path_to_AMQPComms_props_file");
    }

    Properties p = new Properties ();
    p.load (new FileInputStream (args [0]));
    String dbTypeProp = p.getProperty ("org.renci.databridge.relevancedb.dbType", "mongo");
    String dbName = p.getProperty ("org.renci.databridge.relevancedb.dbName", "test2");
    String dbHost = p.getProperty ("org.renci.databridge.relevancedb.dbHost", "localhost");
    int dbPort = Integer.parseInt (p.getProperty ("org.renci.databridge.relevancedb.dbPort", "27017"));

    int dbType = -1; 
    if (dbTypeProp.compareToIgnoreCase ("mongo") != 0) {
      throw new RuntimeException ("Unsupported database type: " + dbTypeProp);
    } else {
      dbType = MetadataDAOFactory.MONGODB;
    }

    AMQPMessageListener aml = new AMQPMessageListener (args [0], new IngestMetadataMessage (), new IngestMetadataAMQPMessageHandler (dbType, dbName, dbHost, dbPort), logger);

    aml.start ();
    aml.join (); // keeps main thread from exiting

  }

}
