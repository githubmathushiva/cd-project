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


import java.util.ArrayList;
import java.util.List;

import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchObjectException;
import org.apache.directory.api.ldap.model.message.SearchScope;
import org.apache.directory.ldap.client.api.LdapConnection;
import org.apache.directory.fortress.core.CreateException;
import org.apache.directory.fortress.core.FinderException;
import org.apache.directory.fortress.core.GlobalErrIds;
import org.apache.directory.fortress.core.GlobalIds;
import org.apache.directory.fortress.core.ObjectFactory;
import org.apache.directory.fortress.core.RemoveException;
import org.apache.directory.fortress.core.UpdateException;
import org.apache.directory.fortress.core.ldap.ApacheDsDataProvider;
import org.apache.directory.fortress.core.rbac.Graphable;
import org.apache.directory.fortress.core.rbac.Role;
import org.apache.directory.fortress.core.rbac.RoleUtil;
import org.apache.directory.fortress.core.util.attr.VUtil;
import org.apache.directory.fortress.core.util.time.CUtil;


/**
 * This class perform data access for Fortress Role entity.
 * <p/>
 * The Fortress Role entity is a composite of the following other Fortress structural and aux object classes:
 * <h4>1. ftRls Structural objectclass is used to store the Role information like name and temporal constraint attributes</h4>
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass	( 1.3.6.1.4.1.38088.2.1</code>
 * <li> <code>NAME 'ftRls'</code>
 * <li> <code>DESC 'Fortress Role Object Class'</code>
 * <li> <code>SUP organizationalrole</code>
 * <li> <code>STRUCTURAL</code>
 * <li> <code>MUST ( ftId $ ftRoleName )</code>
 * <li> <code>MAY ( description $ ftCstr ) )</code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>2. ftProperties AUXILIARY Object Class is used to store client specific name/value pairs on target entity</h4>
 * <code># This aux object class can be used to store custom attributes.</code><br />
 * <code># The properties collections consist of name/value pairs and are not constrainted by Fortress.</code><br />
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.2</code>
 * <li> <code>NAME 'ftProperties'</code>
 * <li> <code>DESC 'Fortress Properties AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY ( ftProps ) ) </code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>3. ftMods AUXILIARY Object Class is used to store Fortress audit variables on target entity</h4>
 * <ul>
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.4</code>
 * <li> <code>NAME 'ftMods'</code>
 * <li> <code>DESC 'Fortress Modifiers AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY (</code>
 * <li> <code>ftModifier $</code>
 * <li> <code>ftModCode $</code>
 * <li> <code>ftModId ) )</code>
 * <li>  ------------------------------------------
 * </ul>
 * <p/>
 * This class is thread safe.
 *
 * @author Kevin McKinney
 */
final class RoleDAO extends ApacheDsDataProvider
{
    /*
      *  *************************************************************************
      *  **  OpenAccessMgr ROLE STATICS
      *  ************************************************************************
      */
    private static final String ROLE_NM = "ftRoleName";

    private static final String[] ROLE_NM_ATR =
        {
            ROLE_NM
    };

    private static final String[] ROLE_ATRS =
        {
            GlobalIds.FT_IID, ROLE_NM, SchemaConstants.DESCRIPTION_AT, GlobalIds.CONSTRAINT, SchemaConstants.ROLE_OCCUPANT_AT, GlobalIds.PARENT_NODES
    };


    /**
     * @param entity
     * @return
     * @throws CreateException
     */
    final Role create( Role entity ) throws CreateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity.getName(), entity.getContextId() );

        try
        {
            Entry entry = new DefaultEntry( dn );
            entry.add( SchemaConstants.OBJECT_CLASS_AT, GlobalIds.ROLE_OBJ_CLASS );
            entity.setId();
            entry.add( GlobalIds.FT_IID, entity.getId() );
            entry.add( ROLE_NM, entity.getName() );

            // description field is optional on this object class:
            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                entry.add( SchemaConstants.DESCRIPTION_AT, entity.getDescription() );
            }

            // CN attribute is required for this object class:
            entry.add( SchemaConstants.CN_AT, entity.getName() );
            entry.add( GlobalIds.CONSTRAINT, CUtil.setConstraint( entity ) );

            // These multi-valued attributes are optional.  The utility function will return quietly if items are not loaded into collection:
            loadAttrs( entity.getParents(), entry, GlobalIds.PARENT_NODES );

            ld = getAdminConnection();
            add( ld, entry, entity );
        }
        catch ( LdapException e )
        {
            String error = "create role [" + entity.getName() + "] caught LdapException=" + e.getMessage();
            throw new CreateException( GlobalErrIds.ROLE_ADD_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param entity
     * @return
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     */
    final Role update( Role entity ) throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity.getName(), entity.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();

            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                mods.add( new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE,
                    SchemaConstants.DESCRIPTION_AT, entity.getDescription() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getOccupants() ) )
            {
                for ( String name : entity.getOccupants() )
                {
                    mods.add( new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE,
                        SchemaConstants.ROLE_OCCUPANT_AT, entity.getOccupants().toArray( new String[]
                            {} ) ) );
                }
            }

            if ( entity.isTemporalSet() )
            {
                String szRawData = CUtil.setConstraint( entity );

                if ( VUtil.isNotNullOrEmpty( szRawData ) )
                {
                    mods.add( new DefaultModification( ModificationOperation.REPLACE_ATTRIBUTE,
                        GlobalIds.CONSTRAINT, szRawData ) );
                }
            }

            loadAttrs( entity.getParents(), mods, GlobalIds.PARENT_NODES );

            if ( mods.size() > 0 )
            {
                ld = getAdminConnection();
                modify( ld, dn, mods, entity );
            }
        }
        catch ( LdapException e )
        {
            String error = "update name [" + entity.getName() + "] caught LdapException=" + e.getMessage();
            throw new UpdateException( GlobalErrIds.ROLE_UPDATE_FAILED, error, e );
        }
        catch ( Exception e )
        {
            String error = "update name [" + entity.getName() + "] caught LdapException=" + e.getMessage();
            throw new UpdateException( GlobalErrIds.ROLE_UPDATE_FAILED, error, e );
        }
        finally
        {
            try
            {
                closeAdminConnection( ld );
            }
            catch ( Exception e )
            {
                String error = "update name [" + entity.getName() + "] caught LdapException=" + e.getMessage();
                throw new UpdateException( GlobalErrIds.ROLE_UPDATE_FAILED, error, e );
            }
        }

        return entity;
    }


    /**
     *
     * @param entity
     * @throws UpdateException
     */
    final void deleteParent( Role entity ) throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity.getName(), entity.getContextId() );
        try
        {
            List<Modification> mods = new ArrayList<Modification>();
            mods.add( new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE,
                GlobalIds.PARENT_NODES ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, entity );
        }
        catch ( LdapException e )
        {
            String error = "deleteParent name [" + entity.getName() + "] caught LdapException=" + e.getMessage();
            throw new UpdateException( GlobalErrIds.ROLE_REMOVE_PARENT_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param entity
     * @param userDn
     * @return
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     */
    final Role assign( Role entity, String userDn ) throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity.getName(), entity.getContextId() );

        try
        {
            //ld = getAdminConnection();
            List<Modification> mods = new ArrayList<Modification>();
            mods.add( new DefaultModification( ModificationOperation.ADD_ATTRIBUTE, SchemaConstants.ROLE_OCCUPANT_AT, userDn ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, entity );
        }
        catch ( LdapException e )
        {
            String error = "assign role name [" + entity.getName() + "] user dn [" + userDn + "] caught LdapException="
                + e.getMessage();
            throw new UpdateException( GlobalErrIds.ROLE_USER_ASSIGN_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param entity
     * @param userDn
     * @return
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     */
    final Role deassign( Role entity, String userDn ) throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity.getName(), entity.getContextId() );
        try
        {
            List<Modification> mods = new ArrayList<Modification>();
            mods.add( new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE, SchemaConstants.ROLE_OCCUPANT_AT, userDn ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, entity );
        }
        catch ( LdapException e )
        {
            String error = "deassign role name [" + entity.getName() + "] user dn [" + userDn
                + "] caught LdapException=" + e.getMessage();
            throw new UpdateException( GlobalErrIds.ROLE_USER_DEASSIGN_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param role
     * @throws RemoveException
     */
    final void remove( Role role )
        throws RemoveException
    {
        LdapConnection ld = null;
        String dn = getDn( role.getName(), role.getContextId() );

        try
        {
            ld = getAdminConnection();
            delete( ld, dn, role );
        }
        catch ( LdapException e )
        {
            String error = "remove role name=" + role.getName() + " LdapException=" + e.getMessage();
            throw new RemoveException( GlobalErrIds.ROLE_DELETE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param role
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final Role getRole( Role role )
        throws FinderException
    {
        Role entity = null;
        LdapConnection ld = null;
        String dn = getDn( role.getName(), role.getContextId() );

        try
        {
            ld = getAdminConnection();
            Entry findEntry = read( ld, dn, ROLE_ATRS );
            if(findEntry != null)
            {
                entity = unloadLdapEntry( findEntry, 0, role.getContextId() );
            }
            if ( entity == null )
            {
                String warning = "getRole no entry found dn [" + dn + "]";
                throw new FinderException( GlobalErrIds.ROLE_NOT_FOUND, warning );
            }
        }
        catch ( LdapNoSuchObjectException e )
        {
            String warning = "getRole Obj COULD NOT FIND ENTRY for dn [" + dn + "]";
            throw new FinderException( GlobalErrIds.ROLE_NOT_FOUND, warning );
        }
        catch ( LdapException e )
        {
            String error = "getRole dn [" + dn + "] LEXCD=" + e;
            throw new FinderException( GlobalErrIds.ROLE_READ_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param role
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Role> findRoles( Role role )
        throws FinderException
    {
        List<Role> roleList = new ArrayList<>();
        LdapConnection ld = null;
        String roleRoot = getRootDn( role.getContextId(), GlobalIds.ROLE_ROOT );
        String filter = null;

        try
        {
            String searchVal = encodeSafeText( role.getName(), GlobalIds.ROLE_LEN );
            filter = GlobalIds.FILTER_PREFIX + GlobalIds.ROLE_OBJECT_CLASS_NM + ")("
                + ROLE_NM + "=" + searchVal + "*))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, roleRoot,
                SearchScope.ONELEVEL, filter, ROLE_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                roleList.add( unloadLdapEntry( searchResults.getEntry(), sequence++, role.getContextId() ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findRoles filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findRoles filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return roleList;
    }


    /**
     * @param role
     * @param limit
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<String> findRoles( Role role, int limit )
        throws FinderException
    {
        List<String> roleList = new ArrayList<>();
        LdapConnection ld = null;
        String roleRoot = getRootDn( role.getContextId(), GlobalIds.ROLE_ROOT );
        String filter = null;

        try
        {
            String searchVal = encodeSafeText( role.getName(), GlobalIds.ROLE_LEN );
            filter = GlobalIds.FILTER_PREFIX + GlobalIds.ROLE_OBJECT_CLASS_NM + ")("
                + ROLE_NM + "=" + searchVal + "*))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, roleRoot,
                SearchScope.ONELEVEL, filter, ROLE_NM_ATR, false, GlobalIds.BATCH_SIZE, limit );

            while ( searchResults.next() )
            {
                Entry entry = searchResults.getEntry();
                roleList.add( getAttribute( entry, ROLE_NM ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findRoles filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findRoles filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return roleList;
    }


    /**
     *
     * @param userDn
     * @param contextId
     * @return
     * @throws FinderException
     */
    final List<String> findAssignedRoles( String userDn, String contextId )
        throws FinderException
    {
        List<String> roleNameList = new ArrayList<>();
        LdapConnection ld = null;
        String roleRoot = getRootDn( contextId, GlobalIds.ROLE_ROOT );

        try
        {
            String filter = GlobalIds.FILTER_PREFIX + GlobalIds.ROLE_OBJECT_CLASS_NM + ")";
            filter += "(" + SchemaConstants.ROLE_OCCUPANT_AT + "=" + userDn + "))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, roleRoot,
                SearchScope.ONELEVEL, filter, ROLE_NM_ATR, false, GlobalIds.BATCH_SIZE );

            while ( searchResults.next() )
            {
                roleNameList.add( getAttribute( searchResults.getEntry(), ROLE_NM ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findAssignedRoles userDn [" + userDn + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_OCCUPANT_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findAssignedRoles userDn [" + userDn + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_OCCUPANT_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return roleNameList;
    }


    /**
     *
     * @param contextId
     * @return
     * @throws FinderException
     */
    final List<Graphable> getAllDescendants( String contextId )
        throws FinderException
    {
        String[] DESC_ATRS =
            { ROLE_NM, GlobalIds.PARENT_NODES };
        List<Graphable> descendants = new ArrayList<>();
        LdapConnection ld = null;
        String roleRoot = getRootDn( contextId, GlobalIds.ROLE_ROOT );
        String filter = null;

        try
        {
            filter = GlobalIds.FILTER_PREFIX + GlobalIds.ROLE_OBJECT_CLASS_NM + ")("
                + GlobalIds.PARENT_NODES + "=*))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, roleRoot,
                SearchScope.ONELEVEL, filter, DESC_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                descendants.add( unloadDescendants( searchResults.getEntry(), sequence++, contextId ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "getAllDescendants filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "getAllDescendants filter [" + filter + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.ROLE_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return descendants;
    }


    /**
     *
     * @param le
     * @param sequence
     * @param contextId
     * @return
     * @throws LdapInvalidAttributeValueException 
     * @throws LdapException
     */
    private Graphable unloadDescendants( Entry le, long sequence, String contextId )
        throws LdapInvalidAttributeValueException
    {
        Role entity = new ObjectFactory().createRole();
        entity.setSequenceId( sequence );
        entity.setName( getAttribute( le, ROLE_NM ) );
        entity.setParents( getAttributeSet( le, GlobalIds.PARENT_NODES ) );
        return entity;
    }


    /**
     *
     * @param le
     * @param sequence
     * @param contextId
     * @return
     * @throws LdapInvalidAttributeValueException 
     * @throws LdapException
     */
    private Role unloadLdapEntry( Entry le, long sequence, String contextId ) throws LdapInvalidAttributeValueException
    {
        Role entity = new ObjectFactory().createRole();
        entity.setSequenceId( sequence );
        entity.setId( getAttribute( le, GlobalIds.FT_IID ) );
        entity.setName( getAttribute( le, ROLE_NM ) );
        entity.setDescription( getAttribute( le, SchemaConstants.DESCRIPTION_AT ) );
        entity.setOccupants( getAttributes( le, SchemaConstants.ROLE_OCCUPANT_AT ) );
        //entity.setParents(RoleUtil.getParents(entity.getName().toUpperCase(), contextId));
        entity.setChildren( RoleUtil.getChildren( entity.getName().toUpperCase(), contextId ) );
        entity.setParents( getAttributeSet( le, GlobalIds.PARENT_NODES ) );
        unloadTemporal( le, entity );

        return entity;
    }


    private String getDn( String name, String contextId )
    {
        return SchemaConstants.CN_AT + "=" + name + "," + getRootDn( contextId, GlobalIds.ROLE_ROOT );
    }
}