package org.infinispan.dataplacement.c50;

import org.infinispan.dataplacement.c50.keyfeature.Feature;
import org.infinispan.dataplacement.c50.keyfeature.FeatureValue;
import org.infinispan.dataplacement.c50.keyfeature.KeyFeatureManager;
import org.infinispan.dataplacement.c50.lookup.BloomFilter;
import org.infinispan.dataplacement.c50.tree.DecisionTree;
import org.infinispan.dataplacement.lookup.ObjectLookup;
import org.infinispan.dataplacement.stats.IncrementableLong;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * the object lookup implementation for the Bloom Filter + Machine Learner technique
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class C50MLObjectLookup implements ObjectLookup {

   private final BloomFilter bloomFilter;
   private final DecisionTree[] decisionTreeArray;
   private transient KeyFeatureManager keyFeatureManager;

   public C50MLObjectLookup(int numberOfOwners, BloomFilter bloomFilter) {
      this.bloomFilter = bloomFilter;
      decisionTreeArray = new DecisionTree[numberOfOwners];
   }

   public void setDecisionTreeList(int index, DecisionTree decisionTree) {
      decisionTreeArray[index] = decisionTree;
   }

   public void setKeyFeatureManager(KeyFeatureManager keyFeatureManager) {
      this.keyFeatureManager = keyFeatureManager;
   }

   public BloomFilter getBloomFilter() {
      return bloomFilter;
   }

   public DecisionTree[] getDecisionTreeArray() {
      return decisionTreeArray;
   }

   @Override
   public List<Integer> query(Object key) {
       if(true)
           return null;
      if (!bloomFilter.contains(key)) {
         return null;
      } else {
         Map<Feature, FeatureValue> keyFeatures = keyFeatureManager.getFeatures(key);
         List<Integer> owners = new LinkedList<Integer>();

         for (DecisionTree tree : decisionTreeArray) {
            owners.add(tree.query(keyFeatures));
         }
         return owners;
      }
   }

   @Override
   public List<Integer> queryWithProfiling(Object key, IncrementableLong[] phaseDurations) {
      long ts0 = System.nanoTime();
      if (!bloomFilter.contains(key)) {
         long ts1 = System.nanoTime();
         if (phaseDurations.length > 0) {
            phaseDurations[0].add(ts1 - ts0);
         }
         return null;
      } else {
         long ts1 = System.nanoTime();
         List<Integer> owners = new LinkedList<Integer>();
         Map<Feature, FeatureValue> keyFeatures = keyFeatureManager.getFeatures(key);
         long ts2 = System.nanoTime();

         for (DecisionTree tree : decisionTreeArray) {
            owners.add(tree.query(keyFeatures));
         }

         long ts3 = System.nanoTime();

         if (phaseDurations.length > 2) {
            phaseDurations[0].add(ts1 - ts0);
            phaseDurations[1].add(ts2 - ts1);
            phaseDurations[2].add(ts3 - ts2);
         } else if (phaseDurations.length > 1) {
            phaseDurations[0].add(ts1 - ts0);
            phaseDurations[1].add(ts2 - ts1);
         } else if (phaseDurations.length > 0) {
            phaseDurations[0].add(ts1 - ts0);
         }

         return owners;
      }
   }
}
