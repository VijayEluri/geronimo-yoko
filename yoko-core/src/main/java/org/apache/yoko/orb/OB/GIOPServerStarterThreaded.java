/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
*  contributor license agreements.  See the NOTICE file distributed with
*  this work for additional information regarding copyright ownership.
*  The ASF licenses this file to You under the Apache License, Version 2.0
*  (the "License"); you may not use this file except in compliance with
*  the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.yoko.orb.OB;

import org.apache.yoko.orb.OCI.Acceptor;
import org.apache.yoko.orb.OCI.Transport;

import java.util.concurrent.ExecutorService;
import java.util.logging.Level;

final class GIOPServerStarterThreaded extends GIOPServerStarter {
    //
    // The starter thread
    //
    protected final class Starter implements Runnable {

        public void run() {
            try {
                starterRun();
            } catch (RuntimeException ex) {
                Assert._OB_assert(ex);
            }

            logger.fine("Shutting down ORB server listener thread"); 
            //
            // Shutdown the acceptor so that no further connections are
            // accepted
            //
            logCloseAcceptor();
            acceptor_.shutdown();

            //
            // Accept all connections which might have queued up in the
            // listen() backlog
            do {

                try {
                    Transport t = acceptor_.accept(false);
                    if (t == null) {
                        logger.fine("Null transport received from a connect");
                        break;
                    }
                    GIOPConnection connection = new GIOPConnectionThreaded(orbInstance_, t, oaInterface_);
                    connection.setState(GIOPConnection.State.Closing);
                } catch (org.omg.CORBA.SystemException ex) {
                }
            } while (true);

            //
            // Close the acceptor
            //
            acceptor_.close();
            orbInstance_.getServerPhaser().arriveAndDeregister();

        }
    }

    // ----------------------------------------------------------------------
    // GIOPServerStarterThreaded package member implementation
    // ----------------------------------------------------------------------

    GIOPServerStarterThreaded(ORBInstance orbInstance, Acceptor acceptor, OAInterface oaInterface) {
        super(orbInstance, acceptor, oaInterface);

        logger.fine("GIOPServer thread started " + this + " using acceptor " + acceptor); 
        try {
            orbInstance_.getServerPhaser().register();
            //
            // Retrieve the thread group for the servers
            //
            ExecutorService executor = orbInstance_.getServerExecutor();

            //
            // Start starter thread
            //
            executor.submit(new Starter());
        } catch (OutOfMemoryError ex) {
            acceptor_.close();
            state_ = StateClosed;
            throw new org.omg.CORBA.IMP_LIMIT(org.apache.yoko.orb.OB.MinorCodes
                    .describeImpLimit(org.apache.yoko.orb.OB.MinorCodes.MinorThreadLimit),
                    org.apache.yoko.orb.OB.MinorCodes.MinorThreadLimit,
                    org.omg.CORBA.CompletionStatus.COMPLETED_NO);
        }
    }

    // ----------------------------------------------------------------------
    // GIOPServerStarterThreaded public member implementation
    // ----------------------------------------------------------------------

    //
    // Change the state of the worker
    //
    synchronized public void setState(int state) {
        //
        // Don't do anything if there is no state change
        //
        if (state_ == state) {
            return;
        }
        
        logger.fine("Setting server state to " + state); 

        //
        // It is not possible to transition backwards, except if we are
        // in holding state
        //
        if (state_ != StateHolding && state < state_) {
            return;
        }

        switch (state) {
        case StateActive: {
            for (int i = 0; i < connections_.size(); i++) {
                GIOPConnection w = (GIOPConnection) connections_.elementAt(i);
                w.setState(GIOPConnection.State.Active);
            }

            break;
        }

        case StateHolding:
            for (int i = 0; i < connections_.size(); i++) {
                GIOPConnection w = (GIOPConnection) connections_.elementAt(i);
                w.setState(GIOPConnection.State.Holding);
            }
            break;

        case StateClosed: {
            for (int i = 0; i < connections_.size(); i++) {
                GIOPConnection w = (GIOPConnection) connections_.elementAt(i);
                w.setState(GIOPConnection.State.Closing);
            }
            connections_.removeAllElements();

            //
            // Connect to this starter's acceptor, to unblock the call
            // to accept() in the starter thread
            //
            try {
                org.apache.yoko.orb.OCI.Transport tr = acceptor_.connect_self();
                tr.close();
            } catch (org.omg.CORBA.SystemException ex) {
                //
                // Ignore all system exceptions
                //
            }

            break;
        }
        }

        //
        // Update the state and notify about the state change
        //
        state_ = state;
        notifyAll();
    }

    //
    // Run method for starter thread
    //
    private void starterRun() {
        while (true) {
            //
            // Get new transport, blocking
            //
            org.apache.yoko.orb.OCI.Transport transport = null;
            try {
                transport = acceptor_.accept(true);
                Assert._OB_assert(transport != null);
            } catch (org.omg.CORBA.NO_PERMISSION ex) {
                //
                // Ignore NO_PERMISSION exceptions
                //
            } catch (org.omg.CORBA.SystemException ex) {
                //
                // Ignore exception. This probably means that the server
                // exceeded the number of available file descriptors.
                //
            }

            synchronized (this) {
                //
                // Reap the existing set of workers
                //
                reapWorkers();

                //
                // Check whether we are on hold
                //
                while (state_ == StateHolding) {
                    try {
                        logger.fine("Waiting on an inbound connection because the state is holding.  acceptor=" + acceptor_); 
                        wait();
                    } catch (InterruptedException ex) {
                    }
                }

                logger.fine("Processing an inbound connection with state=" + state_); 
                if (transport != null) {
                    try {
                        if (state_ == StateActive) {
                            //
                            // If we're active, we create and add a new
                            // worker to the worker list
                            //
                            GIOPConnection connection = new GIOPConnectionThreaded(
                                    orbInstance_, transport, oaInterface_);
                            connections_.addElement(connection);
                            connection.setState(GIOPConnection.State.Active);
                        } else {
                            logger.fine("Processing an inbound connection because state is closed"); 
                            //
                            // If we're closed, we create a new dummy
                            // worker, only in order to set it to
                            // StateClosing for proper connection shutdown
                            //
                            Assert._OB_assert(state_ == StateClosed);
                            logger.fine("Processing an inbound connection because state is closed"); 
                            GIOPConnection connection = new GIOPConnectionThreaded(
                                    orbInstance_, transport, oaInterface_);
                            logger.fine("Created connection " + connection); 

                            connection.setState(GIOPConnection.State.Closing);
                            logger.fine("set connection state to closing"); 
                        }
                    } catch (org.omg.CORBA.SystemException ex) {
                        String msg = "can't accept connection\n" + ex.getMessage();
                        logger.log(java.util.logging.Level.WARNING, msg, ex);
                    }
                }

                if (state_ == StateClosed) {
                    logger.fine("Shutting down server thread"); 
                    break;
                }
            }
        }
    }
}
