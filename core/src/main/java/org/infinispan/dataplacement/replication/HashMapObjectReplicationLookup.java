package org.infinispan.dataplacement.replication;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.dataplacement.lookup.ObjectReplicationLookup;
import org.infinispan.dataplacement.stats.IncrementableLong;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * the object lookup implementation for the Hash Map technique
 * 
 * @author João Paiva
 * @since 5.2
 */
public class HashMapObjectReplicationLookup implements ObjectReplicationLookup {
	private static final Log log = LogFactory.getLog(HashMapObjectReplicationLookup.class);
	
	private static final long serialVersionUID = -388401031961540173L;
	private final Map<Object, Integer> lookup;

	public HashMapObjectReplicationLookup(Map<Object, Integer> keysToMove) {
		lookup = new HashMap<Object, Integer>(keysToMove);
		log.debugf("created a new lookup with: %s", lookup);
	}

	@Override
	public Integer query(Object key) {
		return lookup.get(key);
	}

	@Override
	public Integer queryWithProfiling(Object key,IncrementableLong[] phaseDurations) {
		long start = System.nanoTime();
		Integer result = lookup.get(key);
		long end = System.nanoTime();

		if (phaseDurations.length == 1) {
			phaseDurations[0].add(end - start);
		}

		return result;
	}
}
