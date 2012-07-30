/*
 *  Copyright 2010-2012 Eric Wendelin
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.eriwen.rtm

import java.util.prefs.Preferences
import groovy.util.slurpersupport.GPathResult
import org.eriwen.rtm.model.*
import org.xml.sax.SAXParseException

/**
 * Provides a Java/Groovy API to access Remember The Milk using their REST API.
 * More details available at <a href="http://www.rememberthemilk.com/services/api/">http://www.rememberthemilk.com/services/api/</a>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 * @version 2.1
 * @see <a href="http://www.rememberthemilk.com/services/api/">Remember The Milk REST API</a>
 */
public class GroovyRtm {
    private final String encoding = 'UTF-8'
    synchronized lastCallTimeMillis = new Date().time

    public String currentUser = ''
    private String apiKey
    private String secret
    private String perms
    private String frob
    private def curTimeline
    private final Preferences prefs = Preferences.userNodeForPackage(GroovyRtm.class)
    private final GroovyRtmUtils utils = new GroovyRtmUtils()
    private RtmCollectionParser parser = new RtmCollectionParser()

    GroovyRtm(final String key, final String sharedSecret, final String permissions = 'delete') {
        apiKey = key
        perms = permissions
        secret = sharedSecret
    }

    /**
     * Ensures application is authenticated before sending params to be executed
     *
     * @param params List of URL query parameters
     * @return <code>GPathResult</code> XML result from the RTM call
     * @throws GroovyRtmException when the HTTP request failed
     */
    protected GPathResult execMethod(final List params) throws GroovyRtmException {
        if (!currentUser) {
            throw new GroovyRtmException("Error: You must set currentUser before invoking authenticated methods!")
        } else if (!isAuthenticated(currentUser)) {
            throw new GroovyRtmException("Error: ${currentUser} is not authenticated!")
        }
        params << "auth_token=" + getAuthToken(currentUser)
        execUnauthenticatedMethod(params)
    }

    /**
     * Ensures all criteria is met for making the call to the RTM REST API
     *
     * @param params List of URL query parameters
     * @return <code>GPathResult</code> XML result from the RTM call
     * @throws GroovyRtmException when the HTTP request failed
     */
    protected GPathResult execUnauthenticatedMethod(final List params) throws GroovyRtmException {
        synchronized (lastCallTimeMillis) {
            params << "api_key=" + apiKey
            params << "api_sig=" + utils.getApiSignature(params, secret)
            enforceMinDelay()
            return utils.getRtmResponse("http://api.rememberthemilk.com/services/rest/?" + params.join("&"))
        }
    }

    /**
     * Adds a timeline to the passed List of URL parameters, creating a timeline if one does not exist
     *
     * @param params List of URL query parameters
     * @return <code>GPathResult</code> XML result from the RTM call
     * @throws GroovyRtmException when the HTTP request failed
     */
    protected GPathResult execTimelineMethod(final List params) throws GroovyRtmException {
        if (!curTimeline) {
            curTimeline = timelinesCreate()
        }
        params << "timeline=" + curTimeline
        execMethod(params)
    }

    /**
     * Forces successive calls to the RTM API to be spaced by at least the
     * milliseconds specified in API_DELAY_THRESHOLD by putting the execution
     * thread to sleep and synchronizing on the variable used to keep track
     */
    private void enforceMinDelay() {
        synchronized (lastCallTimeMillis) {
            def now = new Date().time
            def delay = (lastCallTimeMillis - now) + 1050L
            if (delay > 0) {
                Thread.sleep(delay)
            }
            lastCallTimeMillis = new Date().time
        }
    }

    /**
     * Checks the API key and tests connectivity to Remember The Milk
     *
     * @return Returns true if the test was successful
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.test.echo.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public boolean testEcho() throws GroovyRtmException {
        def params = ["method=rtm.test.echo"]
        GPathResult resp = execUnauthenticatedMethod(params)
        return resp ? resp.@stat.equals('ok') : false
    }

    /**
     * Checks if the application is logged in
     *
     * @return Returns true if the application is logged in to RTM
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.test.login.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public boolean testLogin() throws GroovyRtmException {
        def params = ["method=rtm.test.login"]
        GPathResult resp = execMethod(params)
        resp?.@stat?.equals('ok')
    }

    /**
     * Initiates RTM authorization by requesting a frob and getting the
     * associated URL
     *
     * @return the authorization URL. This is unique for every call to <code>getAuthUrl</code>
     * @throws GroovyRtmException when the HTTP request failed
     */
    protected String getAuthUrl() throws GroovyRtmException {
        frob = authGetFrob()
        return getAuthUrl(frob)
    }

    protected String getAuthUrl(final String frob) {
        def params = ['perms=' + perms, 'frob=' + frob]
        params << "api_key=" + apiKey
        params << "api_sig=" + utils.getApiSignature(params, secret)
        "http://www.rememberthemilk.com/services/auth/?${params.join('&')}"
    }

    /**
     * Checks if the stored auth token is valid by calling the
     * rtm.auth.checkToken REST method from the RTM API
     *
     * @return True if the stored token is valid, otherwise False
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.auth.checkToken.rtm">REST API Documentation</a>
     */
    public boolean authCheckToken(final String token) {
        def params = ['method=rtm.auth.checkToken']
        execMethod(params)?.@stat?.equals('ok')
    }

    /**
     * Gets an RTM Frob for use with authentication
     *
     * @return a new Frob string
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.auth.getFrob.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String authGetFrob() throws GroovyRtmException {
        def params = ['method=rtm.auth.getFrob']
        execUnauthenticatedMethod(params).toString()
    }

    /**
     * Given a frob, gets a new Auth token from RTM. Note that this only needs
     * to occur once per user, per application
     *
     * @return a new auth token or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.auth.getToken.rtm">REST API Documentation</a>
     */
    public String authGetToken() {
        def params = ['method=rtm.auth.getToken','frob=' + frob]
        GPathResult rsp = execUnauthenticatedMethod(params)
        setAuthToken(rsp.auth.token.toString(), rsp.auth.user.@username.toString())
        rsp.auth.token.toString()
    }

    public String getNewAuthToken() {
        authGetToken()
    }

    public void removeAuthToken(final String user = null) {
        prefs.put("authToken${user ?: currentUser}", "")
    }

    public String getAuthToken(final String user = null) {
        prefs.get("authToken${user ?: currentUser}", "")
    }

    public void setAuthToken(final String token, final String user = null) {
        prefs.put("authToken${user ?: currentUser}", token)
    }

    public boolean isAuthenticated(final String user = null) {
        !!getAuthToken(user)
    }

    /**
     * Adds a new contact
     *
     * @param contact should be a username or email address of a Remember The Milk user.
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.contacts.add.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String contactsAdd(final String contact) throws GroovyRtmException {
        def params = ["method=rtm.contacts.add"]
        params << "contact=" + URLEncoder.encode(contact, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id.toString() : null
    }

    /**
     * Deletes a contact
     *
     * @param contactId the numeric ID of the contact
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.contacts.delete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String contactsDelete(final String contactId) throws GroovyRtmException {
        def params = ["method=rtm.contacts.delete"]
        params << "contact_id=" + URLEncoder.encode(contactId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id.toString() : null
    }

    /**
     * Retrieves a List of contacts (id, fullname, and username)
     *
     * @return List of LinkedHashMaps representing contacts or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.contacts.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List contactsGetList() throws GroovyRtmException {
        def params = ["method=rtm.contacts.getList"]
        GPathResult resp = execMethod(params)
        if (!resp) {
            return null
        }
        def contacts = []
        resp.contacts.contact.each {
            contacts.push([id:it.@id.toString(),fullname:it.@fullname.toString(),username:it.@username.toString()])
        }
        contacts
    }

    /**
     * Adds a new group
     *
     * @param groupName a valid name for a group of contacts
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.groups.add.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String groupsAdd(final String groupName) throws GroovyRtmException {
        def params = ["method=rtm.groups.add"]
        params << "group=" + URLEncoder.encode(groupName, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id.toString() : null
    }

    /**
     * Adds a contact to the specified group
     *
     * @param contactId the numeric ID of an existing RTM contact
     * @param groupId the numeric ID of an existing RTM group
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.groups.addContact.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String groupsAddContact(final String contactId, final String groupId) throws GroovyRtmException {
        def params = ["method=rtm.groups.addContact"]
        params << "contact_id=" + URLEncoder.encode(contactId, encoding)
        params << "group_id=" + URLEncoder.encode(groupId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id.toString() : null
    }

    /**
     * Deletes a group
     *
     * @param groupId the numeric ID of the group
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.groups.delete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String groupsDelete(final String groupId) throws GroovyRtmException {
        def params = ["method=rtm.groups.delete"]
        params << "group_id=" + URLEncoder.encode(groupId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id : null
    }

    /**
     * Retrieves a Map representing the group with name groupName
     *
     * @param String group name to search for
     * @return LinkedHashMap for group if found or <code>null</code> otherwise
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Map groupsGetGroupByName(final String groupName) throws GroovyRtmException {
        groupsGetList().find { it.name == groupName } as Map
    }

    /**
     * Retrieves a List of groups (id and name)
     *
     * @return List of LinkedHashMaps representing groups
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.groups.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List groupsGetList() throws GroovyRtmException {
        def params = ["method=rtm.groups.getList"]
        GPathResult resp = execMethod(params)
        def groups = []
        resp?.groups?.group?.each {
            groups.push([id:it.@id.toString(),name:it.@name.toString()])
        }
        return groups
    }

    /**
     * Deletes a contact from the specified group
     *
     * @param contactId the numeric ID of an existing RTM contact
     * @param groupId the numeric ID of an existing RTM group
     * @return the transaction ID or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.groups.removeContact.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String groupsRemoveContact(final String contactId, final String groupId) throws GroovyRtmException {
        def params = ["method=rtm.groups.removeContact"]
        params << "contact_id=" + URLEncoder.encode(contactId, encoding)
        params << "group_id=" + URLEncoder.encode(groupId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.transaction.@id : null
    }

    /**
     * Adds a task list
     *
     * @param listName a valid name for a task list
     * @param filter a String with query words used to create a "Smart" list
     * @return Map of the new list's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.add.rtm">REST API Documentation</a>
     * @see <a href="http://www.rememberthemilk.com/help/answers/search/advanced.rtm">Advanced search documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsAdd(final String listName, final String filter = null) throws GroovyRtmException {
        def params = ["method=rtm.lists.add"]
        params << "name=" + URLEncoder.encode(listName, encoding)
        if (filter) params << "filter=" + URLEncoder.encode(filter, encoding)
        parser.parseList(execTimelineMethod(params))
    }

    /**
     * Archives an existing task list
     *
     * @param listId an existing list's ID
     * @return Map of the archived list's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.archive.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsArchive(final String listId) throws GroovyRtmException {
        def params = ["method=rtm.lists.archive"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        parser.parseList(execTimelineMethod(params))
    }

    /**
     * Deletes an existing task list
     *
     * @param listId an existing list's ID
     * @return Map of the deleted list's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.delete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsDelete(final String listId) throws GroovyRtmException {
        def params = ["method=rtm.lists.delete"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        parser.parseList(execTimelineMethod(params))
    }

    /**
     * Retrieves a List of task lists
     *
     * @return List of LinkedHashMaps containing ID and name or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List<TaskList> listsGetList() throws GroovyRtmException {
        def params = ["method=rtm.lists.getList"]
        parser.parseLists(execMethod(params))
    }

    /**
     * Retrieves a Map representing the list with name listName
     *
     * @return LinkedHashMap for list if found or <code>null</code> otherwise
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsGetListByName(final String listName) throws GroovyRtmException {
        List lists = listsGetList()
        lists.find { it.name.equals(listName) }
    }

    /**
     * Sets the given list as default (new tasks go there unless specified
     * otherwise), or Inbox if the list isn't provided
     *
     * @param listId the numeric ID of the task list to set as default. If <code>null</code>, will default to the user's Inbox
     * @return Returns true if the specified listId was successfully set as default
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.setDefaultList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public boolean listsSetDefaultList(final String listId = null) throws GroovyRtmException {
        def params = ["method=rtm.lists.setDefaultList"]
        if (listId) {
            params << "list_id=" + URLEncoder.encode(listId, encoding)
        }
        GPathResult resp = execTimelineMethod(params)
        resp ? resp.@stat.equals('ok') : false
    }

    /**
     * Sets the name of a given list
     *
     * @param listId an existing list's ID
     * @param newName the new name of the list
     * @return Map of the changed list's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.setName.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsSetName(final String listId, final String newName) throws GroovyRtmException {
        def params = ["method=rtm.lists.setName"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "name=" + URLEncoder.encode(newName, encoding)
        parser.parseList(execTimelineMethod(params))
    }

    /**
     * Un-archives an existing, archived task list
     *
     * @param listId an existing list's ID
     * @return Map of the un-archived list's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.lists.unarchive.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public TaskList listsUnarchive(final String listId) throws GroovyRtmException {
        def params = ["method=rtm.lists.unarchive"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        parser.parseList(execTimelineMethod(params))
    }

    /**
     * Retrieves a location specified by name
     *
     * @param locationName the name of an existing location
     * @return Map containing ID and Name of the location or <code>null</code>
     * if an error occurred
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Map locationsGetLocationByName(final String locationName) throws GroovyRtmException {
        List locations = locationsGetList()
        locations.find { it.name.equals locationName }
    }

    /**
     * Retrieves a List of locations (id and name)
     *
     * @return List of LinkedHashMaps representing locations
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.locations.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List locationsGetList() throws GroovyRtmException {
        def params = ["method=rtm.locations.getList"]
        GPathResult resp = execMethod(params)
        if (!resp) {
            return null
        }
        def locations = []
        resp.locations.location.each {
            locations.push([id:it.@id.toString(),name:it.@name.toString()])
        }
        locations
    }

    /**
     * Retrieves a Map of user settings (timezone, time format, language, etc.)
     *
     * @return LinkedHashMap with RTM-specific settings
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.settings.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Map settingsGetList() throws GroovyRtmException {
        def params = ["method=rtm.settings.getList"]
        parser.parseSettings(execMethod(params))
    }

    /**
     * Adds a task to a list, optionally parsing out a due date
     *
     * @param name a valid name for a task
     * @param listId ID of the list to add the task to. Defaults to the user's default list
     * @param parse enables parsing the task name for a due date if true (default is false)
     * @return Map of the new task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.add.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksAdd(final String name, final String listId = null,
            def parse = false) throws GroovyRtmException {
        def params = ["method=rtm.tasks.add"]
        params << "name=" + URLEncoder.encode(name, encoding)
        if (listId) params << "list_id=" + URLEncoder.encode(listId, encoding)
        if (parse) params << "parse=1"
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Adds a task with lots of options to a given or default list.
     *
     * @param name a valid name for a task
     * @param priority the priority of the task. Can be null
     * @param due a due date for the task. Can be null
     * @param estimate the estimated effort. Can be null
     * @param repeat string representing how often this task should repeat. Can be null
     * @param tags the tags associated with the task. Can be null
     * @param locationId the ID of the location to associate with the task. Can be null
     * @param url a URL to associate with the task. Can be null
     * @param listId ID of the list to add the task to. Defaults to the user's default list
     * @return Map of the new task's attributes or <code>null</code> if an error occurred
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksAdd(final String name, final String priority, final String due,
            final String estimate, final String repeat, final String tags, final String locationId,
            final String url, final String listId = null) throws GroovyRtmException {

        // Get new task details
        def newTask = tasksAdd(name, listId)
        def newListId = newTask.listId
        def newTaskSeriesId = newTask.taskSeriesId
        def newTaskId = newTask.taskId
        def transactionId = newTask.transactionId

        // Set attributes if they are defined
        if (priority) newTask = tasksSetPriority(newListId, newTaskSeriesId, newTaskId, priority)
        // We are auto-parsing date here for convenience
        if (due) newTask = tasksSetDueDate(newListId, newTaskSeriesId, newTaskId, due, false, true)
        if (estimate) newTask = tasksSetEstimate(newListId, newTaskSeriesId, newTaskId, estimate)
        if (repeat) newTask = tasksSetRecurrence(newListId, newTaskSeriesId, newTaskId, repeat)
        if (tags) newTask = tasksAddTags(newListId, newTaskSeriesId, newTaskId, tags)
        if (locationId) newTask = tasksSetLocation(newListId, newTaskSeriesId, newTaskId, locationId)
        if (url) newTask = tasksSetUrl(newListId, newTaskSeriesId, newTaskId, url)

        //Reset to original transaction_id so that transactionsUndo will remove task
        newTask.transactionId = transactionId
        newTask
    }

    /**
     * Adds tags to an existing task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param tags comma separated list of tags to add
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.addTags.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksAddTags(final String listId, final String taskseriesId,
            final String taskId, final String tags) throws GroovyRtmException {
        def params = ["method=rtm.tasks.addTags"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        params << "tags=" + URLEncoder.encode(tags, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Completes a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.complete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksComplete(final String listId, final String taskseriesId,
            final String taskId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.complete"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Deletes a specified task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @return Map of the deleted task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.delete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksDelete(final String listId, final String taskSeriesId,
            final String taskId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.delete"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskSeriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Retrieves a List of tasks
     *
     * @param listId (optional) if specified, the list from which to get tasks. Defaults to all lists
     * @param filter (optional) if specified, a filter to be applied to tasks.
     * @param lastSync (optional) An ISO 8601 formatted time value. If last_sync is provided, only tasks modified since last_sync will be returned
     * @return List of RTM tasks
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List<Task> tasksGetList(final String listId = null, final String filter = null,
            final String lastSync = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.getList"]
        if (listId) params << "list_id=" + URLEncoder.encode(listId, encoding)
        if (filter) params << "filter=" + URLEncoder.encode(filter, encoding)
        if (lastSync) params << "last_sync=" + URLEncoder.encode(lastSync, encoding)
        return parser.parseTaskList(execMethod(params))
    }

    /**
     * Adjusts the specified task's priority 1 step up or down
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param increasePriority will increase priority by 1 if true otherwise decrease
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see {@link #tasksMovePriority(String, String, String, String) alternate
     * method}
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksMovePriority(final String listId, final String taskseriesId,
            final String taskId, final Boolean increasePriority) throws GroovyRtmException {
        def direction = 'down'
        if (increasePriority) {
            direction = 'up'
        }
        tasksMovePriority(listId, taskseriesId, taskId, direction)
    }

    /**
     * Adjusts the specified task's priority 1 step up or down
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param direction "up" or "down"
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.movePriority.rtm">REST API Documentation</a>
     * @see {@link #tasksMovePriority(String, String, String, boolean) alternate
     * method}
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksMovePriority(final String listId, final String taskseriesId,
            final String taskId, final String direction) throws GroovyRtmException {
        def params = ["method=rtm.tasks.movePriority"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        params << "direction=" + URLEncoder.encode(direction, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Moves a given task to another list
     *
     * @param fromListId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param toListId ID of the list to move the task to
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.moveTo.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksMoveTo(final String fromListId, final String taskseriesId,
            final String taskId, final String toListId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.moveTo"]
        params << "from_list_id=" + URLEncoder.encode(fromListId, encoding)
        params << "to_list_id=" + URLEncoder.encode(toListId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Postpones a task 1 day
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.postpone.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksPostpone(final String listId, final String taskseriesId,
            final String taskId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.postpone"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Removes tags from an existing task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param tags comma separated list of tags to remove
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.removeTags.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksRemoveTags(final String listId, final String taskseriesId,
            final String taskId, final String tags) throws GroovyRtmException {
        def params = ["method=rtm.tasks.removeTags"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        params << "tags=" + URLEncoder.encode(tags, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the due date of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param due (optional) Due date for a task, in ISO 8601 format. If parse is specified and has a value of 1, due is parsed as per rtm.time.parse. due is parsed in the context of the user's Remember The Milk timezone.
     * @param hasDueTime (optional) true if this date has a time associated with it. Default is false
     * @param parse (optional) Specifies whether to parse due with rtm.time.parse. Default is false
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setDueDate.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetDueDate(final String listId, final String taskseriesId,
            final String taskId, final String due = null, boolean hasDueTime = false, boolean parse = false) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setDueDate"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (due) params << "due=" + URLEncoder.encode(due, encoding)
        if (hasDueTime) params << "has_due_time=1"
        if (parse) params << "parse=1"
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the estimate of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param estimate (optional) The time estimate for a task. Specified in units of days, hours or minutes. If left as <code>null</code>, any existing time estimate will be unset.
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setEstimate.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetEstimate(final String listId, final String taskseriesId,
            final String taskId, final String estimate = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setEstimate"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (estimate) params << "estimate=" + URLEncoder.encode(estimate, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the location of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param locationId (optional) The id of a location. If left as <code>null</code>, any existing location will be unset.
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setLocation.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetLocation(final String listId, final String taskseriesId,
            final String taskId, final String locationId = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setLocation"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (locationId) params << "location_id=" + URLEncoder.encode(locationId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the name of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param name new name for the task
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setName.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetName(final String listId, final String taskseriesId,
            final String taskId, final String name) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setName"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        params << "name=" + URLEncoder.encode(name, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the priority of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param priority (optional) "1", "2", "3" or "N". If left as <code>null</code>, priority defaults to "N"
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setPriority.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetPriority(final String listId, final String taskseriesId,
            final String taskId, final String priority = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setPriority"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (priority) params << "priority=" + URLEncoder.encode(priority, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the recurrance of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param repeat (optional) The recurrence pattern for a task. Valid values of repeat are detailed here. An empty value unsets any existing recurrence pattern.
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setRecurrence.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetRecurrence(final String listId, final String taskseriesId,
            final String taskId, final String repeat = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setRecurrence"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (repeat) params << "repeat=" + URLEncoder.encode(repeat, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the tags of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param tags (optional) A comma delimited list of tags. An empty value removes any existing tags.
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setTags.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetTags(final String listId, final String taskseriesId,
            final String taskId, final String tags = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setTags"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (tags) params << "tags=" + URLEncoder.encode(tags, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Sets the URL of a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param url (optional) The URL associated with a task. Valid protocols are http, https, ftp and file. If left empty, any existing URL will be unset
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.setURL.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksSetUrl(final String listId, final String taskseriesId,
            final String taskId, final String url = null) throws GroovyRtmException {
        def params = ["method=rtm.tasks.setURL"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        if (url) params << "url=" + URLEncoder.encode(url, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Un-completes a completed task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @return Map of the modified task's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.uncomplete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Task tasksUncomplete(final String listId, final String taskseriesId,
            final String taskId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.uncomplete"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        parser.parseTask(execTimelineMethod(params))
    }

    /**
     * Adds a new note to a task
     *
     * @param listId ID of the list containing the task to be modified
     * @param taskseriesId ID of the taskseries containing the task to be modified
     * @param taskId ID of the task to be modified
     * @param noteTitle title of the note
     * @param noteText body of the note
     * @return Map of the new note's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.notes.add.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Note tasksNotesAdd(final String listId, final String taskseriesId,
            final String taskId, final String noteTitle, final String noteText) throws GroovyRtmException {
        def params = ["method=rtm.tasks.notes.add"]
        params << "list_id=" + URLEncoder.encode(listId, encoding)
        params << "taskseries_id=" + URLEncoder.encode(taskseriesId, encoding)
        params << "task_id=" + URLEncoder.encode(taskId, encoding)
        params << "note_title=" + URLEncoder.encode(noteTitle, encoding)
        params << "note_text=" + URLEncoder.encode(noteText, encoding)
        parser.parseNote(execTimelineMethod(params))
    }

    /**
     * Adds a new note to a task
     *
     * @param noteId ID of the note
     * @return Returns true if the note was successfully deleted, otherwise false
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.notes.delete.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public boolean tasksNotesDelete(final String noteId) throws GroovyRtmException {
        def params = ["method=rtm.tasks.notes.delete"]
        params << "note_id=" +  URLEncoder.encode(noteId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.@stat.equals('ok') : false
    }

    /**
     * Modifies a note
     *
     * @param noteId ID of the note to be modified
     * @param noteTitle new title of the note
     * @param noteText new body of the note
     * @return Map of the modified note's attributes or <code>null</code> if an error occurred
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.tasks.notes.edit.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Note tasksNotesEdit(final String noteId, final String noteTitle,
            final String noteText) throws GroovyRtmException {
        def params = ["method=rtm.tasks.notes.edit"]
        params << "note_id=" +  URLEncoder.encode(noteId, encoding)
        params << "note_title=" + URLEncoder.encode(noteTitle, encoding)
        params << "note_text=" + URLEncoder.encode(noteText, encoding)
        parser.parseNote(execTimelineMethod(params))
    }

    /**
     * Creates a timeline to allow operations to be undone.
     *
     * @return String with the timeline ID
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.timelines.create.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String timelinesCreate() throws GroovyRtmException {
        def params = ["method=rtm.timelines.create"]
        GPathResult resp = execMethod(params)
        resp.timeline.toString()
    }

    /**
     * Returns the specified time in the desired timezone.
     *
     * @param toTimezone Target timezone. A list of valid timezones can be retrieved with <code>timezonesGetList</code>
     * @param fromTimezone (optional) Originating timezone. Defaults to UTC if left <code>null</code>
     * @param time (optional) Time to convert in ISO 8601 format. Defaults to now
     * @return String containing the ISO 8601 date
     * @see {@link #timezonesGetList() timezonesGetList}
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.time.convert.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String timeConvert(final String toTimezone, final String fromTimezone = null,
            final Date time = null) throws GroovyRtmException {
        def params = ["method=rtm.time.convert"]
        params << "to_timezone=" + URLEncoder.encode(toTimezone, encoding)
        if (fromTimezone) params << "from_timezone=" + URLEncoder.encode(fromTimezone, encoding)
        //FIXME: Format time because it's invalid
        if (time) params << "time=" + URLEncoder.encode(time.toString(), encoding)
        GPathResult resp = execMethod(params)
        return resp ? resp.time.toString() : null
    }

    /**
     * Returns the specified time in the desired timezone.
     *
     * @param text date/time text to parse
     * @param timezoneId (optional) If specified, text is parsed in the context of timezone. A list of valid timezones can be retrieved with <code>timezonesGetList</code>. Defaults to UTC
     * @param europeanFormat (optional) true indicates an European date format (14/02/2006). false indicates an American date format (02/14/2006). This value is used in case a date is ambiguous. Default is false
     * @return String containing the ISO 8601 date
     * @see {@link #timezonesGetList() timezonesGetList}
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.time.parse.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String timeParse(final String text, final String timezoneId = null,
            final Boolean europeanFormat = false) throws GroovyRtmException {
        def params = ["method=rtm.time.parse"]
        params << "text=" + URLEncoder.encode(text, encoding)
        if (timezoneId) params << "timezone=" + URLEncoder.encode(timezoneId, encoding)
        if (europeanFormat) params << "date_format=0"
        GPathResult resp = execMethod(params)
        return resp ? resp.time.toString() : null
    }

    /**
     * Retrieves a List of timezones (id, name, dst, offset, currentOffset)
     *
     * @return List of LinkedHashMaps representing timezones
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.timezones.getList.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public List<Timezone> timezonesGetList() throws GroovyRtmException {
        def params = ["method=rtm.timezones.getList"]
        GPathResult resp
        try {
            resp = execMethod(params)
        } catch (SAXParseException saxpe) {
            //The fixes RTM's bug of cutting off this XML
            //This is just a workaround until the RTM crew fixes their issue
            // FIXME: this may not handle DST
            resp = new XmlSlurper().parseText('<rsp stat="ok"><timezones><timezone id="351" name="Pacific/Apia" dst="0" offset="-39600" current_offset="-39600"/><timezone id="371" name="Pacific/Midway" dst="0" offset="-39600" current_offset="-39600"/><timezone id="373" name="Pacific/Niue" dst="0" offset="-39600" current_offset="-39600"/><timezone id="376" name="Pacific/Pago_Pago" dst="0" offset="-39600" current_offset="-39600"/><timezone id="357" name="Pacific/Fakaofo" dst="0" offset="-36000" current_offset="-36000"/><timezone id="364" name="Pacific/Honolulu" dst="0" offset="-36000" current_offset="-36000"/><timezone id="365" name="Pacific/Johnston" dst="0" offset="-36000" current_offset="-36000"/><timezone id="381" name="Pacific/Rarotonga" dst="0" offset="-36000" current_offset="-36000"/><timezone id="383" name="Pacific/Tahiti" dst="0" offset="-36000" current_offset="-36000"/><timezone id="370" name="Pacific/Marquesas" dst="0" offset="-34200" current_offset="-34200"/><timezone id="53" name="America/Adak" dst="1" offset="-32400" current_offset="-36000"/><timezone id="361" name="Pacific/Gambier" dst="0" offset="-32400" current_offset="-32400"/><timezone id="54" name="America/Anchorage" dst="1" offset="-28800" current_offset="-32400"/><timezone id="119" name="America/Juneau" dst="1" offset="-28800" current_offset="-32400"/><timezone id="142" name="America/Nome" dst="1" offset="-28800" current_offset="-32400"/><timezone id="178" name="America/Yakutat" dst="1" offset="-28800" current_offset="-32400"/><timezone id="378" name="Pacific/Pitcairn" dst="0" offset="-28800" current_offset="-28800"/><timezone id="90" name="America/Dawson" dst="1" offset="-25200" current_offset="-28800"/><timezone id="91" name="America/Dawson_Creek" dst="0" offset="-25200" current_offset="-25200"/><timezone id="110" name="America/Hermosillo" dst="0" offset="-25200" current_offset="-25200"/><timezone id="124" name="America/Los_Angeles" dst="1" offset="-25200" current_offset="-28800"/><timezone id="148" name="America/Phoenix" dst="0" offset="-25200" current_offset="-25200"/><timezone id="172" name="America/Tijuana" dst="1" offset="-25200" current_offset="-28800"/><timezone id="175" name="America/Vancouver" dst="1" offset="-25200" current_offset="-28800"/><timezone id="176" name="America/Whitehorse" dst="1" offset="-25200" current_offset="-28800"/><timezone id="74" name="America/Belize" dst="0" offset="-21600" current_offset="-21600"/><timezone id="77" name="America/Boise" dst="1" offset="-21600" current_offset="-25200"/><timezone id="78" name="America/Cambridge_Bay" dst="1" offset="-21600" current_offset="-25200"/><timezone id="85" name="America/Chihuahua" dst="1" offset="-21600" current_offset="-25200"/><timezone id="86" name="America/Costa_Rica" dst="0" offset="-21600" current_offset="-21600"/><timezone id="92" name="America/Denver" dst="1" offset="-21600" current_offset="-25200"/><timezone id="95" name="America/Edmonton" dst="1" offset="-21600" current_offset="-25200"/><timezone id="97" name="America/El_Salvador" dst="0" offset="-21600" current_offset="-21600"/><timezone id="105" name="America/Guatemala" dst="0" offset="-21600" current_offset="-21600"/><timezone id="116" name="America/Inuvik" dst="1" offset="-21600" current_offset="-25200"/><timezone id="127" name="America/Managua" dst="0" offset="-21600" current_offset="-21600"/><timezone id="130" name="America/Mazatlan" dst="1" offset="-21600" current_offset="-25200"/><timezone id="156" name="America/Regina" dst="0" offset="-21600" current_offset="-21600"/><timezone id="162" name="America/Shiprock" dst="1" offset="-21600" current_offset="-25200"/><timezone id="168" name="America/Swift_Current" dst="0" offset="-21600" current_offset="-21600"/><timezone id="169" name="America/Tegucigalpa" dst="0" offset="-21600" current_offset="-21600"/><timezone id="179" name="America/Yellowknife" dst="1" offset="-21600" current_offset="-25200"/><timezone id="354" name="Pacific/Easter" dst="0" offset="-21600" current_offset="-21600"/><timezone id="360" name="Pacific/Galapagos" dst="0" offset="-21600" current_offset="-21600"/><timezone id="404" name="America/Atikokan" dst="0" offset="-18000" current_offset="-18000"/><timezone id="76" name="America/Bogota" dst="0" offset="-18000" current_offset="-18000"/><timezone id="80" name="America/Cancun" dst="1" offset="-18000" current_offset="-21600"/><timezone id="83" name="America/Cayman" dst="0" offset="-18000" current_offset="-18000"/><timezone id="84" name="America/Chicago" dst="1" offset="-18000" current_offset="-21600"/><timezone id="96" name="America/Eirunepe" dst="0" offset="-18000" current_offset="-18000"/><timezone id="106" name="America/Guayaquil" dst="0" offset="-18000" current_offset="-18000"/><timezone id="112" name="America/Indiana/Knox" dst="1" offset="-18000" current_offset="-21600"/><timezone id="118" name="America/Jamaica" dst="0" offset="-18000" current_offset="-18000"/><timezone id="123" name="America/Lima" dst="0" offset="-18000" current_offset="-18000"/><timezone id="131" name="America/Menominee" dst="1" offset="-18000" current_offset="-21600"/><timezone id="132" name="America/Merida" dst="1" offset="-18000" current_offset="-21600"/><timezone id="133" name="America/Mexico_City" dst="1" offset="-18000" current_offset="-21600"/><timezone id="135" name="America/Monterrey" dst="1" offset="-18000" current_offset="-21600"/><timezone id="144" name="America/North_Dakota/Center" dst="1" offset="-18000" current_offset="-21600"/><timezone id="405" name="America/North_Dakota/New_Salem" dst="1" offset="-18000" current_offset="-21600"/><timezone id="145" name="America/Panama" dst="0" offset="-18000" current_offset="-18000"/><timezone id="149" name="America/Port-au-Prince" dst="0" offset="-18000" current_offset="-18000"/><timezone id="153" name="America/Rainy_River" dst="1" offset="-18000" current_offset="-21600"/><timezone id="154" name="America/Rankin_Inlet" dst="1" offset="-18000" current_offset="-21600"/><timezone id="157" name="America/Rio_Branco" dst="0" offset="-18000" current_offset="-18000"/><timezone id="177" name="America/Winnipeg" dst="1" offset="-18000" current_offset="-21600"/><timezone id="81" name="America/Caracas" dst="0" offset="-16200" current_offset="-16200"/><timezone id="55" name="America/Anguilla" dst="0" offset="-14400" current_offset="-14400"/><timezone id="56" name="America/Antigua" dst="0" offset="-14400" current_offset="-14400"/><timezone id="69" name="America/Aruba" dst="0" offset="-14400" current_offset="-14400"/><timezone id="70" name="America/Asuncion" dst="0" offset="-14400" current_offset="-14400"/><timezone id="72" name="America/Barbados" dst="0" offset="-14400" current_offset="-14400"/><timezone id="403" name="America/Blanc-Sablon" dst="0" offset="-14400" current_offset="-14400"/><timezone id="75" name="America/Boa_Vista" dst="0" offset="-14400" current_offset="-14400"/><timezone id="79" name="America/Campo_Grande" dst="0" offset="-14400" current_offset="-14400"/><timezone id="87" name="America/Cuiaba" dst="0" offset="-14400" current_offset="-14400"/><timezone id="88" name="America/Curacao" dst="0" offset="-14400" current_offset="-14400"/><timezone id="93" name="America/Detroit" dst="1" offset="-14400" current_offset="-18000"/><timezone id="94" name="America/Dominica" dst="0" offset="-14400" current_offset="-14400"/><timezone id="102" name="America/Grand_Turk" dst="1" offset="-14400" current_offset="-18000"/><timezone id="103" name="America/Grenada" dst="0" offset="-14400" current_offset="-14400"/><timezone id="104" name="America/Guadeloupe" dst="0" offset="-14400" current_offset="-14400"/><timezone id="107" name="America/Guyana" dst="0" offset="-14400" current_offset="-14400"/><timezone id="109" name="America/Havana" dst="1" offset="-14400" current_offset="-18000"/><timezone id="111" name="America/Indiana/Indianapolis" dst="1" offset="-14400" current_offset="-18000"/><timezone id="113" name="America/Indiana/Marengo" dst="1" offset="-14400" current_offset="-18000"/><timezone id="392" name="America/Indiana/Petersburg" dst="1" offset="-14400" current_offset="-18000"/><timezone id="114" name="America/Indiana/Vevay" dst="1" offset="-14400" current_offset="-18000"/><timezone id="393" name="America/Indiana/Vincennes" dst="1" offset="-14400" current_offset="-18000"/><timezone id="117" name="America/Iqaluit" dst="1" offset="-14400" current_offset="-18000"/><timezone id="120" name="America/Kentucky/Louisville" dst="1" offset="-14400" current_offset="-18000"/><timezone id="121" name="America/Kentucky/Monticello" dst="1" offset="-14400" current_offset="-18000"/><timezone id="122" name="America/La_Paz" dst="0" offset="-14400" current_offset="-14400"/><timezone id="128" name="America/Manaus" dst="0" offset="-14400" current_offset="-14400"/><timezone id="129" name="America/Martinique" dst="0" offset="-14400" current_offset="-14400"/><timezone id="137" name="America/Montreal" dst="1" offset="-14400" current_offset="-18000"/><timezone id="138" name="America/Montserrat" dst="0" offset="-14400" current_offset="-14400"/><timezone id="139" name="America/Nassau" dst="1" offset="-14400" current_offset="-18000"/><timezone id="140" name="America/New_York" dst="1" offset="-14400" current_offset="-18000"/><timezone id="141" name="America/Nipigon" dst="1" offset="-14400" current_offset="-18000"/><timezone id="146" name="America/Pangnirtung" dst="1" offset="-14400" current_offset="-18000"/><timezone id="150" name="America/Port_of_Spain" dst="0" offset="-14400" current_offset="-14400"/><timezone id="151" name="America/Porto_Velho" dst="0" offset="-14400" current_offset="-14400"/><timezone id="152" name="America/Puerto_Rico" dst="0" offset="-14400" current_offset="-14400"/><timezone id="158" name="America/Santiago" dst="0" offset="-14400" current_offset="-14400"/><timezone id="159" name="America/Santo_Domingo" dst="0" offset="-14400" current_offset="-14400"/><timezone id="164" name="America/St_Kitts" dst="0" offset="-14400" current_offset="-14400"/><timezone id="165" name="America/St_Lucia" dst="0" offset="-14400" current_offset="-14400"/><timezone id="166" name="America/St_Thomas" dst="0" offset="-14400" current_offset="-14400"/><timezone id="167" name="America/St_Vincent" dst="0" offset="-14400" current_offset="-14400"/><timezone id="171" name="America/Thunder_Bay" dst="1" offset="-14400" current_offset="-18000"/><timezone id="173" name="America/Toronto" dst="1" offset="-14400" current_offset="-18000"/><timezone id="174" name="America/Tortola" dst="0" offset="-14400" current_offset="-14400"/><timezone id="185" name="Antarctica/Palmer" dst="0" offset="-14400" current_offset="-14400"/><timezone id="277" name="Atlantic/Stanley" dst="0" offset="-14400" current_offset="-14400"/><timezone id="57" name="America/Araguaina" dst="0" offset="-10800" current_offset="-10800"/><timezone id="58" name="America/Argentina/Buenos_Aires" dst="0" offset="-10800" current_offset="-10800"/><timezone id="59" name="America/Argentina/Catamarca" dst="0" offset="-10800" current_offset="-10800"/><timezone id="61" name="America/Argentina/Cordoba" dst="0" offset="-10800" current_offset="-10800"/><timezone id="62" name="America/Argentina/Jujuy" dst="0" offset="-10800" current_offset="-10800"/><timezone id="63" name="America/Argentina/La_Rioja" dst="0" offset="-10800" current_offset="-10800"/><timezone id="64" name="America/Argentina/Mendoza" dst="0" offset="-10800" current_offset="-10800"/><timezone id="65" name="America/Argentina/Rio_Gallegos" dst="0" offset="-10800" current_offset="-10800"/><timezone id="66" name="America/Argentina/San_Juan" dst="0" offset="-10800" current_offset="-10800"/><timezone id="67" name="America/Argentina/Tucuman" dst="0" offset="-10800" current_offset="-10800"/><timezone id="68" name="America/Argentina/Ushuaia" dst="0" offset="-10800" current_offset="-10800"/><timezone id="71" name="America/Bahia" dst="0" offset="-10800" current_offset="-10800"/><timezone id="73" name="America/Belem" dst="0" offset="-10800" current_offset="-10800"/><timezone id="82" name="America/Cayenne" dst="0" offset="-10800" current_offset="-10800"/><timezone id="98" name="America/Fortaleza" dst="0" offset="-10800" current_offset="-10800"/><timezone id="99" name="America/Glace_Bay" dst="1" offset="-10800" current_offset="-14400"/><timezone id="101" name="America/Goose_Bay" dst="1" offset="-10800" current_offset="-14400"/><timezone id="108" name="America/Halifax" dst="1" offset="-10800" current_offset="-14400"/><timezone id="126" name="America/Maceio" dst="0" offset="-10800" current_offset="-10800"/><timezone id="394" name="America/Moncton" dst="1" offset="-10800" current_offset="-14400"/><timezone id="136" name="America/Montevideo" dst="0" offset="-10800" current_offset="-10800"/><timezone id="147" name="America/Paramaribo" dst="0" offset="-10800" current_offset="-10800"/><timezone id="155" name="America/Recife" dst="0" offset="-10800" current_offset="-10800"/><timezone id="160" name="America/Sao_Paulo" dst="0" offset="-10800" current_offset="-10800"/><timezone id="170" name="America/Thule" dst="1" offset="-10800" current_offset="-14400"/><timezone id="186" name="Antarctica/Rothera" dst="0" offset="-10800" current_offset="-10800"/><timezone id="268" name="Atlantic/Bermuda" dst="1" offset="-10800" current_offset="-14400"/><timezone id="163" name="America/St_Johns" dst="1" offset="-9000" current_offset="-12600"/><timezone id="100" name="America/Godthab" dst="1" offset="-7200" current_offset="-10800"/><timezone id="134" name="America/Miquelon" dst="1" offset="-7200" current_offset="-10800"/><timezone id="143" name="America/Noronha" dst="0" offset="-7200" current_offset="-7200"/><timezone id="275" name="Atlantic/South_Georgia" dst="0" offset="-7200" current_offset="-7200"/><timezone id="270" name="Atlantic/Cape_Verde" dst="0" offset="-3600" current_offset="-3600"/><timezone id="1" name="Africa/Abidjan" dst="0" offset="0" current_offset="0"/><timezone id="2" name="Africa/Accra" dst="0" offset="0" current_offset="0"/><timezone id="6" name="Africa/Bamako" dst="0" offset="0" current_offset="0"/><timezone id="8" name="Africa/Banjul" dst="0" offset="0" current_offset="0"/><timezone id="9" name="Africa/Bissau" dst="0" offset="0" current_offset="0"/><timezone id="14" name="Africa/Casablanca" dst="0" offset="0" current_offset="0"/><timezone id="16" name="Africa/Conakry" dst="0" offset="0" current_offset="0"/><timezone id="17" name="Africa/Dakar" dst="0" offset="0" current_offset="0"/><timezone id="21" name="Africa/El_Aaiun" dst="0" offset="0" current_offset="0"/><timezone id="22" name="Africa/Freetown" dst="0" offset="0" current_offset="0"/><timezone id="32" name="Africa/Lome" dst="0" offset="0" current_offset="0"/><timezone id="41" name="Africa/Monrovia" dst="0" offset="0" current_offset="0"/><timezone id="45" name="Africa/Nouakchott" dst="0" offset="0" current_offset="0"/><timezone id="46" name="Africa/Ouagadougou" dst="0" offset="0" current_offset="0"/><timezone id="48" name="Africa/Sao_Tome" dst="0" offset="0" current_offset="0"/><timezone id="89" name="America/Danmarkshavn" dst="0" offset="0" current_offset="0"/><timezone id="161" name="America/Scoresbysund" dst="1" offset="0" current_offset="-3600"/><timezone id="267" name="Atlantic/Azores" dst="1" offset="0" current_offset="-3600"/><timezone id="274" name="Atlantic/Reykjavik" dst="0" offset="0" current_offset="0"/><timezone id="276" name="Atlantic/St_Helena" dst="0" offset="0" current_offset="0"/><timezone id="4" name="Africa/Algiers" dst="0" offset="3600" current_offset="3600"/><timezone id="7" name="Africa/Bangui" dst="0" offset="3600" current_offset="3600"/><timezone id="11" name="Africa/Brazzaville" dst="0" offset="3600" current_offset="3600"/><timezone id="20" name="Africa/Douala" dst="0" offset="3600" current_offset="3600"/><timezone id="29" name="Africa/Kinshasa" dst="0" offset="3600" current_offset="3600"/><timezone id="30" name="Africa/Lagos" dst="0" offset="3600" current_offset="3600"/><timezone id="31" name="Africa/Libreville" dst="0" offset="3600" current_offset="3600"/><timezone id="33" name="Africa/Luanda" dst="0" offset="3600" current_offset="3600"/><timezone id="36" name="Africa/Malabo" dst="0" offset="3600" current_offset="3600"/><timezone id="43" name="Africa/Ndjamena" dst="0" offset="3600" current_offset="3600"/><timezone id="44" name="Africa/Niamey" dst="0" offset="3600" current_offset="3600"/><timezone id="47" name="Africa/Porto-Novo" dst="0" offset="3600" current_offset="3600"/><timezone id="52" name="Africa/Windhoek" dst="0" offset="3600" current_offset="3600"/><timezone id="269" name="Atlantic/Canary" dst="1" offset="3600" current_offset="0"/><timezone id="401" name="Atlantic/Faroe" dst="1" offset="3600" current_offset="0"/><timezone id="273" name="Atlantic/Madeira" dst="1" offset="3600" current_offset="0"/><timezone id="300" name="Europe/Dublin" dst="1" offset="3600" current_offset="0"/><timezone id="399" name="Europe/Guernsey" dst="1" offset="3600" current_offset="0"/><timezone id="400" name="Europe/Isle_of_Man" dst="1" offset="3600" current_offset="0"/><timezone id="402" name="Europe/Jersey" dst="1" offset="3600" current_offset="0"/><timezone id="306" name="Europe/Lisbon" dst="1" offset="3600" current_offset="0"/><timezone id="308" name="Europe/London" dst="1" offset="3600" current_offset="0"/><timezone id="10" name="Africa/Blantyre" dst="0" offset="7200" current_offset="7200"/><timezone id="12" name="Africa/Bujumbura" dst="0" offset="7200" current_offset="7200"/><timezone id="15" name="Africa/Ceuta" dst="1" offset="7200" current_offset="3600"/><timezone id="23" name="Africa/Gaborone" dst="0" offset="7200" current_offset="7200"/><timezone id="24" name="Africa/Harare" dst="0" offset="7200" current_offset="7200"/><timezone id="25" name="Africa/Johannesburg" dst="0" offset="7200" current_offset="7200"/><timezone id="28" name="Africa/Kigali" dst="0" offset="7200" current_offset="7200"/><timezone id="34" name="Africa/Lubumbashi" dst="0" offset="7200" current_offset="7200"/><timezone id="35" name="Africa/Lusaka" dst="0" offset="7200" current_offset="7200"/><timezone id="37" name="Africa/Maputo" dst="0" offset="7200" current_offset="7200"/><timezone id="38" name="Africa/Maseru" dst="0" offset="7200" current_offset="7200"/><timezone id="39" name="Africa/Mbabane" dst="0" offset="7200" current_offset="7200"/><timezone id="50" name="Africa/Tripoli" dst="0" offset="7200" current_offset="7200"/><timezone id="51" name="Africa/Tunis" dst="1" offset="7200" current_offset="3600"/><timezone id="190" name="Arctic/Longyearbyen" dst="1" offset="7200" current_offset="3600"/><timezone id="272" name="Atlantic/Jan_Mayen" dst="1" offset="7200" current_offset="3600"/><timezone id="288" name="Europe/Amsterdam" dst="1" offset="7200" current_offset="3600"/><timezone id="289" name="Europe/Andorra" dst="1" offset="7200" current_offset="3600"/><timezone id="292" name="Europe/Belgrade" dst="1" offset="7200" current_offset="3600"/><timezone id="293" name="Europe/Berlin" dst="1" offset="7200" current_offset="3600"/><timezone id="294" name="Europe/Bratislava" dst="1" offset="7200" current_offset="3600"/><timezone id="295" name="Europe/Brussels" dst="1" offset="7200" current_offset="3600"/><timezone id="297" name="Europe/Budapest" dst="1" offset="7200" current_offset="3600"/><timezone id="299" name="Europe/Copenhagen" dst="1" offset="7200" current_offset="3600"/><timezone id="301" name="Europe/Gibraltar" dst="1" offset="7200" current_offset="3600"/><timezone id="307" name="Europe/Ljubljana" dst="1" offset="7200" current_offset="3600"/><timezone id="309" name="Europe/Luxembourg" dst="1" offset="7200" current_offset="3600"/><timezone id="310" name="Europe/Madrid" dst="1" offset="7200" current_offset="3600"/><timezone id="311" name="Europe/Malta" dst="1" offset="7200" current_offset="3600"/><timezone id="314" name="Europe/Monaco" dst="1" offset="7200" current_offset="3600"/><timezone id="317" name="Europe/Oslo" dst="1" offset="7200" current_offset="3600"/><timezone id="318" name="Europe/Paris" dst="1" offset="7200" current_offset="3600"/><timezone id="398" name="Europe/Podgorica" dst="1" offset="7200" current_offset="3600"/><timezone id="319" name="Europe/Prague" dst="1" offset="7200" current_offset="3600"/><timezone id="321" name="Europe/Rome" dst="1" offset="7200" current_offset="3600"/><timezone id="323" name="Europe/San_Marino" dst="1" offset="7200" current_offset="3600"/><timezone id="324" name="Europe/Sarajevo" dst="1" offset="7200" current_offset="3600"/><timezone id="326" name="Europe/Skopje" dst="1" offset="7200" current_offset="3600"/><timezone id="328" name="Europe/Stockholm" dst="1" offset="7200" current_offset="3600"/><timezone id="330" name="Europe/Tirane" dst="1" offset="7200" current_offset="3600"/><timezone id="332" name="Europe/Vaduz" dst="1" offset="7200" current_offset="3600"/><timezone id="333" name="Europe/Vatican" dst="1" offset="7200" current_offset="3600"/><timezone id="334" name="Europe/Vienna" dst="1" offset="7200" current_offset="3600"/><timezone id="336" name="Europe/Warsaw" dst="1" offset="7200" current_offset="3600"/><timezone id="337" name="Europe/Zagreb" dst="1" offset="7200" current_offset="3600"/><timezone id="339" name="Europe/Zurich" dst="1" offset="7200" current_offset="3600"/><timezone id="3" name="Africa/Addis_Ababa" dst="0" offset="10800" current_offset="10800"/><timezone id="396" name="Africa/Asmara" dst="0" offset="10800" current_offset="10800"/><timezone id="13" name="Africa/Cairo" dst="1" offset="10800" current_offset="7200"/><timezone id="18" name="Africa/Dar_es_Salaam" dst="0" offset="10800" current_offset="10800"/><timezone id="19" name="Africa/Djibouti" dst="0" offset="10800" current_offset="10800"/><timezone id="26" name="Africa/Kampala" dst="0" offset="10800" current_offset="10800"/><timezone id="27" name="Africa/Khartoum" dst="0" offset="10800" current_offset="10800"/><timezone id="40" name="Africa/Mogadishu" dst="0" offset="10800" current_offset="10800"/><timezone id="42" name="Africa/Nairobi" dst="0" offset="10800" current_offset="10800"/><timezone id="188" name="Antarctica/Syowa" dst="0" offset="10800" current_offset="10800"/><timezone id="191" name="Asia/Aden" dst="0" offset="10800" current_offset="10800"/><timezone id="193" name="Asia/Amman" dst="1" offset="10800" current_offset="7200"/><timezone id="198" name="Asia/Baghdad" dst="0" offset="10800" current_offset="10800"/><timezone id="199" name="Asia/Bahrain" dst="0" offset="10800" current_offset="10800"/><timezone id="202" name="Asia/Beirut" dst="1" offset="10800" current_offset="7200"/><timezone id="209" name="Asia/Damascus" dst="1" offset="10800" current_offset="7200"/><timezone id="214" name="Asia/Gaza" dst="1" offset="10800" current_offset="7200"/><timezone id="219" name="Asia/Istanbul" dst="1" offset="10800" current_offset="7200"/><timezone id="222" name="Asia/Jerusalem" dst="1" offset="10800" current_offset="7200"/><timezone id="231" name="Asia/Kuwait" dst="0" offset="10800" current_offset="10800"/><timezone id="237" name="Asia/Nicosia" dst="1" offset="10800" current_offset="7200"/><timezone id="244" name="Asia/Qatar" dst="0" offset="10800" current_offset="10800"/><timezone id="247" name="Asia/Riyadh" dst="0" offset="10800" current_offset="10800"/><timezone id="290" name="Europe/Athens" dst="1" offset="10800" current_offset="7200"/><timezone id="296" name="Europe/Bucharest" dst="1" offset="10800" current_offset="7200"/><timezone id="298" name="Europe/Chisinau" dst="1" offset="10800" current_offset="7200"/><timezone id="302" name="Europe/Helsinki" dst="1" offset="10800" current_offset="7200"/><timezone id="303" name="Europe/Istanbul" dst="1" offset="10800" current_offset="7200"/><timezone id="304" name="Europe/Kaliningrad" dst="1" offset="10800" current_offset="7200"/><timezone id="305" name="Europe/Kiev" dst="1" offset="10800" current_offset="7200"/><timezone id="312" name="Europe/Mariehamn" dst="1" offset="10800" current_offset="7200"/><timezone id="313" name="Europe/Minsk" dst="1" offset="10800" current_offset="7200"/><timezone id="316" name="Europe/Nicosia" dst="1" offset="10800" current_offset="7200"/><timezone id="320" name="Europe/Riga" dst="1" offset="10800" current_offset="7200"/><timezone id="325" name="Europe/Simferopol" dst="1" offset="10800" current_offset="7200"/><timezone id="327" name="Europe/Sofia" dst="1" offset="10800" current_offset="7200"/><timezone id="329" name="Europe/Tallinn" dst="1" offset="10800" current_offset="7200"/><timezone id="331" name="Europe/Uzhgorod" dst="1" offset="10800" current_offset="7200"/><timezone id="335" name="Europe/Vilnius" dst="1" offset="10800" current_offset="7200"/><timezone id="338" name="Europe/Zaporozhye" dst="1" offset="10800" current_offset="7200"/><timezone id="340" name="Indian/Antananarivo" dst="0" offset="10800" current_offset="10800"/><timezone id="344" name="Indian/Comoro" dst="0" offset="10800" current_offset="10800"/><timezone id="349" name="Indian/Mayotte" dst="0" offset="10800" current_offset="10800"/><timezone id="212" name="Asia/Dubai" dst="0" offset="14400" current_offset="14400"/><timezone id="236" name="Asia/Muscat" dst="0" offset="14400" current_offset="14400"/><timezone id="256" name="Asia/Tbilisi" dst="0" offset="14400" current_offset="14400"/><timezone id="315" name="Europe/Moscow" dst="1" offset="14400" current_offset="10800"/><timezone id="397" name="Europe/Volgograd" dst="1" offset="14400" current_offset="10800"/><timezone id="346" name="Indian/Mahe" dst="0" offset="14400" current_offset="14400"/><timezone id="348" name="Indian/Mauritius" dst="0" offset="14400" current_offset="14400"/><timezone id="350" name="Indian/Reunion" dst="0" offset="14400" current_offset="14400"/><timezone id="223" name="Asia/Kabul" dst="0" offset="16200" current_offset="16200"/><timezone id="257" name="Asia/Tehran" dst="1" offset="16200" current_offset="12600"/><timezone id="195" name="Asia/Aqtau" dst="0" offset="18000" current_offset="18000"/><timezone id="196" name="Asia/Aqtobe" dst="0" offset="18000" current_offset="18000"/><timezone id="197" name="Asia/Ashgabat" dst="0" offset="18000" current_offset="18000"/><timezone id="200" name="Asia/Baku" dst="1" offset="18000" current_offset="14400"/><timezone id="213" name="Asia/Dushanbe" dst="0" offset="18000" current_offset="18000"/><timezone id="225" name="Asia/Karachi" dst="0" offset="18000" current_offset="18000"/><timezone id="240" name="Asia/Oral" dst="0" offset="18000" current_offset="18000"/><timezone id="250" name="Asia/Samarkand" dst="0" offset="18000" current_offset="18000"/><timezone id="255" name="Asia/Tashkent" dst="0" offset="18000" current_offset="18000"/><timezone id="266" name="Asia/Yerevan" dst="1" offset="18000" current_offset="14400"/><timezone id="322" name="Europe/Samara" dst="1" offset="18000" current_offset="14400"/><timezone id="345" name="Indian/Kerguelen" dst="0" offset="18000" current_offset="18000"/><timezone id="347" name="Indian/Maldives" dst="0" offset="18000" current_offset="18000"/><timezone id="205" name="Asia/Calcutta" dst="0" offset="19800" current_offset="19800"/><timezone id="208" name="Asia/Colombo" dst="0" offset="19800" current_offset="19800"/><timezone id="227" name="Asia/Katmandu" dst="0" offset="20700" current_offset="20700"/><timezone id="183" name="Antarctica/Mawson" dst="0" offset="21600" current_offset="21600"/><timezone id="189" name="Antarctica/Vostok" dst="0" offset="21600" current_offset="21600"/><timezone id="192" name="Asia/Almaty" dst="0" offset="21600" current_offset="21600"/><timezone id="203" name="Asia/Bishkek" dst="0" offset="21600" current_offset="21600"/><timezone id="210" name="Asia/Dhaka" dst="0" offset="21600" current_offset="21600"/><timezone id="245" name="Asia/Qyzylorda" dst="0" offset="21600" current_offset="21600"/><timezone id="258" name="Asia/Thimphu" dst="0" offset="21600" current_offset="21600"/><timezone id="265" name="Asia/Yekaterinburg" dst="1" offset="21600" current_offset="18000"/><timezone id="341" name="Indian/Chagos" dst="0" offset="21600" current_offset="21600"/><timezone id="246" name="Asia/Rangoon" dst="0" offset="23400" current_offset="23400"/><timezone id="343" name="Indian/Cocos" dst="0" offset="23400" current_offset="23400"/><timezone id="181" name="Antarctica/Davis" dst="0" offset="25200" current_offset="25200"/><timezone id="201" name="Asia/Bangkok" dst="0" offset="25200" current_offset="25200"/><timezone id="217" name="Asia/Hovd" dst="0" offset="25200" current_offset="25200"/><timezone id="220" name="Asia/Jakarta" dst="0" offset="25200" current_offset="25200"/><timezone id="238" name="Asia/Novosibirsk" dst="1" offset="25200" current_offset="21600"/><timezone id="239" name="Asia/Omsk" dst="1" offset="25200" current_offset="21600"/><timezone id="241" name="Asia/Phnom_Penh" dst="0" offset="25200" current_offset="25200"/><timezone id="242" name="Asia/Pontianak" dst="0" offset="25200" current_offset="25200"/><timezone id="248" name="Asia/Saigon" dst="0" offset="25200" current_offset="25200"/><timezone id="262" name="Asia/Vientiane" dst="0" offset="25200" current_offset="25200"/><timezone id="342" name="Indian/Christmas" dst="0" offset="25200" current_offset="25200"/><timezone id="180" name="Antarctica/Casey" dst="0" offset="28800" current_offset="28800"/><timezone id="204" name="Asia/Brunei" dst="0" offset="28800" current_offset="28800"/><timezone id="207" name="Asia/Chongqing" dst="0" offset="28800" current_offset="28800"/><timezone id="215" name="Asia/Harbin" dst="0" offset="28800" current_offset="28800"/><timezone id="216" name="Asia/Hong_Kong" dst="0" offset="28800" current_offset="28800"/><timezone id="226" name="Asia/Kashgar" dst="0" offset="28800" current_offset="28800"/><timezone id="228" name="Asia/Krasnoyarsk" dst="1" offset="28800" current_offset="25200"/><timezone id="229" name="Asia/Kuala_Lumpur" dst="0" offset="28800" current_offset="28800"/><timezone id="230" name="Asia/Kuching" dst="0" offset="28800" current_offset="28800"/><timezone id="232" name="Asia/Macau" dst="0" offset="28800" current_offset="28800"/><timezone id="234" name="Asia/Makassar" dst="0" offset="28800" current_offset="28800"/><timezone id="235" name="Asia/Manila" dst="0" offset="28800" current_offset="28800"/><timezone id="252" name="Asia/Shanghai" dst="0" offset="28800" current_offset="28800"/><timezone id="253" name="Asia/Singapore" dst="0" offset="28800" current_offset="28800"/><timezone id="254" name="Asia/Taipei" dst="0" offset="28800" current_offset="28800"/><timezone id="260" name="Asia/Ulaanbaatar" dst="0" offset="28800" current_offset="28800"/><timezone id="261" name="Asia/Urumqi" dst="0" offset="28800" current_offset="28800"/><timezone id="286" name="Australia/Perth" dst="0" offset="28800" current_offset="28800"/><timezone id="395" name="Australia/Eucla" dst="0" offset="31500" current_offset="31500"/><timezone id="206" name="Asia/Choibalsan" dst="0" offset="32400" current_offset="32400"/><timezone id="211" name="Asia/Dili" dst="0" offset="32400" current_offset="32400"/><timezone id="218" name="Asia/Irkutsk" dst="1" offset="32400" current_offset="28800"/><timezone id="221" name="Asia/Jayapura" dst="0" offset="32400" current_offset="32400"/><timezone id="243" name="Asia/Pyongyang" dst="0" offset="32400" current_offset="32400"/><timezone id="251" name="Asia/Seoul" dst="0" offset="32400" current_offset="32400"/><timezone id="259" name="Asia/Tokyo" dst="0" offset="32400" current_offset="32400"/><timezone id="377" name="Pacific/Palau" dst="0" offset="32400" current_offset="32400"/><timezone id="278" name="Australia/Adelaide" dst="0" offset="34200" current_offset="34200"/><timezone id="280" name="Australia/Broken_Hill" dst="0" offset="34200" current_offset="34200"/><timezone id="281" name="Australia/Darwin" dst="0" offset="34200" current_offset="34200"/><timezone id="182" name="Antarctica/DumontDUrville" dst="0" offset="36000" current_offset="36000"/><timezone id="264" name="Asia/Yakutsk" dst="1" offset="36000" current_offset="32400"/><timezone id="279" name="Australia/Brisbane" dst="0" offset="36000" current_offset="36000"/><timezone id="391" name="Australia/Currie" dst="0" offset="36000" current_offset="36000"/><timezone id="282" name="Australia/Hobart" dst="0" offset="36000" current_offset="36000"/><timezone id="283" name="Australia/Lindeman" dst="0" offset="36000" current_offset="36000"/><timezone id="285" name="Australia/Melbourne" dst="0" offset="36000" current_offset="36000"/><timezone id="287" name="Australia/Sydney" dst="0" offset="36000" current_offset="36000"/><timezone id="363" name="Pacific/Guam" dst="0" offset="36000" current_offset="36000"/><timezone id="380" name="Pacific/Port_Moresby" dst="0" offset="36000" current_offset="36000"/><timezone id="382" name="Pacific/Saipan" dst="0" offset="36000" current_offset="36000"/><timezone id="386" name="Pacific/Truk" dst="0" offset="36000" current_offset="36000"/><timezone id="284" name="Australia/Lord_Howe" dst="0" offset="37800" current_offset="37800"/><timezone id="249" name="Asia/Sakhalin" dst="1" offset="39600" current_offset="36000"/><timezone id="263" name="Asia/Vladivostok" dst="1" offset="39600" current_offset="36000"/><timezone id="355" name="Pacific/Efate" dst="0" offset="39600" current_offset="39600"/><timezone id="362" name="Pacific/Guadalcanal" dst="0" offset="39600" current_offset="39600"/><timezone id="367" name="Pacific/Kosrae" dst="0" offset="39600" current_offset="39600"/><timezone id="375" name="Pacific/Noumea" dst="0" offset="39600" current_offset="39600"/><timezone id="379" name="Pacific/Ponape" dst="0" offset="39600" current_offset="39600"/><timezone id="374" name="Pacific/Norfolk" dst="0" offset="41400" current_offset="41400"/><timezone id="184" name="Antarctica/McMurdo" dst="0" offset="43200" current_offset="43200"/><timezone id="187" name="Antarctica/South_Pole" dst="0" offset="43200" current_offset="43200"/><timezone id="233" name="Asia/Magadan" dst="1" offset="43200" current_offset="39600"/><timezone id="352" name="Pacific/Auckland" dst="0" offset="43200" current_offset="43200"/><timezone id="358" name="Pacific/Fiji" dst="0" offset="43200" current_offset="43200"/><timezone id="359" name="Pacific/Funafuti" dst="0" offset="43200" current_offset="43200"/><timezone id="368" name="Pacific/Kwajalein" dst="0" offset="43200" current_offset="43200"/><timezone id="369" name="Pacific/Majuro" dst="0" offset="43200" current_offset="43200"/><timezone id="372" name="Pacific/Nauru" dst="0" offset="43200" current_offset="43200"/><timezone id="384" name="Pacific/Tarawa" dst="0" offset="43200" current_offset="43200"/><timezone id="387" name="Pacific/Wake" dst="0" offset="43200" current_offset="43200"/><timezone id="388" name="Pacific/Wallis" dst="0" offset="43200" current_offset="43200"/><timezone id="353" name="Pacific/Chatham" dst="0" offset="45900" current_offset="45900"/><timezone id="194" name="Asia/Anadyr" dst="1" offset="46800" current_offset="43200"/><timezone id="224" name="Asia/Kamchatka" dst="1" offset="46800" current_offset="43200"/><timezone id="356" name="Pacific/Enderbury" dst="0" offset="46800" current_offset="46800"/><timezone id="385" name="Pacific/Tongatapu" dst="0" offset="46800" current_offset="46800"/><timezone id="366" name="Pacific/Kiritimati" dst="0" offset="50400" current_offset="50400"/></timezones></rsp>')
        }
        parser.parseTimezones(resp)
    }

    /**
     * Retrieves a timezone specified by name
     *
     * @param timezoneName the name of a timezone
     * @return Map containing timezone info for the timezone or <code>null</code>
     * if the timezone could not be found
     * @throws GroovyRtmException when the HTTP request failed
     */
    public Timezone timezonesGetTimezoneByName(final String timezoneName) throws GroovyRtmException {
        List timezones = timezonesGetList()
        timezones.find { it.name.equals(timezoneName) }
    }

    /**
     * Reverts the affects of an action.
     *
     * @param transactionId the ID of a transaction within a timeline
     * @return List of LinkedHashMaps representing timezones
     * @see <a href="http://www.rememberthemilk.com/services/api/methods/rtm.transactions.undo.rtm">REST API Documentation</a>
     * @throws GroovyRtmException when the HTTP request failed
     */
    public boolean transactionsUndo(final String transactionId) throws GroovyRtmException {
        def params = ["method=rtm.transactions.undo"]
        params << "transaction_id=" + URLEncoder.encode(transactionId, encoding)
        GPathResult resp = execTimelineMethod(params)
        return resp ? resp.@stat.equals('ok') : false
    }
}
