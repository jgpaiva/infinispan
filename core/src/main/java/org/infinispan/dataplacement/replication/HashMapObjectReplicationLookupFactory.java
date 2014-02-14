package org.infinispan.dataplacement.replication;

import java.util.Map;

import org.infinispan.configuration.cache.Configuration;
import org.infinispan.dataplacement.lookup.ObjectReplicationLookup;
import org.infinispan.dataplacement.lookup.ObjectReplicationLookupFactory;

/**
 * Object Lookup Factory when Hash Map based technique is used
 *
 * @author João Paiva
 * @since 5.2
 */
public class HashMapObjectReplicationLookupFactory implements ObjectReplicationLookupFactory {

   @Override
   public void setConfiguration(Configuration configuration) {
      //nothing
   }

   @Override
   public ObjectReplicationLookup createObjectReplicationLookup(Map<Object, Integer> keysToMove) {
      return new HashMapObjectReplicationLookup(keysToMove);
   }

   @Override
   public void init(ObjectReplicationLookup objectLookup) {
      //nothing to init
   }

   @Override
   public int getNumberOfQueryProfilingPhases() {
      return 1;
   }
}
