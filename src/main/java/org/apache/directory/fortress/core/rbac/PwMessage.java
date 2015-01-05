/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */
package org.apache.directory.fortress.core.rbac;

import java.util.List;

/**
 * Interface is implemented by {@link org.apache.directory.fortress.core.rbac.Session} and prescribes methods used to return Fortress
 * password messages to the caller.
 * <p/>

 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
public interface PwMessage
{
    /**
     * Return the {@link org.apache.directory.fortress.core.rbac.User#userId} from entity.
     *
     * @param userId maps to {@code uid} attribute on inetOrgPerson object class.
     */
    public void setUserId(String userId);

    /**
     * Set the {@link org.apache.directory.fortress.core.rbac.User#userId} in entity.
     *
     * @return userId maps to {@code uid} attribute on inetOrgPerson object class.
     */
    public String getUserId();

    /**
     * Contains the message that corresponds to password.  These messages map to {@link GlobalPwMsgIds#pwMsgs}
     *
     * @param message
     */
    public void setMsg(String message);

    /**
     * Return the message that corresponds to last password check.
     *
     * @return message maps to {@link GlobalPwMsgIds#pwMsgs}
     */
    public String getMsg();

    /**
     * If set to true the user's password check out good.
     *
     * @param isAuthenticated
     */
    public void setAuthenticated(boolean isAuthenticated);

    /**
     * If set to true the user's password check out good.
     *
     * @return param isAuthenticated
     */
    public boolean isAuthenticated();

    /**
     * Return the warning id that pertain to User's password. This attribute maps to values between 0 and 100 contained within here {@link GlobalPwMsgIds}
     *
     * @param warning contains warning id.
     */
    public void setWarning(Warning warning);
    public void setWarnings(List<Warning> warnings);
    //public void setWarningId(int warning);


    /**
     * Set the warning id that pertain to User's password. This attribute maps to values between 0 and 100 contained within here {@link GlobalPwMsgIds}
     *
     * @return warning contains warning id.
     */
    public List<Warning> getWarnings();
    //public int getWarningId();

    /**
     * Set the error id that pertain to User's password. This attribute maps to values greater than or equal to 100 contained within here {@link GlobalPwMsgIds}
     *
     * @param error contains error id that maps to {@link GlobalPwMsgIds#pwIds}
     */
    public void setErrorId(int error);

    /**
     * Return the error id that pertain to User's password. This attribute maps to values greater than or equal to 100 contained within here {@link GlobalPwMsgIds}
     *
     * @return error contains error id that maps to {@link GlobalPwMsgIds#pwIds}
     */
    public int getErrorId();

    /**
     * Grace count indicates how many binds User can perform before password slips into expired state.
     *
     * @param grace integer containing number of binds allowed for user.
     */
    public void setGraceLogins(int grace);

    /**
     * Get the grace count which indicates how many binds User can perform before password slips into expired state.
     *
     * @return grace integer containing number of binds allowed for user.
     */
    public int getGraceLogins();

    /**
     * The number of seconds until the User's password expires.
     *
     * @param expire value is computed by ldap server and contains the number of seconds until password will expire.
     */
    public void setExpirationSeconds(int expire);

    /**
     * Get the number of seconds until the User's password expires.
     *
     * @return expire value is computed by ldap server and contains the number of seconds until password will expire.
     */
    public int getExpirationSeconds();
}
