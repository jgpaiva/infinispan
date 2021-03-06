package org.infinispan.dataplacement;

import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.DataPlacementConsistentHash;
import org.infinispan.remoting.transport.Address;
import org.infinispan.stats.topK.StreamLibContainer;
import org.infinispan.stats.topK.StreamLibContainer.Stat;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import static org.infinispan.stats.topK.StreamLibContainer.Stat.*;

/**
 * Manages all the remote access and creates the request list to send to each other member
 *
 * @author Zhongmiao Li
 * @author João Paiva
 * @author Pedro Ruivo
 * @since 5.2
 */
public class AccessesManager {
   private static final Log log = LogFactory.getLog(AccessesManager.class);

   private final DistributionManager distributionManager;

   private ClusterSnapshot clusterSnapshot;

   private Accesses[] accessesByPrimaryOwner;

   private final StreamLibContainer streamLibContainer;

   private boolean hasAccessesCalculated;

   private int maxNumberOfKeysToRequest;

   public AccessesManager(DistributionManager distributionManager, int maxNumberOfKeysToRequest) {
      this.distributionManager = distributionManager;
      this.maxNumberOfKeysToRequest = maxNumberOfKeysToRequest;
      streamLibContainer = StreamLibContainer.getInstance();
   }

   /**
    * reset the state (before each round)
    *
    * @param clusterSnapshot  the current cluster snapshot
    */
   public final synchronized void resetState(ClusterSnapshot clusterSnapshot) {
      this.clusterSnapshot = clusterSnapshot;
      accessesByPrimaryOwner = new Accesses[clusterSnapshot.size()];
      for (int i = 0; i < accessesByPrimaryOwner.length; ++i) {
         accessesByPrimaryOwner[i] = new Accesses();
      }
      hasAccessesCalculated = false;
   }

   /**
    * returns the request object list for the {@code member}
    *
    * @param member  the destination member
    * @return        the request object list. It can be empty if no requests are necessary
    */
   public synchronized final ObjectRequest getObjectRequestForAddress(Address member) {
      int addressIndex = clusterSnapshot.indexOf(member);

      if (addressIndex == -1) {
         log.warnf("Trying to get Object Requests to send to %s but it does not exists in cluster snapshot %s",
                   member, clusterSnapshot);
         return new ObjectRequest(null, null);
      }

      ObjectRequest request = accessesByPrimaryOwner[addressIndex].toObjectRequest();

      if (log.isInfoEnabled()) {
         log.debugf("Getting request list for %s. Request is %s", member, request.toString(log.isDebugEnabled()));
      }

      return request;
   }

   /**
    * sets the max number of keys to request to a new value, only if is higher than zero
    *
    * @param maxNumberOfKeysToRequest  the new value
    */
   public synchronized final void setMaxNumberOfKeysToRequest(int maxNumberOfKeysToRequest) {
      if (maxNumberOfKeysToRequest > 0) {
         this.maxNumberOfKeysToRequest = maxNumberOfKeysToRequest;
      }
   }

   /**
    * returns the max number of keys to request
    *
    * @return  returns the max number of keys to request
    */
   public int getMaxNumberOfKeysToRequest() {
      return maxNumberOfKeysToRequest;
   }

   /**
    * calculates the object request list to request to each member
    */
   public synchronized final void calculateAccesses(){
      if (hasAccessesCalculated) {
         return;
      }
      hasAccessesCalculated = true;

      if (log.isTraceEnabled()) {
         log.trace("Calculating accessed keys for data placement optimization");
      }

      RemoteTopKeyRequest request = new RemoteTopKeyRequest(streamLibContainer.getCapacity() * 2);

      request.merge(streamLibContainer.getTopKFrom(REMOTE_PUT, maxNumberOfKeysToRequest), 2);
      request.merge(streamLibContainer.getTopKFrom(REMOTE_GET, maxNumberOfKeysToRequest), 1);
      request.clear();

      sortObjectsByPrimaryOwner(request.toRequestMap(maxNumberOfKeysToRequest), true);

      request.clear();

      LocalTopKeyRequest localTopKeyRequest = new LocalTopKeyRequest();

      localTopKeyRequest.merge(streamLibContainer.getTopKFrom(LOCAL_PUT), 2);
      localTopKeyRequest.merge(streamLibContainer.getTopKFrom(LOCAL_GET), 1);
      request.clear();

      sortObjectsByPrimaryOwner(localTopKeyRequest.toRequestMap(), false);

      request.clear();

      if (log.isTraceEnabled()) {
         StringBuilder stringBuilder = new StringBuilder("Accesses:\n");
         for (int i = 0; i < accessesByPrimaryOwner.length; ++i) {
            stringBuilder.append(clusterSnapshot.get(i)).append(" ==> ").append(accessesByPrimaryOwner[i]).append("\n");
         }
         log.debug(stringBuilder);
      }
      
      saveTopKeyRequests(streamLibContainer);
      
      streamLibContainer.resetStat(REMOTE_GET);
      streamLibContainer.resetStat(LOCAL_GET);
      streamLibContainer.resetStat(REMOTE_PUT);
      streamLibContainer.resetStat(LOCAL_PUT);
   }
   
   public static class Pair<X,Y>{
      public Pair(X x, Y y){
         this.x = x;
         this.y = y;
      }
      X x;
      Y y;
   }

   static int round = 0;

   private void saveTopKeyRequests(StreamLibContainer streamLibContainer) {
      String filePath = "roundKeys_" + (round++);

      List<Pair<StreamLibContainer.Stat, String>> lst = new ArrayList<AccessesManager.Pair<StreamLibContainer.Stat, String>>();
      lst.add(new Pair<StreamLibContainer.Stat, String>(REMOTE_PUT,"remote puts"));
      lst.add(new Pair<StreamLibContainer.Stat, String>(REMOTE_GET,"remote gets"));
      lst.add(new Pair<StreamLibContainer.Stat, String>(LOCAL_PUT, "local puts"));
      lst.add(new Pair<StreamLibContainer.Stat, String>(LOCAL_GET, "local gets"));

      try {
         BufferedWriter bufferedWriter = new BufferedWriter(new FileWriter(filePath));

         for (int size : new int[] { maxNumberOfKeysToRequest, streamLibContainer.MAX_CAPACITY }) {
            for (Pair<Stat, String> i : lst) {
               Map<Object, Long> map = streamLibContainer.getTopKFrom(i.x, size);
               bufferedWriter.write(i.y + " at " + size + "= " + map.toString());
               bufferedWriter.newLine();
            }
            bufferedWriter.newLine();
         }
         bufferedWriter.flush();
         bufferedWriter.close();
      } catch (IOException e) {
         e.printStackTrace();
      }
   }

   public final ObjectRequest[] getAccesses() {
      ObjectRequest[] objectRequests = new ObjectRequest[accessesByPrimaryOwner.length];
      for (int i = 0; i < objectRequests.length; ++i) {
         objectRequests[i] = accessesByPrimaryOwner[i].toObjectRequest();
      }
      return objectRequests;
   }

   /**
    * sort the keys and number of access by primary owner
    *
    *
    * @param accesses   the remote accesses
    * @param remote     true if the accesses to process are from remote access, false otherwise
    */
   @SuppressWarnings("unchecked")
   private void sortObjectsByPrimaryOwner(Map<Object, Long> accesses, boolean remote) {
      Map<Object, List<Address>> primaryOwners = getDefaultConsistentHash().locateAll(accesses.keySet(), 1);

      for (Entry<Object, Long> entry : accesses.entrySet()) {
         Object key = entry.getKey();
         Address primaryOwner = primaryOwners.remove(key).get(0);
         int addressIndex = clusterSnapshot.indexOf(primaryOwner);

         if (addressIndex == -1) {
            log.warnf("Primary owner [%s] does not exists in cluster snapshot %s", primaryOwner, clusterSnapshot);
            continue;
         }

         accessesByPrimaryOwner[addressIndex].add(entry.getKey(), entry.getValue(), remote);
      }
   }

   /**
    * returns the actual consistent hashing
    *
    * @return  the actual consistent hashing
    */
   public final ConsistentHash getDefaultConsistentHash() {
      ConsistentHash hash = this.distributionManager.getConsistentHash();
      return hash instanceof DataPlacementConsistentHash ?
            ((DataPlacementConsistentHash) hash).getDefaultHash() :
            hash;
   }

   private class Accesses {
      private final Map<Object, Long> localAccesses;
      private final Map<Object, Long> remoteAccesses;

      private Accesses() {
         localAccesses = new HashMap<Object, Long>();
         remoteAccesses = new HashMap<Object, Long>();
      }

      private void add(Object key, long accesses, boolean remote) {
         Map<Object, Long> toPut = remote ? remoteAccesses : localAccesses;
         toPut.put(key, accesses);
      }

      private ObjectRequest toObjectRequest() {
         return new ObjectRequest(remoteAccesses.size() == 0 ? null : remoteAccesses,
                                  localAccesses.size() == 0 ? null : localAccesses);
      }

      @Override
      public String toString() {
         return "Accesses{" +
               "localAccesses=" + localAccesses.size() +
               ", remoteAccesses=" + remoteAccesses.size() +
               '}';
      }
   }

   public static class RemoteTopKeyRequest {

      private final Map<Object, Integer> keyAccessIndexMap;
      private final ArrayList<KeyAccess> sortedKeyAccess;

      public RemoteTopKeyRequest(int expectedSize) {
         keyAccessIndexMap = new HashMap<Object, Integer>();
         sortedKeyAccess = new ArrayList<KeyAccess>(expectedSize * 2);
      }

      private void add(Object key, long accesses) {
         if (accesses <= 0) {
            return;
         }

         Integer index = keyAccessIndexMap.get(key);

         if (index == null) {
            KeyAccess keyAccess = new KeyAccess(key, accesses);
            add(keyAccess);
         } else {
            KeyAccess keyAccess = sortedKeyAccess.get(index);
            keyAccess.accesses += accesses;
            update(index);
         }
      }

      private void add(KeyAccess keyAccess) {
         int indexToInsert = 0;
         while (indexToInsert < sortedKeyAccess.size()) {
            KeyAccess current = sortedKeyAccess.get(indexToInsert);
            if (keyAccess.accesses >= current.accesses) {
               addAndUpdateIndexes(keyAccess, indexToInsert);
               return;
            }
            indexToInsert++;
         }
         addAndUpdateIndexes(keyAccess, indexToInsert);
      }

      private void update(int index) {
         KeyAccess toUpdate = sortedKeyAccess.remove(index);
         if (index == sortedKeyAccess.size()) {
            index--;
         }

         while (index >= 0) {
            KeyAccess current = sortedKeyAccess.get(index);
            if (toUpdate.accesses <= current.accesses) {
               addAndUpdateIndexes(toUpdate, index + 1);
               return;
            }
            index--;
         }
         addAndUpdateIndexes(toUpdate, index + 1);
      }

      private void addAndUpdateIndexes(KeyAccess keyAccess, int index) {
         sortedKeyAccess.add(index, keyAccess);
         keyAccessIndexMap.put(keyAccess.key, index);
         for (int i = index + 1; i < sortedKeyAccess.size(); ++i) {
            keyAccessIndexMap.put(sortedKeyAccess.get(i).key, i);
         }
      }

      public final boolean contains(Object key) {
         return keyAccessIndexMap.containsKey(key);
      }

      public final KeyAccess get(Object key) {
         Integer index = keyAccessIndexMap.get(key);
         return index == null ? null : sortedKeyAccess.get(index);
      }

      public final ArrayList<KeyAccess> getSortedKeyAccess() {
         return sortedKeyAccess;
      }

      public final void merge(Map<Object, Long> toMerge, double multiplierFactor) {
         for (Entry<Object, Long> entry : toMerge.entrySet()) {
            add(entry.getKey(), (long) (entry.getValue() * multiplierFactor));
         }
      }

      public final Map<Object, Long> toRequestMap(int maxSize) {
         Map<Object, Long> map = new HashMap<Object, Long>();
         int size = 0;
         for (KeyAccess keyAccess : sortedKeyAccess) {
            if (size >= maxSize) {
               return map;
            }
            map.put(keyAccess.key, keyAccess.accesses);
            size++;
         }
         return map;
      }

      public final void clear() {
         keyAccessIndexMap.clear();
         sortedKeyAccess.clear();
      }

      @Override
      public String toString() {
         return "RemoteTopKeyRequest{" +
               "keyAccessIndexMap=" + keyAccessIndexMap +
               ", sortedKeyAccess=" + sortedKeyAccess +
               '}';
      }
   }

   public static class LocalTopKeyRequest {

      private final Map<Object, KeyAccess> keyAccessMap;

      public LocalTopKeyRequest() {
         keyAccessMap = new HashMap<Object, KeyAccess>();
      }

      private void add(Object key, long accesses) {
         if (accesses <= 0) {
            return;
         }

         KeyAccess access = keyAccessMap.get(key);

         if (access == null) {
            access = new KeyAccess(key, accesses);
            keyAccessMap.put(key, access);
         } else {
            access.accesses += accesses;
         }
      }

      public final boolean contains(Object key) {
         return keyAccessMap.containsKey(key);
      }

      public final KeyAccess get(Object key) {
         return keyAccessMap.get(key);
      }

      public final void merge(Map<Object, Long> toMerge, double multiplierFactor) {
         for (Entry<Object, Long> entry : toMerge.entrySet()) {
            add(entry.getKey(), (long) (entry.getValue() * multiplierFactor));
         }
      }

      public final Map<Object, Long> toRequestMap() {
         Map<Object, Long> map = new HashMap<Object, Long>();
         for (KeyAccess keyAccess : keyAccessMap.values()) {
            map.put(keyAccess.key, keyAccess.accesses);
         }
         return map;
      }

      public final void clear() {
         keyAccessMap.clear();
      }

      @Override
      public String toString() {
         return "LocalTopKeyRequest{" +
               "keyAccessMap=" + keyAccessMap +
               '}';
      }
   }

   public static class KeyAccess {

      private final Object key;
      private long accesses;

      public KeyAccess(Object key, long accesses) {
         this.key = key;
         this.accesses = accesses;
      }

      public final Object getKey() {
         return key;
      }

      public final long getAccesses() {
         return accesses;
      }

      @Override
      public String toString() {
         return "KeyAccess{" +
               "key=" + key +
               ", accesses=" + accesses +
               '}';
      }
   }
}
