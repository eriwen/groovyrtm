package org.eriwen.rtm

import static org.junit.Assert.*

import org.junit.After
import org.junit.Before
import org.junit.Ignore
import org.junit.Test

import org.eriwen.rtm.model.*

/**
 * Integration test class for <code>org.eriwen.rtm.GroovyRtm</code>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */
class GroovyRtmIntegrationTest {
    private GroovyRtm instance = null

    @Before void setUp() {
        //NOTE: put your test user and API keys here
//        def config = new ConfigSlurper().parse(new File('groovyrtm.conf').toURL())
//        instance = new GroovyRtm(config.api.key, config.api.sharedsecret, config.api.perms)
        instance = new GroovyRtm('c11620c31ac1c7e410f039d41c813a2b', '0ce781140c74257e')
        instance.currentUser = 'todofx'
    }
    @After void tearDown() {
        instance = null
    }

    @Test void testTestEcho() {
        assertTrue 'echo unsuccessful', instance.testEcho()
    }

    @Test void testGetAuthUrl() {
        assertNotNull 'expected valid URL but got nothing', instance.getAuthUrl()
    }

    @Ignore
    @Test void testGetNewAuthToken() {
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

    @Ignore
    @Test void testTestLogin() {
        assert instance.testLogin() : 'login unsuccessful'
    }

    @Test void testEnforceMinDelay() {
        long start = System.currentTimeMillis()
        Thread.start { instance.testEcho() }
        Thread.start { instance.testEcho() }
        Thread.start { instance.testEcho() }
        instance.testEcho()
        long end = System.currentTimeMillis()
        //Should enforce that there was at least a 1 second gap between calls
        assert end - start >= 1000 : 'Expected time to complete >= 1000ms but got ' + (end - start)
    }

    @Ignore
    @Test void testContactsGetList() {
        def contacts = instance.contactsGetList()
        assert contacts instanceof List
    }

    @Ignore
    @Test void testContactsAdd() {
        //NOTE: Assumes at least 1 existing contact. This is done to prevent
        //having to store a static user ID here
        def contact = instance.contactsGetList()[0]
        assert instance.contactsDelete(contact['id'].toString()) : 'delete contact returned null, expected transactionId'
        assert !(instance.contactsGetList().find{it.username.equals(contact['username'])}) : 'contact should not exist'
        assert instance.contactsAdd(contact['username'].toString()) : 'add contact returned null, expected transactionId'
        assert instance.contactsGetList().find{it.username.equals(contact['username'])} : 'contact should exist'
    }

    @Ignore
    @Test void testContactsDelete() {
        //NOTE: Assumes at least 1 existing contact.
        def contact = instance.contactsGetList()[0]
        assert instance.contactsDelete(contact['id'].toString()) : 'delete contact returned null, expected transactionId'
        assert !(instance.contactsGetList().find{it.username.equals(contact['username'])}) : 'contact should not exist'
        assert instance.contactsAdd(contact['username'].toString()) : 'add contact returned null, expected transactionId'
        assert instance.contactsGetList().find{it.username.equals(contact['username'])} : 'contact should exist'
    }

    @Ignore
    @Test void testGroupsAdd() {
        String groupName = 'test group add'
        def group = instance.groupsAdd(groupName)
        assert group : 'Expected transactionId returned but got: ' + group
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        assert instance.groupsDelete(groupId.toString())
    }

    @Ignore
    @Test void testGroupsAddContact() {
        String groupName = 'test group add contact'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        //NOTE: Assumes at least 1 existing contact.
        def contactId = instance.contactsGetList()[0]['id']
        assert instance.groupsAddContact(contactId.toString(), groupId.toString())
        assert instance.groupsDelete(groupId.toString())
    }

    @Ignore
    @Test void testGroupsDelete() {
        String groupName = 'test group delete'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        assert instance.groupsDelete(groupId.toString())
    }

    @Ignore
    @Test void testGroupsGetGroupByName() {
        String groupName = 'test group name'
        instance.groupsAdd(groupName)
        def group = instance.groupsGetGroupByName(groupName)
        assert group instanceof Map : 'wrong return type'
        assert group.get('id')
        assert group.get('name').equals(groupName)
        assert instance.groupsDelete(group.get('id').toString())
    }

    @Ignore
    @Test void testGroupsGetList() {
        def groupList = instance.groupsGetList()
        assert groupList instanceof List : 'expected List returned but got ' + groupList.class.toString()
    }

    @Ignore
    @Test void testGroupsRemoveContact() {
        String groupName = 'test group remove contact'
        instance.groupsAdd(groupName)
        def groupId = instance.groupsGetGroupByName(groupName)['id']
        def contact = instance.contactsGetList()[0]
        assert instance.groupsAddContact(contact['id'].toString(), groupId.toString()) : 'unable to add contact'
        assert instance.groupsRemoveContact(contact['id'].toString(), groupId.toString()) : 'unable to remove contact'
        assert instance.groupsDelete(groupId.toString()) : 'unable to delete group'
    }

    @Ignore
    @Test void testListsAdd() {
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

    @Ignore
    @Test void testListsArchive() {
        def list = instance.listsAdd('TestArchive')
        assert !list.archived : 'expected list archived == 0 but got ' + list.archived
        list = instance.listsArchive(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert list.archived : 'expected list archived but got ' + list.archived
        assert instance.listsDelete(list.id)
    }

    @Ignore
    @Test void testListsDelete() {
        def list = instance.listsAdd('Test List Delete')
        assert list : 'expected list to be returned but got ' + list
        assert !list.deleted : 'expected list deleted to be 0 but got ' + list.deleted
        list = instance.listsDelete(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert list.deleted : 'expected list deleted to be 1 but got ' + list.deleted
    }

    @Ignore
    @Test void testListsGetList() {
        def lists = instance.listsGetList()
        assert lists instanceof List<TaskList> : 'wrong return type'
        assert lists && lists != 'null' : 'lists cannot be null'
        assert lists.size() > 0 : 'lists cannot be empty'
    }

    @Ignore
    @Test void testListsGetListByName() {
        def list = instance.listsGetListByName('Inbox')
        assert list instanceof TaskList : 'wrong return type'
        assert list.id : 'List should have associated ID'
        assert list.name : 'List should have its name'
    }

    @Ignore
    @Test void testListsSetDefaultList() {
        String listName = 'TestSetDefault'
        def list = instance.listsAdd(listName)
        def result = instance.listsSetDefaultList(list.id)
        assert result && result != 'null' : 'could not set default list to ' + listName
        assert instance.listsDelete(list.id)
    }

    @Ignore
    @Test void testListsSetName() {
        String listName = 'Test Set Name'
        String newListName = 'Test New Name'
        def list = instance.listsAdd(listName)
        assert list.name.equals(listName) : 'expected list name of ' + listName + ' but got ' + list.name
        list = instance.listsSetName(list.id, newListName)
        assert list : 'expected list to be returned but got ' + list
        assert list.name.equals(newListName) : 'expected list name of ' + newListName + ' but got ' + list.name
        assert instance.listsDelete(list.id)
    }

    @Ignore
    @Test void testListsUnarchive() {
        def list = instance.listsAdd('TestUnarchive')
        assert instance.listsArchive(list.id)
        list = instance.listsUnarchive(list.id)
        assert list : 'expected list to be returned but got ' + list
        assert !list.archived : 'expected list archived == 0 but got ' + list.archived
        assert instance.listsDelete(list.id)
    }

    @Ignore
    @Test void testLocationsGetLocationByName() {
        // Get location list
        def location = instance.locationsGetList()[0]
        def locationByName = instance.locationsGetLocationByName(location['name'])
        assert locationByName : 'Location should not be null'
        assert locationByName instanceof Map : 'Location object should be a map'
        assert locationByName['id'] == location['id'] : 'Expected ' + location['id'] + " but got " + locationByName['id']
        assert locationByName['name'] == location['name'] : 'Expected ' + location['name'] + " but got " + locationByName['name']
    }

    @Ignore
    @Test void testLocationsGetList() {
        def locations = instance.locationsGetList()
        assert locations : 'Locations should not be null'
        assert locations instanceof List
        assert locations[0]['id'] : 'Location ID not present'
        assert locations[0]['name'] : 'Location Name not present'
    }

    @Ignore
    @Test void testSettingsGetList() {
        def settings = instance.settingsGetList()
        assert settings
        assert settings instanceof Map
    }

    @Ignore
    @Test void testTasksAdd() {
        def taskName = 'test tasks add'
        def task = instance.tasksAdd(taskName)
        assertEquals taskName, task.name
        assertNotNull instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSmartAdd() {
        def taskName = 'test tasks smart add !2 #coffee'
        def task = instance.tasksAdd(taskName, null, true)
        assert task : 'task could not be added'
        assert task instanceof Task : 'task should be a Task'
        assert task.name.equals('test tasks smart add') : 'task name does not match, got ' + task.name
        assert task.priority.equals('2') : 'task priority should be "2" but got ' + task.priority
        assert task.tags.join(', ').equals('coffee') : 'task should have tag "coffee"'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksAddAllParams() {
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

    @Ignore
    @Test void testTasksAddTags() {
        def task = instance.tasksAdd('test tasks add tags')
        assert task : 'task should have been added'
        task = instance.tasksAddTags(task.listId, task.taskSeriesId, task.taskId, 'testing, tasks')
        assert task && task.tags.join(', ').contains('testing') : 'testing tag does not exist'
        assert task.tags.join(', ').contains('tasks') : 'tasks tag does not exist'
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
    }

    @Ignore
    @Test void testTasksComplete() {
        def task = instance.tasksAdd('test tasks complete')
        assert task && !task.completed : 'expected blank completed but got ' + task.completed
        task = instance.tasksComplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.completed : 'expected completed task but got ' + task.completed
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
    }

    @Ignore
    @Test void testTasksDelete() {
        def task = instance.tasksAdd('test tasks delete')
        assert task : 'task should have been added'
        assert !task.deleted : 'task should not be deleted yet'
        task = instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
        assert task : 'task should still be returned'
        assert task.deleted : 'task should be deleted'
    }

    @Ignore
    @Test void testTasksGetList() {
        def task = instance.tasksAdd('test tasks get list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        //Get All Tasks
        def tasks = instance.tasksGetList()
        assertTrue tasks.size > 0
        assertNotNull instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksGetListFromList() {
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

    @Ignore
    @Test void testTasksGetSmartList() {
        def task = instance.tasksAdd('test tasks get smart list')
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, 'today', false, true)
        //Tasks due today
        def taskList = instance.tasksGetList(null, 'due:today')
        assert taskList : "Expected non-null task list"
        assert taskList.size > 0 : 'Expected taskList size > 0 but got ' + taskList.length
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksGetSmartListFromList() {
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

    @Ignore
    @Test void testTasksMovePriority() {
        def task = instance.tasksAdd('test tasks move priority')
        assert task && task.priority.equals('N') : 'expected priority N but got ' + task.priority
        task = instance.tasksMovePriority(task.listId, task.taskSeriesId, task.taskId, 'up')
        assert task && task.priority.equals('3') : 'expected priority 3 but got ' + task.priority
        task = instance.tasksMovePriority(task.listId, task.taskSeriesId, task.taskId, false)
        assert task && task.priority.equals('N') : 'expected priority N but got ' + task.priority
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksMoveTo() {
        def task = instance.tasksAdd('test tasks move')
        //Every account has an Inbox and a Sent list that can't be changed
        def inboxListId = instance.listsGetListByName('Inbox').id
        def sentListId = instance.listsGetListByName('Sent').id
        assert task && task.listId.equals(inboxListId) : 'expected ' + inboxListId + ' but got ' + task.listId
        task = instance.tasksMoveTo(task.listId, task.taskSeriesId, task.taskId, sentListId)
        assert task && task.listId.equals(sentListId) : 'expected ' + sentListId + ' but got ' + task.listId
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksPostpone() {
        def task = instance.tasksAdd('test tasks postpone')
        assert task && !task.postponed : 'task should not be postponed'
        task = instance.tasksPostpone(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.postponed : 'task should be postponed'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksRemoveTags() {
        def task = instance.tasksAdd('test tasks remove tags')
        assert task : 'task should have been added'
        task = instance.tasksAddTags(task.listId, task.taskSeriesId, task.taskId, 'testing, tasks, remove')
        assert task && task.tags.join(', ').equals('remove, tasks, testing') : 'incorrect tags after add'
        task = instance.tasksRemoveTags(task.listId, task.taskSeriesId, task.taskId, 'testing, remove')
        assert task && task.tags.join(', ').equals('tasks') : 'incorrect tags after remove'
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetDueDate() {
        def task = instance.tasksAdd('test tasks set due')
        assert task && task.due.equals('') : 'expected blank due date but got ' + task.due
        task = instance.tasksSetDueDate(task.listId, task.taskSeriesId, task.taskId, '12/31/2010', false, true)
        assert task && !task.due.equals('') : 'expected due date but got ' + task.due
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetEstimate() {
        def task = instance.tasksAdd('test tasks set estimate')
        assert task && task.estimate.equals('') : 'expected blank estimate date but got ' + task.estimate
        task = instance.tasksSetEstimate(task.listId, task.taskSeriesId, task.taskId, '2 hours')
        assert task && task.estimate.equals('2 hours') : 'expected 2 hours estimate but got ' + task.estimate
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetLocation() {
        def task = instance.tasksAdd('test tasks set location')
        assert task && task.locationId.equals('') : 'expected blank location but got ' + task.locationId
        //NOTE: Assumes at least 1 location exists
        def locationId = instance.locationsGetList()[0].id
        task = instance.tasksSetLocation(task.listId, task.taskSeriesId, task.taskId, locationId)
        assert task && task.locationId.equals(locationId) : 'expected ' + locationId + ' location but got ' + task.locationId
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetName() {
        def task = instance.tasksAdd('test tasks set name')
        assert task && task.name.equals('test tasks set name') : 'expected test tasks set name but got ' + task.name
        task = instance.tasksSetName(task.listId, task.taskSeriesId, task.taskId, 'test renamed')
        assert task && task.name.equals('test renamed') : 'expected test renamed name but got ' + task.name
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetPriority() {
        def task = instance.tasksAdd('test tasks set priority')
        assert task && task.priority.equals('N') : 'expected no priority but got ' + task.priority
        task = instance.tasksSetPriority(task.listId, task.taskSeriesId, task.taskId, '2')
        assert task && task.priority.equals('2') : 'expected 2 priority but got ' + task.priority
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetRecurrence() {
        def task = instance.tasksAdd('test tasks set repeat')
        assert task && !task.repeat : 'expected no repeat but got ' + task.repeat
        task = instance.tasksSetRecurrence(task.listId, task.taskSeriesId, task.taskId, 'every week')
        assert task && task.repeat.equals('FREQ=WEEKLY;INTERVAL=1') : 'expected every 1 week repeat but got ' + task.repeat
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetTags() {
        def task = instance.tasksAdd('test tasks set tags')
        assert task && task.tags.join(', ').equals('') : 'expected blank tags but got ' + task.tags.join(', ')
        task = instance.tasksSetTags(task.listId, task.taskSeriesId, task.taskId, 'testing')
        assert task && task.tags.join(', ').equals('testing') : 'expected testing tag but got ' + task.tags.join(', ')
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksSetUrl() {
        def task = instance.tasksAdd('test tasks set url')
        assert task && task.url.equals('') : 'expected blank url but got ' + task.url
        task = instance.tasksSetUrl(task.listId, task.taskSeriesId, task.taskId, 'http://eriwen.com')
        assert task && task.url.equals('http://eriwen.com') : 'expected http://eriwen.com url date but got ' + task.url
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksUncomplete() {
        def task = instance.tasksAdd('test tasks uncomplete')
        assert task && !task.completed : 'expected blank completed but got ' + task.completed
        task = instance.tasksComplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && task.completed : 'expected completed date but got ' + task.completed
        task = instance.tasksUncomplete(task.listId, task.taskSeriesId, task.taskId)
        assert task && !task.completed : 'expected blank completed date but got ' + task.completed
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksNotesAdd() {
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

    @Ignore
    @Test void testTasksNotesDelete() {
        String noteTitle = 'Test Note Title'
        String noteText = 'Test Note Text'
        def task = instance.tasksAdd('test tasks notes delete')
        assert task && task.notes : 'expected blank notes but got ' + task.notes
        def note = instance.tasksNotesAdd(task.listId, task.taskSeriesId, task.taskId, noteTitle, noteText)
        assert instance.tasksNotesDelete(note.id)
        assert instance.tasksDelete(task.listId, task.taskSeriesId, task.taskId)
    }

    @Ignore
    @Test void testTasksNotesEdit() {
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

    @Ignore
    @Test void testTimeConvert() {
        //NOTE: We know there are a bunch of existing timezones
        def timezonesList = instance.timezonesGetList()
        String result = instance.timeConvert(timezonesList[2].name.toString(), timezonesList[7].name.toString())
        assert result && result != 'null' : 'result is null'
    }

    @Ignore
    @Test void testTimeParse() {
        String time = instance.timeParse('6/15/2009')
        assert time.equals('2009-06-15T00:00:00Z')
    }

    @Ignore
    @Test void testTimelinesCreate() {
        String timeline = instance.timelinesCreate()
        assert timeline : 'Unable to create timeline'
    }

    @Ignore
    @Test void testTimezonesGetList() {
        def timezonesList = instance.timezonesGetList()
        assert timezonesList : 'timezones list cannot be empty'
        assert timezonesList instanceof List : 'wrong type returned, expecting List'
    }

    @Ignore
    @Test void testTimezonesGetTimezoneByName() {
        def timezone = instance.timezonesGetTimezoneByName('Asia/Hong_Kong')
        assert timezone : 'timezone should not be null'
        assert timezone instanceof Timezone : 'wrong type returned, expecting LinkedHashMap'
        assert timezone.name.equals('Asia/Hong_Kong') : 'Expected timezone name Asia/Hong_Kong but got ' + timezone.name
    }

    @Ignore
    @Test void testTransactionsUndo() {
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
