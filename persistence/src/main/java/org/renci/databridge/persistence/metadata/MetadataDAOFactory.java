package org.renci.databridge.persistence.metadata;
import  java.util.*;

public abstract class MetadataDAOFactory {
    // Right now we are only supporting Mongdo DB
    public static final int MONGODB = 1;

    public abstract CollectionDAO getCollectionDAO();
    public abstract FileDAO getFileDAO();
    public abstract VariableDAO getVariableDAO();
    public abstract SimilarityInstanceDAO getSimilarityInstanceDAO();
    public abstract SNAInstanceDAO getSNAInstanceDAO();
    public abstract ActionDAO getActionDAO();

    public static MetadataDAOFactory getMetadataDAOFactory(int factoryType,
                                                           String db,
                                                           String host,
                                                           int port) {
    
        switch (factoryType) {
            case MONGODB:
                return new MongoDAOFactory(db, host, port);
            default: 
                return null;
        }
    }
}
