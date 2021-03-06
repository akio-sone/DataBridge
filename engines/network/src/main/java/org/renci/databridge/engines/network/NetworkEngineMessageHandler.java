package org.renci.databridge.engines.network;
import org.renci.databridge.util.*;
import org.renci.databridge.persistence.metadata.*;
import org.renci.databridge.persistence.network.*;
import java.util.logging.Logger;
import java.util.logging.Level;
import com.rabbitmq.client.*;
import java.lang.Thread;
import java.lang.Exception;
import java.lang.InterruptedException;
import java.io.IOException;
import org.renci.databridge.message.*;
import java.lang.reflect.*;
import org.la4j.*;
import org.la4j.matrix.functor.*;
import java.util.*;
import java.net.*;
import java.io.*;
import com.google.gson.*;


/**
 * This class is executed in a thread of the Network Engine. The Network Engine
 * calls the constructor for this class with the AMQP message as a parameter.  It's
 * up to this class to decode the message according to the headers and implement the
 * required behaviors.
 *
 * @author Howard Lander -RENCI (www.renci.org)
 */

public class NetworkEngineMessageHandler implements AMQPMessageHandler {

   private Logger logger = Logger.getLogger ("org.renci.databridge.engine.network");

  // These are the individual portions of the message.
  // The routing key.
  private String routingKey;

  // The properties class.
  private com.rabbitmq.client.AMQP.BasicProperties properties;

  // The headers, expressed as a map of strings.
  private Map<String, String> stringHeaders;

  // The byte array for the contents of the message.
  private byte[] bytes;

  /**
  * Private class used to represent nodes in the network
  */
  class JsonNode {
     public String name;
     public String title;
     public String group;
     public String URL;
     public String description;

    /**
      * Constructor that includes name (id) and the title of the nodes. 
      * @param name The id of the node, currently from the metadata database.
      * @param title The title of the node, currently from the metadata database.
      */
     public JsonNode(String name, String title, String group, String URL, String description) {
        this.name = name;
        this.title = title;
        this.group = group;
        this.URL = URL;
        this.description = description;
     }

     public String toString() {
         return (this.name + " " + this.title + " " + this.group);
     }
  }

  /**
   * Private class used to represent links (edges) in the network
   */
  class JsonLink {
     public int source;
     public int target;
     public Double value;

    /**
      * Constructor that includes source node id, target node id and the similarity of the nodes.
      * @param source The source node id as an index into the array of nodes
      * @param target The target node id as an index into the array of nodes
      * @param value  The similarity between the nodes.
      */
     public JsonLink(int source, int target, Double value) {
        this.source = source;
        this.target = target;
        this.value = value;
     }

     public String toString() {
         return (this.source + " " + this.target + " " + this.value);
     }
  }

  /**
   * Private class used to represent an entire JSON file as an array of nodes and links.
   */
  class JsonNetworkFile {
     public ArrayList<JsonNode> nodes;
     public ArrayList<JsonLink> links;

    /**
      * Constructor that initializes the node and link ArrayLists.
      */
     public JsonNetworkFile() {
         this.nodes = new ArrayList<JsonNode>();
         this.links = new ArrayList<JsonLink>();
     }

    /**
      * Add a node to the JSON file
      * @param newNode theNode to add
      */
     public void addNode(JsonNode newNode) {
         this.nodes.add(newNode);
     }

    /**
      * Add a link to the JSON file
      * @param newLink thelink to add
      */
     public void addLink(JsonLink newLink) {
         this.links.add(newLink);
     }

     public String toString() {
         StringBuilder sb = new StringBuilder();
         sb.append("Nodes: ");
         for (JsonNode jsn: this.nodes) {
             sb.append(System.getProperty("line.separator"));
             sb.append(jsn.toString());
         }

         sb.append(System.getProperty("line.separator"));
         sb.append("Links: ");
         for (JsonLink jsn: this.links) {
             sb.append(System.getProperty("line.separator"));
             sb.append(jsn.toString());
         }
         return sb.toString();
     }
  }
  
  public void handle (AMQPMessage amqpMessage, Object extra) throws Exception {
      // Get the individual components of the the message and store
      // them in the fields
      routingKey = amqpMessage.getRoutingKey();
      properties = amqpMessage.getProperties();
      stringHeaders = amqpMessage.getStringHeaders();
      System.out.println("headers: " + stringHeaders);
      bytes = amqpMessage.getBytes();

      // get the message name
      String messageName = stringHeaders.get(NetworkEngineMessage.NAME);
      if (null == messageName) {
         System.out.println("messageName is missing");
      }
      System.out.println("messageName: " + messageName);

      // Call the function appropriate for the message
      if (messageName.compareTo(NetworkEngineMessage.INSERT_SIMILARITYMATRIX_JAVA_URI_NETWORKDB) == 0) {
         processInsertSimilarityMatrixJavaMessage(stringHeaders, extra);
      } else if (messageName.compareTo(NetworkEngineMessage.RUN_SNA_ALGORITHM_JAVA_NETWORKDB) == 0) {
         processRunSnaAlgorithmJavaMessage(stringHeaders, extra);
      } else if (messageName.compareTo(NetworkEngineMessage.CREATE_JSON_FILE_NETWORKDB_URI) == 0) {
         processCreateJSONFileMessage(stringHeaders, extra);
      } else {
         System.out.println("unimplemented messageName: " + messageName);
      }
  }

    /**
     * Handle the CREATE_JSON_FILE_NETWORKDB_URI  message by reading the network information and using
     * the Google gson code to write out a Json formatted file for the use of several applications. Note
     * that the initial supported JSON file format has been defined by an existing viz application. So
     * some of the field names don't currently match the DataBridge terminology.  
     * @param stringHeaders A map of the headers provided in the message
     * @param extra An object containing the needed DAO objects
     */
  public void processCreateJSONFileMessage( Map<String, String> stringHeaders, Object extra) {

      // We need several pieces of information before we can continue.  This info has to 
      // all be in the headers or we are toast.

      // 1) the nameSpace
      String nameSpace = stringHeaders.get(NetworkEngineMessage.NAME_SPACE);    
      if (null == nameSpace) {
         this.logger.log (Level.SEVERE, "No nameSpace in message");
         return;
      }

      // 2) the similarity_id
      String similarityId = stringHeaders.get(NetworkEngineMessage.SIMILARITY_ID);    
      if (null == similarityId) {
         this.logger.log (Level.SEVERE, "No similarityId in message");
         return;
      }

      // 3) the sna_id: This can be null, user just won't get any cluster info
      String snaId = stringHeaders.get(NetworkEngineMessage.SNA_ID);    

      // 4) the output file
      String outputFile = stringHeaders.get(NetworkEngineMessage.OUTPUT_FILE);    
      if (null == outputFile) {
         this.logger.log (Level.SEVERE, "No output file in message");
         return;
      }

      // In this case the extra parameter is an array of 2 objects, which are the metadata and
      // network factories.
      Object factoryArray[] = (Object[]) extra;

      MetadataDAOFactory metadataFactory = (MetadataDAOFactory) factoryArray[0];
      if (null == metadataFactory) {
         this.logger.log (Level.SEVERE, "MetadataDAOFactory is null");
         return;
      } 
      CollectionDAO theCollectionDAO = metadataFactory.getCollectionDAO();
      if (null == theCollectionDAO) {
         this.logger.log (Level.SEVERE, "CollectionDAO is null");
         return;
      } 

      NetworkDAOFactory networkFactory = (NetworkDAOFactory) factoryArray[1];
      if (null == networkFactory) {
         this.logger.log (Level.SEVERE, "NetworkDAOFactory is null");
         return;
      } 

      NetworkDyadDAO theDyadDAO = networkFactory.getNetworkDyadDAO();
      if (null == theDyadDAO) {
         this.logger.log (Level.SEVERE, "theDyadDAO is null");
         return;
      } 

      NetworkNodeDAO theNodeDAO = networkFactory.getNetworkNodeDAO();
      if (null == theNodeDAO) {
         this.logger.log (Level.SEVERE, "theNodeDAO is null");
         return;
      } 

      // Preliminaries are out of the way. We can proceed to the main algorithm
      // Used to assure that we only add each node to the file once.  
      ArrayList<String> theNames = new ArrayList<String>();
      JsonNetworkFile theJson = new JsonNetworkFile();

      // All the dyads are accounted for so we can use the google gson lib to write this out.
      Gson gson = new GsonBuilder().setFieldNamingPolicy(FieldNamingPolicy.IDENTITY).setPrettyPrinting().serializeNulls().disableHtmlEscaping().create();

      // Let's get all of the Dyads
      Iterator<NetworkDyadTransferObject> theDyads = theDyadDAO.getNetworkDyads(nameSpace, similarityId);
      while (theDyads.hasNext()) {
         NetworkDyadTransferObject thisDyad = theDyads.next();
         String id1 = thisDyad.getNode1MetadataId();
         String id2 = thisDyad.getNode2MetadataId();
         int index1 = theNames.indexOf(id1);
         if (index1 == -1) {
            theNames.add(id1);
            index1 = theNames.indexOf(id1);

            // Get the relevant collection from the metadata database
            CollectionTransferObject node1Collection = theCollectionDAO.getCollectionById(id1);

            // if the user has provided an snaID, than they want cluster info for the node.
            String clusterString = "";
            if (null != snaId) {
                NetworkNodeTransferObject node1 = theNodeDAO.getNetworkNode(thisDyad.getNode1DataStoreId());
                clusterString = (String)theNodeDAO.getPropertyFromNetworkNode(node1, snaId);
            }
   
            // Now we can add the first node to the file
            JsonNode jNode = new JsonNode(id1, node1Collection.getTitle(), clusterString,
                                          node1Collection.getURL(), node1Collection.getDescription());
            System.out.println("Description: " + node1Collection.getDescription());
            theJson.addNode(jNode);
         }

         // Dyad could be a singleton so check for null.
         if (id2 != null) {
            // There is a second node, so we can add both it and the link;
            int index2 = theNames.indexOf(id2);
            if (index2 == -1) {
               theNames.add(id2);
               index2 = theNames.indexOf(id2);
               // Get the relevant collection from the metadata database
               CollectionTransferObject node2Collection = theCollectionDAO.getCollectionById(id2);
   
               // if the user has provided an snaID, than they want cluster info for the node.
               String clusterString2 = "";
               if (null != snaId) {
                   NetworkNodeTransferObject node2 = theNodeDAO.getNetworkNode(thisDyad.getNode2DataStoreId());
                   clusterString2= (String)theNodeDAO.getPropertyFromNetworkNode(node2, snaId);
               }

               // Now we can add the second node to the file
               JsonNode jNode2 = new JsonNode(id2, node2Collection.getTitle(), clusterString2,
                                              node2Collection.getURL(), node2Collection.getDescription());
               theJson.addNode(jNode2);
            }

            // We can also add the link
            JsonLink theLink = new JsonLink(index1, index2, thisDyad.getSimilarity());
            theJson.addLink(theLink);
         }
      }

      try {
         BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8"));
         writer.write(gson.toJson(theJson));
         writer.close();
      } catch (Exception e) {
         this.logger.log (Level.SEVERE, "exception in processCreateJSONFileMessage: "+ e.getMessage(), e);
         return;
      }
  }


    /**
     * Handle the INSERT_SIMILARITYMATRIX_JAVA_URI_NETWORKDB message by inserting the
     * nodes and relationships into the database.
     * @param stringHeaders A map of the headers provided in the message
     * @param extra An object containing the needed DAO objects
     */
  public void processInsertSimilarityMatrixJavaMessage( Map<String, String> stringHeaders, Object extra) {

      // This function is going to need a private class to use with the eachNonZero call in the 
      // matrix, so here it is.
      class RelationshipInserter implements MatrixProcedure {
          private ArrayList<NetworkNodeTransferObject> nodeList;
          private NetworkRelationshipDAO theNetworkRelationshipDAO; 
          private String similarityInstanceId;

          /**
           * Constructor that includes the nodeList, DAO and similarity ID
           * needed by the callback function
           * @param nodeList An ArrayList of all of the node objects
           * @param theNetworkRelationshipDAO Needed to do the insert
           * @param similarityInstanceId Used as the type of the relationship. Comes from
           *                             the metadata database.
           */
          RelationshipInserter(ArrayList<NetworkNodeTransferObject> nodeList,
                               NetworkRelationshipDAO theNetworkRelationshipDAO,
                               String similarityInstanceId) {
              this.nodeList = nodeList;
              this.theNetworkRelationshipDAO = theNetworkRelationshipDAO;
              this.similarityInstanceId = similarityInstanceId;
          }

          /**
           * Callback function called for every non zero element in the sparse matrix.
           * The function is used to insert the relationships into the network that
           * represent the similarity values for the matrix. The lable for the relationship
           * is the id in the metadata database that represents the similarity instance that 
           * produced the similarity matrix.
           * @param i The row for the entry
           * @param j The column for the entry
           * @param value The value in the (i,j) entry for the matrix
           */
          public void apply(int i, int j, double value) {
              NetworkRelationshipTransferObject theNetworkTransfer = new NetworkRelationshipTransferObject();

              // Set the type using the similarityInstanceId from the metadata database
              theNetworkTransfer.setType(this.similarityInstanceId);

              // Add the value attribute to the relationship
              HashMap<String, Object> netAttributes = new HashMap<String, Object>();
              netAttributes.put(NetworkRelationshipDAO.METADATA_SIMILARITY_PROPERTY_NAME, value);
              theNetworkTransfer.setAttributes(netAttributes);

              // Should eventually log the result
              boolean result = this.theNetworkRelationshipDAO.insertNetworkRelationship(theNetworkTransfer, 
                                                                                        this.nodeList.get(i), 
                                                                                        this.nodeList.get(j));
              System.out.println("result in apply is: " + result);
          }
      }

      // We need several pieces of information before we can continue.  This info has to 
      // all be in the headers or we are toast.

      // 1) the input uri
      String inputURI = stringHeaders.get(NetworkEngineMessage.INPUT_URI);    
      if (null == inputURI) {
         this.logger.log (Level.SEVERE, "No inputURI in message");
         return;
      }

      // In this case the extra parameter is an array of 2 objects, which are the metadata and
      // network factories.
      Object factoryArray[] = (Object[]) extra;

      MetadataDAOFactory metadataFactory = (MetadataDAOFactory) factoryArray[0];
      if (null == metadataFactory) {
         this.logger.log (Level.SEVERE, "MetadataDAOFactory is null");
         return;
      } 
      CollectionDAO theCollectionDAO = metadataFactory.getCollectionDAO();
      if (null == theCollectionDAO) {
         this.logger.log (Level.SEVERE, "CollectionDAO is null");
         return;
      } 

      NetworkDAOFactory networkFactory = (NetworkDAOFactory) factoryArray[1];
      if (null == networkFactory) {
         this.logger.log (Level.SEVERE, "NetworkDAOFactory is null");
         return;
      } 

      // We'll need a DAO
      NetworkNodeDAO theNetworkNodeDAO = networkFactory.getNetworkNodeDAO();
      NetworkRelationshipDAO theNetworkRelationshipDAO = networkFactory.getNetworkRelationshipDAO();

      // Let's start by reading in the URI/file. If the first character is a slash
      // then we'll assume it's a file, otherwise we'll assume it's a URI. Note, this may not
      // work on windows.
      SimilarityFile theFile = new SimilarityFile();
      try {
          theFile.readFromURL(inputURI);
      } catch (Exception e) {
          this.logger.log (Level.SEVERE, "exception in processInsertSimilarityMatrixJavaMessage: "+ e.getMessage(), e);
          e.printStackTrace();
      }
      String nameSpace = theFile.getNameSpace();

      // Here is a classic space vs time tradeoff: we are going to keep all of the Node
      // structures in memory because we will need them later to insert the relationships. The 
      // alternative would be lots of search and retrieve.
      ArrayList<NetworkNodeTransferObject> nodeList = new ArrayList<NetworkNodeTransferObject>();

      //System.out.println("\tnameSpace: " + theFile.getNameSpace());
      // Now we'll add all of the nodes.
      for (String theId: theFile.getCollectionIds()) {
          NetworkNodeTransferObject theNode = new NetworkNodeTransferObject();
          theNode.setNameSpace(nameSpace);

          // Save for later
          theNode.setNodeId(theId);
          nodeList.add(theNode);
          int result = theNetworkNodeDAO.insertNetworkNode(theNode);
          if (result < 0) {
             this.logger.log (Level.SEVERE, "failure on insertNetworkNode");
             return;
          }
      }

      // Add the similarity matrix as relationships between nodes.
      org.la4j.matrix.sparse.CRSMatrix theMatrix = theFile.getSimilarityMatrix();

      // Create an instance of the inserter class, which actually does all the work.
      RelationshipInserter theInserter = 
          new RelationshipInserter(nodeList, theNetworkRelationshipDAO, theFile.getSimilarityInstanceId());
      theMatrix.eachNonZero(theInserter);
 
  }

    /**
     * Handle the RUN_SNA_ALGORITHM_JAVA_NETWORKDB message by executing the specified
     * code to run the SNA algorithm.
     * @param stringHeaders A map of the headers provided in the message
     * @param extra An object containing the needed DAO objects
     */
  public void processRunSnaAlgorithmJavaMessage( Map<String, String> stringHeaders, Object extra) {
      // We need several pieces of information before we can continue.  This info has to 
      // all be in the headers or we are toast.

      // 1) the class name
      String className = stringHeaders.get(NetworkEngineMessage.CLASS);    
      if (null == className) {
         this.logger.log (Level.SEVERE, "No class name in message");
         return;
      }

      // Let's try to load the class.
      Class<?> theClass = null;
      ClassLoader classLoader = NetworkEngineMessageHandler.class.getClassLoader();
      try {
         theClass = classLoader.loadClass(className);
      } catch (ClassNotFoundException e) {
         this.logger.log (Level.SEVERE, "Can't instantiate class " + className + ": " + e.getMessage(), e);
         return;
      }

      // We'll need an object of this type as well.
      Constructor<?> cons = null;
      Object theObject = null;
      NetworkProcessor thisProcessor = null;
      try {
         cons = (Constructor<?>) theClass.getConstructor(null);
         theObject = cons.newInstance(null);
         thisProcessor = (NetworkProcessor) theObject;
      } catch (Exception e) {
         this.logger.log (Level.SEVERE, "Can't create instance" + e.getMessage(), e);
         return;
      }

      // 2) the name space
      String nameSpace = stringHeaders.get(NetworkEngineMessage.NAME_SPACE);    
      if (null == nameSpace) {
         this.logger.log (Level.SEVERE, "No name space in message");
         return;
      }

      // 3) the similarityId
      String similarityId = stringHeaders.get(NetworkEngineMessage.SIMILARITY_ID);    
      if (null == similarityId) {
         this.logger.log (Level.SEVERE, "No similarityId in message");
         return;
      }

      // 4) any extra params to pass.  This can be null
      String params = stringHeaders.get(NetworkEngineMessage.PARAMS);    

      // In this case the extra parameter is an array of 2 objects, which are the metadata and
      // network factories.
      Object[] factoryArray = (Object[]) extra;

      MetadataDAOFactory metadataFactory = (MetadataDAOFactory) factoryArray[0];
      if (null == metadataFactory) {
         this.logger.log (Level.SEVERE, "MetadataDAOFactory is null");
         return;
      } 
      CollectionDAO theCollectionDAO = metadataFactory.getCollectionDAO();
      if (null == theCollectionDAO) {
         this.logger.log (Level.SEVERE, "CollectionDAO is null");
         return;
      } 

      NetworkDAOFactory networkFactory = (NetworkDAOFactory) factoryArray[1];
      if (null == networkFactory) {
         this.logger.log (Level.SEVERE, "NetworkDAOFactory is null");
         return;
      } 

      SNAInstanceDAO theSNAInstanceDAO = metadataFactory.getSNAInstanceDAO();
      if (null == theSNAInstanceDAO) {
         this.logger.log (Level.SEVERE, "SNAInstanceDAO is null");
         return;
      }

      NetworkNodeDAO theNetworkNodeDAO = networkFactory.getNetworkNodeDAO();
      if (null == theSNAInstanceDAO) {
         this.logger.log (Level.SEVERE, "SNAInstanceDAO is null");
         return;
      }

      // Let's add the SNAInstance.
      SNAInstanceTransferObject theSNAInstance = new SNAInstanceTransferObject();
      theSNAInstance.setNameSpace(nameSpace);
      theSNAInstance.setClassName(className);
      theSNAInstance.setMethod("processNetwork");
      theSNAInstance.setSimilarityInstanceId(similarityId);

      // let's find the highest version for this combination of nameSpace, className and method (if any)
      HashMap<String, String> versionMap = new HashMap<String, String>();
      versionMap.put("nameSpace", nameSpace);
      versionMap.put("className", className);
      versionMap.put("method", "processNetwork");
      versionMap.put("similarityInstanceId", similarityId);

      HashMap<String, String> sortMap = new HashMap<String, String>();
      sortMap.put("version", SNAInstanceDAO.SORT_DESCENDING);
      Integer limit = new Integer(1);

      // This is for the case of no previous instance
      theSNAInstance.setVersion(1);
      Iterator<SNAInstanceTransferObject> versionIterator =
          theSNAInstanceDAO.getSNAInstances(versionMap, sortMap, limit);
      if (versionIterator.hasNext()) {
         // Found a previous instance
         SNAInstanceTransferObject prevInstance = versionIterator.next();
         theSNAInstance.setVersion(prevInstance.getVersion() + 1);
      }

      try {
         boolean result = theSNAInstanceDAO.insertSNAInstance(theSNAInstance);
         System.out.println("Inserted Instance: " + theSNAInstance.getDataStoreId());
      } catch (Exception e) {
         this.logger.log (Level.SEVERE, "Can't insert SNA instance");
         return;
      }

      NetworkDyadDAO theNetworkDyadDAO = networkFactory.getNetworkDyadDAO();
      Iterator<NetworkDyadTransferObject> theDyads = 
           theNetworkDyadDAO.getNetworkDyads(nameSpace, similarityId);

      HashMap<String, String[]> clusterList = null;
      try {
          // Invoke the method
          clusterList = (HashMap<String, String[]>)thisProcessor.processNetwork(theDyads, params);
          String nReturnedClusters = Integer.toString(clusterList.size());
          HashMap<String, String> updateMap = new HashMap<String, String>();
          updateMap.put("nResultingClusters", nReturnedClusters);
          boolean result = theSNAInstanceDAO.updateSNAInstance(theSNAInstance, updateMap);
          String SNAId = theSNAInstance.getDataStoreId();
         
          // For each returned cluster, add the cluster info to the nodes.
          // We are allowing the possibility of a node being in more than one cluster
          for (Map.Entry<String, String[]> thisCluster : clusterList.entrySet()){
             String clusterKey = thisCluster.getKey();
             String[] nodesInThisCluster = thisCluster.getValue();
             for (String thisNodeId: nodesInThisCluster) {
                 NetworkNodeTransferObject theNode = theNetworkNodeDAO.getNetworkNode(thisNodeId);
                 String clusterString = 
                    (String) theNetworkNodeDAO.getPropertyFromNetworkNode(theNode, SNAId);
                 if (null == clusterString) {
                    // No problem, just means this node is not already in a cluster
                    theNetworkNodeDAO.addPropertyToNetworkNode(theNode, SNAId, clusterKey);
                 } else {
                    // This node was already assigned to a cluster, so we add this id
                    String multiClusterString = clusterString = "," + clusterKey;
                    theNetworkNodeDAO.deletePropertyFromNetworkNode(theNode, SNAId);
                    theNetworkNodeDAO.addPropertyToNetworkNode(theNode, SNAId, multiClusterString);
                 }
             }
          }
          
      } catch (Exception e) {
          this.logger.log (Level.SEVERE, "Can't invoke method " + "processNetwork" + " " + e.getMessage(), e);
          return;
      }

  }
 
  public void handleException (Exception exception) {

    this.logger.log (Level.WARNING, "handler received exception: ", exception);

// todo

  }
} 
