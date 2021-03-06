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

package org.apache.yoko.orb.OCI.IIOP;

import static org.apache.yoko.orb.OCI.IIOP.Exceptions.*;
import static org.apache.yoko.orb.OB.MinorCodes.*;

import org.apache.yoko.orb.OB.MinorCodes;
import org.apache.yoko.orb.OCI.Acceptor;
import org.apache.yoko.orb.OCI.SendReceiveMode;
import org.omg.CORBA.COMM_FAILURE;
import org.omg.CORBA.CompletionStatus;
import org.omg.CORBA.NO_IMPLEMENT;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.apache.yoko.orb.OCI.SendReceiveMode.*;

final public class Transport_impl extends org.omg.CORBA.LocalObject implements
        org.apache.yoko.orb.OCI.Transport {
    // This data member must not be private because the info object
    // must be able to access it
    public final java.net.Socket socket_; // The socket

    private java.io.InputStream in_; // The socket's input stream

    private java.io.OutputStream out_; // The socket's output stream

    private volatile boolean shutdown_; // True if shutdown() was called

    private int soTimeout_ = 0; // The value for setSoTimeout()

    private TransportInfo_impl info_; // Transport information
    
    // the real logger backing instance.  We use the interface class as the locator
    static final Logger logger = Logger.getLogger(org.apache.yoko.orb.OCI.Transport.class.getName());

    // ------------------------------------------------------------------
    // Private and protected member implementations
    // ------------------------------------------------------------------

    private void setSoTimeout(int t) {
        if (soTimeout_ != t) {
            soTimeout_ = t;

            try {
                socket_.setSoTimeout(soTimeout_);
            } catch (java.net.SocketException ex) {
                logger.log(Level.FINE, "Socket setup error", ex); 
                
                throw (COMM_FAILURE)new COMM_FAILURE(
                        org.apache.yoko.orb.OB.MinorCodes
                                .describeCommFailure(org.apache.yoko.orb.OB.MinorCodes.MinorSetSoTimeout)
                                + ": socket error during setSoTimeout: "
                                + ex.getMessage(),
                        org.apache.yoko.orb.OB.MinorCodes.MinorSetSoTimeout,
                        org.omg.CORBA.CompletionStatus.COMPLETED_NO).initCause(ex);
            } catch (java.lang.NullPointerException ex) {
                logger.log(Level.FINE, "Socket setup error", ex); 
                throw (COMM_FAILURE)new COMM_FAILURE(
                        org.apache.yoko.orb.OB.MinorCodes
                                .describeCommFailure(org.apache.yoko.orb.OB.MinorCodes.MinorSetSoTimeout)
                                + ": NullPointerException error during setSoTimeout: "
                                + ex.getMessage(), 
                        org.apache.yoko.orb.OB.MinorCodes.MinorSetSoTimeout,
                        org.omg.CORBA.CompletionStatus.COMPLETED_NO).initCause(ex); 
            }
        }
    }

    private void setBlock(boolean block) {
        if (block)
            setSoTimeout(0);
        else
            setSoTimeout(1);
    }

    //
    // Shutdown the sending or receiving side of a socket. If how == 0,
    // shutdown the receiving side. If how == 1, shutdown the sending
    // side. If how == 2, shutdown both.
    //
    private void shutdownSocket() {
        try {
                try {
                    socket_.shutdownInput();
                } catch (UnsupportedOperationException e) {
                // if we're using an SSL connection, this is an unsupported operation.
                // just ignore the error and proceed to the close.
                }
                try {
                    socket_.shutdownOutput();
                } catch (UnsupportedOperationException e) {
                // if we're using an SSL connection, this is an unsupported operation.
                // just ignore the error and proceed to the close.
                }
        } catch (java.net.SocketException ex) {
            //
            // Some VMs (namely JRockit) raise a SocketException if
            // the socket has already been closed.
            // This exception can be ignored.
            //
        } catch (IOException ex) {
            logger.log(Level.FINE, "Socket shutdown error", ex); 
            throw (InternalError)new InternalError().initCause(ex);
        }
    }

    // ------------------------------------------------------------------
    // Standard IDL to Java Mapping
    // ------------------------------------------------------------------

    public String id() {
        return PLUGIN_ID.value;
    }

    public int tag() {
        return org.omg.IOP.TAG_INTERNET_IOP.value;
    }

    public SendReceiveMode mode() {
        return SendReceive;
    }

    public int handle() {
        throw new NO_IMPLEMENT();
    }

    public void close() {
        //
        // I must set socket_ to null *before* the close or the code
        // below, to avoid a race condition with send/receive
        //

        //
        // Close the socket
        //
        shutdownSocket(); // This helps to unblock threads
        // blocking in recv()
        try {
            socket_.close();
        } catch (IOException ex) {
        }
    }

    public void shutdown() {
        logger.info("shutdown: " + this); 
        shutdown_ = true;
        shutdownSocket(); // Shutdown send side only
        // blocking in recv()
        try {
            socket_.close();
        } catch (IOException ex) {
        }
    }

    public void receive(org.apache.yoko.orb.OCI.Buffer buf, boolean block) {
        setBlock(block);

        logger.fine("receiving a buffer of " + buf.rest_length() + " from " + socket_ + " using transport " + this); 
        while (!buf.is_full()) {
            try {
                int result = in_.read(buf.data(), buf.pos(), buf.rest_length());
                if (result <= 0) {
                    throw new COMM_FAILURE(describeCommFailure(MinorRecvZero), MinorRecvZero, CompletionStatus.COMPLETED_NO);
                }
                buf.advance(result);
            } catch (InterruptedIOException ex) {
                logger.log(Level.FINE, "Received interrupted exception", ex); 
                buf.advance(ex.bytesTransferred);

                if (!block)
                    return;
                if (shutdown_)
                    throw asCommFailure(ex, MinorCodes.MinorRecv, "Interrupted I/O exception during shutdown");
            } catch (java.io.IOException ex) {
                logger.log(Level.FINE, "Socket read error", ex); 
                throw asCommFailure(ex, MinorCodes.MinorRecv, "I/O error during read");
            } catch (java.lang.NullPointerException ex) {
                logger.log(Level.FINE, "Socket read error", ex);
                throw asCommFailure(ex, MinorCodes.MinorRecv, "NullPointerException during read");
            }
        }
    }


    public boolean receive_detect(org.apache.yoko.orb.OCI.Buffer buf,
            boolean block) {
        setBlock(block);

        while (!buf.is_full()) {
            try {
                int result = in_.read(buf.data(), buf.pos(), buf.rest_length());
                if (result <= 0)
                    return false;
                buf.advance(result);
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);

                if (!block)
                    return true;
            } catch (java.io.IOException ex) {
                return false;
            } catch (java.lang.NullPointerException ex) {
                return false;
            }
        }

        return true;
    }

    public void receive_timeout(org.apache.yoko.orb.OCI.Buffer buf, int t) {
        if (t < 0)
            throw new InternalError();

        if (t == 0) {
            receive(buf, false);
            return;
        }

        setSoTimeout(t);

        while (!buf.is_full()) {
            try {
                int result = in_.read(buf.data(), buf.pos(), buf.rest_length());
                if (result <= 0) {
                    throw new COMM_FAILURE(describeCommFailure(MinorRecvZero), MinorRecvZero, CompletionStatus.COMPLETED_NO);
                }
                buf.advance(result);
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);
                return;
            } catch (java.io.IOException ex) {
                logger.log(Level.FINE, "Socket read error", ex); 
                throw asCommFailure(ex, MinorRecv, "I/O error during read");
            } catch (java.lang.NullPointerException ex) {
                logger.log(Level.FINE, "Socket read error", ex); 
                throw asCommFailure(ex, MinorRecv, "NullPointerException during read");
            }
        }
    }

    public boolean receive_timeout_detect(org.apache.yoko.orb.OCI.Buffer buf,
            int t) {
        if (t < 0)
            throw new InternalError();

        if (t == 0)
            return receive_detect(buf, false);

        setSoTimeout(t);

        while (!buf.is_full()) {
            try {
                int result = in_.read(buf.data(), buf.pos(), buf.rest_length());
                if (result <= 0)
                    return false;
                buf.advance(result);
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);
                return true;
            } catch (java.io.IOException ex) {
                return false;
            } catch (java.lang.NullPointerException ex) {
                return false;
            }
        }

        return true;
    }

    public void send(org.apache.yoko.orb.OCI.Buffer buf, boolean block) {
        setBlock(block);
        
        logger.fine("Sending buffer of size " + buf.rest_length() + " to " + socket_); 
        
        while (!buf.is_full()) {
            try {
                out_.write(buf.data(), buf.pos(), buf.rest_length());
                out_.flush();
                buf.pos(buf.length());
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);

                if (!block)
                    return;
            } catch (java.io.IOException ex) {
                logger.log(Level.FINE, "Socket write error", ex);
                throw asCommFailure(ex, MinorSend, "I/O error during write");
            } catch (java.lang.NullPointerException ex) {
                logger.log(Level.FINE, "Socket write error", ex);
                throw asCommFailure(ex, MinorSend, "NullPointerException during write");
            }
        }
    }

    public boolean send_detect(org.apache.yoko.orb.OCI.Buffer buf, boolean block) {
        setBlock(block);

        while (!buf.is_full()) {
            try {
                out_.write(buf.data(), buf.pos(), buf.rest_length());
                out_.flush();
                buf.pos(buf.length());
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);

                if (!block)
                    return true;
            } catch (java.io.IOException ex) {
                return false;
            } catch (java.lang.NullPointerException ex) {
                return false;
            }
        }

        return true;
    }

    public void send_timeout(org.apache.yoko.orb.OCI.Buffer buf, int t) {
        if (t < 0)
            throw new InternalError();

        if (t == 0) {
            send(buf, false);
            return;
        }

        setSoTimeout(t);

        while (!buf.is_full()) {
            try {
                out_.write(buf.data(), buf.pos(), buf.rest_length());
                out_.flush();
                buf.pos(buf.length());
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);
                return;
            } catch (java.io.IOException ex) {
                logger.log(Level.FINE,  "Socket write error", ex);
                throw asCommFailure(ex, MinorSend, "I/O error during write");
            } catch (java.lang.NullPointerException ex) {
                logger.log(Level.FINE, "Socket write error", ex);
                throw asCommFailure(ex, MinorSend, "NullPointerException during write");
            }
        }
    }

    public boolean send_timeout_detect(org.apache.yoko.orb.OCI.Buffer buf, int t) {
        if (t < 0)
            throw new InternalError();

        if (t == 0)
            return send_detect(buf, false);

        setSoTimeout(t);

        while (!buf.is_full()) {                                 
            try {
                out_.write(buf.data(), buf.pos(), buf.rest_length());
                out_.flush();
                buf.pos(buf.length());
            } catch (java.io.InterruptedIOException ex) {
                buf.advance(ex.bytesTransferred);
                return true;
            } catch (java.io.IOException ex) {
                return false;
            } catch (java.lang.NullPointerException ex) {
                return false;
            }
        }

        return true;
    }

    public org.apache.yoko.orb.OCI.TransportInfo get_info() {
        return info_;
    }

    // ------------------------------------------------------------------
    // Yoko internal functions
    // Application programs must not use these functions directly
    // ------------------------------------------------------------------

    public Transport_impl(java.net.Socket socket, ListenerMap lm) {
        socket_ = socket;
        shutdown_ = false;

        //
        // Cache the streams associated with the socket, for
        // performance reasons
        //
        try {
            in_ = socket_.getInputStream();
            out_ = socket_.getOutputStream();
        } catch (java.io.IOException ex) {
            logger.log(Level.FINE, "Socket setup error", ex);
            throw asCommFailure(ex, MinorSocket, "unable to obtain socket InputStream");
        }

        //
        // Since the Constructor of TransportInfo uses this object create
        // it after all members are initialized
        //
        info_ = new TransportInfo_impl(this, lm);
    }

    public Transport_impl(Acceptor acceptor, Socket socket, ListenerMap lm) {
        socket_ = socket;
        shutdown_ = false;
        
        logger.fine("Creating new transport for socket " + socket); 

        //
        // Cache the streams associated with the socket, for
        // performance reasons
        //
        try {
            in_ = socket_.getInputStream();
            out_ = socket_.getOutputStream();
        } catch (java.io.IOException ex) {
            logger.log(Level.FINE, "Socket setup error", ex);
            throw asCommFailure(ex, MinorSocket, "unable to obtain socket InputStream");
        }

        //
        // Since the Constructor of TransportInfo uses this object create
        // it after all members are initialized
        //
        info_ = new TransportInfo_impl(this, acceptor, lm);
    }

    public void finalize() throws Throwable {
        if (socket_ != null)
            close();

        super.finalize();
    }
    
    public String toString() {
        return "iiop transport using socket " + socket_; 
    }
}
