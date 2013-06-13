/*
 * Copyright (c) 2009-2013, JoshuaTree. All Rights Reserved.
 */

package us.jts.fortress.ant;

import us.jts.fortress.ldap.container.OrganizationalUnit;

import java.util.ArrayList;
import java.util.List;


/**
 * The class is used by {@link FortressAntTask} to remove {@link us.jts.fortress.ldap.container.OrganizationalUnit}s  used to drive {@link us.jts.fortress.ldap.container.OrganizationalUnitP#delete(us.jts.fortress.ldap.container.OrganizationalUnit)}.
 * It is not intended to be callable by programs outside of the Ant load utility.  The class name itself maps to the xml tag used by load utility.
 * <p>This class name, 'Delcontainer', is used for the xml tag in the load script.</p>
 * <pre>
 * {@code
 * <target name="all">
 *     <FortressAdmin>
 *         <delcontainer>
 *           ...
 *         </delcontainer>
 *     </FortressAdmin>
 * </target>
 * }
 * </pre>
 * <p/>
 * <font size="4" color="red">
 * This class is destructive as it will remove all nodes below the container using recursive delete function.<BR>
 * Extreme care should be taken during execution to ensure target dn is correct and permanent removal of data is intended.  There is no
 * 'undo' for this operation.
 * </font>
 * <p/>
 *
 * @author Shawn McKinney
 */
public class Delcontainer
{
    final private List<OrganizationalUnit> containers = new ArrayList<>();


    /**
     * All Ant data entities must have a default constructor.
     */
    public Delcontainer()
    {
    }


    /**
     * <p>This method name, 'addContainer', is used for derived xml tag 'container' in the load script.</p>
     * <pre>
     * {@code
     * <delcontainer>
     *     <container name="Config"/>
     *     <container name="People"/>
     *     <container name="Policies"/>
     *     <container name="RBAC"/>
     *     <container name="Roles" parent="RBAC"/>
     *     <container name="Permissions" parent="RBAC"/>
     *     <container name="Constraints" parent="RBAC"/>
     *     <container name="ARBAC"/>
     *     <container name="OS-U" parent="ARBAC"/>
     *     <container name="OS-P" parent="ARBAC"/>
     *     <container name="AdminRoles" parent="ARBAC"/>
     *     <container name="AdminPerms" parent="ARBAC"/>
     * </delcontainer>
     * }
     * </pre>
     *
     * @param ou contains reference to data element targeted for deletion.
     */
    public void addContainer(us.jts.fortress.ldap.container.OrganizationalUnit ou)
    {
        this.containers.add(ou);
    }


    /**
     * Used by {@link FortressAntTask#deleteContainers()} to retrieve list of OrganizationalUnits as defined in input xml file.
     *
     * @return collection containing {@link us.jts.fortress.ldap.container.OrganizationalUnit}s targeted for removal.
     */
    public List<us.jts.fortress.ldap.container.OrganizationalUnit> getContainers()
    {
        return this.containers;
    }
}
