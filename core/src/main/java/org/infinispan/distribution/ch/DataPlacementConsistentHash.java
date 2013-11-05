package org.infinispan.distribution.ch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.infinispan.dataplacement.ClusterSnapshot;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * The consistent hash function implementation that the Object Lookup implementations from the Data Placement 
 * optimization
 *
 * @author Zhongmiao Li
 * @author João Paiva
 * @author Pedro Ruivo
 * @since 5.2
 */
public class DataPlacementConsistentHash extends AbstractConsistentHash {
	private static final Log log = LogFactory.getLog(DataPlacementConsistentHash.class);

   private ConsistentHash defaultConsistentHash;
   private static ArrayList<ArrayList<ObjectLookup>> objectsLookup;
   private final ClusterSnapshot clusterSnapshot;

   @SuppressWarnings("unchecked")
   public DataPlacementConsistentHash(ClusterSnapshot clusterSnapshot) {
      this.clusterSnapshot = clusterSnapshot;
      if(objectsLookup == null || objectsLookup.size() != clusterSnapshot.size()) {
	      objectsLookup = new ArrayList<ArrayList<ObjectLookup>>();
	      for(int i = 0; i < clusterSnapshot.size(); i++) {
	    	  objectsLookup.add(new ArrayList<ObjectLookup>());
	      }
      }
   }

   public void addObjectLookup(Address address, ObjectLookup objectLookup) {
      if (objectLookup == null) {
         return;
      }
      int index = clusterSnapshot.indexOf(address);
      if (index == -1) {
         return;
      }
      ArrayList<ObjectLookup> lst = objectsLookup.get(index);
      if(lst.isEmpty()) {
    	  lst.add(objectLookup);
    	  log.info("added FIRST object lookup for index=" + index + " for " + this);
      }else {
    	  if(objectLookup.getEpoch() == lst.get(0).getEpoch()) {
    		  lst.set(0, objectLookup);
    		  log.info("set object lookup for index=" + index + " for " + this);
    	  }else {
    		  lst.add(0, objectLookup);
    		  log.info("added NEW object lookup for index=" + index + " for " + this);
    	  }
      }
   }

   @Override
   public void setCaches(Set<Address> caches) {
      defaultConsistentHash.setCaches(caches);
   }

   @Override
   public Set<Address> getCaches() {
      return defaultConsistentHash.getCaches();
   }

   @Override
   public List<Address> locate(Object key, int replCount) {
      List<Address> defaultOwners = defaultConsistentHash.locate(key, replCount);
      int primaryOwnerIndex = clusterSnapshot.indexOf(defaultOwners.get(0));

      if (primaryOwnerIndex == -1) {
         return defaultOwners;
      }

      List<ObjectLookup> lookup = objectsLookup.get(primaryOwnerIndex);

      
      if (lookup == null) {
         return defaultOwners;
      }

      for(ObjectLookup it : lookup) {
    	  List<Integer> newOwners = it.query(key);
    	  
    	  if (newOwners == null || newOwners.size() != defaultOwners.size()) {
    	         continue;
    	  }

          List<Address> ownersAddress = new LinkedList<Address>();
          for (int index : newOwners) {
             Address owner = clusterSnapshot.get(index);
             if (owner == null) {
                return defaultOwners;
             }
             ownersAddress.add(owner);
          }

          return ownersAddress;
      }
      
      return defaultOwners;
   }

   @Override
   public List<Integer> getHashIds(Address a) {
      return Collections.emptyList();
   }


   public void setDefault(ConsistentHash defaultHash) {
      defaultConsistentHash = defaultHash;
   }

   public ConsistentHash getDefaultHash() {
      return defaultConsistentHash;
   }
   
   public String toString() {
	   return "DataPlacementConsistentHash{" +
	            "hashcode=" + this.hashCode() +
	            ", defaultConsistentHash=" + defaultConsistentHash +
	            ", clusterSnapshot=" + clusterSnapshot +
	            ", objectsLookup=" + objectsLookup +
	            '}';
   }
}
