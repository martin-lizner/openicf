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
 * http://IdentityConnectors.dev.java.net/legal/license.txt
 * See the License for the specific language governing permissions and limitations 
 * under the License. 
 * 
 * When distributing the Covered Code, include this CDDL Header Notice in each file
 * and include the License file at identityconnectors/legal/license.txt.
 * If applicable, add the following below this CDDL Header, with the fields 
 * enclosed by brackets [] replaced by your own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * ====================
 * Portions Copyrighted 2012-2014 ForgeRock AS.
 */
using System;
using System.Reflection;
using System.Collections.Generic;
using Org.IdentityConnectors.Common;
using Org.IdentityConnectors.Framework.Spi;
using Org.IdentityConnectors.Framework.Spi.Operations;
using System.Diagnostics;
using Org.IdentityConnectors.Framework.Common.Objects;
using Org.IdentityConnectors.Framework.Common.Exceptions;
using System.DirectoryServices;
using DS = System.DirectoryServices;
using System.DirectoryServices.ActiveDirectory;
using System.Text;
using Org.IdentityConnectors.Common.Script;
using System.Globalization;

namespace Org.IdentityConnectors.ActiveDirectory
{
    public enum UpdateType
    {
        ADD,
        DELETE,
        REPLACE
    }

    /// <summary>
    /// The Active Directory Connector
    /// </summary>
    [ConnectorClass("connector_displayName",
                      typeof(ActiveDirectoryConfiguration),
                      MessageCatalogPaths = new String[] { "Org.IdentityConnectors.ActiveDirectory.Messages" }
                      )]
    public class ActiveDirectoryConnector : CreateOp, Connector, SchemaOp, DeleteOp,
        SearchOp<String>, TestOp, UpdateAttributeValuesOp, ScriptOnResourceOp, SyncOp,
        AuthenticateOp, PoolableConnector
	{
        // tracing
        internal static TraceSource LOGGER = new TraceSource(TraceNames.DEFAULT);
        private static TraceSource LOGGER_API = new TraceSource(TraceNames.API);
        internal const int CAT_DEFAULT = 1;      // default tracing event category

        /// <summary>
        /// Which AD attributes are returned by default (i.e. without client explicitly asking for them).
        /// </summary>
        public IDictionary<ObjectClass, ICollection<string>> _attributesReturnedByDefault = null;

        /// <summary>
        /// Cached schema.
        /// </summary>
        private Schema _schema = null;

        /// <summary>
        /// Cached object class infos.
        /// </summary>
        private IDictionary<ObjectClass, ObjectClassInfo> _objectClassInfos = null;

        // special attribute names
        public static readonly string ATT_CONTAINER = "ad_container";
        public static readonly string ATT_USER_PASSWORD = "userPassword";
        public static readonly string ATT_CN = "cn";
        public static readonly string ATT_OU = "ou";
        public static readonly string ATT_OBJECT_GUID = "objectGuid";
        public static readonly string ATT_IS_DELETED = "isDeleted";
        public static readonly string ATT_USN_CHANGED = "uSNChanged";
        public static readonly string ATT_DISTINGUISHED_NAME = "distinguishedName";
        public static readonly string ATT_SAMACCOUNT_NAME = "sAMAccountName";
        public static readonly string ATT_MEMBER = "member";
        public static readonly string ATT_MEMBEROF = "memberOf";
        public static readonly string ATT_HOME_DIRECTORY = "homeDirectory";
        public static readonly string ATT_OBJECT_SID = "objectSid";
        public static readonly string ATT_PWD_LAST_SET = "pwdLastSet";
        public static readonly string ATT_ACCOUNT_EXPIRES = "accountExpires";
        public static readonly string ATT_LOCKOUT_TIME = "lockoutTime";
        public static readonly string ATT_GROUP_TYPE = "groupType";
        public static readonly string ATT_DESCRIPTION = "description";
        public static readonly string ATT_SHORT_NAME = "name";
        public static readonly string ATT_DISPLAY_NAME = "displayName";
        public static readonly string ATT_USER_ACOUNT_CONTROL = "userAccountControl";
        public static readonly string ATT_PASSWORD_NEVER_EXPIRES = "PasswordNeverExpires";
        public static readonly string ATT_ACCOUNTS = ConnectorAttributeUtil.CreateSpecialName("ACCOUNTS");
        public static readonly string OBJECTCLASS_OU = "organizationalUnit";
        public static readonly string OBJECTCLASS_GROUP = "Group";
        public static readonly string OPTION_DOMAIN = "w2k_domain";
        public static readonly string OPTION_RETURN_UID_ONLY = "returnUidOnly";

        public static readonly ObjectClass ouObjectClass = new ObjectClass(OBJECTCLASS_OU);
        public static readonly ObjectClass groupObjectClass = new ObjectClass(OBJECTCLASS_GROUP);

        private static readonly string OLD_SEARCH_FILTER_STRING = "Search Filter String";
        private static readonly string OLD_SEARCH_FILTER = "searchFilter";

        private ActiveDirectoryConfiguration _configuration = null;
        private ActiveDirectoryUtils _utils = null;
        private DirectoryEntry _dirHandler = null;
        //private DirectorySearcher searcher = null;
        public ActiveDirectoryConnector()
        {
        }

        #region CreateOp Members
        // implementation of CreateSpiOp
        public virtual Uid Create(ObjectClass oclass,
            ICollection<ConnectorAttribute> attributes, OperationOptions options)
        {
            Uid uid = null;
            bool created = false;
            DirectoryEntry containerDe = null;
            DirectoryEntry newDe = null;

            // I had lots of problems here.  Here are the things
            // that seemed to make everything work:
            // - Create the object with the minimum data and commit it,
            //   then update the object with the rest of the info.
            // - After updating an object and committing, be sure to 
            //   do a refresh cache before continuing to use it.  If
            //   not, it seems like multi-value attributes get hosed.
            // - Group membership cannot be change by memberOf, but must
            //   be changed by changing the members property of the group

            Stopwatch watch = new Stopwatch();
            watch.Start();

            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, 
                "AD.Create method starting; oclass: {0}, attributes:\n{1}", 
                oclass.GetObjectClassValue(),
                CommonUtils.DumpConnectorAttributes(attributes));

            if (_configuration == null)
            {
                throw new ConfigurationException(_configuration.ConnectorMessages.Format(
                    "ex_ConnectorNotConfigured", "Connector has not been configured"));
            }
            Name nameAttribute = ConnectorAttributeUtil.GetNameFromAttributes(attributes);
            if (nameAttribute == null)
            {
                throw new ConnectorException(
                    _configuration.ConnectorMessages.Format("ex_OperationalAttributeNull",
                        "The name operational attribute cannot be null"));
            }
            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT,
                "Name attribute = {0}", nameAttribute.GetDetails());

            String ldapContainerPath = ActiveDirectoryUtils.GetLDAPPath(_configuration.LDAPHostName,
                ActiveDirectoryUtils.GetParentDn(nameAttribute.GetNameValue()));
            String ldapEntryPath = ActiveDirectoryUtils.GetLDAPPath(_configuration.LDAPHostName,
                nameAttribute.GetNameValue());

            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT,
                "LdapContainerPath = {0}, LdapEntryPath = {1}", ldapContainerPath, ldapEntryPath);

            try
            {
                if (!DirectoryEntry.Exists(ldapContainerPath))
                {
                    throw new ConnectorException("Container " + ldapContainerPath + " does not exist");
                }

                // Get the correct container, and put the new user in it
                containerDe = new DirectoryEntry(ldapContainerPath,
                    _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);
                newDe = containerDe.Children.Add(
                    ActiveDirectoryUtils.GetRelativeName(nameAttribute),
                    _utils.GetADObjectClass(oclass));

                if (oclass.Equals(ActiveDirectoryConnector.groupObjectClass))
                {
                    ConnectorAttribute groupAttribute =
                        ConnectorAttributeUtil.Find(ActiveDirectoryConnector.ATT_GROUP_TYPE, attributes);
                    if (groupAttribute != null)
                    {
                        int? groupType = ConnectorAttributeUtil.GetIntegerValue(groupAttribute);
                        if (groupType.HasValue)
                        {
                            newDe.Properties[ActiveDirectoryConnector.ATT_GROUP_TYPE].Value = groupType;
                        }
                    }
                }

                newDe.CommitChanges();
                created = true;
                // default to creating users enabled
                if ((ObjectClass.ACCOUNT.Equals(oclass)) &&
                    (ConnectorAttributeUtil.Find(OperationalAttributes.ENABLE_NAME, attributes) == null))
                {
                    ICollection<ConnectorAttribute> temp = new HashSet<ConnectorAttribute>(attributes);
                    temp.Add(ConnectorAttributeBuilder.Build(OperationalAttributes.ENABLE_NAME, true));
                    _utils.UpdateADObject(oclass, newDe, temp, UpdateType.REPLACE, _configuration);
                }
                else
                {
                    _utils.UpdateADObject(oclass, newDe, attributes, UpdateType.REPLACE, _configuration);
                }
                Object guidValue = newDe.Properties["objectGUID"].Value;
                if (guidValue != null)
                {
                    // format the uid in the special way required for searching
                    String guidString =
                        ActiveDirectoryUtils.ConvertUIDBytesToGUIDString(
                        (Byte[])guidValue);

                    LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, 
                        "Created object with uid {0}", guidString);
                    uid = new Uid(guidString);
                }
                else
                {
                    LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT,
                        "Unable to find uid attribute for newly created object");
                }


            }
            catch (DirectoryServicesCOMException exception)
            {
                // have to make sure the new thing gets deleted in 
                // the case of error
                LOGGER.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "Caught COM exception: " + exception);
                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, "Exception: " + exception.Message);
                if (created)
                {
                    // In the case of an exception, make sure we
                    // don't leave any partial objects around
                    newDe.DeleteTree();
                }
                throw ActiveDirectoryUtils.ComToIcfException(exception, "when creating " + ldapEntryPath);
            }
            catch (System.Runtime.InteropServices.COMException e)
            {
                throw ActiveDirectoryUtils.OtherComToIcfException(e, "when creating " + ldapEntryPath);
            }
            catch (UnauthorizedAccessException e)
            {
                throw new PermissionDeniedException("permission to create " + ldapEntryPath + " denied", e);
            }
            catch (Exception exception)
            {
                //Console.WriteLine("caught general exception:" + exception);
                LOGGER.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "Caught general exception: " + exception);
                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, "Exception: " + exception.Message);
                if (created)
                {
                    // In the case of an exception, make sure we
                    // don't leave any partial objects around
                    newDe.DeleteTree();
                }
                throw;
            }
            finally
            {
                if (containerDe != null)
                {
                    containerDe.Dispose();
                }
                if (newDe != null)
                {
                    newDe.Dispose();
                }
            }
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Create returning UID: {0}, finishing in {1} ms", uid.GetUidValue(), watch.ElapsedMilliseconds);
            return uid;
        }

        #endregion

        #region Connector Members

        // implementation of Connector
        public virtual void Init(Configuration configuration)
        {
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Init method starting");

            configuration.Validate();
            _configuration = (ActiveDirectoryConfiguration)configuration;
            _utils = new ActiveDirectoryUtils(_configuration);

            // since we are a poolable connector, let's establish a persistent connection to AD
            bool useGC = false;
            if (_configuration.SearchChildDomains)
            {
                useGC = true;
            }
            string path = GetSearchContainerPath(useGC, _configuration.LDAPHostName, _configuration.Container);
            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Search: Getting root node for search");
            _dirHandler = new DirectoryEntry(path, _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);

            _schema = null;
            _objectClassInfos = null;
            Schema();           // initializes e.g. _attributesReturnedByDefault (used throughout this connector)

            //searcher = new DirectorySearcher(_dirHandler);
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Init method finishing");
        }

        #endregion

        #region IDisposable Members

        public virtual void Dispose()
        {
            if (_dirHandler != null)
            {
                _dirHandler.Dispose();
            }
        }

        #endregion

        #region SchemaOp Members
        // implementation of SchemaSpiOp
        public virtual Schema Schema()
        {
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Schema method starting");
            if (_schema != null)
            {
                LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Schema method exiting, returning cached schema");
                return _schema;
            }
            else
            {
                _schema = SchemaUtils.BuildSchema(this,
                                GetSupportedObjectClasses,
                                GetObjectClassInfo,
                                GetSupportedOperations,
                                GetUnSupportedOperations);
                _attributesReturnedByDefault = SchemaUtils.GetAttributesReturnedByDefault(
                                GetSupportedObjectClasses,
                                GetObjectClassInfo);
                LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Schema method exiting, returning freshly computed schema");
                return _schema;
            }
        }

        /// <summary>
        /// Defines the supported object classes by the connector, used for schema building
        /// </summary>
        /// <returns>List of supported object classes</returns>
        public ICollection<ObjectClass> GetSupportedObjectClasses() {
            return GetObjectClassInfos().Keys;
        }

        /// <summary>
        /// Gets the object class info for specified object class, used for schema building
        /// </summary>
        /// <param name="oc">ObjectClass to get info for</param>
        /// <returns>ObjectClass' ObjectClassInfo</returns>
        public ObjectClassInfo GetObjectClassInfo(ObjectClass oc) {
            return GetObjectClassInfos()[oc];
        }

        private IDictionary<ObjectClass, ObjectClassInfo> GetObjectClassInfos() {
            if (_objectClassInfos == null) {
                var infos = new List<IDictionary<ObjectClass, ObjectClassInfo>>();

                if (_configuration.ObjectClassesReplacementFile != null) {
                    infos.Add(CommonUtils.GetOCInfoFromFile(_configuration.ObjectClassesReplacementFile));
                } else {
                    infos.Add(CommonUtils.GetOCInfoFromExecutingAssembly("Org.IdentityConnectors.ActiveDirectory.ObjectClasses.xml"));
                }
                if (_configuration.ObjectClassesExtensionFile != null) {
                    infos.Add(CommonUtils.GetOCInfoFromFile(_configuration.ObjectClassesExtensionFile));
                }
                _objectClassInfos = CommonUtils.MergeOCInfo(infos);
            }
            return _objectClassInfos;
        }

        /// <summary>
        /// Gets the list of supported operations by the object class, used for schema building
        /// </summary>
        /// <param name="oc"></param>
        /// <returns></returns>
        public IList<SafeType<SPIOperation>> GetSupportedOperations(ObjectClass oc) {
            return null;
        }

        /// <summary>
        /// Gets the list of UNsupported operations by the object class, used for schema building
        /// </summary>
        /// <param name="oc"></param>
        /// <returns></returns>
        public IList<SafeType<SPIOperation>> GetUnSupportedOperations(ObjectClass oc) {
            if (oc.Equals(ActiveDirectoryConnector.groupObjectClass) || oc.Equals(ouObjectClass)) {
                return new List<SafeType<SPIOperation>> {
                    SafeType<SPIOperation>.Get<AuthenticateOp>(),
                    SafeType<SPIOperation>.Get<SyncOp>()};          // TODO why is SyncOp not supported for groups/ou? [med]
            }
            return null;
        }

        #endregion

        #region SearchOp<string> Members

        // implementation of SearchSpiOp
        public virtual Org.IdentityConnectors.Framework.Common.Objects.Filters.FilterTranslator<string> CreateFilterTranslator(ObjectClass oclass, OperationOptions options)
        {
            return new ActiveDirectoryFilterTranslator();
        }

        // implementation of SearchSpiOp
        public void ExecuteQuery(ObjectClass oclass, string query, ResultsHandler handler, OperationOptions options)
        {
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "ExecuteQuery starting, query = {0}", query);
            ExecuteQueryInternal(oclass, query, handler, options, GetAdAttributesToReturn(oclass, options));
        }

        public void ExecuteQueryInternal(ObjectClass oclass, string query, ResultsHandler handler, OperationOptions options, ICollection<string> adAttributesToReturn)
        {
            try
            {
                bool useGC = false;
                if (_configuration.SearchChildDomains)
                {
                    useGC = true;
                }

                IDictionary<string, object> searchOptions = options.Options;

                SearchScope searchScope = GetADSearchScopeFromOptions(options);
                string searchContainer = GetADSearchContainerFromOptions(options);

                // for backward compatibility, support old query style from resource adapters
                // but log a warning
                if ((query == null) || (query.Length == 0))
                {
                    if ((options != null) && (options.Options != null))
                    {
                        Object oldStyleQuery = null;
                        if (options.Options.Keys.Contains(OLD_SEARCH_FILTER_STRING))
                        {
                            oldStyleQuery = options.Options[OLD_SEARCH_FILTER_STRING];
                        }
                        else if (options.Options.Keys.Contains(OLD_SEARCH_FILTER))
                        {
                            oldStyleQuery = options.Options[OLD_SEARCH_FILTER];
                        }
                        if ((oldStyleQuery != null) && (oldStyleQuery is string))
                        {
                            query = (string)oldStyleQuery;
                            LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, 
                                _configuration.ConnectorMessages.Format(
                                "warn_CompatibilityModeQuery",
                                "Using Identity Manger Resource Adapter style query ''{0}''.  This should be updated to use the new connector query syntax.",
                                ((query != null) && (query.Length > 0)) ? query : ""));
                        }
                    }
                }

                SortOption sortOption = null;
                if (options.SortKeys != null && options.SortKeys.Length > 0)
                {
                    if (options.SortKeys.Length > 1)
                    {
                        throw new ArgumentException("At most one sort key is supported");
                    }
                    Org.IdentityConnectors.Framework.Common.Objects.SortKey key = options.SortKeys[0];
                    var attributeName = CustomAttributeHandlers.ToRealName(key.Field);
                    sortOption = new SortOption(attributeName, key.IsAscendingOrder() ? SortDirection.Ascending : SortDirection.Descending);
                }

                ExecuteQueryInternal(oclass, query, handler, options,
                    false, sortOption, _configuration.LDAPHostName, useGC, searchContainer, searchScope, adAttributesToReturn);
            }
            catch (DirectoryServicesCOMException e)
            {
                throw ActiveDirectoryUtils.ComToIcfException(e, "");
            }
            catch (System.Runtime.InteropServices.COMException e)
            {
                throw ActiveDirectoryUtils.OtherComToIcfException(e, "");
            }
            catch (UnauthorizedAccessException e)
            {
                throw new PermissionDeniedException(e);
            }
            catch (Exception e)
            {
                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, "Caught Exception: {0}", e);
                throw;
            }
        }

        public string GetADSearchContainerFromOptions(OperationOptions options)
        {
            if (options != null)
            {
                QualifiedUid qUid = options.getContainer;
                if (qUid != null)
                {
                    return ConnectorAttributeUtil.GetStringValue(qUid.Uid);
                }
            }

            return _configuration.Container;
        }

        public SearchScope GetADSearchScopeFromOptions(OperationOptions options)
        {
            if (options != null)
            {
                string scope = options.Scope;
                if (scope != null)
                {
                    if (scope.Equals(OperationOptions.SCOPE_ONE_LEVEL))
                    {
                        return SearchScope.OneLevel;
                    }
                    else if (scope.Equals(OperationOptions.SCOPE_SUBTREE))
                    {
                        return SearchScope.Subtree;
                    }
                    else if (scope.Equals(OperationOptions.SCOPE_OBJECT))
                    {
                        return SearchScope.Base;
                    }
                    else
                    {
                        throw new ConnectorException(_configuration.ConnectorMessages.Format(
                            "ex_invalidSearchScope", "An invalid search scope was specified: {0}", scope));
                    }
                }
            }

            // default value is subtree;
            return SearchScope.Subtree;
        }

        // this is used by the ExecuteQuery method of SearchSpiOp, and
        // by the SyncSpiOp 
        public void ExecuteQueryInternal(ObjectClass oclass, string query,
            ResultsHandler handler, OperationOptions options, bool includeDeleted,
            SortOption sortOption, string serverName, bool useGlobalCatalog, 
            string searchRoot, SearchScope searchScope, ICollection<string> attributesToReturn)
        {
            bool pagedSearch = options.PageSize.HasValue && options.PageSize.Value > 0;

            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "AD.ExecuteQueryInternal: modifying query; attributesToReturn = {0}, pageSize = {1}", 
                CollectionUtil.Dump(attributesToReturn), options.PageSize);

            StringBuilder fullQueryBuilder = new StringBuilder();
            if (query == null)
            {
                fullQueryBuilder.Append("(objectclass=");
                fullQueryBuilder.Append(_utils.GetADObjectClass(oclass));
                fullQueryBuilder.Append(")");
            }
            else
            {
                fullQueryBuilder.Append("(&(objectclass=");
                fullQueryBuilder.Append(_utils.GetADObjectClass(oclass));
                fullQueryBuilder.Append(")");
                fullQueryBuilder.Append(query);
                fullQueryBuilder.Append(")");
            }

            query = fullQueryBuilder.ToString();

            if (query == null)
            {
                LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "query is null");
            }
            else
            {
                // for backward compatibility ...
                if ((ObjectClass.ACCOUNT.Equals(oclass)) && (!includeDeleted))
                {
                    query = String.Format("(&(ObjectCategory=Person){0})", query);
                }

                LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Setting search string to \'{0}\'", query);
            }

            DirectorySearcher searcher = null;
            DirectoryEntry searchRootEntry = null;
            int totalCount = -1;
            try
            {
                if (searchRoot.Equals(_configuration.Container, StringComparison.OrdinalIgnoreCase))
                {
                    searcher = new DirectorySearcher(_dirHandler, query);
                }
                else
                {
                    // options give a different root context for search, let use a new connection
                    string path;
                    path = GetSearchContainerPath(useGlobalCatalog, serverName, searchRoot);
                    LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Search: Getting root node for search");
                    searchRootEntry = new DirectoryEntry(path, _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);
                    searcher = new DirectorySearcher(searchRootEntry, query);
                }

                searcher.SearchScope = searchScope;

                int offset = 1;

                if (pagedSearch)
                {
                    DirectoryVirtualListView vlv = new DirectoryVirtualListView();
                    if (options.PagedResultsCookie != null)
                    {
                        LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "PagedResultsCookie is not supported, the provided value of '{0}' will be ignored.", options.PagedResultsCookie);
                    }
                    if (options.PagedResultsOffset.HasValue)
                    {
                        if (options.PagedResultsOffset.Value == 0)
                        {
                            throw new ArgumentException("PagedResultsOffset value of 0 is not supported.");     // TODO or issue warning only?
                        }
                        offset = options.PagedResultsOffset.Value;
                        vlv.Offset = offset;
                    }
                    vlv.BeforeCount = 0;
                    vlv.AfterCount = options.PageSize.Value - 1;

                    vlv.ApproximateTotal = 1000000;         // TODO what with this?
                    searcher.VirtualListView = vlv;
                    LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Search: creating VLV: BeforeCount={0}, AfterCount={1}, Offset={2}",
                        vlv.BeforeCount, vlv.AfterCount, vlv.Offset);

                    // when using VLV we have to provide some sorting
                    if (sortOption == null)
                    {
                        sortOption = new SortOption(ATT_CN, SortDirection.Ascending);
                        LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "No sort option specified, but VLV requires one. Default one will be used.");
                    }
                }
                else
                {
                    searcher.PageSize = 1000;               // this must be set in order to retrieve all relevant records
                }

                if (includeDeleted)
                {
                    searcher.Tombstone = true;
                }

                if (sortOption != null)
                {
                    searcher.Sort = sortOption;
                    LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Sort option: {0} / {1}", sortOption.PropertyName, sortOption.Direction);
                }

                LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Search: Performing query");
                
                Stopwatch stopWatch = new Stopwatch();
    			stopWatch.Start();

                SearchResultCollection resultSet = null;
                int count = 0;
                try
                {
                    resultSet = searcher.FindAll();
                    TimeSpan ts = stopWatch.Elapsed;	// Get the elapsed time as a TimeSpan value.
					string elapsedTime = String.Format("{0:00}:{1:00}:{2:00}.{3:000}",
        				ts.Hours, ts.Minutes, ts.Seconds,
	        			ts.Milliseconds);
   					LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "searcher.FindAll took {0}", elapsedTime);

                    foreach (DS.SearchResult result in resultSet)
                    {
                        count++;
                        if (!buildConnectorObject(result, oclass, useGlobalCatalog, searchRoot, attributesToReturn, handler))
                        {
                            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Processing search results was stopped on the client request");
                            break;
                        }
                    }

                    if (pagedSearch)
                    {
                        DirectoryVirtualListView vlv = null;
                        try
                        {
                            vlv = searcher.VirtualListView;
                        }
                        catch (Exception e)
                        {
                            LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "Couldn't get VirtualListView from the DirectorySearcher instance. Exception = {0}", e);
                        }
                        if (vlv != null)
                        {
                            totalCount = vlv.ApproximateTotal;
                            LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "VLV returned = {0}; BeforeCount={1}, AfterCount={2}, Offset={3}, ApproxTotal={4}, DirVLVContext={5}, Target={6}, TargetPerc={7}", vlv, 
                                vlv.BeforeCount, vlv.AfterCount, vlv.Offset, vlv.ApproximateTotal, vlv.DirectoryVirtualListViewContext, vlv.Target, vlv.TargetPercentage);
                        }
                    }

                }
                finally
                {
                    if (handler is SearchResultsHandler && pagedSearch)
                    {
                        int remainingEntries = totalCount - (offset - 1) - count;           // may be incorrect if processing was stopped on request by handler
                        Org.IdentityConnectors.Framework.Common.Objects.SearchResult searchResult = 
                            new Org.IdentityConnectors.Framework.Common.Objects.SearchResult("dummy cookie", remainingEntries);
                        var searchHandler = handler as SearchResultsHandler;
                        searchHandler.HandleResult(searchResult);
                    }

                	stopWatch.Stop();
                    TimeSpan ts = stopWatch.Elapsed;
					string elapsedTime = String.Format("{0:00}:{1:00}:{2:00}.{3:000}", ts.Hours, ts.Minutes, ts.Seconds, ts.Milliseconds);
                    LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Search: found {0} results, took {1}", count, elapsedTime);
                    // Important to dispose to avoid memory leak
                    if (resultSet != null)
                    {
                        resultSet.Dispose();
                    }
                }
            }
            finally
            {
                if (searcher != null)
                {
                    searcher.Dispose();
                }
                if (searchRootEntry != null)
                {
                    searchRootEntry.Dispose();
                }
            }
        }

        // returns true if the processing should continue
        private bool buildConnectorObject(DS.SearchResult result, ObjectClass oclass, bool useGlobalCatalog, string searchRoot, ICollection<string> attributesToReturn, ResultsHandler handler)
        {
			Stopwatch stopWatch = new Stopwatch();
			stopWatch.Start();

            bool rv = true;

            try
            {
                LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "Found object {0}", result.Path);
                ConnectorObjectBuilder builder = new ConnectorObjectBuilder();
                builder.ObjectClass = oclass;

                bool isDeleted = false;
                if (result.Properties.Contains(ATT_IS_DELETED))
                {
                    ResultPropertyValueCollection pvc = result.Properties[ATT_IS_DELETED];
                    if (pvc.Count > 0)
                    {
                        isDeleted = (bool)pvc[0];
                    }
                }

                if (isDeleted.Equals(false))
                {
                    // if we were using the global catalog (gc), we have to 
                    // now retrieve the object from a domain controller (dc) 
                    // because the gc may not have have all of the attributes,
                    // depending on which attributes are replicated to the gc.                    
                    DS.SearchResult savedGcResult = null;
                    DS.SearchResult savedDcResult = result;
                    if (useGlobalCatalog)
                    {
                        savedGcResult = result;

                        String dcSearchRootPath = ActiveDirectoryUtils.GetLDAPPath(
                            _configuration.LDAPHostName, searchRoot);

                        DirectoryEntry dcSearchRoot = new DirectoryEntry(dcSearchRootPath,
                            _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);

                        string dcSearchQuery = String.Format("(" + ATT_DISTINGUISHED_NAME + "={0})",
                            ActiveDirectoryUtils.GetDnFromPath(savedGcResult.Path));
                        DirectorySearcher dcSearcher =
                            new DirectorySearcher(dcSearchRoot, dcSearchQuery);
                        savedDcResult = dcSearcher.FindOne();
                        LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "after dcSearcher.FindOne: T={0} ms", stopWatch.ElapsedMilliseconds);
                        if (savedDcResult == null)
                        {
                            // in this case, there is no choice, but to use
                            // what is in the global catalog.  We would have 
                            //liked to have read from the regular ldap, but there
                            // is not one.  This is the case for domainDNS objects
                            // (at least for child domains in certain or maybe all
                            // circumstances).
                            savedDcResult = savedGcResult;
                        }
                        dcSearcher.Dispose();
                        dcSearchRoot.Dispose();
                        LOGGER.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, "after dcSearchRoot.Dispose: T={0} ms", stopWatch.ElapsedMilliseconds);
                    }
                    
                    DirectoryEntry entryDc = savedDcResult != null ? savedDcResult.GetDirectoryEntry() : null;
                    DirectoryEntry entryGc = savedGcResult != null ? savedGcResult.GetDirectoryEntry() : null;

                    try
                    {
                    	foreach (string attributeName in attributesToReturn)
                    	{
                        	DS.SearchResult savedResults = savedDcResult;
	                        DirectoryEntry entry = entryDc;
    	                    // if we are using the global catalog, we had to get the
    	                    // dc's version of the directory entry, but for usnchanged, 
    	                    // we need the gc version of it
    	                    if (useGlobalCatalog && attributeName.Equals(ATT_USN_CHANGED,
    	                        StringComparison.CurrentCultureIgnoreCase))
    	                    {
    	                        savedResults = savedGcResult;
    	                        entry = entryGc;
    	                    }
	
    	                    //Stopwatch attw = new Stopwatch();
    	                    //attw.Start();
    	                    AddAttributeIfNotNull(builder,
    	                        _utils.GetConnectorAttributeFromADEntry(
    	                        oclass, attributeName, savedResults, entry));
    	                    //attw.Stop();
							//Trace.TraceInformation("after AddAttributeIfNotNull({0}): T={1} ms, this attribute took={2} ticks", attributeName, stopWatch.ElapsedMilliseconds, attw.ElapsedTicks);
    	                }
                    }
                    finally
                    {
                    	if (entryDc != null) {
                    		entryDc.Dispose();
                    	}
                    	if (entryGc != null) {
                    		entryGc.Dispose();
                    	}
                    }
                }
                else
                {
                    // get uid
                    AddAttributeIfNotNull(builder,
                        _utils.GetConnectorAttributeFromADEntry(
                        oclass, Uid.NAME, result, null));

                    // get uid
                    AddAttributeIfNotNull(builder,
                        _utils.GetConnectorAttributeFromADEntry(
                        oclass, Name.NAME, result, null));

                    // get usnchanged
                    AddAttributeIfNotNull(builder,
                        _utils.GetConnectorAttributeFromADEntry(
                        oclass, ATT_USN_CHANGED, result, null));

                    // add isDeleted 
                    builder.AddAttribute(ATT_IS_DELETED, true);
                }

                String msg = String.Format("Returning ''{0}'', in {1} ms",
                    (result.Path != null) ? result.Path : "<path is null>", stopWatch.ElapsedMilliseconds);
                LOGGER_API.TraceEvent(TraceEventType.Verbose, CAT_DEFAULT, msg);
                rv = handler.Handle(builder.Build());
            }
            catch (DirectoryServicesCOMException e)
            {
                // there is a chance that we found the result, but
                // in the mean time, it was deleted.  In that case, 
                // log an error and continue
                LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "Error in creating ConnectorObject from DirectoryEntry.  It may have been deleted during search.");
                LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "Exception details: " + e);
            }
            catch (Exception e)
            {
                // In that case, of any error, try to continue
                LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "Error in creating ConnectorObject from DirectoryEntry.");
                LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "Exception details: " + e);
            }
			stopWatch.Stop();
            return rv;
        }

        private string GetSearchContainerPath(bool useGC, string hostname, string searchContainer)
        {
            String path;

            if (useGC)
            {
                path = ActiveDirectoryUtils.GetGCPath(hostname, searchContainer);
            }
            else
            {
                path = ActiveDirectoryUtils.GetLDAPPath(hostname, searchContainer);
            }

            return path;
        }

        public ICollection<string> GetAdAttributesToReturn(ObjectClass oclass, OperationOptions options)
        {
            ICollection<string> attributeNames = null;

            if (options != null && options.AttributesToGet != null && options.AttributesToGet.Length > 0)
            {
                attributeNames = new HashSet<string>(options.AttributesToGet);
            }
            else
            {
                attributeNames = _attributesReturnedByDefault[oclass];
            }

            // Uid and name are always returned
            attributeNames.Add(Uid.NAME);
            attributeNames.Add(Name.NAME);
            return attributeNames;
        }

        private void AddAttributeIfNotNull(ConnectorObjectBuilder builder,
            ConnectorAttribute attribute)
        {
            if (attribute != null)
            {
                builder.AddAttribute(attribute);
            }
        }

        #endregion

        #region TestOp Members

        public virtual void Test()
        {
            _configuration.Validate();

            bool objectFound = true;
            // now make sure they specified a valid value for the User Object Class
            ActiveDirectorySchema ADSchema = _utils.GetADSchema();
            ActiveDirectorySchemaClass ADSchemaClass = null;
            try
            {
                ADSchemaClass = ADSchema.FindClass(_configuration.ObjectClass);

            }
            catch (ActiveDirectoryObjectNotFoundException exception)
            {
                objectFound = false;
            }
            if ((!objectFound) || (ADSchemaClass == null))
            {
                throw new ConnectorException(
                    _configuration.ConnectorMessages.Format(
                    "ex_InvalidObjectClassInConfiguration",
                    "Invalid Object Class was specified in the connector configuration.  Object Class \'{0}\' was not found in Active Directory",
                    _configuration.ObjectClass));
            }

            try
            {
                // see if the Container exists
                if (!DirectoryEntry.Exists(GetSearchContainerPath(UseGlobalCatalog(),
                                                                  _configuration.LDAPHostName, _configuration.Container)))
                {
                    throw new ConnectorException(
                        _configuration.ConnectorMessages.Format(
                            "ex_InvalidContainerInConfiguration",
                            "An invalid container was supplied:  {0}",
                            _configuration.Container));
                }
            }
            catch (DirectoryServicesCOMException dscex)
            {
                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, string.Format(CultureInfo.InvariantCulture,
                    "Failed to determine whether the Container '{0}' exists. Exception: {1}", _configuration.Container, dscex));

                throw new ConnectorException(
                        _configuration.ConnectorMessages.Format(
                            "ex_ContainerNotFound",
                            "Could not find the Container '{0}', the following message was returned from the server: {1}",
                            _configuration.Container, dscex.Message), dscex);
            }
        }

        #endregion

        #region AdvancedUpdateOp Members
        public Uid Update(ObjectClass objclass, Uid uid, ICollection<ConnectorAttribute> attrs, OperationOptions options)
        {
            return Update(UpdateType.REPLACE, objclass, ConnectorAttributeUtil.AddUid(attrs, uid), options);
        }

        // this one is used from Exchange connector
        public Uid Update(UpdateType updateType, ObjectClass objclass, Uid uid, ICollection<ConnectorAttribute> attrs, OperationOptions options) 
        {
            return Update(updateType, objclass, ConnectorAttributeUtil.AddUid(attrs, uid), options);
        }


        public Uid AddAttributeValues(ObjectClass objclass,
                Uid uid,
                ICollection<ConnectorAttribute> valuesToAdd,
                OperationOptions options)
        {
            return Update(UpdateType.ADD, objclass, ConnectorAttributeUtil.AddUid(valuesToAdd, uid), options);
        }

        public Uid RemoveAttributeValues(ObjectClass objclass,
                Uid uid,
                ICollection<ConnectorAttribute> valuesToRemove,
                OperationOptions options)
        {
            return Update(UpdateType.DELETE, objclass, ConnectorAttributeUtil.AddUid(valuesToRemove, uid), options);
        }

        // implementation of AdvancedUpdateSpiOp
        public Uid Update(UpdateType type, ObjectClass oclass, 
            ICollection<ConnectorAttribute> attributes, OperationOptions options)
        {
            Stopwatch watch = new Stopwatch();
            watch.Start();

            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Update method; type = {0}, oclass = {1}, attributes:\n{2}", type, oclass, CommonUtils.DumpConnectorAttributes(attributes));

            Uid updatedUid = ConnectorAttributeUtil.GetUidAttribute(attributes);

            if (attributes.Count == 0 || (updatedUid != null && attributes.Count == 1))
            {
                // nothing besides UID
                LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Update method finishing - nothing to do");
                return updatedUid;
            }

            if (_configuration == null)
            {
                throw new ConfigurationException(_configuration.ConnectorMessages.Format(
                    "ex_ConnectorNotConfigured", "Connector has not been configured"));
            }
            
            if (updatedUid == null)
            {
                throw new ConnectorException(_configuration.ConnectorMessages.Format(
                    "ex_UIDNotPresent", "Uid was not present"));
            }

            DirectoryEntry updateEntry = null;
            try
            {
                updateEntry = ActiveDirectoryUtils.GetDirectoryEntryFromUid(_configuration.LDAPHostName, updatedUid,
                    _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);

                _utils.UpdateADObject(oclass, updateEntry,
                    attributes, type, _configuration);
            }
            catch (DirectoryServicesCOMException e)
            {
                Exception e1 = ActiveDirectoryUtils.ComToIcfException(e, "when updating " + updatedUid.GetUidValue());
                if (e1 is NoSuchAdObjectException && updateEntry == null) {
                    throw new UnknownUidException(e1.Message, e1);
                } else {
                    throw e1;
                }
            }
            catch (System.Runtime.InteropServices.COMException e)
            {
                throw ActiveDirectoryUtils.OtherComToIcfException(e, "when updating " + updatedUid.GetUidValue());
            }
            catch (UnauthorizedAccessException e)
            {
                throw new PermissionDeniedException("permission to update " + updatedUid.GetUidValue() + " denied", e);
            }
            catch (Exception e)
            {
                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, "Got exception when updating: {0}", e);
                throw;
            }
            finally
            {
                if (updateEntry != null) {
                    updateEntry.Dispose();
                }
            }
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Update method finishing in {0} ms", watch.ElapsedMilliseconds);
            return updatedUid;
        }

        #endregion

        #region DeleteOp Members

        // implementation of DeleteSpiOp
        public void Delete(ObjectClass objClass, Uid uid, OperationOptions options)
        {
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Delete; uid = {0}", uid != null ? uid.GetUidValue() : "(null)");
            DirectoryEntry de = null;
            try
            {
                de = ActiveDirectoryUtils.GetDirectoryEntryFromUid(_configuration.LDAPHostName, uid,
                    _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);
            }
            catch (System.DirectoryServices.DirectoryServicesCOMException e)
            {
                // if it's not found, throw that, else just rethrow
                if (e.ErrorCode == -2147016656)
                {
                    throw new UnknownUidException(uid, objClass);
                }
                else
                {
                    throw ActiveDirectoryUtils.ComToIcfException(e, "when deleting " + uid.GetUidValue());
                }
            }
            catch (System.Runtime.InteropServices.COMException e)
            {
                throw ActiveDirectoryUtils.OtherComToIcfException(e, "when deleting " + uid.GetUidValue());
            }
            catch (UnauthorizedAccessException e)
            {
                throw new PermissionDeniedException(e);
            }

            try
            {
                if (objClass.Equals(ObjectClass.ACCOUNT))
                {
                    // if it's a user account, get the parent's child list
                    // and remove this entry
                    DirectoryEntry parent = de.Parent;
                    parent.Children.Remove(de);
                }
                else
                {
                    // translate the object class.  We dont care what
                    // it is, but this will throw the correct exception
                    // if it's an invalid one.
                    _utils.GetADObjectClass(objClass);
                    // delete this entry and all it's children
                    de.DeleteTree();
                }
            }
            catch (UnauthorizedAccessException e)
            {
                throw new PermissionDeniedException("permission to delete " + uid.GetUidValue() + " denied", e);
            }
            catch (DirectoryServicesCOMException e)
            {
                throw ActiveDirectoryUtils.ComToIcfException(e, "when deleting " + uid.GetUidValue());
            }
            catch (System.Runtime.InteropServices.COMException e)
            {
                throw ActiveDirectoryUtils.OtherComToIcfException(e, "when deleting " + uid.GetUidValue());
            }
            finally
            {
                de.Dispose();
            }
            LOGGER_API.TraceEvent(TraceEventType.Information, CAT_DEFAULT, "AD.Delete method finishing");
        }

        #endregion

        #region ScriptOnResourceOp Members

        public object RunScriptOnResource(ScriptContext request, OperationOptions options)
        {
            IDictionary<string, object> arguments = new Dictionary<string, object>(request.ScriptArguments);
            // per Will D.  batch scripts need special parameters set, but other scripts 
            // don't.  He doesn't feel that this can be changed at present, so setting 
            // the parameters here.

            // Cant find a constant for the string to represent the shell script executor,
            // replace embedded string constant if one turns up.
            if (request.ScriptLanguage.Equals("Shell", StringComparison.CurrentCultureIgnoreCase))
            {
                IDictionary<string, object> shellArguments = new Dictionary<string, object>();
                String shellPrefix = "";
                if (options.Options.ContainsKey("variablePrefix"))
                {
                    shellPrefix = (string)options.Options["variablePrefix"];
                }

                foreach (String argumentName in arguments.Keys)
                {
                    shellArguments.Add((shellPrefix + argumentName), arguments[argumentName]);
                }

                arguments = shellArguments;

                if (options.RunAsUser != null)
                {
                    arguments.Add("USERNAME", options.RunAsUser);
                    arguments.Add("PASSWORD",
                                  options.RunWithPassword.ToSecureString());
                }
            }


            ScriptExecutorFactory factory = ScriptExecutorFactory.NewInstance(request.ScriptLanguage);
            ScriptExecutor executor = factory.NewScriptExecutor(new Assembly[0], request.ScriptText, true);
            return executor.Execute(arguments);
        }

        #endregion

        #region SyncOp Members

        // implementation of SyncSpiOp
        public class SyncResults
        {
            SyncResultsHandler _syncResultsHandler;
            ActiveDirectorySyncToken _adSyncToken;
            ActiveDirectoryConfiguration _configuration;

            internal SyncResults(SyncResultsHandler syncResultsHandler,
                ActiveDirectorySyncToken adSyncToken, ActiveDirectoryConfiguration configuration)
            {
                _syncResultsHandler = syncResultsHandler;
                _adSyncToken = adSyncToken;
                _configuration = configuration;
            }

            public ResultsHandler SyncHandler
            {
                get
                {
                    return new ResultsHandler()
                    {
                        Handle = obj =>
                        {
                            SyncDeltaBuilder builder = new SyncDeltaBuilder();
                            ICollection<ConnectorAttribute> attrs = new HashSet<ConnectorAttribute>();
                            foreach (ConnectorAttribute attribute in obj.GetAttributes())
                            {
                                // add all attributes to the object except the
                                // one used to flag deletes.
                                if (!attribute.Name.Equals(ATT_IS_DELETED))
                                {
                                    attrs.Add(attribute);
                                }
                            }

                            ConnectorObjectBuilder coBuilder = new ConnectorObjectBuilder();
                            coBuilder.SetName(obj.Name);
                            coBuilder.SetUid(obj.Uid);
                            coBuilder.ObjectClass = obj.ObjectClass;
                            coBuilder.AddAttributes(attrs);
                            builder.Object = coBuilder.Build();

                            ConnectorAttribute tokenAttr =
                                ConnectorAttributeUtil.Find(ATT_USN_CHANGED, obj.GetAttributes());
                            if (tokenAttr == null)
                            {
                                string msg = _configuration.ConnectorMessages.Format("ex_missingSyncAttribute",
                                    "Attribute {0} is not present in connector object.  Cannot proceed with Synchronization",
                                    ATT_USN_CHANGED);
                                LOGGER.TraceEvent(TraceEventType.Error, CAT_DEFAULT, msg);
                                throw new ConnectorException(msg);
                            }
                            long tokenUsnValue = (long)ConnectorAttributeUtil.GetSingleValue(tokenAttr);

                            bool? isDeleted = false;
                            ConnectorAttribute isDeletedAttr =
                                ConnectorAttributeUtil.Find(ATT_IS_DELETED, obj.GetAttributes());
                            if (isDeletedAttr != null)
                            {
                                isDeleted = (bool?)ConnectorAttributeUtil.GetSingleValue(isDeletedAttr);
                                _adSyncToken.LastDeleteUsn = tokenUsnValue;
                            }
                            else
                            {
                                _adSyncToken.LastModifiedUsn = tokenUsnValue;
                            }

                            builder.Token = _adSyncToken.GetSyncToken();

                            if ((isDeleted != null) && (isDeleted.Equals(true)))
                            {
                                builder.DeltaType = SyncDeltaType.DELETE;
                            }
                            else
                            {
                                builder.DeltaType = SyncDeltaType.CREATE_OR_UPDATE;
                            }

                            builder.Uid = obj.Uid;
                            _syncResultsHandler.Handle(builder.Build());
                            return true;
                        }
                    };
                }
            }
        }

        public void Sync(ObjectClass objClass, SyncToken token,
            SyncResultsHandler handler, OperationOptions options)
        {
            SyncInternal(objClass, token, handler, options, GetAdAttributesToReturn(objClass, options));
        }

        public void SyncInternal(ObjectClass objClass, SyncToken token, 
            SyncResultsHandler handler, OperationOptions options, ICollection<string> attributesToReturn)
        {
            String serverName = GetSyncServerName();

            ActiveDirectorySyncToken adSyncToken =
                new ActiveDirectorySyncToken(token, serverName, UseGlobalCatalog());

            string modifiedQuery = GetSyncUpdateQuery(adSyncToken);
            string deletedQuery = GetSyncDeleteQuery(adSyncToken);

            OperationOptionsBuilder builder = new OperationOptionsBuilder();
            SyncResults syncResults = new SyncResults(handler, adSyncToken, _configuration);

            // find modified usn's
            ExecuteQueryInternal(objClass, modifiedQuery, syncResults.SyncHandler, builder.Build(),
                false, new SortOption(ATT_USN_CHANGED, SortDirection.Ascending),
                serverName, UseGlobalCatalog(), GetADSearchContainerFromOptions(null), SearchScope.Subtree, attributesToReturn);

            // find deleted usn's
            DirectoryContext domainContext = new DirectoryContext(DirectoryContextType.DirectoryServer,
                        serverName,
                        _configuration.DirectoryAdminName,
                        _configuration.DirectoryAdminPassword);
            Domain domain = Domain.GetDomain(domainContext);
            String deleteObjectsSearchRoot = null;
            if (domain != null)
            {
                DirectoryEntry domainDe = domain.GetDirectoryEntry();
                deleteObjectsSearchRoot = ActiveDirectoryUtils.GetDnFromPath(domainDe.Path);
                domainDe.Dispose();
            }
            ExecuteQueryInternal(objClass, deletedQuery, syncResults.SyncHandler, builder.Build(),
                true, new SortOption(ATT_USN_CHANGED, SortDirection.Ascending),
                serverName, UseGlobalCatalog(), deleteObjectsSearchRoot, SearchScope.Subtree, attributesToReturn);
        }

        public SyncToken GetLatestSyncToken(ObjectClass objectClass)
        {
            string serverName = GetSyncServerName();
            long highestCommittedUsn = 0;
            bool useGlobalCatalog = UseGlobalCatalog();
            if (useGlobalCatalog)
            {
                DirectoryContext context = new DirectoryContext(DirectoryContextType.DirectoryServer,
                    serverName, _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);
                GlobalCatalog gc = GlobalCatalog.GetGlobalCatalog(context);
                highestCommittedUsn = gc.HighestCommittedUsn;
            }
            else
            {
                DirectoryContext context = new DirectoryContext(DirectoryContextType.DirectoryServer,
                    serverName, _configuration.DirectoryAdminName, _configuration.DirectoryAdminPassword);
                DomainController dc = DomainController.GetDomainController(context);
                highestCommittedUsn = dc.HighestCommittedUsn;
            }

            ActiveDirectorySyncToken token =
                new ActiveDirectorySyncToken("", serverName, useGlobalCatalog);
            token.LastDeleteUsn = highestCommittedUsn;
            token.LastModifiedUsn = highestCommittedUsn;
            return token.GetSyncToken();
        }

        string GetSyncServerName()
        {
            string serverName = null;

            if (UseGlobalCatalog())
            {
                serverName = _configuration.SyncGlobalCatalogServer;
            }
            else
            {
                serverName = _configuration.SyncDomainController;
            }

            if ((serverName == null) || (serverName.Length == 0))
            {
                LOGGER.TraceEvent(TraceEventType.Warning, CAT_DEFAULT, "No server was configured for synchronization, so picking one.  You should configure a server for best performance.");
                // we have to know which server we are working against,
                // so find one.
                if (UseGlobalCatalog())
                {
                    DirectoryContext context = new DirectoryContext(
                        DirectoryContextType.Forest, _configuration.DomainName,
                        _configuration.DirectoryAdminName,
                        _configuration.DirectoryAdminPassword);
                    GlobalCatalog gc = GlobalCatalog.FindOne(context);
                    _configuration.SyncGlobalCatalogServer = gc.ToString();
                    serverName = _configuration.SyncGlobalCatalogServer;
                }
                else
                {
                    DirectoryContext context = new DirectoryContext(
                        DirectoryContextType.Domain, _configuration.DomainName,
                        _configuration.DirectoryAdminName,
                        _configuration.DirectoryAdminPassword);
                    DomainController controller = DomainController.FindOne(context);
                    _configuration.SyncDomainController = controller.ToString();
                    serverName = _configuration.SyncDomainController;
                }
            }
            return serverName;
        }

        bool UseGlobalCatalog()
        {
            return (_configuration.SearchChildDomains);
        }

        String GetSyncUpdateQuery(ActiveDirectorySyncToken adSyncToken)
        {
            string modifiedQuery = null;

            // if the token is not null, we may be able to start from 
            // the usn contained there
            if (adSyncToken != null)
            {
                modifiedQuery = string.Format("(!({0}<={1}))", ATT_USN_CHANGED, adSyncToken.LastModifiedUsn);
            }

            return modifiedQuery;
        }

        String GetSyncDeleteQuery(ActiveDirectorySyncToken adSyncToken)
        {
            string deletedQuery = null;

            // if the token is not null, we may be able to start from 
            // the usn contained there
            if (adSyncToken != null)
            {
                deletedQuery = string.Format("(&(!({0}<={1}))(isDeleted=TRUE))", ATT_USN_CHANGED, adSyncToken.LastDeleteUsn);
            }
            else
            {
                deletedQuery = string.Format("(isDeleted=TRUE)");
            }

            return deletedQuery;
        }

        #endregion

        #region AuthenticateOp Members

        public Uid Authenticate(ObjectClass objectClass, string username,
            Org.IdentityConnectors.Common.Security.GuardedString password,
            OperationOptions options)
        {
            bool returnUidOnly = false;

            if (options != null)
            {
                if (options.Options.ContainsKey(OPTION_DOMAIN))
                {
                    string domainName = options.Options[OPTION_DOMAIN].ToString();
                    if ((domainName != null) && (domainName.Length > 0))
                    {
                        username = string.Format("{0}@{1}", username, options.Options["w2k_domain"]);
                    }
                }
                else if (options.Options.ContainsKey(OPTION_RETURN_UID_ONLY))
                {
                    returnUidOnly = true;
                }
            }

            PasswordChangeHandler handler = new PasswordChangeHandler(_configuration);
            return handler.Authenticate(username, password, returnUidOnly);
        }

        #endregion

        #region PoolableConnector Members

        public void CheckAlive()
        {
            return;
        }

        #endregion
    }
}
