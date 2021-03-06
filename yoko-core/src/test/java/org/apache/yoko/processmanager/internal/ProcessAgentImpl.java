/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
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

package org.apache.yoko.processmanager.internal;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.UnicastRemoteObject;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.apache.yoko.osgi.ProviderLocator;

public class ProcessAgentImpl extends UnicastRemoteObject implements ProcessAgent {
    private static final long serialVersionUID = 1L;
    private String name;
    private ProcessManagerRemoteIF processManager;
    protected ProcessAgentImpl() throws RemoteException {
        super();
    }

    private CountDownLatch shutdownCountDown = new CountDownLatch(1);
    private boolean agentExited = false;
    private boolean exitedFromParent = false;
    private int exitCode = 0;
    private Thread shutdownHook, mainThread;
    public String getName() { return name; }
    private void init(String name, ProcessManagerRemoteIF processManager) {
        this.name = name;
        this.processManager = processManager;
        this.mainThread = Thread.currentThread();
        try {
            processManager.agentReady(this);
        } catch (RemoteException e) {
            e.printStackTrace();
            System.exit(1);
        }
    }

    /** this is the main routine run in test agents */
    public static void main(String[] args) throws Exception {
        String agentName = args[0];
        String registryHost = args[1];
        int registryPort = Integer.parseInt(args[2]);
        String processManagerName = args[3];

        Registry reg = LocateRegistry.getRegistry(registryHost, registryPort);

        final ProcessAgentImpl agent = new ProcessAgentImpl();

        agent.shutdownHook = new Thread(new Runnable() {
                public void run() {
                    agent.shutdownCountDown.countDown();
                    try {
                        agent.mainThread.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
        });
        Runtime.getRuntime().addShutdownHook(agent.shutdownHook);

        ProcessManagerRemoteIF manager = (ProcessManagerRemoteIF) reg.lookup(processManagerName);
        agent.init(agentName,manager);

        agent.waitForShutdown();
        agent.notifyManagerOnExit();
        if(agent.exitedFromParent) {
            Runtime.getRuntime().removeShutdownHook(agent.shutdownHook);
            System.exit(agent.exitCode);
        }
    }

    public static void startLocalProcess(ProcessManagerRemoteIF manager, String agentName) throws Exception {
        new ProcessAgentImpl().init(agentName,manager);
    }

    private void waitForShutdown() {
        try {
            while(true) {
                if(shutdownCountDown.await(1000, TimeUnit.MILLISECONDS)) {
                    break;
                }
                // Throws RemoteException if processManager is gone.
                processManager.isAlive();
            }
        }
        catch(Exception e) {
            // Parent process died.
            Runtime.getRuntime().removeShutdownHook(shutdownHook);
            System.exit(1);
        }
    }

    private void notifyManagerOnExit() {
        try {
            if(!agentExited) {
                processManager.agentExited(this);
                agentExited = true;
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        
    }
    
    public void exit(int exitCode) throws RemoteException {
        this.exitedFromParent = true;
        this.exitCode = exitCode;
        shutdownCountDown.countDown();
    }

    public Object invokeStatic(String className, String methodName, Object[] args) {
        try {
            Class[] parameters = new Class[args.length];
            for(int i = 0; i < parameters.length; i++) {
                parameters[i] = args[i].getClass();
            }
            // get the appropriate class for the loading.
            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            Class cl = ProviderLocator.loadClass(className, getClass(), loader);
            Method method = cl.getMethod(methodName, parameters);
            return method.invoke(null, args);
        }
        catch(InvocationTargetException e) {
            return e;
        }
        catch(Exception e) {
            throw new Error(e);
        }
    }
}
