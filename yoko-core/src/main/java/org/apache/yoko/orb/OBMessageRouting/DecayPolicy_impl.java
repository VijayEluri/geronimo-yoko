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

package org.apache.yoko.orb.OBMessageRouting;

public class DecayPolicy_impl extends org.omg.MessageRouting.DecayPolicy {
    public DecayPolicy_impl() {
    }

    public DecayPolicy_impl(int decaySeconds) {
        decay_seconds = decaySeconds;
    }

    public int policy_type() {
        return org.omg.MessageRouting.DECAY_POLICY_TYPE.value;
    }

    public org.omg.CORBA.Policy copy() {
        return null;
    }

    public void destroy() {
    }
}
