package org.infinispan.dataplacement.stats;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.Map;
import java.util.Map.Entry;

import org.infinispan.dataplacement.DataPlacementManager;
import org.infinispan.dataplacement.c50.C50MLObjectLookup;
import org.infinispan.dataplacement.c50.lookup.BloomFilter;
import org.infinispan.dataplacement.c50.tree.DecisionTree;
import org.infinispan.dataplacement.lookup.ObjectReplicationLookup;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Task that checks the number of keys whose replication degree was increased,
 * the average query duration and the size of the object lookup
 * 
 * @author João Paiva
 * @since 5.2
 */
public class ObjectReplicationLookupTask implements Runnable {

	private static final Log log = LogFactory
			.getLog(ObjectReplicationLookupTask.class);

	private final ObjectReplicationLookup objectLookup;
	private final Map<Object, Integer> ownersInfoMap;
	private final Stats stats;
	private final IncrementableLong[] phaseDurations;

   private DataPlacementManager dataPlacementManager;

	public ObjectReplicationLookupTask(DataPlacementManager dataPlacementManager, Map<Object, Integer> ownersInfoMap,
			ObjectReplicationLookup objectLookup, Stats stats) {
		this.ownersInfoMap = ownersInfoMap;
		this.objectLookup = objectLookup;
		this.stats = stats;
		this.phaseDurations = stats.createQueryPhaseDurationsArray();
		this.dataPlacementManager = dataPlacementManager;
	}

	@Override
	public void run() {
		int errors = 0;
		for (Entry<Object, Integer> entry : ownersInfoMap.entrySet()) {
			Integer result = objectLookup.queryWithProfiling(entry.getKey(),
					phaseDurations);
			errors += result != entry.getValue() ? 1 : 0;
		}
		stats.wrongOwnersErrors(errors);
		stats.totalKeysMoved(ownersInfoMap.size());
		stats.queryDuration(phaseDurations);
		stats.objectLookupSize(serializedSize(objectLookup));
		dataPlacementManager.setTotalKeysMoved(ownersInfoMap.size());
      {
         int acc = 0;
         for (Integer i : ownersInfoMap.values()) {
            acc += i;
         }
         dataPlacementManager.setDPAverageReplDegree(acc/ownersInfoMap.size());
      }
		dataPlacementManager.setWrongOwnersErrors(errors);
		// TODO: update with C50 real implementation
	}

	private int serializedSize(Object object) {
		int size = 0;
		try {
			ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
			ObjectOutputStream objectOutputStream = new ObjectOutputStream(
					byteArrayOutputStream);
			objectOutputStream.writeObject(object);
			objectOutputStream.flush();

			size = byteArrayOutputStream.toByteArray().length;
			objectOutputStream.close();
			byteArrayOutputStream.close();
		} catch (IOException e) {
			log.warnf(e, "Error calculating object size of %s", object);
		}
		return size;
	}
}
