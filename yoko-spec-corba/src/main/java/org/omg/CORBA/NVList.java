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
package org.omg.CORBA;

public abstract class NVList {
    public abstract int count();

    public abstract NamedValue add(int flags);

    public abstract NamedValue add_item(String item_name, int flags);

    public abstract NamedValue add_value(String item_name, Any val, int flags);

    public abstract NamedValue item(int index) throws org.omg.CORBA.Bounds;

    public abstract void remove(int index) throws org.omg.CORBA.Bounds;
}
