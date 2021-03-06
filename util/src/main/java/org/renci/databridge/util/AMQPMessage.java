package org.renci.databridge.util;
import com.rabbitmq.client.MessageProperties;
import com.rabbitmq.client.AMQP.BasicProperties;
import java.util.Map;
import java.util.HashMap;

/**
 * This class holds the message type and byte array for a 
 * message sent over the AMQP system. It is used for both sending
 * and receiving messages in the AMQPComms class.
 * 
 * @author Howard Lander -RENCI (www.renci.org)
 * 
 */
public class AMQPMessage {

     /** The routing key */
     private String routingKey;

     /** The message properties */
     private BasicProperties properties;

     /** The actual message */
     private byte[] bytes;

     /**
      * Default constructor with no arguments
      *
      */
     public AMQPMessage() {
     }


     /**
      * Constructor with the message as bytes
      *
      *  @param  theBytes A byte array containing the actual message
      */
     public AMQPMessage( byte[] theBytes) {
         bytes = theBytes;
     }

     
     /**
      * Get bytes for the message.
      *
      * @return message as byte[].
      */
     public byte[] getBytes()
     {
         return bytes;
     }
     
     /**
      * Get message element at specified index.
      *
      * @param index the index.
      * @return message at index as byte.
      */
     public byte getBytes(int index)
     {
         return bytes[index];
     }
     
     /**
      * Set message.
      *
      * @param message the value to set.
      */
     public void setBytes(byte[] message)
     {
         this.bytes = message;
     }
     
     /**
      * Set message at the specified index.
      *
      * @param message the value to set.
      * @param index the index.
      */
     public void setBytes(byte message, int index)
     {
         this.bytes[index] = message;
     }

     /**
      * Get properties.
      *
      * @return properties as AMQP.BasicProperties.
      */
     public BasicProperties getProperties()
     {
         return properties;
     }
     
     /**
      * Set properties.
      *
      * @param properties the value to set.
      */
     public void setProperties(BasicProperties properties)
     {
         this.properties = properties;
     }

     /**
      * Get Headers.
      *
      * @return headers as a Map<java.lang.String,java.lang.Object>
      */
     public Map<java.lang.String,java.lang.Object> getHeaders()
     {
         return properties.getHeaders();
     }

     /**
      * Get String Headers.
      *
      * @return headers whose value is a string as a Map<java.lang.String,java.lang.String>
      */
     public Map<String,String> getStringHeaders()
     {
         // Note that we expect all of the headers to have string values, but we want the user
         // to be able to depend on it. At the moment we are just going to drop any headers
         // with non-string valuse, but we could just as easily convert those values to string.
         Map<String, String> stringHeaders = new HashMap<String, String>();

         // Get the map<String,Object>
         Map<String,Object> props = properties.getHeaders();
         System.out.println("Getting ready to map properties");

         for (Map.Entry<String, Object> entry : props.entrySet()){
              // Note that this is assuming there is a useful toString method.  Rabbit seems
              // to be assuring that this is true, and since all of our headers will initially be 
              // Strings, this should be OK.  But if this code starts to misbehave, check
              // the type of the value object. 
              stringHeaders.put(entry.getKey(), entry.getValue().toString());
         }
         return stringHeaders;
     }
     
     /**
      * Get routingKey.
      *
      * @return routingKey as String.
      */
     public String getRoutingKey()
     {
         return routingKey;
     }
     
     /**
      * Set routingKey.
      *
      * @param routingKey the value to set.
      */
     public void setRoutingKey(String routingKey)
     {
         this.routingKey = routingKey;
     }
}
