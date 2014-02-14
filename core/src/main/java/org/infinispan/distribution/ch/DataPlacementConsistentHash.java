package org.infinispan.distribution.ch;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.infinispan.dataplacement.ClusterSnapshot;
import org.infinispan.dataplacement.lookup.ObjectReplicationLookup;
import org.infinispan.remoting.transport.Address;

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

   private ConsistentHash defaultConsistentHash;
   private final ObjectReplicationLookup[] objectsReplicationLookup;
   private final ClusterSnapshot clusterSnapshot;

   public DataPlacementConsistentHash(ClusterSnapshot clusterSnapshot) {
      this.clusterSnapshot = clusterSnapshot;
      objectsReplicationLookup = new ObjectReplicationLookup[clusterSnapshot.size()];
   }

   public void addObjectReplicationLookup(Address address, ObjectReplicationLookup objectLookup) {
      if (objectLookup == null) {
         return;
      }
      int index = clusterSnapshot.indexOf(address);
      if (index == -1) {
         return;
      }
      objectsReplicationLookup[index] = objectLookup;
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

      ObjectReplicationLookup lookup = objectsReplicationLookup[primaryOwnerIndex];

      if (lookup == null) {
         return defaultOwners;
      }

      Integer newReplication = lookup.query(key);

      if (newReplication == null) {
         return defaultOwners;
      }

      List<Address> newOwners = defaultConsistentHash.locate(key, newReplication);
      return newOwners;
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
}
