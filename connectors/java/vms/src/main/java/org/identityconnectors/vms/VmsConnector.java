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
package org.identityconnectors.vms;

import static org.identityconnectors.vms.VmsConstants.ATTR_ACCOUNT;
import static org.identityconnectors.vms.VmsConstants.ATTR_ASTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_BATCH;
import static org.identityconnectors.vms.VmsConstants.ATTR_BIOLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_BYTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_CLI;
import static org.identityconnectors.vms.VmsConstants.ATTR_CLITABLES;
import static org.identityconnectors.vms.VmsConstants.ATTR_COPY_LOGIN_SCRIPT;
import static org.identityconnectors.vms.VmsConstants.ATTR_CPUTIME;
import static org.identityconnectors.vms.VmsConstants.ATTR_CREATE_DIRECTORY;
import static org.identityconnectors.vms.VmsConstants.ATTR_DEFAULT;
import static org.identityconnectors.vms.VmsConstants.ATTR_DEFPRIVILEGES;
import static org.identityconnectors.vms.VmsConstants.ATTR_DEVICE;
import static org.identityconnectors.vms.VmsConstants.ATTR_DIALUP;
import static org.identityconnectors.vms.VmsConstants.ATTR_DIOLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_DIRECTORY;
import static org.identityconnectors.vms.VmsConstants.ATTR_ENQLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_FILLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_FLAGS;
import static org.identityconnectors.vms.VmsConstants.ATTR_GRANT_IDS;
import static org.identityconnectors.vms.VmsConstants.ATTR_JTQUOTA;
import static org.identityconnectors.vms.VmsConstants.ATTR_LGICMD;
import static org.identityconnectors.vms.VmsConstants.ATTR_LOCAL;
import static org.identityconnectors.vms.VmsConstants.ATTR_LOGIN_FAILS;
import static org.identityconnectors.vms.VmsConstants.ATTR_LOGIN_SCRIPT_SOURCE;
import static org.identityconnectors.vms.VmsConstants.ATTR_MAXACCTJOBS;
import static org.identityconnectors.vms.VmsConstants.ATTR_MAXDETACH;
import static org.identityconnectors.vms.VmsConstants.ATTR_MAXJOBS;
import static org.identityconnectors.vms.VmsConstants.ATTR_NETWORK;
import static org.identityconnectors.vms.VmsConstants.ATTR_OWNER;
import static org.identityconnectors.vms.VmsConstants.ATTR_PBYTLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_PGFLQUOTA;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRCLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIMEDAYS;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIORITY;
import static org.identityconnectors.vms.VmsConstants.ATTR_PRIVILEGES;
import static org.identityconnectors.vms.VmsConstants.ATTR_PWDEXPIRED;
import static org.identityconnectors.vms.VmsConstants.ATTR_PWDMINIMUM;
import static org.identityconnectors.vms.VmsConstants.ATTR_QUEPRIO;
import static org.identityconnectors.vms.VmsConstants.ATTR_REMOTE;
import static org.identityconnectors.vms.VmsConstants.ATTR_SHRFILLM;
import static org.identityconnectors.vms.VmsConstants.ATTR_TQELM;
import static org.identityconnectors.vms.VmsConstants.ATTR_UIC;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSDEFAULT;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSEXTENT;
import static org.identityconnectors.vms.VmsConstants.ATTR_WSQUOTA;
import static org.identityconnectors.vms.VmsConstants.FLAG_DISUSER;

import java.io.IOException;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.identityconnectors.common.CollectionUtil;
import org.identityconnectors.common.StringUtil;
import org.identityconnectors.common.logging.Log;
import org.identityconnectors.common.script.ScriptExecutor;
import org.identityconnectors.common.script.ScriptExecutorFactory;
import org.identityconnectors.common.security.GuardedString;
import org.identityconnectors.framework.common.exceptions.AlreadyExistsException;
import org.identityconnectors.framework.common.exceptions.ConnectorException;
import org.identityconnectors.framework.common.exceptions.UnknownUidException;
import org.identityconnectors.framework.common.objects.Attribute;
import org.identityconnectors.framework.common.objects.AttributeBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfo;
import org.identityconnectors.framework.common.objects.AttributeInfoBuilder;
import org.identityconnectors.framework.common.objects.AttributeInfoUtil;
import org.identityconnectors.framework.common.objects.AttributeUtil;
import org.identityconnectors.framework.common.objects.ConnectorObject;
import org.identityconnectors.framework.common.objects.ConnectorObjectBuilder;
import org.identityconnectors.framework.common.objects.Name;
import org.identityconnectors.framework.common.objects.ObjectClass;
import org.identityconnectors.framework.common.objects.ObjectClassInfo;
import org.identityconnectors.framework.common.objects.ObjectClassInfoBuilder;
import org.identityconnectors.framework.common.objects.OperationOptions;
import org.identityconnectors.framework.common.objects.OperationalAttributeInfos;
import org.identityconnectors.framework.common.objects.OperationalAttributes;
import org.identityconnectors.framework.common.objects.PredefinedAttributeInfos;
import org.identityconnectors.framework.common.objects.PredefinedAttributes;
import org.identityconnectors.framework.common.objects.ResultsHandler;
import org.identityconnectors.framework.common.objects.Schema;
import org.identityconnectors.framework.common.objects.SchemaBuilder;
import org.identityconnectors.framework.common.objects.ScriptContext;
import org.identityconnectors.framework.common.objects.Uid;
import org.identityconnectors.framework.common.objects.filter.EqualsFilter;
import org.identityconnectors.framework.common.objects.filter.FilterTranslator;
import org.identityconnectors.framework.spi.AttributeNormalizer;
import org.identityconnectors.framework.spi.Configuration;
import org.identityconnectors.framework.spi.ConnectorClass;
import org.identityconnectors.framework.spi.PoolableConnector;
import org.identityconnectors.framework.spi.operations.AuthenticateOp;
import org.identityconnectors.framework.spi.operations.CreateOp;
import org.identityconnectors.framework.spi.operations.DeleteOp;
import org.identityconnectors.framework.spi.operations.ResolveUsernameOp;
import org.identityconnectors.framework.spi.operations.SchemaOp;
import org.identityconnectors.framework.spi.operations.ScriptOnResourceOp;
import org.identityconnectors.framework.spi.operations.SearchOp;
import org.identityconnectors.framework.spi.operations.UpdateOp;
import org.identityconnectors.patternparser.MapTransform;
import org.identityconnectors.patternparser.Transform;

@ConnectorClass(displayNameKey = "VMSConnector", configurationClass = VmsConfiguration.class)
public class VmsConnector implements PoolableConnector, AuthenticateOp, CreateOp, DeleteOp,
        SearchOp<String>, UpdateOp, SchemaOp, AttributeNormalizer, ScriptOnResourceOp,
        ResolveUsernameOp {
    private static final Log LOG = Log.getLog(VmsConnector.class);
    private DateFormat vmsDateFormatWithSecs;
    private DateFormat vmsDateFormatWithoutSecs;
    private VmsConfiguration configuration;
    private VmsConnection connection;
    private Schema schema;
    private Map<String, AttributeInfo> attributeInfoMap;
    private Set<String> returnedByDefault;

    private String changeOwnPasswordCommandScript;
    private ScriptExecutor changeOwnPasswordCommandExecutor;
    private String multipleAuthorizeCommandScript;
    private ScriptExecutor multipleAuthorizeCommandExecutor;
    private String dateCommandScript;
    private ScriptExecutor dateCommandExecutor;

    private final static Pattern ERROR_PATTERN = Pattern.compile("%\\w+-\\w-\\w+,[^\\n]*\\n");

    private final static String VMS_DELTA_FORMAT =
            "{0,number,##}-{1,number,##}:{2,number,##}:{3,number,##}.{4,number,00}";

    private static final String SEPARATOR = "Username: ";
    private static final String UAF_PROMPT = "UAF>";
    private static final String UAF_PROMPT_CONTINUE = "_UAF>";
    private static final Transform TRANSFORM = new MapTransform(VmsAuthorizeInfo.getInfo());

    public static final int LONG_WAIT = 60000;
    public static final int SHORT_WAIT = 60000;
    private static final int SEGMENT_MAX = 250;

    /* @formatter:off */
    // VMS messages we search for
    //
    private static final String CLI_WARNING      = "%CLI-W-";            // various CLI errors
    private static final String USER_ADDED       = "%UAF-I-ADDMSG,";     // user record successfully added
    private static final String USER_EXISTS      = "%UAF-E-UAEERR,";     // invalid user name, user name already exists
    private static final String USER_REMOVED     = "%UAF-I-REMMSG,";     // record removed from system authorization file
    private static final String USER_RENAMED     = "%UAF-I-RENMSG,";     // user record renamed
    private static final String NO_MODS          = "%UAF-I-NOMODS,";     // no modifications made
    private static final String USER_UPDATED     = "%UAF-I-MDFYMSG,";    // user record(s) updated
    private static final String BAD_USER         = "%UAF-W-BADUSR,";     // user name does not exist
    private static final String BAD_SPEC         = "%UAF-W-BADSPC,";     // no user matches specification
    private static final String UAF_ERROR        = "%UAF-E-";            // any UAF error message
    private static final String DUP_IDENT        = "-SYSTEM-F-DUPIDENT"; // duplicate identifier
    private static final String DUPLNAM          = "-SYSTEM-F-DUPLNAM";  // duplicate name
    /* @formatter:on */

    public VmsConnector() {
        try {
            changeOwnPasswordCommandScript =
                    VmsUtilities
                            .readFileFromClassPath("org/identityconnectors/vms/UserPasswordScript.txt");
            multipleAuthorizeCommandScript =
                    VmsUtilities
                            .readFileFromClassPath("org/identityconnectors/vms/MultipleAuthorizeCommandScript.txt");
            dateCommandScript =
                    VmsUtilities
                            .readFileFromClassPath("org/identityconnectors/vms/DateCommandScript.txt");
            if (StringUtil.isEmpty(changeOwnPasswordCommandScript)) {
                throw new ConnectorException("Internal error locating command scripts");
            }
        } catch (IOException ioe) {
            throw ConnectorException.wrap(ioe);
        }
    }

    /**
     * Since the exclamation point is a comment delimiter in DCL (and AUTHORIZE
     * input), we must quote any strings in which it appears. We also need to
     * quote if any whitespace is contained
     *
     * @param unquoted
     * @return
     */

    protected String quoteForAuthorizeWhenNeeded(String unquoted) {
        boolean quote = !Pattern.matches("(\\w)+", unquoted);
        if (unquoted.length() == 0) {
            quote = true;
        }

        if (!quote) {
            return unquoted;
        }

        return quoteStringForAuthorize(unquoted);
    }

    protected String quoteForDCLWhenNeeded(String unquoted, boolean needsQuote) {
        boolean quote = needsQuote || !Pattern.matches("(\\w)+", unquoted);
        if (unquoted.length() == 0) {
            quote = true;
        }

        if (!quote) {
            return unquoted;
        }

        return quoteStringForDCL(unquoted);
    }

    /**
     * VMS quoting rules:
     * <ul>
     * <li>replace a single quote with the sequence "+"'"+" <br>
     * - end the string, append a single quote, append the remainder
     * <li>replace a double quote with the sequence ""
     * </ul>
     * Then add double quotes around the entire string
     *
     * @param string
     * @return
     */
    private String quoteStringForDCL(String string) {
        return "\"" + string.replaceAll("\"", "\"\"").replaceAll("'", "\"+\"'\"+\"") + "\"";
    }

    /**
     * Authorize allows quoted strings with an embedded '"' indicated by '""'.
     *
     * @param string
     * @return
     */
    private String quoteStringForAuthorize(String string) {
        return "\"" + string.replaceAll("\"", "\"\"") + "\"";
    }

    protected String asString(Object object) {
        if (object instanceof GuardedString) {
            GuardedString guarded = (GuardedString) object;
            GuardedStringAccessor accessor = new GuardedStringAccessor();
            guarded.access(accessor);
            char[] result = accessor.getArray();
            return new String(result);
        } else if (object != null) {
            return object.toString();
        } else {
            return null;
        }
    }

    protected String listToVmsValueList(String name, List<Object> values) {
        if (values.size() == 1) {
            if (values.get(0) == null) {
                return null;
            } else {
                String result = asString(values.get(0));
                if (mayNeedQuotes(name)) {
                    result = quoteForAuthorizeWhenNeeded(result);
                }
                return result;
            }
        } else {
            StringBuffer buffer = new StringBuffer();
            char separator = '(';
            for (Object value : values) {
                buffer.append(separator);
                String result = asString(value);
                if (mayNeedQuotes(name)) {
                    result = quoteForAuthorizeWhenNeeded(result);
                }
                buffer.append(result);
                separator = ',';
            }
            buffer.append(")");
            return buffer.toString();
        }
    }

    private boolean mayNeedQuotes(String name) {
        return (name.equalsIgnoreCase(OperationalAttributes.PASSWORD_NAME)
                || name.equalsIgnoreCase(ATTR_OWNER) || name.equalsIgnoreCase(ATTR_ACCOUNT));
    }

    protected boolean isPresent(String base, String subString) {
        if (base == null) {
            return false;
        } else {
            return base.contains(subString);
        }
    }

    /**
     * Split the privileges into two command segments, so that they fit even in
     * old VMS.
     *
     * @param prefix
     * @param value
     * @return
     */
    private List<StringBuffer> privsCommand(String prefix, List<Object> value) {
        List<StringBuffer> commandList = new LinkedList<StringBuffer>();
        StringBuffer command = new StringBuffer();
        commandList.add(command);

        StringBuffer buffer = new StringBuffer();
        buffer.append("MODIFY " + prefix + "=(");
        String separator = "";
        int i;
        for (i = 0; i < value.size(); i++) {
            String string = value.get(i).toString();
            if (buffer.length() + (string.length() + 2) > 250) {
                break;
            }
            buffer.append(separator + string);
            separator = ",";
        }
        buffer.append("-");
        command.append(buffer.toString());

        command = new StringBuffer();
        commandList.add(command);

        buffer = new StringBuffer();
        for (; i < value.size(); i++) {
            String string = value.get(i).toString();
            if (buffer.length() + (string.length() + 2) > 250) {
                break;
            }
            buffer.append(separator + string);
        }
        buffer.append(")");
        command.append(buffer.toString());

        return commandList;
    }

    private List<StringBuffer> appendAttributes(boolean modify, String prefix,
            Map<String, Attribute> attrMap) {
        List<StringBuffer> commandList = new LinkedList<StringBuffer>();
        StringBuffer command = new StringBuffer();
        if (modify) {
            command.append("MODIFY ");
        } else {
            command.append("ADD ");
        }
        command.append(prefix);
        commandList.add(command);

        // Administrative user doesn't need current password
        //
        Attribute currentPassword = attrMap.remove(OperationalAttributes.CURRENT_PASSWORD_NAME);

        // Password expiration date is handled by the /PWDEXPIRED qualifier
        //
        Attribute expiration = attrMap.remove(OperationalAttributes.PASSWORD_EXPIRED_NAME);
        if (expiration != null) {
            VmsAttributeValidator.validate(OperationalAttributes.PASSWORD_EXPIRED_NAME, expiration
                    .getValue(), configuration);
            if (AttributeUtil.getBooleanValue(expiration)) {
                String value = "/" + ATTR_PWDEXPIRED;
                command = appendToCommand(commandList, command, value);
            } else {
                String value = "/NO" + ATTR_PWDEXPIRED;
                command = appendToCommand(commandList, command, value);
            }
        } else {
            Attribute password = attrMap.get(OperationalAttributes.PASSWORD_NAME);
            // If the password is being changed, VMS automatically pre-expires
            // the password.
            // If it wasn't already expired, we don't want to change that.
            //
            if (password != null && modify) {
                // Get the user and find if currently pre-expired
                //
                List<StringBuffer> addCommand = new LinkedList<StringBuffer>();
                StringBuffer buffer = new StringBuffer();
                buffer.append("SHOW " + prefix);
                addCommand.add(buffer);

                Map<String, Object> variables = getVariablesForCommand();
                fillInCommand(addCommand, variables);

                String result = "";
                try {
                    result = (String) multipleAuthorizeCommandExecutor.execute(variables);
                    if (!isPresent(result, "(pre-expired)")) {
                        String value = "/" + "NO" + ATTR_PWDEXPIRED;
                        command = appendToCommand(commandList, command, value);
                    }
                } catch (Exception e) {
                    LOG.error(e, "error in create");
                    throw new ConnectorException(configuration
                            .getMessage(modify ? VmsMessages.ERROR_IN_MODIFY
                                    : VmsMessages.ERROR_IN_CREATE), e);
                }
                if (isPresent(result, CLI_WARNING)) {
                    throw new ConnectorException(configuration.getMessage(
                            modify ? VmsMessages.ERROR_IN_MODIFY2 : VmsMessages.ERROR_IN_CREATE2,
                            result));
                }
            }
        }

        // Password change interval is handled by the /PWDLIFETIME qualifier
        //
        Attribute changeInterval =
                attrMap.remove(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME);
        if (changeInterval != null) {
            VmsAttributeValidator.validate(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME,
                    changeInterval.getValue(), configuration);
            long expirationTime = 0;
            if (!CollectionUtil.isEmpty(changeInterval.getValue())) {
                expirationTime = AttributeUtil.getLongValue(changeInterval).longValue();
            }
            if (expirationTime == 0) {
                String value = "/NOPWDLIFETIME";
                command = appendToCommand(commandList, command, value);
            } else {
                String deltaValue = remapToDelta(expirationTime);
                String value = "/PWDLIFETIME=" + quoteForAuthorizeWhenNeeded(deltaValue);
                command = appendToCommand(commandList, command, value);
            }
        }

        // Disable Date is handled by the /EXPIRED qualifier
        //
        Attribute disableDate = attrMap.remove(OperationalAttributes.DISABLE_DATE_NAME);
        if (disableDate != null) {
            VmsAttributeValidator.validate(OperationalAttributes.DISABLE_DATE_NAME, disableDate
                    .getValue(), configuration);
            long disableTime = 0;
            if (!CollectionUtil.isEmpty(disableDate.getValue())) {
                disableTime = AttributeUtil.getLongValue(disableDate).longValue();
            }
            if (disableTime == 0) {
                String value = "/NOEXPIRED";
                command = appendToCommand(commandList, command, value);
            } else {
                String deltaValue = vmsDateFormatWithoutSecs.format(new Date(disableTime));
                String value = "/EXPIRED=" + quoteForAuthorizeWhenNeeded(deltaValue);
                command = appendToCommand(commandList, command, value);
            }
        }

        // Various Access modifiers (e.g., BATCH=(PRIMARY, 12-7)) are handled by
        // NETWORK
        // BATCH
        // LOCAL
        // DIALUP
        // REMOTE
        //
        for (String accessorName : ACCESSORS) {
            Attribute accessor = attrMap.remove(accessorName.toUpperCase());
            if (accessor != null) {
                VmsAttributeValidator.validate(accessorName, accessor.getValue(), configuration);
                command =
                        appendToCommand(commandList, command,
                                appendAccessor(accessorName, accessor));
            }
        }

        for (Attribute attribute : attrMap.values()) {
            String name = attribute.getName();
            List<Object> values = new LinkedList<Object>();
            if (attribute.getValue() != null) {
                values.addAll(attribute.getValue());
            }
            // Need to update values for list-valued attributes to specify
            // negated values, as well as positive values
            if (ATTR_FLAGS.equalsIgnoreCase(name)) {
                updateValues(values, VmsAttributeValidator.FLAGS_LIST);
            } else if (ATTR_PRIVILEGES.equalsIgnoreCase(name)) {
                updateValues(values, VmsAttributeValidator.PRIVS_LIST);
            } else if (ATTR_DEFPRIVILEGES.equalsIgnoreCase(name)) {
                updateValues(values, VmsAttributeValidator.PRIVS_LIST);
            } else if (ATTR_PRIMEDAYS.equalsIgnoreCase(name)) {
                updateValues(values, VmsAttributeValidator.PRIMEDAYS_LIST);
            }
            // We treat empty lists as value needed to clear the attribute
            // Dates are removed with NO prefix
            // Deltas are removed with NO prefix
            // Numbers get set to 0
            // Strings get set to empty string
            //
            if (values.size() == 0) {
                schema();
                AttributeInfo info = attributeInfoMap.get(name);
                if (info != null) {
                    if (OperationalAttributes.DISABLE_DATE_NAME.equals(name)
                            || ATTR_CPUTIME.equals(name)
                            || PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME.equals(name)) {
                        values.add(Boolean.FALSE);
                    } else if (info.getClass().isInstance(Number.class)
                            || info.getClass().isInstance(short.class)
                            || info.getClass().isInstance(int.class)
                            || info.getClass().isInstance(float.class)
                            || info.getClass().isInstance(double.class)
                            || info.getClass().isInstance(long.class)) {
                        values.add(0);
                    } else if (info.getClass().isInstance(Boolean.class)) {
                        values.add(Boolean.FALSE);
                    } else {
                        values.add("");
                    }
                } else {
                    values.add("");
                }
            }
            if (!name.equals(Name.NAME) && isNeedsValidation(attribute)) {
                VmsAttributeValidator.validate(name, values, configuration);
                if (values.size() == 1 && values.get(0) instanceof Boolean) {
                    // We use boolean value to indicate negatable, valueless
                    // attributes, and to clear time-based attributes
                    //
                    String value = null;
                    if (((Boolean) values.get(0)).booleanValue()) {
                        value = "/" + remapName(attribute);
                    } else {
                        value = "/NO" + remapName(attribute);
                    }
                    command = appendToCommand(commandList, command, value);
                } else {
                    if (isDateTimeAttribute(name)) {
                        remapToDateTime(values);
                    }
                    if (isDeltaAttribute(name)) {
                        remapToDelta(values);
                    }
                    String value = listToVmsValueList(attribute.getName(), values);
                    String first = "/" + remapName(attribute) + "=";
                    if (command.length() + first.length() + value.length() > SEGMENT_MAX) {
                        command = addNewCommandSegment(commandList, command);
                    }
                    command.append(first);
                    command.append(value);
                }
            }
        }
        return commandList;
    }

    /**
     * When an accessor is specified, both the PRIMARY and SECONDARY values are
     * either present or defaulted. The default is no values, thus
     * ACCESS=(PRIMARY, 12) turns on PRIMARY access at 12, and turns OFF
     * SECONDARY access for all 24 hours NOACCESS=(PRIMARY, 12) turns off
     * PRIMARY access at 12, and turns ON SECONDARY access for all 24 hours
     *
     * @param accessorName
     * @param accessor
     * @return
     */
    private String appendAccessor(String accessorName, Attribute accessor) {
        if (accessor == null) {
            return "";
        }
        List<Object> accessorValues = accessor.getValue();

        // Separate out primary and secondary hours
        //
        String primary = (String) accessorValues.get(0);
        String secondary = (String) accessorValues.get(1);

        // If both noPrimary and noSecondary, we need to use the negative
        // form, otherwise, the positive form
        //
        if (StringUtil.isBlank(primary)) {
            if (StringUtil.isBlank(secondary)) {
                return "/NO" + accessorName.toUpperCase();
            } else {
                return "/" + accessorName.toUpperCase() + "=(SECONDARY," + secondary + ")";
            }
        } else if (StringUtil.isBlank(secondary)) {
            return "/" + accessorName.toUpperCase() + "=(PRIMARY," + primary;
        } else {
            return "/" + accessorName.toUpperCase() + "=(PRIMARY," + primary + ",SECONDARY,"
                    + secondary + ")";
        }
    }

    private StringBuffer appendToCommand(List<StringBuffer> commandList, StringBuffer command,
            String value) {
        if (command.length() + value.length() > SEGMENT_MAX) {
            command = addNewCommandSegment(commandList, command);
        }
        command.append(value);
        return command;
    }

    /**
     * The values list is updated to hold negated values for every possibility
     * that is not in the list.
     *
     * @param values
     * @param possibilities
     */
    private void updateValues(List<Object> values, Collection<String> possibilities) {

        // Do case-insensitive value comparison
        //
        Collection<String> newValues = CollectionUtil.newCaseInsensitiveSet();
        for (Object value : values) {
            newValues.add(value.toString());
        }
        for (String possibility : possibilities) {
            if (!newValues.contains(possibility)) {
                values.add("NO" + possibility);
            }
        }
    }

    private StringBuffer addNewCommandSegment(List<StringBuffer> commandList, StringBuffer command) {
        command.append("-");
        command = new StringBuffer();
        commandList.add(command);
        return command;
    }

    private boolean isDateTimeAttribute(String attributeName) {
        return OperationalAttributes.DISABLE_DATE_NAME.equals(attributeName);
    }

    private boolean isDeltaAttribute(String attributeName) {
        if (PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME.equals(attributeName)) {
            return true;
        }
        if (ATTR_CPUTIME.equals(attributeName)) {
            return true;
        }
        return false;
    }

    private void remapToDateTime(List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            values.set(i, vmsDateFormatWithoutSecs.format(new Date((Long) value)));
        }
    }

    static void remapToDelta(List<Object> values) {
        for (int i = 0; i < values.size(); i++) {
            Object value = values.get(i);
            value = remapToDelta((Long) value);
            values.set(i, value);
        }
    }

    private static final Pattern DELTA_PATTERN = Pattern
            .compile("(?:(\\d+)\\s)?(\\d+):(\\d+)(?::(\\d+))?(?:.(\\d+))?");

    private long remapFromDelta(String delta) {
        if (delta == null) {
            return 0;
        }
        Matcher matcher = DELTA_PATTERN.matcher(delta);
        if (matcher.matches()) {
            String daysS = matcher.group(1);
            String hoursS = matcher.group(2);
            String minutesS = matcher.group(3);
            String secondsS = matcher.group(4);
            String centisecondsS = matcher.group(5);

            long days = daysS == null ? 0 : Long.parseLong(daysS);
            long hours = Long.parseLong(hoursS);
            long minutes = Long.parseLong(minutesS);
            long seconds = secondsS == null ? 0 : Long.parseLong(secondsS);
            long centiseconds = centisecondsS == null ? 0 : Long.parseLong(centisecondsS);
            long result =
                    ((((((((days * 24) + hours) * 60) + minutes) * 60) + seconds) * 100) + centiseconds) * 10;
            return result;
        }
        return 0;
    }

    private static String remapToDelta(Long longValue) {
        // Convert to hundredths of a second
        longValue /= 10;
        long centiseconds = longValue % 100;
        // Convert to seconds
        longValue /= 100;
        long seconds = longValue % 60;
        // Convert to minutes
        longValue /= 60;
        long minutes = longValue % 60;
        // Convert to hours
        longValue /= 60;
        long hours = longValue % 24;
        // Convert to days
        longValue /= 24;
        long days = longValue;
        String value =
                MessageFormat.format(VMS_DELTA_FORMAT, new Object[] { days, hours, minutes,
                    seconds, centiseconds });
        return value;
    }

    private String remapName(Attribute attribute) {
        if (attribute.is(OperationalAttributes.PASSWORD_NAME)) {
            return "PASSWORD";
        }
        if (attribute.is(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME)) {
            return "PWDLIFETIME";
        } else {
            return attribute.getName();
        }
    }

    private boolean isNeedsValidation(Attribute attribute) {
        if (attribute.is(OperationalAttributes.CURRENT_PASSWORD_NAME)) {
            return false;
        }
        if (attribute instanceof Uid) {
            return false;
        }
        return true;
    }

    /**
     * {@inheritDoc}
     */
    public Uid create(ObjectClass objectClass, final Set<Attribute> originalAttrs,
            final OperationOptions options) {
        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_OBJECT_CLASS, objectClass.getObjectClassValue()));
        }

        Map<String, Attribute> attrMap =
                new HashMap<String, Attribute>(AttributeUtil.toMap(originalAttrs));

        // Extract action attributes
        //
        Attribute grantIds = attrMap.remove(ATTR_GRANT_IDS);
        Attribute createDirectory = attrMap.remove(ATTR_CREATE_DIRECTORY);
        Attribute copyLoginScript = attrMap.remove(ATTR_COPY_LOGIN_SCRIPT);
        Attribute loginScriptSource = attrMap.remove(ATTR_LOGIN_SCRIPT_SOURCE);

        Name name = (Name) attrMap.get(Name.NAME);

        // If UIC contains wildcard, compute an appropriate value
        //
        Attribute uic = attrMap.get(ATTR_UIC);
        if (uic == null || StringUtil.isBlank(AttributeUtil.getStringValue(uic))) {
            throw new ConnectorException(configuration.getMessage(VmsMessages.NULL_ATTRIBUTE_VALUE,
                    ATTR_UIC));
        }
        VmsAttributeValidator.validate(ATTR_UIC, uic.getValue(), configuration);
        String unusedUic = null;
        boolean uniqueUicRequired = false;
        String uicValue = AttributeUtil.getStringValue(uic);
        if (uicValue.contains("*")) {
            uniqueUicRequired = true;
            unusedUic = getUnusedUicForGroup(uicValue);
            attrMap.put(ATTR_UIC, AttributeBuilder.build(ATTR_UIC, unusedUic));
        }

        String accountId = name.getNameValue();
        LOG.info("create(''{0}'')", accountId);

        // Enable/disable is handled by FLAGS=([NO]DISUSER)
        // (since VMS defaults to disabled, we enable if not explicitly
        // specified)
        //
        Attribute enable = attrMap.get(OperationalAttributes.ENABLE_NAME);
        if (enable == null) {
            attrMap.put(OperationalAttributes.ENABLE_NAME, AttributeBuilder.build(
                    OperationalAttributes.ENABLE_NAME, Boolean.TRUE));
        }
        updateEnableAttribute(attrMap);

        Attribute privileges =
                configuration.getLongCommands() ? null : attrMap.remove(ATTR_PRIVILEGES);
        Attribute defPrivileges =
                configuration.getLongCommands() ? null : attrMap.remove(ATTR_DEFPRIVILEGES);
        Attribute flags = configuration.getLongCommands() ? null : attrMap.remove(ATTR_FLAGS);

        List<StringBuffer> addCommand = appendAttributes(false, accountId, attrMap);

        String result = "";
        List<List<StringBuffer>> commandList = new LinkedList<List<StringBuffer>>();
        try {
            commandList.add(addCommand);
            Map<String, Object> variables = getVariablesForCommand();
            fillInMultipleCommand(commandList, variables);
            result = (String) multipleAuthorizeCommandExecutor.execute(variables);
        } catch (Exception e) {
            LOG.error(e, "error in create");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_CREATE), e);
        }
        if (isPresent(result, CLI_WARNING)) {
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_CREATE2,
                    result));
        }

        if (isPresent(result, USER_ADDED)) {
            LOG.info("user created");
        } else if (isPresent(result, USER_EXISTS)) {
            throw new AlreadyExistsException();
        } else {
            // If we can locate an error message, we use it
            //
            Matcher matcher = ERROR_PATTERN.matcher(result);
            if (matcher.find()) {
                result = matcher.group();
            }
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_CREATE2,
                    result));
        }

        // We do the modifies separately, just in case the user already existed,
        // and the create failed
        //
        result = "";
        commandList = new LinkedList<List<StringBuffer>>();
        try {
            if (privileges != null) {
                commandList.add(updatePrivileges(accountId, privileges));
            }

            if (defPrivileges != null) {
                commandList.add(updateDefPrivileges(accountId, defPrivileges));
            }

            if (flags != null) {
                commandList.add(updateFlags(accountId, flags));
            }

            if (grantIds != null) {
                for (Object grantId : grantIds.getValue()) {
                    addGrantToCommandList(name.getNameValue(), commandList, grantId);
                }
            }

            if (commandList.size() > 0) {
                Map<String, Object> variables = getVariablesForCommand();
                fillInMultipleCommand(commandList, variables);
                result = (String) multipleAuthorizeCommandExecutor.execute(variables);
            }
        } catch (Exception e) {
            LOG.error(e, "error in create");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_CREATE), e);
        }

        // It's possible another connector (or someone else) got in and took the
        // UIC.
        // If so, we retry with the next UIC
        //
        if (uniqueUicRequired && (isPresent(result, DUP_IDENT) || isPresent(result, DUPLNAM))) {
            do {
                unusedUic = getNextUicForGroup(unusedUic);
                tryAnotherUic(unusedUic, accountId);
                attrMap.put(ATTR_UIC, AttributeBuilder.build(ATTR_UIC, unusedUic));
            } while (!isUnique(unusedUic));
        }

        // Process action attributes
        //
        String createDirCommand = getCreateDirCommand(attrMap, createDirectory);
        String copyLoginCommand = getCopyLoginCommand(attrMap, copyLoginScript, loginScriptSource);

        if (createDirCommand != null) {
            result = executeCommand(connection, createDirCommand);
            Matcher matcher = ERROR_PATTERN.matcher(result);
            if (matcher.find()) {
                result = matcher.group();
                throw new ConnectorException(configuration.getMessage(
                        VmsMessages.ERROR_IN_CREATE_DIRECTORY, result));
            }
        }
        if (copyLoginCommand != null) {
            result = executeCommand(connection, copyLoginCommand);
            Matcher matcher = ERROR_PATTERN.matcher(result);
            if (matcher.find()) {
                result = matcher.group();
                throw new ConnectorException(configuration.getMessage(
                        VmsMessages.ERROR_IN_COPY_LOGIN, result));
            }
        }

        return new Uid(accountId);
    }

    private void addGrantToCommandList(String name, List<List<StringBuffer>> commandList,
            Object grantId) {
        StringBuffer buffer = new StringBuffer();
        List<StringBuffer> grantCommand = new LinkedList<StringBuffer>();
        grantCommand.add(buffer);
        buffer.append("GRANT/IDENTIFIER " + grantId + " " + name);
        commandList.add(grantCommand);
    }

    private void addRevokeToCommandList(String name, List<List<StringBuffer>> commandList,
            Object grantId) {
        StringBuffer buffer = new StringBuffer();
        List<StringBuffer> grantCommand = new LinkedList<StringBuffer>();
        grantCommand.add(buffer);
        buffer.append("REVOKE/IDENTIFIER " + grantId + " " + name);
        commandList.add(grantCommand);
    }

    private List<StringBuffer> updateListAttr(String accountId, Attribute attribute, String name,
            Collection<String> valueSet) {
        List<Object> values = null;
        if (attribute.getValue() == null) {
            values = new ArrayList<Object>();
        } else {
            values = new ArrayList<Object>(attribute.getValue());
        }
        updateValues(values, valueSet);

        return privsCommand(accountId + "/" + name, values);
    }

    private List<StringBuffer> updatePrivileges(String accountId, Attribute privileges) {
        return updateListAttr(accountId, privileges, ATTR_PRIVILEGES,
                VmsAttributeValidator.PRIVS_LIST);
    }

    private List<StringBuffer> updateDefPrivileges(String accountId, Attribute defPrivileges) {
        return updateListAttr(accountId, defPrivileges, ATTR_DEFPRIVILEGES,
                VmsAttributeValidator.PRIVS_LIST);
    }

    private List<StringBuffer> updateFlags(String accountId, Attribute flags) {
        return updateListAttr(accountId, flags, ATTR_FLAGS, VmsAttributeValidator.FLAGS_LIST);
    }

    private void updateEnableAttribute(Map<String, Attribute> attrMap) {
        Attribute enable = attrMap.remove(OperationalAttributes.ENABLE_NAME);
        if (enable != null) {
            Boolean isEnable = AttributeUtil.getBooleanValue(enable);
            Attribute flags = attrMap.remove(ATTR_FLAGS);
            List<Object> flagsValue = new LinkedList<Object>();
            if (flags != null) {
                flagsValue = new LinkedList<Object>(flags.getValue());
            }
            if (isEnable) {
                if (containsInsensitive(flagsValue, FLAG_DISUSER)) {
                    throw new IllegalArgumentException(configuration
                            .getMessage(VmsMessages.DISUSER_ERROR_1));
                } else if (!containsInsensitive(flagsValue, "NO" + FLAG_DISUSER)) {
                    flagsValue.add("NO" + FLAG_DISUSER);
                }
                Attribute disableDate = attrMap.get(OperationalAttributes.DISABLE_DATE_NAME);
                if (disableDate != null) {
                    // throw new
                    // IllegalArgumentException(configuration.getMessage(VmsMessages.DISABLE_AND_ENABLE));
                } else {
                    attrMap.put(OperationalAttributes.DISABLE_DATE_NAME, AttributeBuilder.build(
                            OperationalAttributes.DISABLE_DATE_NAME, Long.valueOf(0)));
                }
            } else {
                if (containsInsensitive(flagsValue, "NO" + FLAG_DISUSER)) {
                    throw new IllegalArgumentException(configuration
                            .getMessage(VmsMessages.DISUSER_ERROR_1));
                } else if (!containsInsensitive(flagsValue, FLAG_DISUSER)) {
                    flagsValue.add(FLAG_DISUSER);
                }
            }
            attrMap.put(ATTR_FLAGS, AttributeBuilder.build(ATTR_FLAGS, flagsValue));
        }
    }

    private boolean containsInsensitive(List<Object> list, String string) {
        for (Object object : list) {
            if (string.equalsIgnoreCase(object.toString())) {
                return true;
            }
        }
        return false;
    }

    private void tryAnotherUic(String unusedUic, String accountId) {
        Map<String, Attribute> uicAttrMap = new HashMap<String, Attribute>();
        uicAttrMap.put(ATTR_UIC, AttributeBuilder.build(ATTR_UIC, unusedUic));
        List<StringBuffer> uicCommand = appendAttributes(true, accountId, uicAttrMap);

        Map<String, Object> uicVariables = getVariablesForCommand();
        fillInCommand(uicCommand, uicVariables);
        String result = "";
        try {
            result = (String) multipleAuthorizeCommandExecutor.execute(uicVariables);
        } catch (Exception e) {
            LOG.error(e, "error in tryAnotherUic");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY), e);
        }
        if (isPresent(result, CLI_WARNING)) {
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY2,
                    result));
        }
    }

    /**
     * {@inheritDoc}
     */
    public void delete(ObjectClass objectClass, Uid uid, OperationOptions options) {
        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_OBJECT_CLASS, objectClass.getObjectClassValue()));
        }

        LOG.info("delete(''{0}'')", uid.getUidValue());

        Map<String, Object> variables = getVariablesForCommand();
        fillInCommand("REMOVE " + uid.getUidValue(), variables);

        String result = "";
        try {
            result = (String) multipleAuthorizeCommandExecutor.execute(variables);
        } catch (Exception e) {
            LOG.error(e, "error in delete");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_DELETE), e);
        }
        if (isPresent(result, CLI_WARNING)) {
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_DELETE2,
                    result));
        }

        if (isPresent(result, USER_REMOVED)) {
            LOG.info("user deleted");
            return;
        } else if (isPresent(result, BAD_USER)) {
            throw new UnknownUidException();
        } else {
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_DELETE2,
                    result));
        }
    }

    VmsConnection getConnection() {
        return connection;
    }

    /**
     * {@inheritDoc}
     */
    public FilterTranslator<String> createFilterTranslator(ObjectClass oclass,
            OperationOptions options) {
        return new VmsFilterTranslator();
    }

    /**
     * {@inheritDoc}
     */
    public void executeQuery(ObjectClass objectClass, String query, ResultsHandler handler,
            OperationOptions options) {
        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_OBJECT_CLASS, objectClass.getObjectClassValue()));
        }
        Set<String> attributesToGet = null;
        if (options != null && options.getAttributesToGet() != null) {
            attributesToGet = CollectionUtil.newReadOnlySet(options.getAttributesToGet());
        }
        schema();
        if (attributesToGet != null) {
            for (String name : attributesToGet) {
                if (!attributeInfoMap.containsKey(name)) {
                    throw new IllegalArgumentException(configuration.getMessage(
                            VmsMessages.NO_SUCH_ATTRIBUTE, name));
                }
            }
        } else {
            attributesToGet = returnedByDefault;
        }
        try {
            if (query == null) {
                query = "*";
            }
            LOG.info("executeQuery(''{0}'')", query);
            List<String> filterStrings = new ArrayList<String>();
            filterStrings.add(query);
            filterUsers(handler, filterStrings, attributesToGet);
        } catch (Exception e) {
            LOG.error(e, "error in executeQuery");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_SEARCH), e);
        }
    }

    private void filterUsers(ResultsHandler handler, List<String> filters,
            Set<String> searchAttributesToGet) throws Exception {
        LinkedList<List<StringBuffer>> commandList = new LinkedList<List<StringBuffer>>();
        for (String filter : filters) {
            List<StringBuffer> commands = new LinkedList<StringBuffer>();
            StringBuffer command = new StringBuffer();
            command.append("SHOW " + filter);
            commands.add(command);
            commandList.add(commands);
        }
        Map<String, Object> variables = getVariablesForCommand();
        fillInMultipleCommand(commandList, variables);

        String users = (String) multipleAuthorizeCommandExecutor.execute(variables);

        String[] userArray = users.split(SEPARATOR);
        List<String> attributesToGet = null;
        if (searchAttributesToGet != null) {
            attributesToGet = CollectionUtil.newList(searchAttributesToGet);
        }
        for (int i = 1; i < userArray.length; i++) {
            String user = SEPARATOR + userArray[i].replaceAll("\r\n", "\n");
            // Now, we truncate from the UAF_PROMPT, if present
            //
            int index = user.indexOf(UAF_PROMPT);
            if (index > -1) {
                user = user.substring(0, index);
            }
            user += "\n";
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> attributes = (Map<String, Object>) TRANSFORM.transform(user);
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.setUid((String) attributes.get(Name.NAME));

                String lastLogin =
                        (String) attributes.remove(PredefinedAttributes.LAST_LOGIN_DATE_NAME);
                String cputime = (String) attributes.remove(ATTR_CPUTIME);
                String lifetime =
                        (String) attributes
                                .remove(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME);

                // ENABLE is handled by checking to see if the DisUser Flag is
                // present
                //
                @SuppressWarnings("unchecked")
                List<String> flags = (List<String>) attributes.get(ATTR_FLAGS);
                if (flags.size() > 0) {
                    if (includeInAttributes(OperationalAttributes.ENABLE_NAME, attributesToGet)) {
                        if (flags.contains(FLAG_DISUSER)) {
                            flags.remove(FLAG_DISUSER);
                            builder.addAttribute(OperationalAttributes.ENABLE_NAME, Boolean.FALSE);
                        } else {
                            builder.addAttribute(OperationalAttributes.ENABLE_NAME, Boolean.TRUE);
                        }
                    }
                } else {
                    if (includeInAttributes(OperationalAttributes.ENABLE_NAME, attributesToGet)) {
                        builder.addAttribute(OperationalAttributes.ENABLE_NAME, Boolean.TRUE);
                    }
                }

                // DISABLE_DATE_NAME is handled by seeing the last password
                // change plus
                // the password lifetime is before the current time.
                // If the password is pre-expired, we always set this to TRUE
                //
                if (includeInAttributes(OperationalAttributes.DISABLE_DATE_NAME, attributesToGet)) {
                    String expired =
                            (String) attributes.get(OperationalAttributes.DISABLE_DATE_NAME);
                    if (expired.contains("(none)")) {
                        builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME);
                    } else {
                        Date expiredDate = vmsDateFormatWithoutSecs.parse(expired);
                        // If the DISABLE_DATE is in the past, we report it as
                        // no value
                        //
                        if (expiredDate.before(new Date())) {
                            builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME);
                        } else {
                            builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME,
                                    expiredDate.getTime());
                        }
                    }
                }

                // PASSWORD_EXPIRED is handled by seeing the last password
                // change plus
                // the password lifetime is before the current time.
                // If the password is pre-expired, we always set this to TRUE
                //
                if (includeInAttributes(OperationalAttributes.PASSWORD_EXPIRED_NAME,
                        attributesToGet)) {
                    String lastChange =
                            (String) attributes
                                    .get(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME);
                    if (lastChange.contains("(pre-expired)")) {
                        builder.addAttribute(OperationalAttributes.PASSWORD_EXPIRED_NAME,
                                Boolean.TRUE);
                    } else {
                        Date expiredDate = getPasswordExpirationDate(lifetime, attributes);
                        Date vmsDate = getVmsDate();
                        builder.addAttribute(OperationalAttributes.PASSWORD_EXPIRED_NAME,
                                expiredDate.before(vmsDate));
                    }
                }

                // ATTR_DEVICE is parsed out from ATTR_DEFAULT
                //
                if (includeInAttributes(ATTR_DEVICE, attributesToGet)) {
                    String defaultString = (String) attributes.get(ATTR_DEFAULT);
                    if (StringUtil.isNotBlank(defaultString)) {
                        String[] defaultInfo = defaultString.split(":");
                        if (defaultInfo.length > 1) {
                            builder.addAttribute(ATTR_DEVICE, defaultInfo[0]);
                        } else {
                            builder.addAttribute(ATTR_DEVICE);
                        }
                    } else {
                        builder.addAttribute(ATTR_DEVICE);
                    }
                }

                // GRANT_IDS may not be in the parse. If not, we reget it.
                //
                if (includeInAttributes(ATTR_GRANT_IDS, attributesToGet)) {
                    List<String> grantIds = (List<String>) attributes.get(ATTR_GRANT_IDS);
                    if (grantIds == null) {
                        builder.addAttribute(ATTR_GRANT_IDS);
                    }
                }

                // ATTR_DIRECTORY is parsed out from ATTR_DEFAULT
                //
                if (includeInAttributes(ATTR_DIRECTORY, attributesToGet)) {
                    String defaultString = (String) attributes.get(ATTR_DEFAULT);
                    if (StringUtil.isNotBlank(defaultString)) {
                        String[] defaultInfo = defaultString.split(":");
                        if (defaultInfo.length > 1) {
                            builder.addAttribute(ATTR_DIRECTORY, defaultInfo[1]);
                        } else {
                            builder.addAttribute(ATTR_DIRECTORY, defaultInfo[0]);
                        }
                    } else {
                        builder.addAttribute(ATTR_DIRECTORY);
                    }
                }

                // Parse out the various access restrictions:
                // NETWORK
                // BATCH
                // LOCAL
                // DIALUP
                // REMOTE
                //
                String accessRestrictions = (String) attributes.get("Access Restrictions");
                parseAccessRestrictions(accessRestrictions, attributesToGet, builder);

                // LAST_LOGIN
                //
                if (includeInAttributes(PredefinedAttributes.LAST_LOGIN_DATE_NAME, attributesToGet)) {
                    if (StringUtil.isNotBlank(lastLogin)) {
                        // Split it out into two separate dates
                        Pattern pattern =
                                Pattern.compile("(.*)\\(interactive\\),(.*)\\(non-interactive\\)");
                        Matcher matcher = pattern.matcher(lastLogin);
                        Object value = null;
                        if (matcher.matches()) {
                            String interactive = matcher.group(1).trim();
                            String noninteractive = matcher.group(2).trim();
                            if (interactive.equals("(none)")) {
                                if (noninteractive.equals("(none)")) {
                                    // no date, so keep null
                                } else {
                                    Date date =
                                            vmsDateFormatWithoutSecs.parse(noninteractive.trim());
                                    value = date.getTime();
                                }
                            } else {
                                if (noninteractive.equals("(none)")) {
                                    Date date = vmsDateFormatWithoutSecs.parse(interactive.trim());
                                    value = date.getTime();
                                } else {
                                    Date interactiveDate =
                                            vmsDateFormatWithoutSecs.parse(interactive.trim());
                                    Date noninteractiveDate =
                                            vmsDateFormatWithoutSecs.parse(noninteractive.trim());
                                    if (interactiveDate.after(noninteractiveDate)) {
                                        value = interactiveDate.getTime();
                                    } else {
                                        value = noninteractiveDate.getTime();
                                    }
                                }
                            }
                            if (value != null) {
                                List<Object> values = new LinkedList<Object>();
                                values.add(value);
                                builder.addAttribute(PredefinedAttributes.LAST_LOGIN_DATE_NAME,
                                        values);
                            } else {
                                builder.addAttribute(PredefinedAttributes.LAST_LOGIN_DATE_NAME);
                            }
                        }

                    }
                }

                // OperationalAttributes.DISABLE_DATE_NAME
                //
                if (includeInAttributes(OperationalAttributes.DISABLE_DATE_NAME, attributesToGet)) {
                    String expiration =
                            (String) attributes.remove(OperationalAttributes.DISABLE_DATE_NAME);
                    if (StringUtil.isNotBlank(expiration)) {
                        if (expiration.trim().equals("(none)")) {
                            builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME);
                        } else {
                            Date date = vmsDateFormatWithoutSecs.parse(expiration.trim());
                            List<Object> value = new LinkedList<Object>();
                            value.add(date.getTime());
                            builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME, value);
                        }
                    } else {
                        builder.addAttribute(OperationalAttributes.DISABLE_DATE_NAME);
                    }
                }

                // CPU
                //
                if (includeInAttributes(ATTR_CPUTIME, attributesToGet)) {
                    if (StringUtil.isNotBlank(cputime)) {
                        long cputimeLong = remapFromDelta(cputime);
                        builder.addAttribute(ATTR_CPUTIME, cputimeLong);
                    } else {
                        builder.addAttribute(ATTR_CPUTIME);
                    }
                }

                // PASSWORD_CHANGE_INTERVAL_NAME
                //
                if (includeInAttributes(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME,
                        attributesToGet)) {
                    if (StringUtil.isNotBlank(lifetime)) {
                        long lifetimeLong = remapFromDelta(lifetime);
                        builder.addAttribute(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME,
                                lifetimeLong);
                    } else {
                        builder.addAttribute(PredefinedAttributes.PASSWORD_CHANGE_INTERVAL_NAME);
                    }
                }

                // LAST_PASSWORD_CHANGE_DATE_NAME
                // If the password is pre-expired, we return no value
                //
                String lastChange =
                        (String) attributes
                                .remove(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME);
                if (includeInAttributes(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME,
                        attributesToGet)) {
                    if (lastChange.contains("(pre-expired)")) {
                        builder.addAttribute(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME);
                    } else {
                        Date date = vmsDateFormatWithoutSecs.parse(lastChange.trim());
                        Object value = date.getTime();
                        List<Object> values = new LinkedList<Object>();
                        values.add(value);
                        builder.addAttribute(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME,
                                values);
                    }
                }

                // PASSWORD_EXPIRATION_DATE is handled by seeing if there is an
                // expiration
                // date, and if so, converting it to milliseconds
                //
                // TODO: I believe we can't always compute this, since the case
                // where the user has
                // never logged in, but there is a password lifetime is not
                // computable.
                //
                if (includeInAttributes(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,
                        attributesToGet)) {
                    if (StringUtil.isNotBlank(lifetime)) {
                        if (lifetime.equalsIgnoreCase("(none)")) {
                            builder.addAttribute(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME);
                        } else if (lastChange.contains("(pre-expired)")) {
                            builder.addAttribute(
                                    OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME, 0L);
                        } else {
                            Date expiredDate = getPasswordExpirationDate(lifetime, attributes);
                            builder.addAttribute(
                                    OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,
                                    expiredDate.getTime());
                        }
                    } else {
                        builder.addAttribute(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME);
                    }
                }

                // All other attributes are handled normally
                //
                for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                    Object value = entry.getValue();
                    String key = entry.getKey();
                    if (includeInAttributes(key, attributesToGet)) {
                        if (value instanceof Collection) {
                            builder.addAttribute(key, (Collection<?>) value);
                        } else if (value != null && StringUtil.isNotEmpty(value.toString())) {
                            builder.addAttribute(key, value);
                        } else {
                            builder.addAttribute(key);
                        }
                    }
                }
                ConnectorObject next = builder.build();
                handler.handle(next);
            } catch (Exception e) {
                LOG.error(e, "error parsing users");
                throw new ConnectorException(e);
            }
        }
    }

    private Date getVmsDate() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("CONNECTION", connection);
        variables.put("SHELL_PROMPT", configuration.getLocalHostShellPrompt());
        variables.put("SHORT_WAIT", SHORT_WAIT);

        String result = "";
        try {
            result = (String) dateCommandExecutor.execute(variables);
            result = result.replaceAll(configuration.getLocalHostShellPrompt(), "").trim();
            Date date = vmsDateFormatWithSecs.parse(result);
            return date;
        } catch (Exception e) {
            LOG.error(e, "error in getVmsDate");
            throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_GETDATE), e);
        }

    }

    private Date getPasswordExpirationDate(String lifetime, Map<String, Object> attributes)
            throws ParseException {
        String lastChange =
                (String) attributes.get(PredefinedAttributes.LAST_PASSWORD_CHANGE_DATE_NAME);
        // If the password is pre-expired, we say it expired at time 0
        //
        if (lastChange.contains("(pre-expired)")) {
            return new Date(0);
        }

        // If there is no lifetime, there is no expiration date
        //
        if (lifetime == null) {
            return null;
        }

        // Otherwise, password expires 'lifetime' after 'lastChangeDate'
        //
        long lastChangeDate = vmsDateFormatWithoutSecs.parse(lastChange).getTime();
        long lifetimeLong = remapFromDelta(lifetime);
        Date expiredDate = new Date(lastChangeDate + lifetimeLong);
        return expiredDate;
    }

    private boolean includeInAttributes(String attribute, List<String> attributesToGet) {
        if (attribute.equalsIgnoreCase(Name.NAME)) {
            return true;
        }
        return attributesToGet.contains(attribute);
    }

    public Uid update(ObjectClass obj, Uid uid, Set<Attribute> attrs, OperationOptions options) {
        return update(obj, AttributeUtil.addUid(attrs, uid), options);
    }

    /**
     * {@inheritDoc}
     */
    Uid update(ObjectClass objectClass, Set<Attribute> attributes, OperationOptions options) {
        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_OBJECT_CLASS, objectClass.getObjectClassValue()));
        }
        Map<String, Attribute> attrMap =
                new HashMap<String, Attribute>(AttributeUtil.toMap(attributes));

        // Grants need to be handled separately
        //
        Attribute grantIds = attrMap.remove(ATTR_GRANT_IDS);

        // Create-only attributes may not be specified
        //
        Attribute createDirectory = attrMap.remove(ATTR_CREATE_DIRECTORY);
        Attribute copyLogin = attrMap.remove(ATTR_COPY_LOGIN_SCRIPT);
        Attribute loginScriptSource = attrMap.remove(ATTR_LOGIN_SCRIPT_SOURCE);

        if (isAttributeTrue(createDirectory)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UPDATE_ATTRIBUTE_VALUE, createDirectory.getName()));
        }
        if (isAttributeTrue(copyLogin)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UPDATE_ATTRIBUTE_VALUE, copyLogin.getName()));
        }

        // Operational Attributes are handled specially
        //
        Uid uid = (Uid) attrMap.remove(Uid.NAME);
        Name name = (Name) attrMap.remove(Name.NAME);
        Attribute currentPassword = attrMap.remove(OperationalAttributes.CURRENT_PASSWORD_NAME);
        Attribute newPassword = attrMap.remove(OperationalAttributes.PASSWORD_NAME);

        // If Password and CurrentPassword
        // are specified, we change password via SET PASSWORD, rather than
        // AUTHORIIZE
        //
        if (currentPassword != null && newPassword != null) {
            LOG.info("update[changePassword](''{0}'')", uid.getUidValue());
            Map<String, Object> variables = new HashMap<String, Object>();
            variables.put("SHELL_PROMPT", configuration.getLocalHostShellPrompt());
            variables.put("SHORT_WAIT", SHORT_WAIT);
            variables.put("USERNAME", uid.getUidValue());
            GuardedString currentGS = AttributeUtil.getGuardedStringValue(currentPassword);
            GuardedString newGS = AttributeUtil.getGuardedStringValue(newPassword);
            GuardedStringAccessor accessor = new GuardedStringAccessor();
            currentGS.access(accessor);
            char[] currentArray = accessor.getArray();
            newGS.access(accessor);
            char[] newArray = accessor.getArray();
            variables.put("CURRENT_PASSWORD", new String(currentArray));
            variables.put("NEW_PASSWORD", new String(newArray));
            variables.put("CONFIGURATION", configuration);

            String result = "";
            try {
                result = (String) changeOwnPasswordCommandExecutor.execute(variables);
                Arrays.fill(currentArray, 0, currentArray.length, ' ');
                Arrays.fill(newArray, 0, newArray.length, ' ');
            } catch (Exception e) {
                Arrays.fill(currentArray, 0, currentArray.length, ' ');
                Arrays.fill(newArray, 0, newArray.length, ' ');
                LOG.error(e, "error in create");
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY),
                        e);
            }

            if (result.indexOf("%SET-") > -1) {
                String errortext = result.substring(result.indexOf("%SET-")).split("\n")[0];
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY2,
                        errortext));
            }
        } else if (newPassword != null) {
            // Put back the new password, so it can be changed administratively
            //
            attrMap.put(OperationalAttributes.PASSWORD_NAME, newPassword);
        }

        // If we have any remaining attributes, process them
        //
        if (attrMap.size() > 0) {

            // If UIC contains wildcard, compute an appropriate value
            //
            boolean uniqueUicRequired = false;
            String unusedUic = null;
            Attribute uic = attrMap.get(ATTR_UIC);
            if (uic != null && StringUtil.isBlank(AttributeUtil.getStringValue(uic))) {
                String uicValue = AttributeUtil.getStringValue(uic);
                if (uicValue.contains("*")) {
                    uniqueUicRequired = true;
                    unusedUic = getUnusedUicForGroup(uicValue);
                    attrMap.put(ATTR_UIC, AttributeBuilder.build(ATTR_UIC, unusedUic));
                }
            }

            // Enable/disable is handled by FLAGS=([NO]DISUSER)
            //
            updateEnableAttribute(attrMap);

            Attribute privileges =
                    configuration.getLongCommands() ? null : attrMap.remove(ATTR_PRIVILEGES);
            Attribute defPrivileges =
                    configuration.getLongCommands() ? null : attrMap.remove(ATTR_DEFPRIVILEGES);
            Attribute flags = configuration.getLongCommands() ? null : attrMap.remove(ATTR_FLAGS);

            String accountId = uid.getUidValue();
            List<StringBuffer> modifyCommand = appendAttributes(true, accountId, attrMap);

            String result = "";
            List<List<StringBuffer>> commandList = new LinkedList<List<StringBuffer>>();
            try {
                commandList.add(modifyCommand);
                if (privileges != null) {
                    commandList.add(updatePrivileges(accountId, privileges));
                }

                if (defPrivileges != null) {
                    commandList.add(updateDefPrivileges(accountId, defPrivileges));
                }

                if (flags != null) {
                    commandList.add(updateFlags(accountId, flags));
                }
                Map<String, Object> variables = getVariablesForCommand();
                fillInMultipleCommand(commandList, variables);
                result = (String) multipleAuthorizeCommandExecutor.execute(variables);
            } catch (Exception e) {
                LOG.error(e, "error in create");
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY),
                        e);
            }
            if (isPresent(result, CLI_WARNING)) {
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY2,
                        result));
            }

            if (isPresent(result, USER_UPDATED) || isPresent(result, NO_MODS)) {
                // OK, drop through and return uid
            } else if (isPresent(result, BAD_SPEC)) {
                throw new UnknownUidException();
            } else {
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY2,
                        result));
            }

            // It's possible another connector (or someone else) got in and took
            // the UIC.
            // If so, we retry with the next UIC
            //
            if (uniqueUicRequired && (isPresent(result, DUP_IDENT) || isPresent(result, DUPLNAM))) {
                do {
                    unusedUic = getNextUicForGroup(unusedUic);
                    tryAnotherUic(unusedUic, uid.getUidValue());
                } while (!isUnique(unusedUic));
            }
        }

        // Now do grants
        //
        if (grantIds != null) {
            setGrants(grantIds, uid.getUidValue());

        }

        // If name is different from Uid, we are performing a RENAME operation.
        // Do this last, so that we don't lose the Uid change on error.
        //
        if (name != null && uid != null && !uid.getUidValue().equals(name.getNameValue())) {
            Map<String, Object> variables = getVariablesForCommand();
            fillInCommand("RENAME " + uid.getUidValue() + " " + name.getNameValue(), variables);

            String result = "";
            try {
                result = (String) multipleAuthorizeCommandExecutor.execute(variables);
            } catch (Exception e) {
                LOG.error(e, "error in rename");
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY),
                        e);
            }
            if (isPresent(result, CLI_WARNING)) {
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_CREATE2,
                        result));
            }

            if (isPresent(result, USER_RENAMED)) {
                uid = new Uid(name.getNameValue());
            } else if (isPresent(result, BAD_SPEC)) {
                throw new UnknownUidException();
            } else if (isPresent(result, USER_EXISTS)) {
                throw new AlreadyExistsException();
            } else {
                throw new ConnectorException(configuration.getMessage(VmsMessages.ERROR_IN_MODIFY2,
                        result));
            }
        }

        return uid;
    }

    private void setGrants(Attribute grantIds, String name) {
        ConnectorObject object = getUser(name);
        if (object != null) {
            Attribute currentGrantsAttr = object.getAttributeByName(ATTR_GRANT_IDS);
            List<Object> currentGrants =
                    currentGrantsAttr == null ? new LinkedList<Object>() : currentGrantsAttr
                            .getValue();
            if (currentGrants != null) {
                List<Object> grants =
                        grantIds.getValue() == null ? new LinkedList<Object>()
                                : new LinkedList<Object>(grantIds.getValue());
                List<Object> revokes = new LinkedList<Object>(currentGrants);
                revokes.removeAll(grants);
                grants.removeAll(currentGrants);

                if (grants.size() > 0 || revokes.size() > 0) {
                    LinkedList<List<StringBuffer>> commandList =
                            new LinkedList<List<StringBuffer>>();
                    for (Object grant : grants) {
                        addGrantToCommandList(name, commandList, (String) grant);
                    }
                    for (Object revoke : revokes) {
                        addRevokeToCommandList(name, commandList, (String) revoke);
                    }
                    Map<String, Object> variables = getVariablesForCommand();
                    fillInMultipleCommand(commandList, variables);
                    String result = "";
                    try {
                        result = (String) multipleAuthorizeCommandExecutor.execute(variables);
                    } catch (Exception e) {
                        LOG.error(e, "error in modify");
                        throw new ConnectorException(configuration
                                .getMessage(VmsMessages.ERROR_IN_MODIFY), e);
                    }
                    if (isPresent(result, CLI_WARNING)) {
                        throw new ConnectorException(configuration.getMessage(
                                VmsMessages.ERROR_IN_MODIFY2, result));
                    }
                    if (isPresent(result, UAF_ERROR)) {
                        throw new ConnectorException(configuration.getMessage(
                                VmsMessages.ERROR_IN_MODIFY2, result));
                    }
                }
            }
        }
    }

    private ConnectorObject getUser(String name) {
        LocalHandler handler = new LocalHandler();
        executeQuery(ObjectClass.ACCOUNT, name, handler, null);
        if (handler.iterator().hasNext()) {
            return handler.iterator().next();
        } else {
            return null;
        }
    }

    private void fillInCommand(String command, Map<String, Object> variables) {
        List<StringBuffer> list = new LinkedList<StringBuffer>();
        StringBuffer buffer = new StringBuffer();
        buffer.append(command);
        list.add(buffer);
        fillInCommand(list, variables);
    }

    private void fillInCommand(List<StringBuffer> command, Map<String, Object> variables) {
        List<List<StringBuffer>> commands = new LinkedList<List<StringBuffer>>();
        commands.add(command);
        fillInMultipleCommand(commands, variables);

    }

    private void fillInMultipleCommand(List<List<StringBuffer>> commands,
            Map<String, Object> variables) {
        List<Object> localCommandList = new LinkedList<Object>();
        variables.put("COMMANDLISTS", localCommandList);
        for (List<StringBuffer> localCommandOrig : commands) {
            List<StringBuffer> localCommand = new LinkedList<StringBuffer>(localCommandOrig);
            StringBuffer lastPart = localCommand.remove(localCommandOrig.size() - 1);
            List<StringBuffer> firstContents = new LinkedList<StringBuffer>();
            for (StringBuffer part : localCommand) {
                firstContents.add(part);
            }
            List<Object> commandValue = new LinkedList<Object>();
            commandValue.add(localCommand);
            commandValue.add(lastPart);
            localCommandList.add(commandValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    public Schema schema() {
        if (schema != null) {
            return schema;
        }

        final SchemaBuilder schemaBuilder = new SchemaBuilder(getClass());
        Set<AttributeInfo> attributes = new HashSet<AttributeInfo>();

        attributes.add(buildRequiredAttribute(Name.NAME, String.class));

        // Required Attributes
        //
        attributes.add(buildRequiredAttribute(ATTR_UIC, String.class));

        // Optional Attributes (have VMS default values)
        //
        attributes.add(AttributeInfoBuilder.build(ATTR_DEVICE, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_DIRECTORY, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_OWNER, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_ACCOUNT, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_CLI, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_CLITABLES, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_LGICMD, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_PWDMINIMUM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_MAXJOBS, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_MAXACCTJOBS, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_SHRFILLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_PBYTLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_MAXDETACH, String.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_BIOLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_JTQUOTA, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_DIOLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_WSDEFAULT, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_PRIORITY, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_WSQUOTA, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_QUEPRIO, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_WSEXTENT, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_CPUTIME, Long.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_ENQLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_PGFLQUOTA, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_TQELM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_ASTLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_BYTLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_FILLM, Integer.class));
        attributes.add(AttributeInfoBuilder.build(ATTR_PRCLM, Integer.class));

        // Multi-valued attributes
        //
        attributes.add(buildMultivaluedAttribute(ATTR_GRANT_IDS, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_FLAGS, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_PRIMEDAYS, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_PRIVILEGES, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_DEFPRIVILEGES, String.class, false));

        attributes.add(buildMultivaluedAttribute(ATTR_NETWORK, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_BATCH, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_LOCAL, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_DIALUP, String.class, false));
        attributes.add(buildMultivaluedAttribute(ATTR_REMOTE, String.class, false));

        // Write-only attributes
        //
        // attributes.add(buildWriteonlyAttribute(ATTR_ALGORITHM, String.class,
        // false, false));
        attributes.add(buildCreateonlyAttribute(ATTR_LOGIN_SCRIPT_SOURCE, String.class, false));
        attributes.add(buildCreateonlyAttribute(ATTR_COPY_LOGIN_SCRIPT, Boolean.class, false));
        attributes.add(buildCreateonlyAttribute(ATTR_CREATE_DIRECTORY, Boolean.class, false));

        // Read-only attributes
        //
        attributes.add(buildReadonlyAttribute(ATTR_LOGIN_FAILS, Integer.class, false));

        // Operational Attributes
        //
        attributes.add(OperationalAttributeInfos.PASSWORD);
        boolean disableUserLogins = isDisableUserLogins(configuration);
        if (!disableUserLogins) {
            attributes.add(OperationalAttributeInfos.CURRENT_PASSWORD);
        }
        attributes.add(OperationalAttributeInfos.ENABLE);
        attributes.add(OperationalAttributeInfos.PASSWORD_EXPIRED);
        attributes.add(OperationalAttributeInfos.DISABLE_DATE);
        // TODO: I believe we can't always compute this, since the case where
        // the user has
        // never logged in, but there is a password lifetime is not computable.
        //
        // attributes.add(buildReadonlyAttribute(OperationalAttributes.PASSWORD_EXPIRATION_DATE_NAME,
        // Long.class, false));

        // Predefined Attributes
        //
        attributes.add(PredefinedAttributeInfos.LAST_LOGIN_DATE);
        attributes.add(PredefinedAttributeInfos.LAST_PASSWORD_CHANGE_DATE);
        attributes.add(PredefinedAttributeInfos.PASSWORD_CHANGE_INTERVAL);

        ObjectClassInfoBuilder ociBuilder = new ObjectClassInfoBuilder();
        ociBuilder.setType(ObjectClass.ACCOUNT_NAME);
        ociBuilder.addAllAttributeInfo(attributes);
        ObjectClassInfo objectClassInfo = ociBuilder.build();
        schemaBuilder.defineObjectClass(objectClassInfo);

        // Remove unsupported operations
        //
        if (disableUserLogins) {
            schemaBuilder.removeSupportedObjectClass(AuthenticateOp.class, objectClassInfo);
        }

        schema = schemaBuilder.build();
        attributeInfoMap = AttributeInfoUtil.toMap(attributes);
        returnedByDefault = new TreeSet<String>();
        for (AttributeInfo attribute : attributes) {
            if (attribute.isReturnedByDefault()) {
                returnedByDefault.add(attribute.getName());
            }
        }
        return schema;
    }

    /**
     * No access pattern, plus partial access patter.
     *
     * <pre>
     *  Primary   000000000011111111112222  Secondary 000000000011111111112222
     *  Day Hours 012345678901234567890123  Day Hours 012345678901234567890123
     *  Network:  -----  No access  ------            -----#-###--------------
     *  Batch:    -----  No access  ------            -----#-###--------------
     *  Local:    -----  No access  ------            -----#-###--------------
     *  Dialup:   -----  No access  ------            -----#-###--------------
     *  Remote:   -----  No access  ------            -----#-###--------------
     *
     * Full access pattern, plus No access pattern
     *  Primary   000000000011111111112222  Secondary 000000000011111111112222
     *  Day Hours 012345678901234567890123  Day Hours 012345678901234567890123
     *  Network:  ##### Full access ######            -----  No access  ------
     *  Batch:    ##### Full access ######            -----  No access  ------
     *  Local:    ##### Full access ######            -----  No access  ------
     *  Dialup:   ##### Full access ######            -----  No access  ------
     *  Remote:   ##### Full access ######            -----  No access  ------
     * </pre>
     *
     * Full access to all pattern No access restrictions
     *
     */
    private void parseAccessRestrictions(String access, List<String> attributesToGet,
            ConnectorObjectBuilder builder) {
        if (access.trim().equals(FULL_ACCESS_FOR_ALL)) {
            for (String accessor : ACCESSORS) {
                if (includeInAttributes(accessor.toUpperCase(), attributesToGet)) {
                    builder.addAttribute(AttributeBuilder.build(accessor.toUpperCase(),
                            new Object[] { "0-23", "0-23" }));
                }
            }
        } else {
            for (String accessor : ACCESSORS) {
                Pattern splitAccessPattern =
                        Pattern.compile(accessor + ":\\s+(.{24})\\s{12}(.{24})");
                Matcher matcher = splitAccessPattern.matcher(access);
                if (matcher.find()
                        && (includeInAttributes(accessor.toUpperCase(), attributesToGet))) {
                    builder.addAttribute(AttributeBuilder.build(accessor.toUpperCase(),
                            convertAccessor(matcher.group(1), matcher.group(2))));
                }

            }
        }
    }

    private List<String> convertAccessor(String primary, String secondary) {
        List<String> result = new ArrayList<String>(2);
        for (String value : new String[] { primary, secondary }) {
            if (value.equals(FULL_ACCESS)) {
                result.add("0-23");
            } else if (value.equals(NO_ACCESS)) {
                result.add("");
            } else {
                int lower = -1;
                char previous = '-';
                StringBuffer buffer = new StringBuffer();
                for (int i = 0; i < 24; i++) {
                    if (value.charAt(i) != previous) {
                        if (value.charAt(i) == '#') {
                            // Starting new string of hours
                            lower = i;
                        } else {
                            // ended previous string of hours
                            if (lower > -1) {
                                if ((i - 1) > lower) {
                                    buffer.append("," + lower + "-" + (i - 1));
                                } else {
                                    buffer.append("," + lower);
                                }
                                lower = -1;
                            }
                        }
                    }
                    previous = value.charAt(i);
                }
                if (lower > -1) {
                    if (lower < 23) {
                        buffer.append("," + lower + "-23");
                    } else {
                        buffer.append("," + lower);
                    }
                }
                result.add(buffer.toString().substring(1));
            }
        }

        return result;
    }

    private static final String[] ACCESSORS = { "Network", "Batch", "Local", "Dialup", "Remote" };
    private static final String FULL_ACCESS_FOR_ALL = "No access restrictions";
    private static final String FULL_ACCESS = "##### Full access ######";
    private static final String NO_ACCESS = "-----  No access  ------";

    private AttributeInfo buildMultivaluedAttribute(String name, Class<?> clazz, boolean required) {
        AttributeInfoBuilder builder = new AttributeInfoBuilder();
        builder.setName(name);
        builder.setType(clazz);
        builder.setRequired(required);
        builder.setMultiValued(true);
        return builder.build();
    }

    private AttributeInfo buildRequiredAttribute(String name, Class<?> clazz) {
        AttributeInfoBuilder builder = new AttributeInfoBuilder();
        builder.setName(name);
        builder.setType(clazz);
        builder.setRequired(true);
        builder.setMultiValued(false);
        builder.setUpdateable(true);
        builder.setCreateable(true);
        return builder.build();
    }

    private AttributeInfo buildReadonlyAttribute(String name, Class<?> clazz, boolean required) {
        AttributeInfoBuilder builder = new AttributeInfoBuilder();
        builder.setName(name);
        builder.setType(clazz);
        builder.setRequired(required);
        builder.setMultiValued(false);
        builder.setCreateable(false);
        builder.setUpdateable(false);
        return builder.build();
    }

    private AttributeInfo buildCreateonlyAttribute(String name, Class<?> clazz, boolean required) {
        AttributeInfoBuilder builder = new AttributeInfoBuilder();
        builder.setName(name);
        builder.setType(clazz);
        builder.setRequired(required);
        builder.setMultiValued(false);
        builder.setUpdateable(false);
        builder.setCreateable(true);
        builder.setReadable(false);
        builder.setReturnedByDefault(false);
        return builder.build();
    }

    /**
     * {@inheritDoc}
     */
    public void dispose() {
        if (connection != null) {
            connection.dispose();
            connection = null;
        }
    }

    /**
     * {@inheritDoc}
     */
    public void checkAlive() {
        connection.test();
    }

    /**
     * {@inheritDoc}
     */
    public Configuration getConfiguration() {
        return configuration;
    }

    /**
     * {@inheritDoc}
     */
    public void init(Configuration cfg) {
        configuration = (VmsConfiguration) cfg;
        vmsDateFormatWithSecs =
                new SimpleDateFormat(configuration.getVmsDateFormatWithSecs(), new Locale(
                        configuration.getVmsLocale()));
        TimeZone timeZone = TimeZone.getTimeZone(configuration.getVmsTimeZone());
        vmsDateFormatWithSecs.setTimeZone(timeZone);
        vmsDateFormatWithoutSecs =
                new SimpleDateFormat(configuration.getVmsDateFormatWithoutSecs(), new Locale(
                        configuration.getVmsLocale()));
        vmsDateFormatWithoutSecs.setTimeZone(timeZone);

        // Internal scripts are all in GROOVY for now
        //
        ScriptExecutorFactory scriptFactory = ScriptExecutorFactory.newInstance("GROOVY");
        multipleAuthorizeCommandExecutor =
                scriptFactory.newScriptExecutor(getClass().getClassLoader(),
                        multipleAuthorizeCommandScript, true);
        changeOwnPasswordCommandExecutor =
                scriptFactory.newScriptExecutor(getClass().getClassLoader(),
                        changeOwnPasswordCommandScript, true);
        dateCommandExecutor =
                scriptFactory.newScriptExecutor(getClass().getClassLoader(), dateCommandScript,
                        true);
        try {
            connection = new VmsConnection(configuration, VmsConnector.SHORT_WAIT);
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    public Object runScriptOnResource(ScriptContext request, OperationOptions options) {
        String user = options.getRunAsUser();
        GuardedString password = options.getRunWithPassword();
        if (user != null && password == null) {
            throw new ConnectorException(configuration
                    .getMessage(VmsMessages.PASSWORD_REQUIRED_FOR_RUN_AS));
        }
        if (user != null && isDisableUserLogins(configuration)) {
            throw new ConnectorException(configuration.getMessage(VmsMessages.RUN_AS_WHEN_DISABLED));
        }

        Map<String, Object> arguments = request.getScriptArguments();
        String language = request.getScriptLanguage();
        if (!"DCL".equalsIgnoreCase(language)) {
            throw new ConnectorException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_SCRIPTING_LANGUAGE, language));
        }

        String script = request.getScriptText();
        try {
            if (user != null) {
                VmsConfiguration configuration = new VmsConfiguration(this.configuration);
                configuration.setUserName(user);
                configuration.setPassword(password);
                VmsConnection connection =
                        new VmsConnection(configuration, VmsConnector.SHORT_WAIT);
                Object result = executeScript(connection, script, SHORT_WAIT, arguments);
                connection.dispose();
                return result;
            } else {
                return executeScript(connection, script, SHORT_WAIT, arguments);
            }
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    private boolean isDisableUserLogins(VmsConfiguration configuration) {
        boolean disableUserLogins = false;
        if (null != configuration && configuration.getDisableUserLogins() != null) {
            disableUserLogins = configuration.getDisableUserLogins();
        }
        return disableUserLogins;
    }

    private boolean isSSH(VmsConfiguration configuration) {
        boolean isSSH = false;
        if (configuration.getSSH() != null) {
            isSSH = configuration.getSSH();
        }
        return isSSH;
    }

    private boolean isAttributeTrue(Attribute attribute) {
        if (attribute == null) {
            return false;
        }
        return AttributeUtil.getBooleanValue(attribute);
    }

    protected String executeCommand(VmsConnection connection, String command) {
        connection.resetStandardOutput();
        try {
            connection.send(command);
            connection.waitFor(configuration.getLocalHostShellPrompt(), SHORT_WAIT);
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
        String output = connection.getStandardOutput();
        int index = output.lastIndexOf(configuration.getLocalHostShellPrompt());
        if (index != -1) {
            output = output.substring(0, index);
        }
        String terminator = null;
        if (isSSH(configuration)) {
            terminator = "\r\n";
        } else {
            terminator = "\n\r";
        }
        // Strip trailing NULs (seen with SSH)
        //
        while (output.endsWith("\0")) {
            output = output.substring(0, output.length() - 1);
        }
        // trim off starting or ending \n
        //
        if (output.startsWith(terminator)) {
            output = output.substring(terminator.length());
        }
        if (output.endsWith(terminator)) {
            output = output.substring(0, output.length() - terminator.length());
        }
        return output;
    }

    protected String[] executeScript(VmsConnection connection, String action, int timeout,
            Map<String, Object> args) {
        // create a temp file
        //
        String tmpfile = UUID.randomUUID().toString();

        // Create an empty error file, so that we can send it back if there are
        // no errors
        //
        executeCommand(connection, "SET DEFAULT SYS$LOGIN");
        executeCommand(connection, "OPEN/WRITE OUTPUT_FILE " + tmpfile + ".ERROR");
        executeCommand(connection, "CLOSE OUTPUT_FILE");

        // create script and append actions
        //
        executeCommand(connection, "OPEN/WRITE OUTPUT_FILE " + tmpfile + ".COM");
        executeCommand(connection, "WRITE OUTPUT_FILE \"$ DEFINE SYS$ERROR " + tmpfile + ".ERROR");
        executeCommand(connection, "WRITE OUTPUT_FILE \"$ DEFINE SYS$OUTPUT " + tmpfile + ".OUTPUT");

        setEnvironmentVariables(connection, args);

        StringTokenizer st = new StringTokenizer(action, "\r\n\f");
        if (st.hasMoreTokens()) {
            do {
                String token = st.nextToken();
                if (!StringUtil.isBlank(token)) {
                    executeCommand(connection, "WRITE OUTPUT_FILE "
                            + quoteForDCLWhenNeeded(token, true));
                }
            } while (st.hasMoreTokens());
        }

        executeCommand(connection, "CLOSE OUTPUT_FILE");

        executeCommand(connection, "@" + tmpfile);

        executeCommand(connection, "CMDSTATUS=$STATUS");
        executeCommand(connection, "SET DEFAULT SYS$LOGIN");

        executeCommand(connection, "DEAS SYS$ERROR");
        executeCommand(connection, "DEAS SYS$OUTPUT");

        // Capture the output
        //
        String status = executeCommand(connection, "WRITE SYS$OUTPUT CMDSTATUS");
        String output = executeCommand(connection, "TYPE " + tmpfile + ".OUTPUT");
        String error = executeCommand(connection, "TYPE " + tmpfile + ".ERROR");
        executeCommand(connection, "DELETE " + tmpfile + ".OUTPUT;");
        executeCommand(connection, "DELETE " + tmpfile + ".ERROR;*");
        executeCommand(connection, "DELETE " + tmpfile + ".COM;");

        return new String[] { status, output, error };
    }

    private void setEnvironmentVariables(VmsConnection connection, Map<String, Object> args) {
        Set<Map.Entry<String, Object>> keyset = args.entrySet();
        for (Map.Entry<String, Object> entry : keyset) {
            String name = entry.getKey();
            Object value = entry.getValue();
            String dclAssignment = "$" + name + "=" + quoteForDCLWhenNeeded(value.toString(), true);
            String line = "WRITE OUTPUT_FILE " + quoteForDCLWhenNeeded(dclAssignment, true);
            if (line.length() < 255) {
                executeCommand(connection, line);
            }
        }
    }

    public Attribute normalizeAttribute(ObjectClass oclass, Attribute attribute) {
        // Because VMS reports NAME (and UID) in upper case, they will not match
        // against a lower case name unless the values are forced to upper case.
        //
        if (attribute instanceof Name) {
            Name name = (Name) attribute;
            return new Name(name.getNameValue().toUpperCase());
        }
        if (attribute instanceof Uid) {
            Uid uid = (Uid) attribute;
            return new Uid(uid.getUidValue().toUpperCase());
        }
        return attribute;
    }

    public Uid authenticate(ObjectClass objectClass, String username, GuardedString password,
            OperationOptions options) {
        VmsConfiguration configuration = new VmsConfiguration(this.configuration);
        configuration.setUserName(username);
        configuration.setPassword(password);
        try {
            VmsConnection connection = new VmsConnection(configuration, VmsConnector.SHORT_WAIT);
            connection.dispose();
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }

        return new Uid(username);
    }

    public Uid resolveUsername(ObjectClass objectClass, String username, OperationOptions options) {
        if (!objectClass.is(ObjectClass.ACCOUNT_NAME)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.UNSUPPORTED_OBJECT_CLASS, objectClass.getObjectClassValue()));
        }
        LocalHandler handler = new LocalHandler();
        List<String> query =
                createFilterTranslator(objectClass, options).translate(
                        new EqualsFilter(AttributeBuilder.build(Name.NAME, username)));
        executeQuery(ObjectClass.ACCOUNT, query.get(0), handler, options);
        if (!handler.iterator().hasNext()) {
            throw new UnknownUidException();
        }
        return handler.iterator().next().getUid();
    }

    private String getCreateDirCommand(Map<String, Attribute> attrMap, Attribute createDirectory) {
        if (createDirectory == null || !AttributeUtil.getBooleanValue(createDirectory)) {
            return null;
        }

        Attribute uicAttr = attrMap.get(ATTR_UIC);
        Attribute deviceAttr = attrMap.get(ATTR_DEVICE);
        Attribute directoryAttr = attrMap.get(ATTR_DIRECTORY);

        if (uicAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_UIC));
        }
        if (deviceAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_DEVICE));
        }
        if (directoryAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_DIRECTORY));
        }

        String uic = AttributeUtil.getStringValue(uicAttr);
        String device = AttributeUtil.getStringValue(deviceAttr);
        String directory = AttributeUtil.getStringValue(directoryAttr);

        if (StringUtil.isBlank(uic)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_UIC));
        }
        if (StringUtil.isBlank(device)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_DEVICE));
        }
        if (StringUtil.isBlank(directory)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_DIRECTORY));
        }

        StringBuffer cmd = new StringBuffer();

        cmd.append("CREATE/DIRECTORY/PROTECTION=SYSTEM:RWED/OWNER_UIC=");
        cmd.append(uic);
        cmd.append(" ");
        if (!device.endsWith(":")) {
            device += ":";
        }
        cmd.append(device);
        if (!directory.endsWith("]")) {
            directory = "[" + directory + "]";
        }
        cmd.append(directory);
        return cmd.toString();
    }

    private String getCopyLoginCommand(Map<String, Attribute> attrMap, Attribute copyLoginScript,
            Attribute loginScriptSourceAttr) {
        if (copyLoginScript == null || !AttributeUtil.getBooleanValue(copyLoginScript)) {
            return null;
        }

        Attribute uicAttr = attrMap.get(ATTR_UIC);
        Attribute deviceAttr = attrMap.get(ATTR_DEVICE);
        Attribute directoryAttr = attrMap.get(ATTR_DIRECTORY);

        if (uicAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_UIC));
        }
        if (deviceAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_DEVICE));
        }
        if (directoryAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_DIRECTORY));
        }
        if (loginScriptSourceAttr == null) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_REQUIRED_ATTRIBUTE, ATTR_LOGIN_SCRIPT_SOURCE));
        }

        String uic = AttributeUtil.getStringValue(uicAttr);
        String device = AttributeUtil.getStringValue(deviceAttr);
        String directory = AttributeUtil.getStringValue(directoryAttr);
        String loginScriptSource = AttributeUtil.getStringValue(loginScriptSourceAttr);

        if (StringUtil.isBlank(uic)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_UIC));
        }
        if (StringUtil.isBlank(device)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_DEVICE));
        }
        if (StringUtil.isBlank(directory)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_DIRECTORY));
        }
        if (StringUtil.isBlank(loginScriptSource)) {
            throw new IllegalArgumentException(configuration.getMessage(
                    VmsMessages.MISSING_ATTRIBUTE_VALUE, ATTR_LOGIN_SCRIPT_SOURCE));
        }

        StringBuffer cmd = new StringBuffer();

        cmd.append("COPY ");
        cmd.append(loginScriptSource);
        cmd.append(" ");
        if (!device.endsWith(":")) {
            device += ":";
        }
        cmd.append(device);
        if (!directory.endsWith("]")) {
            directory = "[" + directory + "]";
        }
        cmd.append(directory);
        return cmd.toString();
    }

    /**
     * Find an unused member in the group.
     *
     * @param uicWithWildCard
     * @return
     */
    private String getUnusedUicForGroup(String uicWithWildCard) {
        try {
            Map<String, Object> variables = getVariablesForCommand();
            fillInCommand("SHOW/BRIEF " + uicWithWildCard + "\n", variables);
            String output = (String) multipleAuthorizeCommandExecutor.execute(variables);
            Pattern uicPattern = Pattern.compile("\\[(\\d+),(\\d+)\\]");
            Matcher matcher = uicPattern.matcher(output);
            int offset = 0;
            int max = 0;
            String group = null;
            while (matcher.find(offset)) {
                group = matcher.group(1);
                int value = Integer.valueOf(matcher.group(2), 8).intValue();
                if (value > max) {
                    max = value;
                }
                offset = matcher.end(1);
            }
            return "[" + group + "," + Integer.toOctalString(max + 1) + "]";
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    private String getNextUicForGroup(String uic) {
        Pattern uicPattern = Pattern.compile("\\[(\\d+),(\\d+)\\]");
        Matcher matcher = uicPattern.matcher(uic);
        if (matcher.matches()) {
            int value = Integer.valueOf(matcher.group(2), 8).intValue();
            return "[" + matcher.group(1) + "," + Integer.toOctalString(value + 1) + "]";
        } else {
            return null;
        }
    }

    /**
     * Determine if the UIC is only assigned to a single user.
     *
     * @param uic
     * @return
     */
    private boolean isUnique(String uic) {
        try {
            Map<String, Object> variables = getVariablesForCommand();
            fillInCommand("SHOW/BRIEF " + uic + "\n", variables);
            String output = (String) multipleAuthorizeCommandExecutor.execute(variables);
            return output.indexOf(uic) == output.lastIndexOf(uic);
        } catch (Exception e) {
            throw ConnectorException.wrap(e);
        }
    }

    public static class LocalHandler implements ResultsHandler, Iterable<ConnectorObject> {
        private List<ConnectorObject> objects = new LinkedList<ConnectorObject>();

        public boolean handle(ConnectorObject object) {
            objects.add(object);
            return true;
        }

        public Iterator<ConnectorObject> iterator() {
            return objects.iterator();
        }
    }

    private Map<String, Object> getVariablesForCommand() {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("SHELL_PROMPT", configuration.getLocalHostShellPrompt());
        variables.put("SHORT_WAIT", SHORT_WAIT);
        variables.put("LONG_WAIT", LONG_WAIT);
        variables.put("UAF_PROMPT", UAF_PROMPT);
        variables.put("UAF_PROMPT_CONTINUE", UAF_PROMPT_CONTINUE);
        variables.put("CONNECTION", connection);
        return variables;
    }

}
