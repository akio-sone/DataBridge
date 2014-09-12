package org.renci.databridge.util;
import org.renci.databridge.persistence.metadata.*;
/**
 * This is the interface for classes that are to be instantiated and executed by the
 * RelevanceEngine
 * 
 * @author Howard Lander -RENCI (www.renci.org)
 * 
 */
public interface RelevanceInterface {

    double compareCollections(CollectionTransferObject collection1, 
                              CollectionTransferObject collection2);
}
