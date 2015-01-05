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


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.directory.api.ldap.model.constants.SchemaConstants;
import org.apache.directory.api.ldap.model.cursor.CursorException;
import org.apache.directory.api.ldap.model.cursor.SearchCursor;
import org.apache.directory.api.ldap.model.entry.DefaultAttribute;
import org.apache.directory.api.ldap.model.entry.DefaultEntry;
import org.apache.directory.api.ldap.model.entry.DefaultModification;
import org.apache.directory.api.ldap.model.entry.Entry;
import org.apache.directory.api.ldap.model.entry.Modification;
import org.apache.directory.api.ldap.model.entry.ModificationOperation;
import org.apache.directory.api.ldap.model.exception.LdapAttributeInUseException;
import org.apache.directory.api.ldap.model.exception.LdapException;
import org.apache.directory.api.ldap.model.exception.LdapInvalidAttributeValueException;
import org.apache.directory.api.ldap.model.exception.LdapNoSuchAttributeException;
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
import org.apache.directory.fortress.core.util.attr.AttrHelper;
import org.apache.directory.fortress.core.util.attr.VUtil;


/**
 * Permission data access class for LDAP. 
 * <p/>
 * This DAO class maintains the PermObj and Permission entities.
 * <h3>The Fortress PermObj Entity Class is a composite of 3 LDAP Schema object classes</h2>
 * <h4>PermObj Base - ftObject STRUCTURAL Object Class is used to store object name, id and type variables on target entity.</h4>
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass	( 1.3.6.1.4.1.38088.2.2</code>
 * <li> <code>NAME 'ftObject'</code>
 * <li> <code>DESC 'Fortress Permission Object Class'</code>
 * <li> <code>SUP organizationalunit</code>                                              GlobalIds
 * <li> <code>STRUCTURAL</code>
 * <li> <code>MUST (</code>
 * <li> <code>ftId $ ftObjNm ) </code>
 * <li> <code>MAY ( ftType ) )  </code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>PermObj - ftProperties AUXILIARY Object Class is used to store client specific name/value pairs on target entity.</h4>
 * <code>This aux object class can be used to store custom attributes.</code><br />
 * <code>The properties collections consist of name/value pairs and are not constrainted by Fortress.</code><br />
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.2</code>
 * <li> <code>NAME 'ftProperties'</code>
 * <li> <code>DESC 'Fortress Properties AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY ( ftProps ) ) </code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>PermObj - ftMods AUXILIARY Object Class is used to store Fortress audit variables on target entity.</h4>
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
 * <h3>The Fortress Permission Entity Class is composite of 3 LDAP Schema object classes</h3>
 * The Permission entity extends a single OpenLDAP standard structural object class, 'organizationalRole' with
 * one extension structural class, ftOperation,  and two auxiliary object classes, ftProperties, ftMods.
 * The following 4 LDAP object classes will be mapped into this entity:
 * <h4>Permission Base - 'ftOperation' STRUCTURAL Object Class is assigned roles and/or users which grants permissions which can be later checked</h4>
 * using either 'checkAccess' or 'sessionPermissions APIs both methods that reside in the 'AccessMgrImpl' class.
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass	( 1.3.6.1.4.1.38088.2.3</code>
 * <li> <code>NAME 'ftOperation'</code>
 * <li> <code>DESC 'Fortress Permission Operation Object Class'</code>
 * <li> <code>SUP organizationalrole</code>
 * <li> <code>STRUCTURAL</code>
 * <li> <code>MUST ( ftId $ ftPermName $</code>
 * <li> <code>ftObjNm $ ftOpNm )</code>
 * <li> <code>MAY ( ftRoles $ ftUsers $</code>
 * <li> <code> ftObjId $ ftType) )</code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>Permission Aux - ftProperties AUXILIARY Object Class is used to store optional client or otherwise custom name/value pairs on target entity.</h4>
 * <code>This aux object class can be used to store custom attributes.</code><br />
 * <code>The properties collections consist of name/value pairs and are not constrainted by Fortress.</code><br />
 * <ul>
 * <li>  ------------------------------------------
 * <li> <code>objectclass ( 1.3.6.1.4.1.38088.3.2</code>
 * <li> <code>NAME 'ftProperties'</code>
 * <li> <code>DESC 'Fortress Properties AUX Object Class'</code>
 * <li> <code>AUXILIARY</code>
 * <li> <code>MAY ( ftProps ) ) </code>
 * <li>  ------------------------------------------
 * </ul>
 * <h4>Permission Aux - ftMods AUXILIARY Object Class is used to store Fortress audit variables on target entity.</h4>
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
 * This class is thread safe.
 * <p/>
 *
 * @author <a href="mailto:dev@directory.apache.org">Apache Directory Project</a>
 */
final class PermDAO extends ApacheDsDataProvider
{
    /*
      *  *************************************************************************
      *  **  OpenAccessMgr PERMISSION STATICS
      *  ************************************************************************
      */
    private static final String TYPE = "ftType";
    private static final String PERM_OBJ_OBJECT_CLASS_NAME = "ftObject";
    private static final String PERM_OP_OBJECT_CLASS_NAME = "ftOperation";

    private static final String PERM_OBJ_OBJ_CLASS[] =
        {
            SchemaConstants.TOP_OC,
            SchemaConstants.ORGANIZATIONAL_UNIT_OC,
            PERM_OBJ_OBJECT_CLASS_NAME,
            GlobalIds.PROPS_AUX_OBJECT_CLASS_NAME,
            GlobalIds.FT_MODIFIER_AUX_OBJECT_CLASS_NAME
    };

    private static final String PERM_OP_OBJ_CLASS[] =
        {
            SchemaConstants.TOP_OC,
            SchemaConstants.ORGANIZATIONAL_ROLE_OC,
            PERM_OP_OBJECT_CLASS_NAME,
            GlobalIds.PROPS_AUX_OBJECT_CLASS_NAME,
            GlobalIds.FT_MODIFIER_AUX_OBJECT_CLASS_NAME
    };

    private static final String PERM_NAME = "ftPermName";
    private static final String ROLES = "ftRoles";
    private static final String USERS = "ftUsers";
    private static final String[] PERMISSION_OP_ATRS =
        {
            GlobalIds.FT_IID, PERM_NAME, GlobalIds.POBJ_NAME, GlobalIds.POP_NAME, SchemaConstants.DESCRIPTION_AT, SchemaConstants.OU_AT,
            GlobalIds.POBJ_ID, TYPE, ROLES, USERS, GlobalIds.PROPS
    };

    private static final String[] PERMISION_OBJ_ATRS =
        {
            GlobalIds.FT_IID, GlobalIds.POBJ_NAME, SchemaConstants.DESCRIPTION_AT, SchemaConstants.OU_AT, TYPE,
            GlobalIds.PROPS
    };


    /**
     * @param entity
     * @return
     * @throws org.apache.directory.fortress.core.CreateException
     *
     */
    final PermObj createObject( PermObj entity ) throws CreateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity, entity.getContextId() );

        try
        {
            Entry entry = new DefaultEntry( dn );
            entry.add( SchemaConstants.OBJECT_CLASS_AT, PERM_OBJ_OBJ_CLASS );
            entry.add( GlobalIds.POBJ_NAME, entity.getObjName() );

            // this will generatre a new random, unique id on this entity:
            entity.setInternalId();

            // create the rDN:
            entry.add( GlobalIds.FT_IID, entity.getInternalId() );

            // ou is required:
            entry.add( SchemaConstants.OU_AT, entity.getOu() );

            // description is optional:
            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                entry.add( SchemaConstants.DESCRIPTION_AT, entity.getDescription() );
            }

            // type is optional:
            if ( VUtil.isNotNullOrEmpty( entity.getType() ) )
            {
                entry.add( TYPE, entity.getType() );
            }

            // props are optional as well:
            //if the props is null don't try to load these attributes
            if ( VUtil.isNotNullOrEmpty( entity.getProperties() ) )
            {
                loadProperties( entity.getProperties(), entry, GlobalIds.PROPS );
            }

            // now add the new entry to directory:
            ld = getAdminConnection();
            add( ld, entry, entity );
            entity.setDn( dn );
        }
        catch ( LdapException e )
        {
            String error = "createObject perm obj [" + entity.getObjName() + "] caught LdapException="
                + e.getMessage();
            throw new CreateException( GlobalErrIds.PERM_ADD_FAILED, error, e );
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
    final PermObj updateObj( PermObj entity )
        throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity, entity.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();

            if ( VUtil.isNotNullOrEmpty( entity.getOu() ) )
            {
                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, SchemaConstants.OU_AT, entity.getOu() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, SchemaConstants.DESCRIPTION_AT, entity.getDescription() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getType() ) )
            {
                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, TYPE, entity.getType() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getProperties() ) )
            {
                loadProperties( entity.getProperties(), mods, GlobalIds.PROPS, true );
            }

            if ( mods.size() > 0 )
            {
                ld = getAdminConnection();
                modify( ld, dn, mods, entity );
                entity.setDn( dn );
            }
        }
        catch ( LdapException e )
        {
            String error = "updateObj objName [" + entity.getObjName() + "] caught LdapException="
                + e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_UPDATE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param entity
     * @throws org.apache.directory.fortress.core.RemoveException
     *
     */
    final void deleteObj( PermObj entity ) throws RemoveException
    {
        LdapConnection ld = null;
        String dn = getDn( entity, entity.getContextId() );

        try
        {
            ld = getAdminConnection();
            deleteRecursive( ld, dn, entity );
        }
        catch ( LdapException e )
        {
            String error = "deleteObj objName [" + entity.getObjName() + "] caught LdapException="
                + e.getMessage();
            throw new RemoveException( GlobalErrIds.PERM_DELETE_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "deleteObj objName [" + entity.getObjName() + "] caught LdapException="
                + e.getMessage();
            throw new RemoveException( GlobalErrIds.PERM_DELETE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param entity
     * @return
     * @throws org.apache.directory.fortress.core.CreateException
     *
     */
    final Permission createOperation( Permission entity ) throws CreateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity, entity.getContextId() );

        try
        {
            Entry entry = new DefaultEntry( dn );

            entry.add( SchemaConstants.OBJECT_CLASS_AT, PERM_OP_OBJ_CLASS );
            entry.add( GlobalIds.POP_NAME, entity.getOpName() );
            entry.add( GlobalIds.POBJ_NAME, entity.getObjName() );
            entity.setAbstractName( entity.getObjName() + "." + entity.getOpName() );

            // this will generate a new random, unique id on this entity:
            entity.setInternalId();

            // create the internal id:
            entry.add( GlobalIds.FT_IID, entity.getInternalId() );

            // description is optional:
            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                entry.add( SchemaConstants.DESCRIPTION_AT, entity.getDescription() );
            }

            // the abstract name is the human readable identifier:
            entry.add( PERM_NAME, entity.getAbstractName() );

            // organizational name requires CN attribute:
            entry.add( SchemaConstants.CN_AT, entity.getAbstractName() );

            // objectid is optional:
            if ( VUtil.isNotNullOrEmpty( entity.getObjId() ) )
            {
                entry.add( GlobalIds.POBJ_ID, entity.getObjId() );
            }

            // type is optional:
            if ( VUtil.isNotNullOrEmpty( entity.getType() ) )
            {
                entry.add( TYPE, entity.getType() );
            }

            // These are multi-valued attributes, use the util function to load:
            // These items are optional as well.  The utility function will return quietly if no items are loaded into collection:
            loadAttrs( entity.getRoles(), entry, ROLES );
            loadAttrs( entity.getUsers(), entry, USERS );

            // props are optional as well:
            //if the props is null don't try to load these attributes
            if ( VUtil.isNotNullOrEmpty( entity.getProperties() ) )
            {
                loadProperties( entity.getProperties(), entry, GlobalIds.PROPS );
            }

            // now add the new entry to directory:
            ld = getAdminConnection();
            add( ld, entry, entity );
            entity.setDn( dn );
        }
        catch ( LdapException e )
        {
            String error = "createOperation objName [" + entity.getObjName() + "] opName ["
                + entity.getOpName() + "] caught LdapException=" + e.getMessage();
            throw new CreateException( GlobalErrIds.PERM_ADD_FAILED, error, e );
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
    final Permission updateOperation( Permission entity )
        throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( entity, entity.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();

            if ( VUtil.isNotNullOrEmpty( entity.getAbstractName() ) )
            {
                // the abstract name is the human readable identifier:
                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, PERM_NAME, entity.getAbstractName() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getDescription() ) )
            {
                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, SchemaConstants.DESCRIPTION_AT, entity.getDescription() ) );
            }

            if ( VUtil.isNotNullOrEmpty( entity.getType() ) )
            {

                mods.add( new DefaultModification(
                    ModificationOperation.REPLACE_ATTRIBUTE, TYPE, entity.getType() ) );
            }

            // These are multi-valued attributes, use the util function to load:
            loadAttrs( entity.getRoles(), mods, ROLES );
            loadAttrs( entity.getUsers(), mods, USERS );
            loadProperties( entity.getProperties(), mods, GlobalIds.PROPS, true );

            if ( mods.size() > 0 )
            {
                ld = getAdminConnection();
                modify( ld, dn, mods, entity );
                entity.setDn( dn );
            }
        }
        catch ( LdapException e )
        {
            String error = "updateOperation objName [" + entity.getObjName() + "] opName ["
                + entity.getOpName() + "] caught LdapException=" + e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_UPDATE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * @param entity
     * @throws org.apache.directory.fortress.core.RemoveException
     *
     */
    final void deleteOperation( Permission entity ) throws RemoveException
    {
        LdapConnection ld = null;
        String dn = getOpRdn( entity.getOpName(), entity.getObjId() ) + "," + GlobalIds.POBJ_NAME + "="
            + entity.getObjName() + "," + getRootDn( entity.isAdmin(), entity.getContextId() );

        try
        {
            ld = getAdminConnection();
            deleteRecursive( ld, dn, entity );
        }
        catch ( LdapException e )
        {
            String error = "deleteOperation objName [" + entity.getObjName() + "] opName ["
                + entity.getOpName() + "] caught LdapException=" + e.getMessage();
            throw new RemoveException( GlobalErrIds.PERM_DELETE_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "deleteOperation objName [" + entity.getObjName() + "] opName ["
                + entity.getOpName() + "] caught LdapException=" + e.getMessage();
            throw new RemoveException( GlobalErrIds.PERM_DELETE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param pOp
     * @param role
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final void grant( Permission pOp, Role role )
        throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( pOp, pOp.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();

            mods.add( new DefaultModification(
                ModificationOperation.ADD_ATTRIBUTE, ROLES, role.getName() ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, pOp );
        }
        catch ( LdapAttributeInUseException e )
        {
            String warning = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] role [" + role.getName() + "] assignment already exists, Fortress rc="
                + GlobalErrIds.PERM_ROLE_EXIST;
            throw new UpdateException( GlobalErrIds.PERM_ROLE_EXIST, warning );
        }
        catch ( LdapNoSuchObjectException e )
        {
            String warning = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] role [" + role.getName() + "] perm not found, Fortress rc="
                + GlobalErrIds.PERM_OP_NOT_FOUND;
            throw new UpdateException( GlobalErrIds.PERM_OP_NOT_FOUND, warning );
        }
        catch ( LdapException e )
        {
            String error = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] name [" + role.getName() + "]  caught LdapException="
                + e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_GRANT_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param pOp
     * @param role
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final void revoke( Permission pOp, Role role )
        throws UpdateException, FinderException
    {
        LdapConnection ld = null;
        String dn = getDn( pOp, pOp.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();
            mods.add( new DefaultModification(
                ModificationOperation.REMOVE_ATTRIBUTE, ROLES, role.getName() ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, pOp );
        }
        catch ( LdapNoSuchAttributeException e )
        {
            String warning = "revoke perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] name [" + role.getName() + "] assignment does not exist.";
            throw new FinderException( GlobalErrIds.PERM_ROLE_NOT_EXIST, warning );
        }
        catch ( LdapException e )
        {
            String error = "revoke perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] name [" + role.getName() + "] caught LdapException=" +
                e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_REVOKE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param pOp
     * @param user
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final void grant( Permission pOp, User user )
        throws UpdateException
    {
        LdapConnection ld = null;
        String dn = getDn( pOp, pOp.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();
            mods.add( new DefaultModification(
                ModificationOperation.ADD_ATTRIBUTE, USERS, user.getUserId() ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, pOp );
        }
        catch ( LdapAttributeInUseException e )
        {
            String warning = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] userId [" + user.getUserId() + "] assignment already exists, Fortress rc="
                + GlobalErrIds.PERM_USER_EXIST;

            throw new UpdateException( GlobalErrIds.PERM_USER_EXIST, warning );
        }
        catch ( LdapNoSuchObjectException e )
        {
            String warning = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] userId [" + user.getUserId() + "] perm not found, Fortress rc="
                + GlobalErrIds.PERM_OP_NOT_FOUND;
            throw new UpdateException( GlobalErrIds.PERM_OP_NOT_FOUND, warning );
        }
        catch ( LdapException e )
        {
            String error = "grant perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] userId [" + user.getUserId() + "] caught LdapException="
                + e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_GRANT_USER_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param pOp
     * @param user
     * @throws org.apache.directory.fortress.core.UpdateException
     *
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final void revoke( Permission pOp, User user )
        throws UpdateException, FinderException
    {
        LdapConnection ld = null;
        String dn = getDn( pOp, pOp.getContextId() );

        try
        {
            List<Modification> mods = new ArrayList<Modification>();

            mods.add( new DefaultModification( ModificationOperation.REMOVE_ATTRIBUTE,
                USERS, user.getUserId() ) );
            ld = getAdminConnection();
            modify( ld, dn, mods, pOp );
        }
        catch ( LdapNoSuchAttributeException e )
        {
            String warning = "revoke perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] userId [" + user.getUserId() + "] assignment does not exist.";
            throw new FinderException( GlobalErrIds.PERM_USER_NOT_EXIST, warning );
        }
        catch ( LdapException e )
        {
            String error = "revoke perm object [" + pOp.getObjName() + "] operation ["
                + pOp.getOpName() + "] userId [" + user.getUserId() + "] caught LdapException="
                + e.getMessage();
            throw new UpdateException( GlobalErrIds.PERM_REVOKE_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
    }


    /**
     * @param permission
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final Permission getPerm( Permission permission )
        throws FinderException
    {
        Permission entity = null;
        LdapConnection ld = null;
        String dn = getOpRdn( permission.getOpName(), permission.getObjId() ) + "," + GlobalIds.POBJ_NAME + "="
            + permission.getObjName() + "," + getRootDn( permission.isAdmin(), permission.getContextId() );

        try
        {
            ld = getAdminConnection();
            Entry findEntry = read( ld, dn, PERMISSION_OP_ATRS );
            entity = unloadPopLdapEntry( findEntry, 0, permission.isAdmin() );

            if ( entity == null )
            {
                String warning = "getPerm no entry found dn [" + dn + "]";
                throw new FinderException( GlobalErrIds.PERM_OP_NOT_FOUND, warning );
            }
        }
        catch ( LdapNoSuchObjectException e )
        {
            String warning = "getPerm Op COULD NOT FIND ENTRY for dn [" + dn + "]";
            throw new FinderException( GlobalErrIds.PERM_OP_NOT_FOUND, warning );
        }
        catch ( LdapException e )
        {
            String error = "getUser [" + dn + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_READ_OP_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
        return entity;
    }


    /**
     * @param permObj
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final PermObj getPerm( PermObj permObj )
        throws FinderException
    {
        PermObj entity = null;
        LdapConnection ld = null;
        String dn = GlobalIds.POBJ_NAME + "=" + permObj.getObjName() + ","
            + getRootDn( permObj.isAdmin(), permObj.getContextId() );

        try
        {
            ld = getAdminConnection();
            Entry findEntry = read( ld, dn, PERMISION_OBJ_ATRS );
            entity = unloadPobjLdapEntry( findEntry, 0,permObj.isAdmin() );

            if ( entity == null )
            {
                String warning = "getPerm Obj no entry found dn [" + dn + "]";
                throw new FinderException( GlobalErrIds.PERM_OBJ_NOT_FOUND, warning );
            }
        }
        catch ( LdapNoSuchObjectException e )
        {
            String warning = "getPerm Obj COULD NOT FIND ENTRY for dn [" + dn + "]";
            throw new FinderException( GlobalErrIds.PERM_OBJ_NOT_FOUND, warning );
        }
        catch ( LdapException e )
        {
            String error = "getPerm Obj dn [" + dn + "] caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_READ_OBJ_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return entity;
    }


    /**
     * This method performs fortress authorization using data passed in (session) and stored on ldap server (permission).  It has been recently changed to use ldap compare operations in order to trigger slapd access log updates in directory.
     * It performs ldap operations:  read and (optionally) compare.  The first is to pull back the permission to see if user has access or not.  The second is to trigger audit
     * record storage on ldap server but can be disabled.
     *
     * @param session contains {@link Session#getUserId()}, for rbac check {@link org.apache.directory.fortress.core.rbac.Session#getRoles()}, for arbac check: {@link org.apache.directory.fortress.core.rbac.Session#getAdminRoles()}.
     * @param inPerm  must contain required attributes {@link Permission#objName} and {@link Permission#opName}.  {@link Permission#objId} is optional.
     * @return boolean containing result of check.
     * @throws org.apache.directory.fortress.core.FinderException
     *          In the event system error occurs looking up data on ldap server.
     */
    final boolean checkPermission( Session session, Permission inPerm ) throws FinderException
    {
        boolean isAuthZd = false;
        LdapConnection ld = null;
        String dn = getOpRdn( inPerm.getOpName(), inPerm.getObjId() ) + "," + GlobalIds.POBJ_NAME + "="
            + inPerm.getObjName() + "," + getRootDn( inPerm.isAdmin(), inPerm.getContextId() );

        try
        {
            ld = getAdminConnection();

            // LDAP Operation #1: Read the targeted permission from ldap server
            Entry entry = read( ld, dn, PERMISSION_OP_ATRS );
            if(entry == null)
            {
                // if permission not found, cannot continue.
                String error = "checkPermission DOES NOT EXIST : obj name [" + inPerm.getObjName() + "], obj id [" + inPerm.getObjId() + "], op name [" + inPerm.getOpName() + "], idAdmin [" + inPerm.isAdmin() + "]";
                throw new FinderException( GlobalErrIds.PERM_NOT_EXIST, error );
            }

            // load the permission entity with data retrieved from the permission node:
            Permission outPerm = unloadPopLdapEntry( entry, 0, inPerm.isAdmin() );

            // The admin flag will be set to 'true' if this is an administrative permission:
            outPerm.setAdmin( inPerm.isAdmin() );

            // Pass the tenant id along:
            outPerm.setContextId( inPerm.getContextId() );

            // The objective of these next steps is to evaluate the outcome of authorization attempt and trigger a write to slapd access logger containing the result.
            // The objectClass triggered by slapd access log write for upcoming ldap op is 'auditCompare'.
            // Set this attribute either with actual operation name that will succeed compare (for authZ success) or bogus value which will fail compare (for authZ failure):
            String attributeValue;

            // This method determines if the user is authorized for this permission:
            isAuthZd = isAuthorized( session, outPerm );

            // This is done to leave an audit trail in ldap server log:
            attributeValue = outPerm.getOpName();
            if ( isAuthZd )
            {
                // Yes, set the operation name onto this attribute for storage into audit trail:
                attributeValue = outPerm.getOpName();
            }
            else
            {
                // Changing this attribute value forces the compare to fail.  This facilitates tracking of authorization failures events in the slapd access log (by searching for compare failures).
                attributeValue = outPerm.getOpName() + GlobalIds.FAILED_AUTHZ_INDICATOR;
            }

            // There is a switch in fortress config to disable audit ops like this one.
            // But if used the compare method will use OpenLDAP's Proxy Authorization Control to assert identity of end user onto connection.
            // LDAP Operation #2: Compare.
            addAuthZAudit( ld, dn, session.getUser().getDn(), attributeValue );
        }
        catch ( LdapException e )
        {
            if ( !( e instanceof LdapNoSuchObjectException ) )
            {
                String error = "checkPermission caught LdapException=" + e.getMessage();
                throw new FinderException( GlobalErrIds.PERM_READ_OP_FAILED, error, e );
            }

            // There is a switch in fortress config to disable the audit ops.
            addAuthZAudit( ld, dn, session.getUser().getDn(), "AuthZ Invalid" );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return isAuthZd;
    }


    /**
     * Perform LDAP compare operation here to associate audit record with user authorization event.
     *
     * @param ld this method expects the ldap connection to be good
     * @param permDn contains distinguished name of the permission object.
     * @param userDn contains the distinguished name of the user object.
     * @param attributeValue string value will be associated with the 'audit' record stored in ldap.
     * @throws FinderException in the event ldap system exception occurs.
     */
    private void addAuthZAudit( LdapConnection ld, String permDn, String userDn, String attributeValue )
        throws FinderException
    {
        // Audit can be turned off here with fortress config param: 'enable.audit=false'
        if ( GlobalIds.IS_AUDIT && GlobalIds.IS_OPENLDAP )
        {
            try
            {
                // The compare method uses OpenLDAP's Proxy Authorization Control to assert identity of end user onto connection:
                // LDAP Operation #2: Compare:
                compareNode( ld, permDn, userDn, new DefaultAttribute( GlobalIds.POP_NAME, attributeValue ) );
            }
            catch ( UnsupportedEncodingException ee )
            {
                String error = "addAuthZAudit caught UnsupportedEncodingException=" + ee.getMessage();
                throw new FinderException( GlobalErrIds.PERM_COMPARE_OP_FAILED, error, ee );
            }
            catch ( LdapException e )
            {
                if ( !( e instanceof LdapNoSuchObjectException ) )
                {
                    String error = "addAuthZAudit caught LdapException=" + e.getMessage();
                    throw new FinderException( GlobalErrIds.PERM_COMPARE_OP_FAILED, error, e );
                }
            }
        }
    }


    /**
     * This function will first compare the userId from the session object with the list of users attached to permission object.
     * If match does not occur there, determine if there is a match between the authorized roles of user with roles attached to permission object.
     * For this use {@link org.apache.directory.fortress.core.rbac.Permission#isAdmin()} to determine if admin permissions or normal permissions have been passed in by caller.
     *
     * @param session contains the {@link org.apache.directory.fortress.core.rbac.Session#getUserId()},{@link Session#getRoles()} or {@link org.apache.directory.fortress.core.rbac.Session#getAdminRoles()}.
     * @param permission contains {@link org.apache.directory.fortress.core.rbac.Permission#getUsers()} and {@link Permission#getRoles()}.
     * @return binary result.
     */
    private boolean isAuthorized( Session session, Permission permission )
    {
        boolean result = false;
        Set<String> userIds = permission.getUsers();

        if ( VUtil.isNotNullOrEmpty( userIds ) && userIds.contains( session.getUserId() ) )
        {
            // user is assigned directly to this permission, no need to look further.
            return true;
        }

        Set<String> roles = permission.getRoles();

        if ( VUtil.isNotNullOrEmpty( roles ) )
        {
            if ( permission.isAdmin() )
            {
                // ARBAC Permission check include's User's inherited admin roles:
                Set<String> activatedRoles = AdminRoleUtil.getInheritedRoles( session.getAdminRoles(),
                    permission.getContextId() );

                for ( String role : roles )
                {
                    // This is case insensitive op determines if user has matching admin role to the admin permission::
                    if ( activatedRoles.contains( role ) )
                    {
                        result = true;
                        break;
                    }
                }
            }
            else
            {
                // RBAC Permission check include's User's inherited roles:
                Set<String> activatedRoles = RoleUtil.getInheritedRoles( session.getRoles(), permission.getContextId() );

                for ( String role : roles )
                {
                    // This is case insensitive op determines if user has matching role:
                    if ( activatedRoles.contains( role ) )
                    {
                        result = true;
                        break;
                    }
                }
            }
        }

        return result;
    }


    /**
     * @param le
     * @param sequence
     * @return
     * @throws LdapInvalidAttributeValueException 
     * @throws LdapException
     */
    private Permission unloadPopLdapEntry( Entry le, long sequence, boolean isAdmin ) throws LdapInvalidAttributeValueException
    {
        Permission entity = new ObjectFactory().createPermission();
        entity.setSequenceId( sequence );
        entity.setAbstractName( getAttribute( le, PERM_NAME ) );
        entity.setObjName( getAttribute( le, GlobalIds.POBJ_NAME ) );
        entity.setObjId( getAttribute( le, GlobalIds.POBJ_ID ) );
        entity.setOpName( getAttribute( le, GlobalIds.POP_NAME ) );
        entity.setInternalId( getAttribute( le, GlobalIds.FT_IID ) );
        entity.setRoles( getAttributeSet( le, ROLES ) );
        entity.setUsers( getAttributeSet( le, USERS ) );
        entity.setType( getAttribute( le, TYPE ) );
        entity.setDescription( getAttribute( le, SchemaConstants.DESCRIPTION_AT ) );
        entity.addProperties( AttrHelper.getProperties( getAttributes( le, GlobalIds.PROPS ) ) );
        entity.setAdmin( isAdmin );

        // TODO: find out the correct way to do this:
        if(le != null)
        {
            entity.setDn( le.getDn().getNormName() );
        }
        return entity;
    }


    /**
     * @param le
     * @param sequence
     * @return
     * @throws LdapInvalidAttributeValueException 
     * @throws LdapException
     */
    private PermObj unloadPobjLdapEntry( Entry le, long sequence, boolean isAdmin ) throws LdapInvalidAttributeValueException
    {
        PermObj entity = new ObjectFactory().createPermObj();
        entity.setSequenceId( sequence );
        entity.setObjName( getAttribute( le, GlobalIds.POBJ_NAME ) );
        entity.setOu( getAttribute( le, SchemaConstants.OU_AT ) );
        entity.setDn( le.getDn().getName() );
        entity.setInternalId( getAttribute( le, GlobalIds.FT_IID ) );
        entity.setType( getAttribute( le, TYPE ) );
        entity.setDescription( getAttribute( le, SchemaConstants.DESCRIPTION_AT ) );
        entity.addProperties( AttrHelper.getProperties( getAttributes( le, GlobalIds.PROPS ) ) );
        entity.setAdmin( isAdmin );
        return entity;
    }


    /**
     * @param permission
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Permission> findPermissions( Permission permission )
        throws FinderException
    {
        List<Permission> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( permission.isAdmin(), permission.getContextId() );

        try
        {
            String permObjVal = encodeSafeText( permission.getObjName(), GlobalIds.PERM_LEN );
            String permOpVal = encodeSafeText( permission.getOpName(), GlobalIds.PERM_LEN );
            String filter = GlobalIds.FILTER_PREFIX + PERM_OP_OBJECT_CLASS_NAME + ")("
                + GlobalIds.POBJ_NAME + "=" + permObjVal + "*)("
                + GlobalIds.POP_NAME + "=" + permOpVal + "*))";

            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISSION_OP_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPopLdapEntry( searchResults.getEntry(), sequence++, permission.isAdmin() ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }
        return permList;
    }


    /**
     * @param permObj
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<PermObj> findPermissions( PermObj permObj )
        throws FinderException
    {
        List<PermObj> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( permObj.isAdmin(), permObj.getContextId() );

        try
        {
            String permObjVal = encodeSafeText( permObj.getObjName(), GlobalIds.PERM_LEN );
            String filter = GlobalIds.FILTER_PREFIX + PERM_OBJ_OBJECT_CLASS_NAME + ")("
                + GlobalIds.POBJ_NAME + "=" + permObjVal + "*))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISION_OBJ_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPobjLdapEntry( searchResults.getEntry(), sequence++, permObj.isAdmin() ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param ou
     * @return
     * @throws FinderException
     */
    final List<PermObj> findPermissions( OrgUnit ou, boolean limitSize ) throws FinderException
    {
        List<PermObj> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( ou.getContextId(), GlobalIds.PERM_ROOT );

        try
        {
            String ouVal = encodeSafeText( ou.getName(), GlobalIds.OU_LEN );
            String filter = GlobalIds.FILTER_PREFIX + PERM_OBJ_OBJECT_CLASS_NAME + ")("
                + SchemaConstants.OU_AT + "=" + ouVal + "*))";
            int maxLimit;

            if ( limitSize )
            {
                maxLimit = 10;
            }
            else
            {
                maxLimit = 0;
            }

            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISION_OBJ_ATRS, false, GlobalIds.BATCH_SIZE, maxLimit );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPobjLdapEntry( searchResults.getEntry(), sequence++, false ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param role
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Permission> findPermissions( Role role ) throws FinderException
    {
        List<Permission> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot;

        boolean isAdmin = false;
        if ( role.getClass().equals( AdminRole.class ) )
        {
            permRoot = getRootDn( role.getContextId(), GlobalIds.ADMIN_PERM_ROOT );
            isAdmin = true;
        }
        else
        {
            permRoot = getRootDn( role.getContextId(), GlobalIds.PERM_ROOT );
        }

        try
        {
            String roleVal = encodeSafeText( role.getName(), GlobalIds.ROLE_LEN );
            String filter = GlobalIds.FILTER_PREFIX + PERM_OP_OBJECT_CLASS_NAME + ")(";
            Set<String> roles;

            if ( role.getClass().equals( AdminRole.class ) )
            {
                roles = AdminRoleUtil.getAscendants( role.getName(), role.getContextId() );
            }
            else
            {
                roles = RoleUtil.getAscendants( role.getName(), role.getContextId() );
            }

            if ( VUtil.isNotNullOrEmpty( roles ) )
            {
                filter += "|(" + ROLES + "=" + roleVal + ")";

                for ( String uRole : roles )
                {
                    filter += "(" + ROLES + "=" + uRole + ")";
                }

                filter += ")";
            }
            else
            {
                filter += ROLES + "=" + roleVal + ")";
            }

            filter += ")";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISSION_OP_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPopLdapEntry( searchResults.getEntry(), sequence++, isAdmin ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_ROLE_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions caught LdapException=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_ROLE_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param user
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Permission> findPermissions( User user ) throws FinderException
    {
        List<Permission> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( user.getContextId(), GlobalIds.PERM_ROOT );

        try
        {
            String filter = GlobalIds.FILTER_PREFIX + PERM_OP_OBJECT_CLASS_NAME + ")(|";
            Set<String> roles = RoleUtil.getInheritedRoles( user.getRoles(), user.getContextId() );

            if ( VUtil.isNotNullOrEmpty( roles ) )
            {
                for ( String uRole : roles )
                {
                    filter += "(" + ROLES + "=" + uRole + ")";
                }
            }

            filter += "(" + USERS + "=" + user.getUserId() + ")))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISSION_OP_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPopLdapEntry( searchResults.getEntry(), sequence++,false ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions user [" + user.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_USER_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions user [" + user.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_USER_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param user
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Permission> findUserPermissions( User user ) throws FinderException
    {
        List<Permission> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( user.getContextId(), GlobalIds.PERM_ROOT );

        try
        {
            String filter = GlobalIds.FILTER_PREFIX + PERM_OP_OBJECT_CLASS_NAME + ")";
            filter += "(" + USERS + "=" + user.getUserId() + "))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISSION_OP_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPopLdapEntry( searchResults.getEntry(), sequence++, false ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findUserPermissions user [" + user.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_USER_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findUserPermissions user [" + user.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_USER_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param session
     * @return
     * @throws org.apache.directory.fortress.core.FinderException
     *
     */
    final List<Permission> findPermissions( Session session, boolean isAdmin ) throws FinderException
    {
        List<Permission> permList = new ArrayList<>();
        LdapConnection ld = null;
        String permRoot = getRootDn( isAdmin, session.getContextId() );

        try
        {
            String filter = GlobalIds.FILTER_PREFIX + PERM_OP_OBJECT_CLASS_NAME + ")(|";
            filter += "(" + USERS + "=" + session.getUserId() + ")";
            Set<String> roles;
            if(isAdmin)
            {
                roles = AdminRoleUtil.getInheritedRoles( session.getAdminRoles(), session.getContextId() );
            }
            else
            {
                roles = RoleUtil.getInheritedRoles( session.getRoles(), session.getContextId() );
            }
            if ( VUtil.isNotNullOrEmpty( roles ) )
            {
                for ( String uRole : roles )
                {
                    filter += "(" + ROLES + "=" + uRole + ")";
                }
            }

            filter += "))";
            ld = getAdminConnection();
            SearchCursor searchResults = search( ld, permRoot,
                SearchScope.SUBTREE, filter, PERMISSION_OP_ATRS, false, GlobalIds.BATCH_SIZE );
            long sequence = 0;

            while ( searchResults.next() )
            {
                permList.add( unloadPopLdapEntry( searchResults.getEntry(), sequence++, isAdmin ) );
            }
        }
        catch ( LdapException e )
        {
            String error = "findPermissions user [" + session.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SESS_SEARCH_FAILED, error, e );
        }
        catch ( CursorException e )
        {
            String error = "findPermissions user [" + session.getUserId()
                + "] caught LdapException in PermDAO.findPermissions=" + e.getMessage();
            throw new FinderException( GlobalErrIds.PERM_SESS_SEARCH_FAILED, error, e );
        }
        finally
        {
            closeAdminConnection( ld );
        }

        return permList;
    }


    /**
     * @param opName
     * @param objId
     * @return
     */
    static String getOpRdn( String opName, String objId )
    {
        String rDn;

        if ( objId != null && objId.length() > 0 )
        {
            rDn = GlobalIds.POP_NAME + "=" + opName + "+" + GlobalIds.POBJ_ID + "=" + objId;
        }
        else
        {
            rDn = GlobalIds.POP_NAME + "=" + opName;
        }

        return rDn;
    }


    private String getDn( Permission pOp, String contextId )
    {
        return getOpRdn( pOp.getOpName(), pOp.getObjId() ) + "," + GlobalIds.POBJ_NAME + "=" + pOp.getObjName()
            + "," + getRootDn( pOp.isAdmin(), contextId );
    }


    private String getDn( PermObj pObj, String contextId )
    {
        return GlobalIds.POBJ_NAME + "=" + pObj.getObjName() + "," + getRootDn( pObj.isAdmin(), contextId );
    }


    private String getRootDn( boolean isAdmin, String contextId )
    {
        String dn;

        if ( isAdmin )
        {
            dn = getRootDn( contextId, GlobalIds.ADMIN_PERM_ROOT );
        }
        else
        {
            dn = getRootDn( contextId, GlobalIds.PERM_ROOT );
        }

        return dn;
    }
}