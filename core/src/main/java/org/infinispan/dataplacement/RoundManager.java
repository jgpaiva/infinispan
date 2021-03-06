package org.infinispan.dataplacement;

import org.infinispan.remoting.transport.Address;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Manages the round Id, blocks commands from round ahead of time and data placement request if another request is 
 * in progress
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class RoundManager {

   private static final Log log = LogFactory.getLog(RoundManager.class);

   private long currentRoundId;
   private long nextRoundId;

   private ClusterSnapshot roundClusterSnapshot;

   //in milliseconds
   private long coolDownTime;
   //after this time, a next round can start
   private long nextRoundTimestamp;

   private boolean roundInProgress;

   private boolean enabled = false;

   public RoundManager(long coolDownTime) {
      this.coolDownTime = coolDownTime;
      roundInProgress = false;
      updateNextRoundTimestamp();
   }

   /**
    * enables the data placement optimization
    */
   public final synchronized void enable() {
      enabled = true;
   }

   /**
    * returns the current round Id
    *
    * @return  the current round Id
    */
   public final synchronized long getCurrentRoundId() {
      return currentRoundId;
   }

   /**
    * returns a new round Id. this is invoked by the coordinator before start a new round´
    *
    * @return           a new round Id
    * @throws Exception if the last request happened recently or another request is in progress
    */
   public final synchronized long getNewRoundId() throws Exception {
      if (!enabled) {
         log.warn("Trying to start data placement algorithm but it is not enabled");
         throw new Exception("Data Placement optimization not enabled");
      }

      if (System.currentTimeMillis() < nextRoundTimestamp) {
         log.warn("Trying to start data placement algorithm but the last round happened recently");
         throw new Exception("Cannot start the next round. The last round happened recently");
      }

      if (roundInProgress) {
         log.warn("Trying to start data placement algorithm but it is already in progress");
         throw new Exception("Cannot start the next round. Another round is in progress");
      }

      updateNextRoundTimestamp();
      return ++nextRoundId;
   }

   /**
    * it blocks the current thread until the current round is higher or equals to the round id
    *
    *
    *
    * @param roundId    the round id
    * @param sender     the sender address 
    * @return           true if the round id is ensured, false otherwise (not enabled or interrupted)    
    */
   public final synchronized boolean ensure(long roundId, Address sender) {
      if (!enabled) {
         log.warnf("Not possible to ensure round %s. Data placement not enabled", roundId);
         return false;
      }

      if (log.isDebugEnabled()) {
         log.debugf("[%s] trying to ensure round %s", Thread.currentThread().getName(), roundId);
      }

      while (roundId > currentRoundId) {
         try {
            wait();
         } catch (InterruptedException e) {
            log.warnf("[%s] interrupted while trying to ensure round %s", Thread.currentThread().getName(), roundId);
            return false;
         }
      }

      if (log.isDebugEnabled()) {
         log.debugf("[%s] ensured round %s", Thread.currentThread().getName(), roundId);
      }

      if (!roundInProgress) {
         log.warnf("Not possible to process command. No data placement protocol is in progress", roundId);
         return false;
      }

      boolean acceptCommand = roundId == currentRoundId;

      if (acceptCommand) {
         if (!roundClusterSnapshot.contains(sender)) {
            log.warnf("RNot possible to process command. The sender [%s] is not in the current snapshot: %s", sender,
                      roundClusterSnapshot);
            return false;
         }
      }

      return acceptCommand;
   }

   /**
    * invoked in all members when a new round starts
    *
    * @param roundId                the new round id
    * @param roundClusterSnapshot   the round cluster snapshot
    * @param myAddress              the node address
    */
   public final synchronized boolean startNewRound(long roundId, ClusterSnapshot roundClusterSnapshot, Address myAddress) {
      currentRoundId = roundId;
      this.roundClusterSnapshot = roundClusterSnapshot;
      roundInProgress = roundClusterSnapshot.contains(myAddress);
      notifyAll();

      if (!roundInProgress) {
         log.warnf("Data placement start received but I [%s] am not in the round cluster snapshot: %s", myAddress,
                   roundClusterSnapshot);
      }

      return roundInProgress;
   }

   /**
    * mark a current round as finished
    */
   public final synchronized void markRoundFinished() {
      roundInProgress = false;
   }

   /**
    * sets the new cool down time. it only takes effect after the next round
    *
    * @param coolDownTime  the new cool down time in milliseconds
    */
   public final synchronized void setCoolDownTime(long coolDownTime) {
      this.coolDownTime = coolDownTime;
   }

   /**
    * returns the current cool down time between data placement rounds
    *
    * @return  the current cool down time between data placement rounds
    */
   public final synchronized long getCoolDownTime() {
      return coolDownTime;
   }

   /**
    * returns true if a data placement round is in progress
    *
    * @return  true if a data placement round is in progress, false otherwise
    */
   public final synchronized boolean isRoundInProgress() {
      return roundInProgress;
   }

   /**
    * returns true if the data placement is enabled
    *
    * @return  true if the data placement is enabled, false otherwise
    */
   public final synchronized boolean isEnabled() {
      return enabled;
   }

   /**
    * updates the cool down time before start a new request
    */
   private void updateNextRoundTimestamp() {
      nextRoundTimestamp = System.currentTimeMillis() + coolDownTime;
   }
}
