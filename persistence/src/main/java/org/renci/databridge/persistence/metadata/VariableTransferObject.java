package org.renci.databridge.persistence.metadata;
import  java.util.*;

public class VariableTransferObject {
    private String name;
    private String description; // Free text metadata about the study
    private HashMap<String, String> extra;

    // These attributes are specific to the DataBridge
    private int    version;
    private int insertTime;     // Seconds since the epoch
    private String dataStoreId; // The id generated at insertion time.
    private String fileDataStoreId;
    
    /**
     * Get name.
     *
     * @return name as String.
     */
    public String getName()
    {
        return name;
    }
    
    /**
     * Set name.
     *
     * @param name the value to set.
     */
    public void setName(String name)
    {
        this.name = name;
    }
    
    /**
     * Get description.
     *
     * @return description as String.
     */
    public String getDescription()
    {
        return description;
    }
    
    /**
     * Set description.
     *
     * @param description the value to set.
     */
    public void setDescription(String description)
    {
        this.description = description;
    }
    
    /**
     * Get version.
     *
     * @return version as int.
     */
    public int getVersion()
    {
        return version;
    }
    
    /**
     * Set version.
     *
     * @param version the value to set.
     */
    public void setVersion(int version)
    {
        this.version = version;
    }
    
    /**
     * Get fileDataStoreId.
     *
     * @return fileDataStoreId as String.
     */
    public String getFileDataStoreId()
    {
        return fileDataStoreId;
    }
    
    /**
     * Set fileDataStoreId.
     *
     * @param fileDataStoreId the value to set.
     */
    public void setFileDataStoreId(String fileDataStoreId)
    {
        this.fileDataStoreId = fileDataStoreId;
    }
    
    /**
     * Get dataStoreId.
     *
     * @return dataStoreId as String.
     */
    public String getDataStoreId()
    {
        return dataStoreId;
    }
    
    /**
     * Set dataStoreId.
     *
     * @param dataStoreId the value to set.
     */
    public void setDataStoreId(String dataStoreId)
    {
        this.dataStoreId = dataStoreId;
    }

    /**
     * Get extra.
     *
     * @return extra as HashMap<String, String>
     */
    public HashMap<String, String> getExtra()
    {
        return extra;
    }

    /**
     * Set extra.
     *
     * @param extra the value to set.
     */
    public void setExtra(HashMap<String, String> extra)
    {
        this.extra = extra;
    }

    @Override
    public String toString ()
    {
        return "{" + getClass ().getName () + ": name: " + getName () + ", description: " + getDescription () + ", extra: " + getExtra () +  ", version: " + getVersion () + ", dataStoreId: " + getDataStoreId () + ", fileDataStoreId: " + getFileDataStoreId () + "}";
    }
    
    /**
     * Get insertTime.
     *
     * @return insertTime as int.
     */
    public int getInsertTime()
    {
        return insertTime;
    }
    
    /**
     * Set insertTime.
     *
     * @param insertTime the value to set.
     */
    public void setInsertTime(int insertTime)
    {
        this.insertTime = insertTime;
    }
}

