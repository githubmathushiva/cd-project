/*
 * Copyright (c) 2009-2013, JoshuaTree. All Rights Reserved.
 */

package us.jts.fortress.rbac;

import us.jts.fortress.GlobalIds;
import us.jts.fortress.ValidationException;
import us.jts.fortress.SecurityException;
import us.jts.fortress.util.attr.VUtil;
import us.jts.fortress.util.cache.CacheMgr;
import us.jts.fortress.util.cache.Cache;
import org.apache.log4j.Logger;
import org.jgrapht.graph.SimpleDirectedGraph;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * This utility wraps {@link HierUtil} methods to provide hierarchical functionality for the {@link us.jts.fortress.rbac.AdminRole} data set.
 * The child to parent relationships are stored within a data cache, {@link #adminRoleCache}, contained within this class.  The parent-child edges are contained in LDAP,
 * in {@code ftParents} attribute.  The ldap data is retrieved {@link AdminRoleP#getAllDescendants(String)} and loaded into {@code org.jgrapht.graph.SimpleDirectedGraph}.
 * The graph...
 * <ol>
 * <li>is stored as singleton in this class with vertices of {@code String}, and edges, as {@link Relationship}s</li>
 * <li>utilizes open source library, see <a href="http://www.jgrapht.org/">JGraphT</a>.</li>
 * <li>contains a general hierarchical data structure i.e. allows multiple inheritance with parents.</li>
 * <li>is a simple directed graph thus does not allow cycles.</li>
 * </ol>
 * After update is performed to ldap, the singleton is refreshed with latest info.
 * <p/>
 * Static methods on this class are intended for use by other Fortress classes, i.e. {@link DelAdminMgrImpl} and {@link us.jts.fortress.rbac.PermDAO}
 * and cannot be directly invoked by outside programs.
 * <p/>
 * This class contains singleton that can be updated but is thread safe.
 * <p/>

 *  @author Shawn McKinney
 */
final class AdminRoleUtil
{
    private static Cache adminRoleCache;
    private static AdminRoleP adminRoleP = new AdminRoleP();
    private static final String CLS_NM = AdminRoleUtil.class.getName();
    private static final Logger log = Logger.getLogger(CLS_NM);

    /**
     * Initialize the AdminRole hierarchies.  This will read the {@link Hier} data set from ldap and load into
     * the JGraphT simple digraph that referenced statically within this class.
     */
    static
    {
        CacheMgr cacheMgr = CacheMgr.getInstance();
        adminRoleCache = cacheMgr.getCache("fortress.admin.roles");
    }

    /**
     * Used to determine if one {@link us.jts.fortress.rbac.AdminRole} is the parent of another.  This method
     * will call recursive routine {@link #getAscendants(String, String)} to walk the {@code org.jgrapht.graph.SimpleDirectedGraph} data structure
     * returning flag indicating if parent-child relationship is valid.
     *
     * @param child maps to logical {@link us.jts.fortress.rbac.AdminRole#name} on 'ftRls' object class.
     * @param parent maps to logical {@link us.jts.fortress.rbac.AdminRole#name} on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return boolean result, 'true' indicates parent/child relationship exists.
     */
    static boolean isParent(String child, String parent, String contextId)
    {
        boolean result = false;
        Set<String> parents = getAscendants(child, contextId);
        if(parents != null && parents.size() > 0)
        {
            result = parents.contains(parent.toUpperCase());
        }
        return result;
    }

    /**
     * Recursively traverse the {@link us.jts.fortress.rbac.AdminRole} graph and return all of the descendants of a given parent {@link us.jts.fortress.rbac.AdminRole#name}.
     * @param roleName {@link us.jts.fortress.rbac.AdminRole#name} maps on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return Set of AdminRole names are children {@link us.jts.fortress.rbac.AdminRole}s of given parent.
     */
    static Set<String> getDescendants(String roleName, String contextId)
    {
        return HierUtil.getDescendants(roleName, getGraph(contextId));
    }

    /**
     * Recursively traverse the hierarchical role graph and return all of the parents of a given child role.
     * @param roleName maps to logical {@link us.jts.fortress.rbac.AdminRole#name} on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return Set of AdminRole names that are descendants of given node.
     */
    static Set<String> getAscendants(String roleName, String contextId)
    {
        return HierUtil.getAscendants(roleName, getGraph(contextId));
    }

    /**
     * Traverse one level of the {@link us.jts.fortress.rbac.AdminRole} graph and return all of the parents (direct ascendants) of a given parent {@link us.jts.fortress.rbac.AdminRole#name}.
     * @param roleName {@link us.jts.fortress.rbac.AdminRole#name} maps on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return Set of AdminRole names are parents {@link us.jts.fortress.rbac.AdminRole}s of given child.
     */
    static Set<String> getParents(String roleName, String contextId)
    {
        return HierUtil.getParents(roleName, getGraph(contextId));
    }

    /**
     * Traverse one level of the hierarchical role graph and return all of the children (direct descendants) of a given parent role.
     * @param roleName maps to logical {@link us.jts.fortress.rbac.AdminRole#name} on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return Set of AdminRole names that are children of given parent.
     */
    static Set<String> getChildren(String roleName, String contextId)
    {
        return HierUtil.getChildren(roleName, getGraph(contextId));
    }

    /**
     * Return number of children (direct descendants) a given parent role has.
     * @param roleName maps to logical {@link us.jts.fortress.rbac.AdminRole#name} on 'ftRls' object class.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return int value contains the number of children of a given parent AdminRole.
     */
    static int numChildren(String roleName, String contextId)
    {
        return HierUtil.numChildren(roleName, getGraph(contextId));
    }

    /**
     * Return Set of {@link us.jts.fortress.rbac.AdminRole#name}s ascendants.  Used by {@link us.jts.fortress.rbac.PermDAO#checkPermission}
     * for computing authorized {@link us.jts.fortress.rbac.UserAdminRole#name}s.
     * @param uRoles contains list of adminRoles activated within a {@link us.jts.fortress.rbac.User}'s {@link us.jts.fortress.rbac.Session}.
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return contains Set of all authorized adminRoles for a given User.
     */
    static Set<String> getInheritedRoles(List<UserAdminRole> uRoles, String contextId)
    {
        // create Set with case insensitive comparator:
        Set<String> iRoles = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        if (VUtil.isNotNullOrEmpty(uRoles))
        {
            for (UserAdminRole uRole : uRoles)
            {
                String rleName = uRole.getName();
                iRoles.add(rleName);
                Set<String> parents = HierUtil.getAscendants(rleName, getGraph(contextId));
                if (VUtil.isNotNullOrEmpty(parents))
                    iRoles.addAll(parents);
            }
        }
        return iRoles;
    }

    /**
     * This api is used by {@link DelAdminMgrImpl} to determine parentage for Hierarchical ARBAC processing.
     * It calls {@link HierUtil#validateRelationship(org.jgrapht.graph.SimpleDirectedGraph, String, String, boolean)} to evaluate three adminRole relationship expressions:
     * <ol>
     * <li>If child equals parent</li>
     * <li>If mustExist true and parent-child relationship exists</li>
     * <li>If mustExist false and parent-child relationship does not exist</li>
     * </ol>
     * Method will throw {@link us.jts.fortress.ValidationException} if rule check fails meaning caller failed validation
     * attempt to add/remove hierarchical relationship failed.
     *
     * @param childRole contains {@link us.jts.fortress.rbac.AdminRole#name} of child.
     * @param parentRole contains {@link us.jts.fortress.rbac.AdminRole#name} of parent.
     * @param mustExist boolean is used to specify if relationship must be true.
     * @throws us.jts.fortress.ValidationException in the event it fails one of the 3 checks.
     */
    static void validateRelationship(AdminRole childRole, AdminRole parentRole, boolean mustExist)
        throws ValidationException
    {
        HierUtil.validateRelationship(getGraph(childRole.getContextId()), childRole.getName(), parentRole.getName(), mustExist);
    }

    /**
     * This api allows synchronized access to allow updates to hierarchical relationships.
     * Method will update the hierarchical data set and reload the JGraphT simple digraph with latest.
     *
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @param relationship contains parent-child relationship targeted for addition.
     * @param op   used to pass the ldap op {@link Hier.Op#ADD}, {@link Hier.Op#MOD}, {@link us.jts.fortress.rbac.Hier.Op#REM}
     * @throws us.jts.fortress.SecurityException in the event of a system error.
     */
    static void updateHier(String contextId, Relationship relationship, Hier.Op op) throws SecurityException
    {
        HierUtil.updateHier(getGraph(contextId), relationship, op);
    }

    /**
     * Read this ldap record,{@code cn=Hierarchies, ou=OS-P} into this entity, {@link Hier}, before loading into this collection class,{@code org.jgrapht.graph.SimpleDirectedGraph}
     * using 3rd party lib, <a href="http://www.jgrapht.org/">JGraphT</a>.
     *
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return
     */
    private static SimpleDirectedGraph<String, Relationship> loadGraph(String contextId)
    {
        Hier inHier = new Hier(Hier.Type.ROLE);
        inHier.setContextId(contextId);
        log.info(CLS_NM + ".loadGraph initializing ADMIN ROLE context [" + inHier.getContextId() + "]");
        List<Graphable> descendants = null;
        try
        {
            descendants = adminRoleP.getAllDescendants(inHier.getContextId());
        }
        catch(SecurityException se)
        {
            log.info(CLS_NM + ".loadGraph caught SecurityException=" + se);
        }
        Hier hier = HierUtil.loadHier(contextId, descendants);
        SimpleDirectedGraph<String, Relationship> graph;
        synchronized (HierUtil.getLock(contextId, HierUtil.Type.ARLE))
        {
            graph = HierUtil.buildGraph(hier);
        }
        adminRoleCache.put(getKey(contextId), graph);
        return graph;
    }

    /**
     * Read this ldap record,{@code cn=Hierarchies, ou=OS-P} into this entity, {@link Hier}, before loading into this collection class,{@code org.jgrapht.graph.SimpleDirectedGraph}
     * using 3rd party lib, <a href="http://www.jgrapht.org/">JGraphT</a>.
     *
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return
     */
    private static SimpleDirectedGraph<String, Relationship> getGraph(String contextId)
    {
        SimpleDirectedGraph<String, Relationship> graph = (SimpleDirectedGraph<String, Relationship>) adminRoleCache.get(getKey(contextId));
        if (graph == null)
        {
            graph = loadGraph(contextId);
        }
        return graph;
    }

    /**
     *
     * @param contextId maps to sub-tree in DIT, for example ou=contextId, dc=jts, dc = com.
     * @return
     */
    private static String getKey(String contextId)
    {
        String key = HierUtil.Type.ARLE.toString();
        if(VUtil.isNotNullOrEmpty(contextId) && !contextId.equalsIgnoreCase(GlobalIds.NULL))
        {
            key += ":" + contextId;
        }
        return key;
    }
}
