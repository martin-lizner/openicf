/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 ForgeRock AS. All Rights Reserved
 *
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License). You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://forgerock.org/license/CDDLv1.0.html
 * See the License for the specific language governing
 * permission and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at http://forgerock.org/license/CDDLv1.0.html
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 */
package org.identityconnectors.ldap.sync.timestamps;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.TimeZone;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.PartialResultException;
import javax.naming.directory.Attributes;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.SyncDeltaBuilder;
import org.identityconnectors.framework.common.objects.SyncDeltaType;
import org.identityconnectors.framework.common.objects.SyncResultsHandler;
import org.identityconnectors.framework.common.objects.SyncToken;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.ldap.LdapConnection;
import static org.identityconnectors.ldap.LdapUtil.buildMemberIdAttribute;
import org.identityconnectors.ldap.ADUserAccountControl;
import org.identityconnectors.ldap.LdapConnection.ServerType;
import org.identityconnectors.ldap.search.LdapInternalSearch;
import org.identityconnectors.ldap.search.SearchResultsHandler;
import org.identityconnectors.ldap.search.SimplePagedSearchStrategy;
import org.identityconnectors.ldap.sync.LdapSyncStrategy;

/**
 *
 * @author Gael Allioux <gael.allioux@forgerock.com>
 */
/**
 * An implementation of the sync operation based on the generic timestamps
 * attribute present in any LDAP directory.
 */
public class TimestampsSyncStrategy implements LdapSyncStrategy {

    private final String createTimestamp = "createTimestamp";
    private final String modifyTimestamp = "modifyTimestamp";
    private final LdapConnection conn;
    private final ObjectClass oclass;
    private static final Log logger = Log.getLog(TimestampsSyncStrategy.class);

    public TimestampsSyncStrategy(LdapConnection conn, ObjectClass oclass) {
        this.conn = conn;
        this.oclass = oclass;
    }

    public SyncToken getLatestSyncToken() {
        return new SyncToken(getNowTime());
    }

    public void sync(SyncToken token, final SyncResultsHandler handler, final OperationOptions options) {
        // ldapsearch -h host -p 389 -b "dc=example,dc=com" -D "cn=administrator,cn=users,dc=example,dc=com" -w xxx "whenchanged>=20130214130642.0Z"
        // on AD
        // ldapsearch -h host -p 389 -b 'dc=example,dc=com' -S modifytimestamp -D 'cn=directory manager' -w xxx "createTimestamp>=20120424080554Z"
        // on other directories

        final String now = getNowTime();
        SearchControls controls = LdapInternalSearch.createDefaultSearchControls();
        controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
        controls.setDerefLinkFlag(false);
        controls.setReturningAttributes(new String[]{"*", createTimestamp, modifyTimestamp});

        LdapInternalSearch search = new LdapInternalSearch(conn,
                generateFilter(oclass, token),
                Arrays.asList(conn.getConfiguration().getBaseContextsToSynchronize()),
                new SimplePagedSearchStrategy(conn.getConfiguration().getBlockSize()),
                controls);

        try {
            search.execute(new SearchResultsHandler() {
                public boolean handle(String baseDN, SearchResult result) throws NamingException {
                    Attributes attrs = result.getAttributes();
                    NamingEnumeration<? extends javax.naming.directory.Attribute> attrsEnum = attrs.getAll();
                    Uid uid = conn.getSchemaMapping().createUid(conn.getConfiguration().getUidAttribute(), attrs);
                    // build the object first
                    ConnectorObjectBuilder cob = new ConnectorObjectBuilder();
                    cob.setUid(uid);
                    cob.setObjectClass(oclass);
                    cob.setName(result.getNameInNamespace());

                    // Let's process ACCOUNT specifics...
                    if (oclass.equals(ObjectClass.ACCOUNT) && conn.getServerType().equals(ServerType.MSAD_GC)) {
                        javax.naming.directory.Attribute uac = attrs.get(ADUserAccountControl.MS_USR_ACCT_CTRL_ATTR);
                        if (uac != null) {
                            String controls = uac.get().toString();
                            cob.addAttribute(AttributeBuilder.buildEnabled(!ADUserAccountControl.isAccountDisabled(controls)));
                            cob.addAttribute(AttributeBuilder.buildLockOut(ADUserAccountControl.isAccountLockOut(controls)));
                            cob.addAttribute(AttributeBuilder.buildPasswordExpired(ADUserAccountControl.isPasswordExpired(controls)));
                        }
                    }
                    // Set all Attributes
                    while (attrsEnum.hasMore()) {
                        javax.naming.directory.Attribute attr = attrsEnum.next();
                        String id = attr.getID();
                        NamingEnumeration vals = attr.getAll();
                        ArrayList values = new ArrayList();
                        while (vals.hasMore()) {
                            values.add(vals.next());
                        }
                        if (conn.getConfiguration().isGetGroupMemberId() && oclass.equals(ObjectClass.GROUP) && id.equalsIgnoreCase("member")) {
                            cob.addAttribute(buildMemberIdAttribute(conn, attr));
                        }
                        cob.addAttribute(AttributeBuilder.build(id, values));
                    }

                    SyncDeltaBuilder syncDeltaBuilder = new SyncDeltaBuilder();
                    syncDeltaBuilder.setToken(new SyncToken(now));
                    syncDeltaBuilder.setDeltaType(SyncDeltaType.CREATE_OR_UPDATE);
                    syncDeltaBuilder.setUid(uid);
                    syncDeltaBuilder.setObject(cob.build());

                    return handler.handle(syncDeltaBuilder.build());
                }
            });
        } catch (ConnectorException e) {
            if (e.getCause() instanceof PartialResultException) {
                logger.warn("PartialResultException has been caught");
            } else {
                throw e;
            }
        }
    }

    private String getNowTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");
        sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

        switch (conn.getServerType()) {
            case MSAD_GC:
                return sdf.format(new Date()) + ".0Z";
            default:
                return sdf.format(new Date()) + "Z";
        }
    }

    private String generateFilter(ObjectClass oc, SyncToken token) {
        StringBuilder filter;
        filter = new StringBuilder();

        if (token == null) {
            token = this.getLatestSyncToken();
        }
        filter.append("(");
        filter.append(modifyTimestamp);
        filter.append(">=");
        filter.append(token.getValue().toString());
        filter.append(")");
        if (ObjectClass.ACCOUNT.equals(oc)) {
            String[] oclasses = conn.getConfiguration().getAccountObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
            if (conn.getConfiguration().getAccountSynchronizationFilter() != null){
                filter.append(conn.getConfiguration().getAccountSynchronizationFilter());
            }
        } else if (ObjectClass.GROUP.equals(oc)) {
            String[] oclasses = conn.getConfiguration().getGroupObjectClasses();
            for (int i = 0; i < oclasses.length; i++) {
                filter.append("(objectClass=");
                filter.append(oclasses[i]);
                filter.append(")");
            }
            if (conn.getConfiguration().getGroupSynchronizationFilter() != null){
                filter.append(conn.getConfiguration().getGroupSynchronizationFilter());
            }
        } else { // we use the ObjectClass value as the filter...
            filter.append("(objectClass=");
            filter.append(oc.getObjectClassValue());
            filter.append(")");
        }

        filter.insert(0, "(&");
        filter.append(")");
        return filter.toString();
    }
}
