package org.eriwen.rtm.test.integration

import org.junit.After
import org.junit.Before
import org.junit.Test

import org.eriwen.rtm.GroovyRtm
import org.eriwen.rtm.model.*

/**
 * Integration test class for <code>org.eriwen.rtm.GroovyRtm</code>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */

class GroovyRtmIntegrationTest {
    private static GroovyRtm instance = null

    @Before void setUp() {
        instance = new GroovyRtm('config/GroovyRtm.properties')
        //NOTE: put your test user here to run the integration tests
        instance.currentUser = '<your test user here>'
    }
    @After void tearDown() {
        instance = null
    }
    
    @Test void testTestEcho() {
        println 'testTestEcho()'
        assert instance.testEcho() : 'echo unsuccessful'
    }

    @Test void testGetAuthUrl() {
        println 'testGetAuthUrl()'
        def authUrl = instance.getAuthUrl()
        assert authUrl instanceof String : 'expected String URL but got a different type'
        assert authUrl : 'expected valid URL but got nothing'
    }

    @Test void testGetNewAuthToken() {
        println 'testGetNewAuthToken()'

        instance.removeAuthToken()
        def authUrl = instance.getAuthUrl()

        //NOTE: This test requires human intervention, because automatic
        //application authorization is not possible
        println 'Please browse to ' + authUrl
        println 'Test will resume in 1 minute, please authorize before that'
        Thread.sleep(60000)

        def loggedInAuthToken = instance.getNewAuthToken()
        assert loggedInAuthToken : 'expected valid auth token but got: ' + loggedInAuthToken
        assert instance.authCheckToken(loggedInAuthToken) : 'User should be logged in but is not'
    }

    @Test void testTestLogin() {
        println 'testTestLogin()'
        boolean success = instance.testLogin()
        assert success : 'login unsuccessful'
    }
    
    @Test void testContactsGetList() {
        println 'testContactsGetList()'
        def contacts = instance.contactsGetList()
        assert contacts instanceof List
    }

    @Test void testContactsAdd() {
        println 'testAddContact()'
        //NOTE: Assumes at least 1 existing contact. This is done to prevent
        //having to store a static user ID here
        def contact = instance.contactsGetList()[0]
        assert instance.contactsDelete(contact['id']) : 'delete contact returned null, expected transactionId'
        assert !(instance.contactsGetList().find{it.username.equals(contact['username'])}) : 'contact should not exist'
        assert instance.contactsAdd(contact['username']) : 'add contact returned null, expected transactionId'
        assert instance.contactsGetList().find{it.username.equals(contact['username'])} : 'contact should exist'
    }

    @Test void testContactsDelete() {
        println 'testDeleteContact()'
        //NOTE: Assumes at least 1 existing contact. 
        def contact = instance.contactsGetList()[0]
        assert instance.contactsDelete(contact['id']) : 'delete contact returned null, expected transactionId'
        assert !(instance.contactsGetList().find{it.username.equals(contact['username'])}) : 'contact should not exist'
        assert instance.contactsAdd(contact['username']) : 'add contact returned null, expected transactionId'
        assert instance.contactsGetList().find{it.username.equals(contact['username'])} : 'contact should exist'
    }

    @Test void testGroupsAdd() {
        println 'testGroupsAdd()'
        String groupName = 'test group add'
        def group = instance.groupsAdd(groupName)
        assert group : 'Expected transactionId returned but got: ' + group
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        assert instance.groupsDelete(groupId.toString())
    }

    @Test void testGroupsAddContact() {
        println 'testGroupsAddContact()'
        String groupName = 'test group add contact'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        //NOTE: Assumes at least 1 existing contact. 
        def contactId = instance.contactsGetList()[0]['id']
        assert instance.groupsAddContact(contactId.toString(), groupId.toString())
        assert instance.groupsDelete(groupId.toString())
    }

    @Test void testGroupsDelete() {
        println 'testGroupsDelete()'
        String groupName = 'test group delete'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        assert instance.groupsDelete(groupId.toString())
    }

    @Test void testGroupsGetGroupByName() {
        println 'testGroupsGetGroupByName()'
        String groupName = 'test group name'
        instance.groupsAdd(groupName)
        group = instance.groupsGetGroupByName(groupName)
        assert group instanceof Map : 'wrong return type'
        assert group.get('id')
        assert group.get('name').equals(groupName)
        assert instance.groupsDelete(group.get('id').toString())
    }

    @Test void testGroupsGetList() {
        println 'testGroupsGetList()'
        def groupList = instance.groupsGetList()
        assert groupList instanceof List : 'expected List returned but got ' + groupList.class.toString()
    }

    @Test void testGroupsRemoveContact() {
        println 'testGroupsRemoveContact()'
        String groupName = 'test group remove contact'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        def contact = instance.contactsGetList()[0]
        assert instance.groupsAddContact(contact['id'].toString(), groupId.toString()) : 'unable to add contact'
        assert instance.groupsRemoveContact(contact['id'].toString(), groupId.toString()) : 'unable to remove contact'
        assert instance.groupsDelete(groupId.toString()) : 'unable to delete group'
    }

    @Test void testListsAdd() {
        println 'testListsAdd()'
        String listName = 'Test List Add'
        def list = instance.listsAdd(listName)
        assert list : 'expected list to be returned but got ' + list
        assert list.id : 'list id is null'
        assert list.name.equals(listName) : 'expected list name of ' + listName + ' but got ' + list.name
        assert !list.smart : 'Expected smart == 0 but got ' + list.smart
        assert instance.listsDelete(list.id)

        def smartList = instance.listsAdd('SmartList', 'priority:1')
        assert smartList : 'expected smart list to be returned but got ' + smartList
        assert smartList.smart : 'Expected smart but got ' + smartList.smart
        assert instance.listsDelete(smartList.id)
    }

    @Test void testListsArchive() {
        println 'testListsArchive()'
        def list = instance.listsAdd('TestArchive')
        assert !list.archived : 'expected list archived == 0 but got ' + list.archived
        list = instance.listsArchive(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert list.archived : 'expected list archived but got ' + list.archived
        assert instance.listsDelete(list.id)
    }

    @Test void testListsDelete() {
        println 'testListsDelete()'
        def list = instance.listsAdd('Test List Delete')
        assert list : 'expected list to be returned but got ' + list
        assert !list.deleted : 'expected list deleted to be 0 but got ' + list.deleted
        list = instance.listsDelete(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert list.deleted : 'expected list deleted to be 1 but got ' + list.deleted
    }

    @Test void testListsGetList() {
        println 'testListsGetList()'
        def lists = instance.listsGetList()
        assert lists instanceof List<TaskList> : 'wrong return type'
        assert lists && lists != 'null' : 'lists cannot be null'
        assert lists.size() > 0 : 'lists cannot be empty'
    }

    @Test void testListsGetListByName() {
        println 'testListsGetListByName()'
        def list = instance.listsGetListByName('Inbox')
        assert list instanceof TaskList : 'wrong return type'
        assert list.id : 'List should have associated ID'
        assert list.name : 'List should have its name'
    }

    @Test void testListsSetDefaultList() {
        println 'testListsSetDefaultList()'
        String listName = 'TestSetDefault'
        def list = instance.listsAdd(listName)
        def result = instance.listsSetDefaultList(list.id)
        assert result && result != 'null' : 'could not set default list to ' + listName
        assert instance.listsDelete(list.id)
    }

    @Test void testListsSetName() {
        println 'testListsSetName()'
        String listName = 'Test Set Name'
        String newListName = 'Test New Name'
        def list = instance.listsAdd(listName)
        assert list.name.equals(listName) : 'expected list name of ' + listName + ' but got ' + list.name
        list = instance.listsSetName(list.id, newListName)
        assert list : 'expected list to be returned but got ' + list
        assert list.name.equals(newListName) : 'expected list name of ' + newListName + ' but got ' + list.name
        assert instance.listsDelete(list.id)
    }

    @Test void testListsUnarchive() {
        println 'testListsUnarchive()'
        def list = instance.listsAdd('TestUnarchive')
        assert instance.listsArchive(list.id)
        list = instance.listsUnarchive(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert !list.archived : 'expected list archived == 0 but got ' + list.archived
        assert instance.listsDelete(list.id)
    }

    @Test void testLocationsGetLocationByName() {
        println 'testLocationsGetLocationByName()'
        // Get location list
        def location = instance.locationsGetList()[0]
        def locationByName = instance.locationsGetLocationByName(location['name'])
        assert locationByName : 'Location should not be null'
        assert locationByName instanceof Map : 'Location object should be a map'
        assert locationByName['id'] == location['id'] : 'Expected ' + location['id'] + " but got " + locationByName['id']
        assert locationByName['name'] == location['name'] : 'Expected ' + location['name'] + " but got " + locationByName['name']
    }

    @Test void testLocationsGetList() {
        println 'testLocationsGetList()'
        def locations = instance.locationsGetList()
        assert locations : 'Locations should not be null'
        assert locations instanceof List
        assert locations[0]['id'] : 'Location ID not present'
        assert locations[0]['name'] : 'Location Name not present'
    }

    @Test void testSettingsGetList() {
        println 'testSettingsGetList()'
        def settings = instance.settingsGetList()
        assert settings
        assert settings instanceof Map
    }

    @Test void testTasksAdd() {
        println 'testTasksAdd()'
        def taskName = 'test tasks add'
        def task = instance.tasksAdd(taskName)
        assert task : 'task could not be added'
        assert task instanceof Task : 'task should be a Task'
        assert task.name.equals(taskName) : 'task name does not match'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSmartAdd() {
        println 'testTasksSmartAdd()'
        def taskName = 'test tasks smart add !2 #coffee'
        def task = instance.tasksAdd(taskName, null, true)
        assert task : 'task could not be added'
        assert task instanceof Task : 'task should be a Task'
        assert task.name.equals('test tasks smart add') : 'task name does not match, got ' + task.name
        assert task.priority.equals('2') : 'task priority should be "2" but got ' + task.priority
        assert task.tags.join(', ').equals('coffee') : 'task should have tag "coffee"'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksAddAllParams() {
        println 'testTasksAddAllParams()'
        def taskName = 'test tasks add all params'
        def taskPriority = '2'
        def taskDue = '07/15/2009'
        def taskEstimate = '10 minutes'
        def taskRepeat = 'every 1 week'
        def taskTags = 'test'
        //NOTE: Assumes at least 1 location exists
        def taskLocationId = instance.locationsGetList()[0].id
        def taskUrl = 'http://eriwen.com'
        def taskListId = instance.listsGetList()[0].id

        def task = instance.tasksAdd(taskName, taskPriority, taskDue, taskEstimate,
            taskRepeat, taskTags, taskLocationId, taskUrl, taskListId)
        assert task : 'task could not be added'
        assert task instanceof Task : 'task should be a Task'

        assert task.name.equals(taskName) : 'Expected task name of ' + taskName + ' but got ' + task.name
        assert task.priority.equals(taskPriority) : 'Expected task priority of ' + taskPriority + ' but got ' + task.priority
        assert !task.due.equals('') : 'expected due date but got ' + task.due
        assert task.estimate.equals(taskEstimate) : 'Expected task estimate of ' + taskEstimate + ' but got ' + task.estimate
        assert task.repeat.equals('FREQ=WEEKLY;INTERVAL=1') : 'Expected task repeat of "FREQ=WEEKLY;INTERVAL=1" but got ' + task.repeat
        assert task.tags.join(', ').equals(taskTags) : 'Expected task tags ' + taskTags + ' but got ' + task.taskTags
        assert task.locationId.equals(taskLocationId) : 'Expected task location ID of ' + taskLocationId + ' but got ' + task.locationId
        assert task.url.equals(taskUrl) : 'Expected task URL of ' + taskUrl + ' but got ' + task.url
        assert task.listId.equals(taskListId) : 'Expected task list ID of ' + taskListId + ' but got ' + task.listId

        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksAddTags() {
        println 'testTasksAddTags()'
        def task = instance.tasksAdd('test tasks add tags')
        assert task : 'task should have been added'
        task = instance.tasksAddTags(task.listId, task.taskSeriesId, task.taskId, 'testing, tasks')
        assert task && task.tags.join(', ').contains('testing') : 'testing tag does not exist'
        assert task.tags.join(', ').contains('tasks') : 'tasks tag does not exist'
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
    }

    @Test void testTasksComplete() {
        println 'testTasksComplete()'
        def task = instance.tasksAdd('test tasks complete')
        assert task && !task.completed : 'expected blank completed but got ' + task.completed
        task = instance.tasksComplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.completed : 'expected completed task but got ' + task.completed
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
    }

    @Test void testTasksDelete() {
        println 'testTasksDelete()'
        def task = instance.tasksAdd('test tasks delete')
        assert task : 'task should have been added'
        assert !task.deleted : 'task should not be deleted yet'
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
        assert task.deleted : 'task should be deleted'
    }

    @Test void testTasksGetList() {
        println 'testTasksGetList()'
        def task = instance.tasksAdd('test tasks get list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        //Get All Tasks
        def tasks = instance.tasksGetList()
        assert tasks : 'tasks should not be null'
        assert tasks instanceof List : 'wrong return type'
        assert tasks.size > 0 : 'Expected tasks length > 0 but got ' + taskList.length
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }
    
    @Test void testTasksGetListFromList() {
        println 'testTasksGetListFromList()'
        def task = instance.tasksAdd('test tasks get list from list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        def list = instance.listsGetListByName('Inbox')
        assert list && list.id : 'could not get list'
        //All tasks in list 'Inbox'
        def taskList = instance.tasksGetList(list.id)
        assert taskList : "Expected non-null task list"
        assert taskList.size > 0 : 'Expected taskList size > 0 but got ' + taskList.length
        assert taskList[0] : 'Expected non-null task'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksGetSmartList() {
        println 'testTasksGetSmartList()'
        def task = instance.tasksAdd('test tasks get smart list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        //Tasks due today
        def taskList = instance.tasksGetList(null, 'due:today')
        assert taskList : "Expected non-null task list"
        assert taskList.size > 0 : 'Expected taskList size > 0 but got ' + taskList.length
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksGetSmartListFromList() {
        println 'testTasksGetSmartListFromList()'
        def task = instance.tasksAdd('test tasks get smart list from list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        //Tasks in Inbox due today
        def list = instance.listsGetListByName('Inbox')
        assert list && list.id : 'could not get list'
        def taskList = instance.tasksGetList(list.id, 'due:today')
        assert taskList : "Expected non-null task list"
        assert taskList.size > 0 : 'Expected taskList size > 0 but got ' + taskList.length
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksMovePriority() {
        println 'testTasksMovePriority()'
        def task = instance.tasksAdd('test tasks move priority')
        assert task && task.priority.equals('N') : 'expected priority N but got ' + task.priority
        task = instance.tasksMovePriority(task.listId, task.taskSeriesId, task.taskId, 'up')
        assert task && task.priority.equals('3') : 'expected priority 3 but got ' + task.priority
        task = instance.tasksMovePriority(task.listId, task.taskSeriesId, task.taskId, false)
        assert task && task.priority.equals('N') : 'expected priority N but got ' + task.priority
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksMoveTo() {
        println 'testTasksMoveTo()'
        def task = instance.tasksAdd('test tasks move')
        //Every account has an Inbox and a Sent list that can't be changed
        def inboxListId = instance.listsGetListByName('Inbox').id
        def sentListId = instance.listsGetListByName('Sent').id
        assert task && task.listId.equals(inboxListId) : 'expected ' + inboxListId + ' but got ' + task.listId
        task = instance.tasksMoveTo(task.listId, task.taskSeriesId, task.taskId, sentListId)
        assert task && task.listId.equals(sentListId) : 'expected ' + sentListId + ' but got ' + task.listId
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksPostpone() {
        println 'testTasksPostpone()'
        def task = instance.tasksAdd('test tasks postpone')
        assert task && !task.postponed : 'task should not be postponed'
        task = instance.tasksPostpone(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.postponed : 'task should be postponed'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksRemoveTags() {
        println 'testTasksRemoveTags()'
        def task = instance.tasksAdd('test tasks remove tags')
        assert task : 'task should have been added'
        task = instance.tasksAddTags(task.listId, task.taskSeriesId, task.taskId, 'testing, tasks, remove')
        assert task && task.tags.join(', ').equals('remove, tasks, testing') : 'incorrect tags after add'
        task = instance.tasksRemoveTags(task.listId, task.taskSeriesId, task.taskId, 'testing, remove')
        assert task && task.tags.join(', ').equals('tasks') : 'incorrect tags after remove'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetDueDate() {
        println 'testTasksSetDueDate()'
        def task = instance.tasksAdd('test tasks set due')
        assert task && task.due.equals('') : 'expected blank due date but got ' + task.due
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, '12/31/2010', false, true)
        assert task && !task.due.equals('') : 'expected due date but got ' + task.due
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetEstimate() {
        println 'testTasksSetEstimate()'
        def task = instance.tasksAdd('test tasks set estimate')
        assert task && task.estimate.equals('') : 'expected blank estimate date but got ' + task.estimate
        task = instance.tasksSetEstimate(task.listId, task.taskSeriesId, task.taskId, '2 hours')
        assert task && task.estimate.equals('2 hours') : 'expected 2 hours estimate but got ' + task.estimate
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetLocation() {
        println 'testTasksSetLocation()'
        def task = instance.tasksAdd('test tasks set location')
        assert task && task.locationId.equals('') : 'expected blank location but got ' + task.locationId
        //NOTE: Assumes at least 1 location exists
        def locationId = instance.locationsGetList()[0].id
        task = instance.tasksSetLocation(task.listId, task.taskSeriesId, task.taskId, locationId)
        assert task && task.locationId.equals(locationId) : 'expected ' + locationId + ' location but got ' + task.locationId
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetName() {
        println 'testTasksSetName()'
        def task = instance.tasksAdd('test tasks set name')
        assert task && task.name.equals('test tasks set name') : 'expected test tasks set name but got ' + task.name
        task = instance.tasksSetName(task.listId, task.taskSeriesId, task.taskId, 'test renamed')
        assert task && task.name.equals('test renamed') : 'expected test renamed name but got ' + task.name
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetPriority() {
        println 'testTasksSetPriority()'
        def task = instance.tasksAdd('test tasks set priority')
        assert task && task.priority.equals('N') : 'expected no priority but got ' + task.priority
        task = instance.tasksSetPriority(task.listId, task.taskSeriesId, task.taskId, '2')
        assert task && task.priority.equals('2') : 'expected 2 priority but got ' + task.priority
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetRecurrence() {
        println 'testTasksSetRecurrence()'
        def task = instance.tasksAdd('test tasks set repeat')
        assert task && !task.repeat : 'expected no repeat but got ' + task.repeat
        task = instance.tasksSetRecurrence(task.listId, task.taskSeriesId, task.taskId, 'every week')
        assert task && task.repeat.equals('FREQ=WEEKLY;INTERVAL=1') : 'expected every 1 week repeat but got ' + task.repeat
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetTags() {
        println 'testTasksSetTags()'
        def task = instance.tasksAdd('test tasks set tags')
        assert task && task.tags.join(', ').equals('') : 'expected blank tags but got ' + task.tags.join(', ')
        task = instance.tasksSetTags(task.listId, task.taskSeriesId, task.taskId, 'testing')
        assert task && task.tags.join(', ').equals('testing') : 'expected testing tag but got ' + task.tags.join(', ')
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksSetUrl() {
        println 'testTasksSetUrl()'
        def task = instance.tasksAdd('test tasks set url')
        assert task && task.url.equals('') : 'expected blank url but got ' + task.url
        task = instance.tasksSetUrl(task.listId, task.taskSeriesId, task.taskId, 'http://eriwen.com')
        assert task && task.url.equals('http://eriwen.com') : 'expected http://eriwen.com url date but got ' + task.url
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksUncomplete() {
        println 'testTasksUncomplete()'
        def task = instance.tasksAdd('test tasks uncomplete')
        assert task && !task.completed : 'expected blank completed but got ' + task.completed
        task = instance.tasksComplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.completed : 'expected completed date but got ' + task.completed
        task = instance.tasksUncomplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && !task.completed : 'expected blank completed date but got ' + task.completed
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksNotesAdd() {
        println 'testTasksNotesAdd()'
        String noteTitle = 'Test Note Title'
        String noteText = 'Test Note Text'
        def task = instance.tasksAdd('test tasks notes add')
        assert task && task.notes : 'expected blank notes but got ' + task.notes
        def note = instance.tasksNotesAdd(task.listId, task.taskSeriesId, task.taskId, noteTitle, noteText)
        assert note : 'note should not be null'
        assert note.id : 'note id should not be null'
        assert note.title.equals(noteTitle) : 'expected note title ' + noteTitle + ' but got ' + note.title
        assert note.text.equals(noteText) : 'expected note text ' + noteText + ' but got ' + note.text
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksNotesDelete() {
        println 'testTasksNotesDelete()'
        String noteTitle = 'Test Note Title'
        String noteText = 'Test Note Text'
        def task = instance.tasksAdd('test tasks notes delete')
        assert task && task.notes : 'expected blank notes but got ' + task.notes
        def note = instance.tasksNotesAdd(task.listId, task.taskSeriesId, task.taskId, noteTitle, noteText)
        assert instance.tasksNotesDelete(note.id)
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTasksNotesEdit() {
        println 'testTasksNotesEdit()'
        String noteTitle = 'Test Note Title'
        String noteText = 'Test Note Text'
        String newNoteTitle = 'New Title'
        String newNoteText = 'New Text'
        def task = instance.tasksAdd('test tasks notes edit')
        def note = instance.tasksNotesAdd(task.listId, task.taskSeriesId, task.taskId, noteTitle, noteText)
        note = instance.tasksNotesEdit(note.id, newNoteTitle, newNoteText)
        assert note : 'note should not be null'
        assert note.id : 'note id should not be null'
        assert note.title.equals(newNoteTitle) : 'expected note title ' + newNoteTitle + ' but got ' + note.title
        assert note.text.equals(newNoteText) : 'expected note text ' + newNoteText + ' but got ' + note.text
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Test void testTimeConvert() {
        println 'testTimeConvert()'
        //NOTE: We know there are a bunch of existing timezones
        def timezonesList = instance.timezonesGetList()
        String result = instance.timeConvert(timezonesList[2].name.toString(), timezonesList[7].name.toString())
        assert result && result != 'null' : 'result is null'
    }

    @Test void testTimeParse() {
        println 'testTimeParse()'
        String time = instance.timeParse('6/15/2009')
        assert time.equals('2009-06-15T00:00:00Z')
    }

    @Test void testTimelinesCreate() {
        println 'testTimelinesCreate()'
        String timeline = instance.timelinesCreate()
        assert timeline : 'Unable to create timeline'
    }

    @Test void testTimezonesGetList() {
        println 'testTimezonesGetList()'
        def timezonesList = instance.timezonesGetList()
        assert timezonesList : 'timezones list cannot be empty'
        assert timezonesList instanceof List : 'wrong type returned, expecting List'
    }

    @Test void testTimezonesGetTimezoneByName() {
        println 'testTimezonesGetTimezoneByName()'
        def timezone = instance.timezonesGetTimezoneByName('Asia/Hong_Kong')
        assert timezone : 'timezone should not be null'
        assert timezone instanceof Timezone : 'wrong type returned, expecting LinkedHashMap'
        assert timezone.name.equals('Asia/Hong_Kong') : 'Expected timezone name Asia/Hong_Kong but got ' + timezone.name
    }

    @Test void testTransactionsUndo() {
        println 'testTransactionsUndo()'
        def list = instance.listsAdd('TestUndo')
        assert !list.archived : 'expected list archived == 0 but got ' + list.archived
        list = instance.listsArchive(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert list.archived : 'expected list archived == 1 but got ' + list.archived
        assert instance.transactionsUndo(list.transactionId) : 'transactionsUndo should return true for success'
        list = instance.listsGetListByName('TestUndo')
        assert !list.archived : 'expected null list archived but got ' + list.archived + ' - undo failed'
        assert instance.listsDelete(list.id)
    }
}
