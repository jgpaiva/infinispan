package org.infinispan.tx.totalorder;

import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.container.versioning.VersionedReplStateTransferTest;
import org.infinispan.transaction.TransactionProtocol;
import org.testng.annotations.Test;

/**
 * @author Mircea Markus <mircea.markus@jboss.com> (C) 2011 Red Hat Inc.
 * @since 5.2
 */
@Test (groups = "functional", testName = "tx.totalorder.StateTransferTest")
public class TotalOrderVersionedStateTransferTest extends VersionedReplStateTransferTest {

   @Override
   protected void amendConfig(ConfigurationBuilder dcc) {
      dcc.transaction().transactionProtocol(TransactionProtocol.TOTAL_ORDER);
   }
}
