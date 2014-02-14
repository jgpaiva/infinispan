package org.infinispan.dataplacement;

import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.infinispan.commons.hash.Hash;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.distribution.ch.DataPlacementConsistentHash;
import org.infinispan.remoting.rpc.RpcManager;
import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Collects all the remote and local access for each member for the key in which this member is the 
 * primary owner
 *
 * @author Zhongmiao Li
 * @author João Paiva
 * @author Pedro Ruivo
 * @since 5.2
 */
public class ObjectPlacementManager {

   private static final Log log = LogFactory.getLog(ObjectPlacementManager.class);

   private ClusterSnapshot clusterSnapshot;

   private ObjectRequest[] objectRequests;
   private Map<Object, Long> localRequests;

   private final BitSet requestReceived;

   private final DistributionManager distributionManager;
   private final int defaultNumberOfOwners;

   private final RpcManager rpcManager; //need this to get the local address
   
   //this can be quite big. save it as an array to save some memory
   private HashSet<Object> allKeysMoved;

   public ObjectPlacementManager(DistributionManager distributionManager, Hash hash, int defaultNumberOfOwners, RpcManager rpcManager){
      this.distributionManager = distributionManager;
      this.defaultNumberOfOwners = defaultNumberOfOwners;
      this.rpcManager = rpcManager;
      this.allKeysMoved = new HashSet<Object>();  

      requestReceived = new BitSet();
   }

   /**
    * reset the state (before each round)
    *
    * @param roundClusterSnapshot the current cluster members
    */
   public final synchronized void resetState(ClusterSnapshot roundClusterSnapshot) {
      clusterSnapshot = roundClusterSnapshot;
      objectRequests = new ObjectRequest[clusterSnapshot.size()];
      requestReceived.clear();
   }

   /**
    * collects the local and remote accesses for each member
    *
    * @param member        the member that sent the {@code objectRequest}
    * @param objectRequest the local and remote accesses
    * @return              true if all requests are received, false otherwise. It only returns true on the first
    *                      time it has all the objects
    */
   public final synchronized boolean aggregateRequest(Address member, ObjectRequest objectRequest) {
      if (hasReceivedAllRequests()) {
         return false;
      }

      int senderIdx = clusterSnapshot.indexOf(member);

      if (senderIdx < 0) {
         log.warnf("Received request list from %s but it does not exits in %s", member, clusterSnapshot);
         return false;
      }

      objectRequests[senderIdx] = objectRequest;
      requestReceived.set(senderIdx);

      logRequestReceived(member, objectRequest);

      return hasReceivedAllRequests();
   }

   /**
    * calculate the new owners based on the requests received.
    * @param localRequests objects requested locally 
    *
    * @return  a map with the keys to be moved and the new owners
    */
   public final synchronized Map<Object, Integer> calculateObjectsToReplicate() {
      Map<Object, Integer> newOwnersMap = new HashMap<Object, Integer>();

      for (Object obj : localRequests.keySet()){
    	  List<Address> tmp = distributionManager.getConsistentHash().locate(obj, defaultNumberOfOwners);
    	  if(tmp != null && tmp.size() > 0){
    		  Address owner = tmp.get(0);
    		  if(rpcManager.getAddress().equals(owner)){
    			  newOwnersMap.put(obj, tmp.size()+1);
    		  }else{
    			  log.warnf("Node %s is not owner for key: %s (%s is).",rpcManager.getAddress(),obj,owner);
    		  }
    	  }else{
    		  log.warnf("Could not get owner for key: ", obj);
    		  continue;
    	  }
      }
      allKeysMoved.addAll(newOwnersMap.keySet());
      return newOwnersMap;
   }
   
   /**
    * returns the local accesses and owners for the {@code key}
    *
    * @param key  the key
    * @return     the local accesses and owners for the key     
    */
   private Map<Integer, Long> getLocalAccesses(Object key) {
      Map<Integer, Long> localAccessesMap = new TreeMap<Integer, Long>();

      for (int memberIndex = 0; memberIndex < objectRequests.length; ++memberIndex) {
         ObjectRequest request = objectRequests[memberIndex];
         if (request == null) {
            continue;
         }
         Long localAccesses = request.getLocalAccesses().remove(key);
         if (localAccesses != null) {
            localAccessesMap.put(memberIndex, localAccesses);
         }
      }

      return localAccessesMap;
   }

   /**
    * returns the actual consistent hashing
    *
    * @return  the actual consistent hashing
    */
   private ConsistentHash getDefaultConsistentHash() {
      ConsistentHash hash = this.distributionManager.getConsistentHash();
      return hash instanceof DataPlacementConsistentHash ?
            ((DataPlacementConsistentHash) hash).getDefaultHash() :
            hash;
   }

   /**
    * returns all keys moved so far
    *
    * @return  all keys moved so far
    */
   public final Collection<Object> getKeysToMove() {
      return allKeysMoved;
   }
   
   private boolean hasReceivedAllRequests() {
      return requestReceived.cardinality() == clusterSnapshot.size();
   }

   private void logRequestReceived(Address sender, ObjectRequest request) {
      if (log.isTraceEnabled()) {
         StringBuilder missingMembers = new StringBuilder();

         for (int i = 0; i < clusterSnapshot.size(); ++i) {
            if (!requestReceived.get(i)) {
               missingMembers.append(clusterSnapshot.get(i)).append(" ");
            }
         }

         log.debugf("Object Request received from %s. Missing request are %s. The Object Request is %s", sender,
                    missingMembers, request.toString(true));
      } else if (log.isDebugEnabled()) {
         log.debugf("Object Request received from %s. Missing request are %s. The Object Request is %s", sender,
                    (clusterSnapshot.size() - requestReceived.cardinality()), request.toString());
      }
   }
   
   public void setLocalRequests(Map<Object, Long> reqs){
	   this.localRequests = reqs;
   }
}

