package org.infinispan.configuration.cache;

import org.infinispan.configuration.AbstractTypedPropertiesConfiguration;
import org.infinispan.dataplacement.lookup.ObjectReplicationLookupFactory;
import org.infinispan.util.TypedProperties;

/**
 * Configures the Data Placement optimization
 *
 * @author Pedro Ruivo
 * @since 5.2
 */
public class DataPlacementConfiguration extends AbstractTypedPropertiesConfiguration {

   private final boolean enabled;
   private final int coolDownTime;
   private final ObjectReplicationLookupFactory objectReplicationLookupFactory;
   private final int maxNumberOfKeysToRequest;

   protected DataPlacementConfiguration(TypedProperties properties, boolean enabled, int coolDownTime,
		   ObjectReplicationLookupFactory objectReplicationLookupFactory, int maxNumberOfKeysToRequest) {
      super(properties);
      this.enabled = enabled;
      this.coolDownTime = coolDownTime;
      this.objectReplicationLookupFactory = objectReplicationLookupFactory;
      this.maxNumberOfKeysToRequest = maxNumberOfKeysToRequest;
   }

   
   public ObjectReplicationLookupFactory objectReplicationLookupFactory() {
	      return objectReplicationLookupFactory;
}

   public boolean enabled() {
      return enabled;
   }

   public int coolDownTime() {
      return coolDownTime;
   }

   public int maxNumberOfKeysToRequest() {
      return maxNumberOfKeysToRequest;
   }

   @Override
   public String toString() {
      return "DataPlacementConfiguration{" +
            "enabled=" + enabled +
            ", coolDownTime=" + coolDownTime +
            ", objectReplicationLookupFactory=" + objectReplicationLookupFactory +
            ", maxNumberOfKeysToRequest=" + maxNumberOfKeysToRequest +
            '}';
   }

   @Override
   public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof DataPlacementConfiguration)) return false;
      if (!super.equals(o)) return false;

      DataPlacementConfiguration that = (DataPlacementConfiguration) o;

      if (coolDownTime != that.coolDownTime) return false;
      if (maxNumberOfKeysToRequest != that.maxNumberOfKeysToRequest) return false;
      if (enabled != that.enabled) return false;
      if (objectReplicationLookupFactory != null ? !objectReplicationLookupFactory.equals(that.objectReplicationLookupFactory) : that.objectReplicationLookupFactory != null)
         return false;

      return true;
   }

   @Override
   public int hashCode() {
      int result = super.hashCode();
      result = 31 * result + (enabled ? 1 : 0);
      result = 31 * result + coolDownTime;
      result = 31 * result + maxNumberOfKeysToRequest;
      result = 31 * result + (objectReplicationLookupFactory != null ? objectReplicationLookupFactory.hashCode() : 0);
      return result;
   }
}
