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

import java.net.Socket;
import java.util.Objects;

import org.apache.yoko.orb.CORBA.InputStream;
import org.apache.yoko.orb.OB.Net;
import org.apache.yoko.orb.OCI.*;
import org.omg.BiDirPolicy.BIDIRECTIONAL_POLICY_TYPE;
import org.omg.BiDirPolicy.BOTH;
import org.omg.BiDirPolicy.BidirectionalPolicy;
import org.omg.BiDirPolicy.BidirectionalPolicyHelper;
import org.omg.CORBA.LocalObject;
import org.omg.CORBA.NO_RESOURCES;
import org.omg.CORBA.Policy;
import org.omg.IIOP.BiDirIIOPServiceContext;
import org.omg.IIOP.BiDirIIOPServiceContextHelper;
import org.omg.IIOP.ListenPoint;
import org.omg.IOP.BI_DIR_IIOP;
import org.omg.IOP.ServiceContext;
import org.omg.IOP.TAG_INTERNET_IOP;

public final class TransportInfo_impl extends LocalObject implements TransportInfo {
    private enum Origin{CLIENT(CLIENT_SIDE.value), SERVER(SERVER_SIDE.value); final short value; Origin(int v) {value = (short)v;}}
    private final Socket socket;
    private final Origin origin;
    private final ListenerMap listenMap_;
    private volatile ListenPoint[] listenPoints_ = null;


    // ------------------------------------------------------------------
    // Standard IDL to Java Mapping
    // ------------------------------------------------------------------

    public String id() {
        return PLUGIN_ID.value;
    }

    public int tag() {
        return TAG_INTERNET_IOP.value;
    }

    public short origin() {
        return origin.value;
    }

    public synchronized String describe() {
        String desc = "id: " + PLUGIN_ID.value;

        String localAddr = addr();
        short localPort = port();
        desc += "\nlocal address: ";
        desc += localAddr;
        desc += ":";
        desc += (localPort < 0 ? 0xffff + (int) localPort + 1 : localPort);

        String remoteAddr = remote_addr();
        short remotePort = remote_port();
        desc += "\nremote address: ";
        desc += remoteAddr;
        desc += ":";
        desc += (remotePort < 0 ? 0xffff + (int) remotePort + 1 : remotePort);

        return desc;
    }

    public Socket getSocket() {return socket;}

    public String addr() {return socket.getLocalAddress().getHostAddress();}

    public short port() {return (short)socket.getLocalPort();}

    public String remote_addr() {return socket.getInetAddress().getHostAddress();}

    public short remote_port() {return (short)socket.getPort();}

    public ServiceContext[] get_service_contexts(Policy[] policies) {
        ServiceContext[] scl;
        boolean bHaveBidir = false;

        for (Policy policy : policies) {
            if (policy.policy_type() == BIDIRECTIONAL_POLICY_TYPE.value) {
                BidirectionalPolicy p = BidirectionalPolicyHelper
                        .narrow(policy);
                if (p.value() == BOTH.value)
                    bHaveBidir = true;
                break;
            }
        }

        if (bHaveBidir) {
            BiDirIIOPServiceContext biDirCtxt = new BiDirIIOPServiceContext();
            biDirCtxt.listen_points = listenMap_.getListenPoints();

            org.apache.yoko.orb.OCI.Buffer buf = new org.apache.yoko.orb.OCI.Buffer();
            org.apache.yoko.orb.CORBA.OutputStream out = new org.apache.yoko.orb.CORBA.OutputStream(
                    buf);

            out._OB_writeEndian();
            org.omg.IIOP.BiDirIIOPServiceContextHelper.write(out, biDirCtxt);

            //
            // Fill in the bidir service context
            //
            org.omg.IOP.ServiceContext context = new org.omg.IOP.ServiceContext();
            context.context_id = org.omg.IOP.BI_DIR_IIOP.value;
            context.context_data = buf.data();

            //
            // Create and fill the return context list
            //
            scl = new org.omg.IOP.ServiceContext[1];
            scl[0] = context;
            return scl;
        }

        //
        // we don't have a bidir service context so return an array of
        // length 0
        //
        scl = new org.omg.IOP.ServiceContext[0];
        return scl;
    }

    public void handle_service_contexts(org.omg.IOP.ServiceContext[] contexts) {
        for (ServiceContext context : contexts) {
            if (context.context_id == BI_DIR_IIOP.value) {
                byte[] pOct = context.context_data;
                int len = context.context_data.length;

                Buffer buf = new Buffer(pOct, len);
                InputStream in = new InputStream(buf, 0, false);
                in._OB_readEndian();

                //
                // unmarshal the octets back to the bidir format
                //
                BiDirIIOPServiceContext biDirCtxt = BiDirIIOPServiceContextHelper.read(in);

                //
                // save the listening points in the transport
                //
                _OB_setListenPoints(biDirCtxt.listen_points);

                break;
            }
        }
    }

    public synchronized boolean received_bidir_SCL() {
        return listenPoints_ != null && (listenPoints_.length > 0);
    }

    public synchronized boolean endpoint_alias_match(org.apache.yoko.orb.OCI.ConnectorInfo connInfo) {
        //
        // we only deal with Connectors that are of our specific type,
        // namely IIOP connectors (and ConnectorInfos)
        //
        org.apache.yoko.orb.OCI.IIOP.ConnectorInfo_impl infoImpl;
        try {
            infoImpl = (org.apache.yoko.orb.OCI.IIOP.ConnectorInfo_impl) connInfo;
        } catch (ClassCastException ex) {
            return false;
        }

        //
        // compare the endpoint information in this connector with the
        // various endpoint inforamtion in our listenMap_
        //
        if (listenPoints_ == null)
            return false;

        short port = infoImpl.remote_port();
        String host = infoImpl.remote_addr();

        for (ListenPoint aListenPoints_ : listenPoints_) {
            if ((aListenPoints_.port == port)
                    && Net.CompareHosts(
                    aListenPoints_.host, host))
                return true;
        }

        return false;
    }

    public synchronized org.omg.IIOP.ListenPoint[] _OB_getListenPoints() {
        return listenPoints_;
    }

    public synchronized void _OB_setListenPoints(org.omg.IIOP.ListenPoint[] lp) {
        listenPoints_ = lp;
    }

    // ------------------------------------------------------------------
    // Yoko internal functions
    // Application programs must not use these functions directly
    // ------------------------------------------------------------------
    private TransportInfo_impl(Socket socket, Origin origin, ListenerMap lm) {
        this.socket = socket;
        this.origin = origin;
        listenMap_ = lm;
    }


    // client-side constructor
    TransportInfo_impl(Transport_impl transport, ListenerMap lm) {
        this(transport.socket_, Origin.CLIENT, lm);
    }

    //server-side constructor
    TransportInfo_impl(Transport_impl transport, Acceptor acceptor, ListenerMap lm) {
        this(transport.socket_, Origin.SERVER, lm);
    }
}
