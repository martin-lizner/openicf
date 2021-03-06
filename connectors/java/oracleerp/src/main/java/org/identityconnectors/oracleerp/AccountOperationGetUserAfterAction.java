/*
 * ====================
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2008-2009 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of the Common Development
 * and Distribution License("CDDL") (the "License").  You may not use this file
 * except in compliance with the License.
 *
 * You can obtain a copy of the License at
 * http://opensource.org/licenses/cddl1.php
 * See the License for the specific language governing permissions and limitations
 * under the License.
 *
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at http://opensource.org/licenses/cddl1.php.
 * If applicable, add the following below this CDDL Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 */
package org.identityconnectors.oracleerp;

import static org.identityconnectors.oracleerp.OracleERPUtil.ACTION;
import static org.identityconnectors.oracleerp.OracleERPUtil.ACTION_CONTEXT;
import static org.identityconnectors.oracleerp.OracleERPUtil.CONN;
import static org.identityconnectors.oracleerp.OracleERPUtil.ERRORS;
import static org.identityconnectors.oracleerp.OracleERPUtil.ID;
import static org.identityconnectors.oracleerp.OracleERPUtil.OP_GET_USER;
import static org.identityconnectors.oracleerp.OracleERPUtil.TRACE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.dbcommon.SQLUtil;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;

/**
 * The schema implementation of the SPI.
 *
 * @author Petr Jung
 * @version $Revision 1.0$
 * @since 1.0
 */
final class AccountOperationGetUserAfterAction extends Operation {

    private static final Log LOG = Log.getLog(AccountOperationGetUserAfterAction.class);

    private static final String CHANGED_ATTRIBUTES = "changedAttributes";
    private static final String CURRENT_ATTRIBUTES = "currentAttributes";

    /**
     * @param conn
     * @param cfg
     */
    AccountOperationGetUserAfterAction(OracleERPConnection conn, OracleERPConfiguration cfg) {
        super(conn, cfg);
    }

    public ConnectorObjectBuilder runScriptOnConnector(Object userName, ConnectorObjectBuilder cob) {
        /*
         * Build the actionContext to pass it to the script according the
         * documentation
         */
        final Map<String, Object> changedAttributes = new HashMap<String, Object>();
        final Map<String, Object> actionContext = new HashMap<String, Object>();
        final Map<String, Object> inputMap = new HashMap<String, Object>();
        final List<String> errorList = new ArrayList<String>();

        // Connection
        actionContext.put(CONN, getConn().getConnection()); // The real
                                                            // connection
        actionContext.put(ACTION, OP_GET_USER); // The action is the operation
                                                // name
                                                // createUser/updateUser/deleteUser/disableUser/enableUser
        final Map<String, Object> scriptAttributes =
                OracleERPUtil.getScriptAttributes(cob.build().getAttributes());
        actionContext.put(CURRENT_ATTRIBUTES, scriptAttributes); // The
                                                                 // attributes
        actionContext.put(CHANGED_ATTRIBUTES, changedAttributes); // The
                                                                  // attributes
        actionContext.put(ID, userName); // The user name
        actionContext.put(TRACE, LOG); // The loging
        actionContext.put(ERRORS, errorList); // The error list
        LOG.ok("runScriptOnConnector action: {0}, ID: {1}, scriptAttributes: {2}", OP_GET_USER,
                userName, scriptAttributes);

        inputMap.put(ACTION_CONTEXT, actionContext);

        /*
         * Build the script executor and run the script
         */
        try {
            final ClassLoader loader = getClass().getClassLoader();
            final String scriptLanguage = getCfg().getUserAfterActionScript().getScriptLanguage();
            final ScriptExecutorFactory scriptExFact =
                    ScriptExecutorFactory.newInstance(scriptLanguage);
            final String scriptText = getCfg().getUserAfterActionScript().getScriptText();

            LOG.ok("execute GetUserAfterAction script: {0}", scriptText);
            final ScriptExecutor scripEx = scriptExFact.newScriptExecutor(loader, scriptText, true);
            scripEx.execute(inputMap);

            // Go through the errors and throw first one
            StringBuilder errorBld = new StringBuilder();
            for (String s : errorList) {
                errorBld.append(s);
                errorBld.append("; ");
            }
            // Any errors, warnings?
            if (errorBld.length() != 0) {
                final String msg = errorBld.toString();
                LOG.error("errors in script: {0}", msg);
                throw new ConnectorException(msg);
            }
            // Make sure, the connection is commit
            getConn().commit();
        } catch (Exception e) {
            LOG.error(e, "error in script");
            SQLUtil.rollbackQuietly(getConn());
            throw ConnectorException.wrap(e);
        }
        // add the attributes to the connector object builder
        for (Entry<String, Object> entry : changedAttributes.entrySet()) {
            cob.addAttribute(entry.getKey(), entry.getValue());
        }
        return cob;
    }

}
