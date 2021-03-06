/*
 * JBoss, Home of Professional Open Source
 * Copyright 2009 Red Hat Inc. and/or its affiliates and other
 * contributors as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a full listing of
 * individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.infinispan.commands.tx;

import org.infinispan.commands.Visitor;
import org.infinispan.context.InvocationContext;
import org.infinispan.context.impl.RemoteTxInvocationContext;
import org.infinispan.context.impl.TxInvocationContext;
import org.infinispan.transaction.xa.GlobalTransaction;
import org.infinispan.transaction.RemoteTransaction;
import org.infinispan.util.logging.Log;
import org.infinispan.util.logging.LogFactory;

/**
 * Command corresponding to a transaction rollback.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 * @author Pedro Ruivo
 * @since 4.0
 */
public class RollbackCommand extends AbstractTransactionBoundaryCommand {
   public static final byte COMMAND_ID = 13;

   private static final Log log = LogFactory.getLog(RollbackCommand.class);
   private boolean prepareSent;

   private RollbackCommand() {
      super(null); // For command id uniqueness test
   }

   public RollbackCommand(String cacheName, GlobalTransaction globalTransaction) {
      super(cacheName);
      this.globalTx = globalTransaction;
   }

   public RollbackCommand(String cacheName) {
      super(cacheName);
   }

   @Override
   public Object acceptVisitor(InvocationContext ctx, Visitor visitor) throws Throwable {
      return visitor.visitRollbackCommand((TxInvocationContext) ctx, this);
   }

   @Override
   public void visitRemoteTransaction(RemoteTransaction tx) {
      tx.invalidate();
   }

   @Override
   public byte getCommandId() {
      return COMMAND_ID;
   }

   @Override
   public String toString() {
      return "RollbackCommand {" + super.toString();
   }

   /**
    * choose the method to invoke depending if the total order protocol is be used or not
    *
    * @param ctx the context
    * @return the value to be returned to the invoked
    * @throws Throwable if something goes wrong
    */
   @Override
   public Object perform(InvocationContext ctx) throws Throwable {
         return super.performIgnoringUnexistingTransaction(ctx);
   }

   public final boolean wasPrepareSent() {
      return prepareSent;
   }

   public final void setPrepareSent(boolean prepareSent) {
      this.prepareSent = prepareSent;
   }

   @Override
   public Object[] getParameters() {
      return new Object[] {globalTx, prepareSent};
   }

   @Override
   public void setParameters(int commandId, Object[] args) {
      globalTx = (GlobalTransaction) args[0];
      prepareSent = (Boolean) args[1];
   }
}
