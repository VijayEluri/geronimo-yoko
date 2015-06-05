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

package org.apache.yoko.rmi.impl;

import java.io.IOException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.omg.CORBA.portable.InputStream;

public abstract class TypeDescriptor extends ModelElement {
    static Logger logger = Logger.getLogger(TypeDescriptor.class.getName());

    protected final Class _java_class;

    protected String _repid;

    protected RemoteInterfaceDescriptor remoteDescriptor;

    private FullKey _key;

    public Class getJavaClass() {
        return _java_class;
    }

    public final FullKey getKey() {
        if (null == _key) {
            _key = new FullKey(getRepositoryID(), getJavaClass());
        }
        return _key;
    }

    public static class SimpleKey {
        private final String repid;

        public SimpleKey(String repid) {
            this.repid = repid;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((repid == null) ? 0 : repid.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof SimpleKey)) return false;
            return Objects.equals(repid, ((SimpleKey)obj).repid);
        }
    }

    public static final class FullKey extends SimpleKey {
        private final Class<?> localType;

        public FullKey(String repid, Class<?> localType) {
            super(repid);
            this.localType = localType;
        }

        @Override
        public int hashCode() {
            // must just be the same as SimpleKey's hashCode
            return super.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (!(obj instanceof SimpleKey)) return false;
            if (obj instanceof FullKey &&
                    !!!Objects.equals(localType, ((FullKey)obj).localType)) return false;
            return super.equals(obj);
        }
    }

    @Override
    public String toString() {
        return String.format("%s{class=\"%s\",repId=\"%s\"}",
                this.getClass().getName(), getJavaClass(),
                getRepositoryID());
    }

    protected TypeDescriptor(Class type, TypeRepository repository) {
        _java_class = type;
        String typeName = type.getName(); 
        setTypeRepository(repository);
        setIDLName(typeName.replace('.', '_'));
        // break up the simple type and package
        int idx = typeName.lastIndexOf('.');
        // if we have a package, split it into the component parts
        if (idx >= 0) {
            setPackageName(typeName.substring(0, idx));
            setTypeName(typeName.substring(idx + 1));
        }
        else {
            // no package...the type is the simple name
            setPackageName(""); 
            setTypeName(typeName); 
        }
    }

    public String getRepositoryID() {
        if (_repid == null)
            _repid = "RMI:" + getJavaClass().getName() + ":0000000000000000";

        return _repid;
    }

    RemoteInterfaceDescriptor getRemoteInterface() {
        return remoteDescriptor;
    }

    void setRemoteInterface(RemoteInterfaceDescriptor desc) {
        remoteDescriptor = desc;
    }

    /** Read an instance of this value from a CDR stream */
    public abstract Object read(org.omg.CORBA.portable.InputStream in);

    /** Write an instance of this value to a CDR stream */
    public abstract void write(org.omg.CORBA.portable.OutputStream out,
            Object val);

    public void init() {
    }

    public boolean isCustomMarshalled() {
        return false;
    }

    static class WrappedIOException extends RuntimeException {
        IOException wrapped;

        WrappedIOException(IOException ex) {
            super("wrapped IO exception");
            this.wrapped = ex;
        }
    }

    CorbaObjectReader makeCorbaObjectReader(final InputStream in,
            final Map offsetMap, final java.io.Serializable obj)
            throws IOException {
        try {
            return (CorbaObjectReader) AccessController
                    .doPrivileged(new PrivilegedAction() {
                        public Object run() {
                            try {
                                return new CorbaObjectReader(in, offsetMap, obj);
                            } catch (IOException ex) {
                                throw new WrappedIOException(ex);
                            }
                        }
                    });
        } catch (WrappedIOException ex) {
            throw ex.wrapped;
        }
    }

    String makeSignature(Class type) {
        if (type.isPrimitive()) {

            if (type == Boolean.TYPE) {
                return "Z";
            } else if (type == Byte.TYPE) {
                return "B";
            } else if (type == Short.TYPE) {
                return "S";
            } else if (type == Character.TYPE) {
                return "C";
            } else if (type == Integer.TYPE) {
                return "I";
            } else if (type == Long.TYPE) {
                return "J";
            } else if (type == Float.TYPE) {
                return "F";
            } else if (type == Double.TYPE) {
                return "D";
            } else if (type == Void.TYPE) {
                return "V";
            } else
                throw new RuntimeException("unknown primitive class" + type);

        } else if (type.isArray()) {
            int i = 0;
            Class elem = type;
            for (; elem.isArray(); elem = elem.getComponentType())
                i += 1;

            StringBuffer sb = new StringBuffer();
            for (int j = 0; j < i; j++)
                sb.append('[');

            sb.append(makeSignature(elem));

            return sb.toString();
        } else {
            return "L" + (type.getName()).replace('.', '/') + ";";
        }
    }

    long getHashCode() {
        return 0L;
    }

    protected org.omg.CORBA.TypeCode _type_code = null;

    abstract org.omg.CORBA.TypeCode getTypeCode();

    Object copyObject(Object value, CopyState state) {
        throw new InternalError("cannot copy " + value.getClass().getName());
    }

    void writeMarshalValue(java.io.PrintWriter pw, String outName,
            String paramName) {
        pw.print(outName);
        pw.print('.');
        pw.print("write_");
        pw.print(getIDLName());
        pw.print('(');
        pw.print(paramName);
        pw.print(')');
    }

    void writeUnmarshalValue(java.io.PrintWriter pw, String inName) {
        pw.print(inName);
        pw.print('.');
        pw.print("read_");
        pw.print(getIDLName());
        pw.print('(');
        pw.print(')');
    }

    void addDependencies(java.util.Set<Class<?>> classes) {
        return;
    }

    boolean copyInStub() {
        return true;
    }

    void print(java.io.PrintWriter pw, java.util.Map<Object,Integer> recurse, Object val) {
        if (val == null) {
            pw.print("null");
        }

        Integer old = (Integer) recurse.get(val);
        if (old != null) {
            pw.print("^" + old);
        } else {
            Integer key = new Integer(System.identityHashCode(val));
            pw.println(getJavaClass().getName() + "@"
                    + Integer.toHexString(key.intValue()));
        }
    }

    synchronized TypeDescriptor getSelf() {
        return this;
    }

    /**
     * Method copyBetweenStates.
     * 
     * @return boolean
     */
    public boolean copyBetweenStates() {
        return true;
    }

    /**
     * Method copyWithinState.
     * 
     * @return boolean
     */
    public boolean copyWithinState() {
        return true;
    }

}
