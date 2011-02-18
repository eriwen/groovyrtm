package org.eriwen.rtm.test

import org.junit.After
import org.junit.Before
import org.junit.Test

import org.gmock.WithGMock

import org.eriwen.rtm.GroovyRtm
import org.eriwen.rtm.model.*

/**
 * Unit test class for <code>org.eriwen.rtm.GroovyRtm</code>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */

@WithGMock
class GroovyRtmTest {
    def mockGroovyRtm = null
    private static GroovyRtm instance = null

    @Before void setUp() {
        instance = new GroovyRtm('config/GroovyRtm.properties')
        instance.currentUser = 'bogus'
        mockGroovyRtm = mock(instance)
        mockGroovyRtm.addCommonParams(match{it}).returns([:]).stub()
    }
    @After void tearDown() {
        mockGroovyRtm = null
        instance = null
    }

    @Test void testTestEcho() {
        mockGroovyRtm.execUnauthenticatedMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><method>rtm.test.echo</method></rsp>')).once()
        play {
            assert instance.testEcho() : 'echo unsuccessful'
        }
    }

    @Test void testEnforceMinDelay() {
        long start = System.currentTimeMillis()
        Thread.start { instance.testEcho() }
        Thread.start { instance.testEcho() }
        Thread.start { instance.testEcho() }
        instance.testEcho()
        long end = System.currentTimeMillis()
        //Should enforce that there was at least a 1 second gap between calls
        assert end - start >= 2000 : 'Expected time to complete >= 2000ms but got ' + (end - start)
    }

    @Test void testTestLogin() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><user id="987654321"><username>bob</username></user></rsp>')).once()
        play {
            boolean success = instance.testLogin()
            assert success : 'login unsuccessful'
        }
    }

    @Test void testGetAuthUrl() {
        mockGroovyRtm.execUnauthenticatedMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><frob>bf617d0d7e0a3c5113b9815de0eab8683e077ea9</frob></rsp>')).once()
        play {
            def authUrl = instance.getAuthUrl()
            assert authUrl : 'expected valid URL but got nothing'
            assert authUrl instanceof String : 'expected String URL but got a different type'
        }
    }

    @Test void testAuthCheckToken() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><auth><token>6410bde19b6dfb474fec71f186bc715831ea6842</token><perms>delete</perms><user id="123" username="bob" fullname="Eric Wendelin"/></auth></rsp>')).once()
        play {
            def isTokenValid = instance.authCheckToken('6410bde19b6dfb474fec71f186bc715831ea6842')
            assert isTokenValid : 'Expected token to be valid'
        }
    }

    @Test void testAuthGetFrob() {
        mockGroovyRtm.execUnauthenticatedMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><frob>0a56717c3561e53584f292bb7081a533c197270c</frob></rsp>')).once()
        play {
            def frob = instance.authGetFrob()
            assert frob && frob instanceof String : 'Expected String frob'
            assert frob.equals('0a56717c3561e53584f292bb7081a533c197270c') : 'expected frob "0a56717c3561e53584f292bb7081a533c197270c" but got ' + frob
        }
    }

    @Test void testAuthGetToken() {
        mockGroovyRtm.execUnauthenticatedMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><auth><token>4286cc6f8c3dcbf09001ecc83f95000efa45c9f5</token><perms>delete</perms><user id="123" username="bob" fullname="Eric Wendelin"/></auth></rsp>')).once()
        play {
            def authToken = instance.authGetToken()
            assert authToken && authToken instanceof String : 'Expected String auth token'
            assert authToken.equals('4286cc6f8c3dcbf09001ecc83f95000efa45c9f5') : 'expected auth token "4286cc6f8c3dcbf09001ecc83f95000efa45c9f5" but got ' + authToken
        }
    }

    @Test void testContactsGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><contacts><contact id="1" fullname="Omar Kilani" username="omar"/></contacts></rsp>')).once()
        play {
            def contacts = instance.contactsGetList()
            assert contacts instanceof List : "Expected List return type"
            assert contacts.size() == 1 : "Contacts should have 1 contact but got " + contacts.size()
            assert contacts[0].username == "omar" : "Expected user omar but got " + contacts[0].username
        }
    }

    @Test void testContactsAdd() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="123"/><contact id="1" fullname="Omar Kilani" username="omar"/></rsp>')).once()
        play {
            def transactionId = instance.contactsAdd('omar')
            assert transactionId : 'add contact returned null, expected transactionId'
            assert transactionId == '123' : 'Expected transaction ID "123" but got ' + transactionId
        }
    }

    @Test void testContactsDelete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/></rsp>')).once()
        play {
            def transactionId = instance.contactsDelete('omar')
            assert transactionId : 'add contact returned null, expected transactionId'
            assert transactionId == '234' : 'Expected transaction ID "234" but got ' + transactionId
        }
    }

    @Test void testGroupsAdd() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><group id="987654321" name="Friends"><contacts /></group></rsp>')).once()
        play {
            def transactionId = instance.groupsAdd('Friends')
            assert transactionId : 'Expected transactionId returned but got: ' + transactionId
            assert transactionId == '234' : 'Expected transaction ID "234" but got ' + transactionId
        }
    }

    @Test void testGroupsAddContact() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/></rsp>')).once()
        play {
            def transactionId = instance.groupsAddContact('123','456')
            assert transactionId : 'Expected transactionId returned but got: ' + transactionId
            assert transactionId == '234' : 'Expected transaction ID "234" but got ' + transactionId
        }
    }

    @Test void testGroupsDelete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/></rsp>')).once()
        play {
            def transactionId = instance.groupsDelete('123')
            assert transactionId : 'Expected transactionId returned but got: ' + transactionId
            assert transactionId == '234' : 'Expected transaction ID "234" but got ' + transactionId
        }
    }

    @Test void testGroupsGetGroupByName() {
        mockGroovyRtm.groupsGetList().returns([
            ['id': '123', 'name': 'Friends'],
            ['id': '124', 'name': 'Enemies']
        ]).once()
        play {
            def group = instance.groupsGetGroupByName('Friends')
            assert group instanceof Map : 'expected Map returned but got ' + group.class.toString()
            assert group['id'].equals('123') : 'Expected group ID 123'
            assert group['name'].equals('Friends'): 'Expected group name "Friends"'
        }
    }

    @Test void testGroupsGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><groups><group id="987654321" name="Friends"><contacts><contact id="1"/></contacts></group></groups></rsp>')).once()
        play {
            def groupList = instance.groupsGetList()
            assert groupList instanceof List : 'expected List returned but got ' + groupList.class.toString()
            assert groupList.size() == 1 : 'Expected list length of 1 but got ' + groupList.size()
            assert groupList[0].get('name') == 'Friends' : 'Expected group name of "Friends" but got ' + groupList[0].get('name')
        }
    }

    @Test void testGroupsRemoveContact() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/></rsp>')).once()
        play {
            def transactionId = instance.groupsRemoveContact('123','456')
            assert transactionId : 'Expected transactionId returned but got: ' + transactionId
            assert transactionId == '234' : 'Expected transaction ID "234" but got ' + transactionId
        }
    }

    @Test void testListsAdd() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><list id="123" name="New List" deleted="0" locked="0" archived="0" position="0" smart="0"/></rsp>')).once()
        play {
            def list = instance.listsAdd('New List', null)
            assert list : 'expected list to be returned'
            assert list['transactionId'] && list['transactionId'] == '234' : 'Expected transaction ID "234"'
            assert list['id'] && list['id'].equals('123') : 'expected list ID "123"'
            assert list['name'] && list['name'].equals('New List') : 'expected list name "New List"'
        }
    }

    @Test void testListsArchive() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><list id="123" name="New List" deleted="0" locked="0" archived="1" position="0" smart="0"/></rsp>')).once()
        play {
            def list = instance.listsArchive('New List')
            assert list : 'expected list to be returned'
            assert list['archived'] : 'expected list archived == 1 but got ' + list['archived']
        }
    }

    @Test void testListsDelete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><list id="123" name="New List" deleted="1" locked="0" archived="0" position="0" smart="0"/></rsp>')).once()
        play {
            def list = instance.listsDelete('123')
            assert list : 'expected list to be returned'
            assert list['deleted'] : 'expected list deleted == 1 but got ' + list['deleted']
        }
    }

    @Test void testListsGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <lists>
                  <list id="100653" name="Inbox"
                       deleted="0" locked="1" archived="0" position="-1" smart="0" />
                  <list id="387549" name="High Priority"
                       deleted="0" locked="0" archived="0" position="0" smart="1">
                    <filter>(priority:1)</filter>
                  </list>
                  <list id="100657" name="Sent"
                       deleted="0" locked="1" archived="0" position="1" smart="0" />
                </lists>
            </rsp>
        ''')).once()
        play {
            def lists = instance.listsGetList()
            assert lists instanceof List : 'Expected List return type, but got ' + lists.class.toString()
            assert lists.size() == 3 : 'Expected 3 lists returned but got ' + lists.size()
        }
    }

    @Test void testListsGetListByName() {
        mockGroovyRtm.listsGetList().returns([
            ['id': '100653', 'name': 'Inbox', 'deleted': '0', 'locked': '1', 'archived': '0', 'position': '-1', 'smart': '0'],
            ['id': '100654', 'name': 'High Priority', 'deleted': '0', 'locked': '0', 'archived': '0', 'position': '0', 'smart': '1'],
            ['id': '100655', 'name': 'Sent', 'deleted': '0', 'locked': '1', 'archived': '0', 'position': '1', 'smart': '0']
        ]).once()
        play {
            def list = instance.listsGetListByName('Inbox')
            assert list instanceof TaskList : 'Expected TaskList return type but got ' + list.class.toString()
            assert list['id'] && list['id'] == '100653' : 'Expected list ID "100653"'
            assert list['name'] && list['name'] == 'Inbox' : 'List name is not "Inbox"'
        }
    }

    @Test void testListsSetDefaultList() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="123"/></rsp>')).once()
        play {
            def success = instance.listsSetDefaultList('123')
            assert success : 'expected success == true but got ' + success.toString()
        }
    }

    @Test void testListsSetName() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="123"/><list id="100654" name="High Priority" deleted="0" locked="0" archived="0" position="0" smart="0"/></rsp>')).once()
        play {
            def list = instance.listsSetName('100654', 'High Priority')
            assert list : 'expected list to be returned'
            assert list['name'] && list['name'].equals('High Priority') : 'expected list name "High Priority" but got ' + list['name']
        }
    }

    @Test void testListsUnarchive() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><list id="123" name="New List" deleted="0" locked="0" archived="0" position="0" smart="0"/></rsp>')).once()
        play {
            def list = instance.listsUnarchive('New List')
            assert list : 'expected list to be returned'
            assert !list['archived'] : 'expected list not archived but got ' + list['archived']
        }
    }

    @Test void testLocationsGetLocationByName() {
        mockGroovyRtm.locationsGetList().returns([
            ['id': '123', 'name': 'Home'],
            ['id': '124', 'name': 'Work'],
            ['id': '125', 'name': 'School']
        ]).once()
        play {
            def loc = instance.locationsGetLocationByName('Work')
            assert loc instanceof Map : 'Expected Map return type but got ' + loc.class.toString()
            assert loc['id'] && loc['id'] == '124' : 'Expected location ID "124"'
            assert loc['name'] && loc['name'] == 'Work' : 'location name is not "Work"'
        }
    }

    @Test void testLocationsGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <locations>
                  <location id="987654321" name="Berlin" longitude="13.411508"
                          latitude="52.524008" zoom="9" address="Berlin, Germany" viewable="1"/>
                  <location id="987654322" name="New York" longitude="-74.00713"
                          latitude="40.71449" zoom="9" address="New York, NY, USA" viewable="1"/>
                  <location id="987654323" name="Sydney" longitude="151.216667"
                           latitude="-33.8833333" zoom="7"
                           address="Sydney, New South Wales, Australia" viewable="1"/>
                </locations>
            </rsp>
        ''')).once()
        play {
            def locations = instance.locationsGetList()
            assert locations instanceof List : 'Expected List return type, but got ' + locations.class.toString()
            assert locations.size() == 3 : 'Expected 3 locations returned but got ' + locations.size()
        }
    }

    @Test void testSettingsGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <settings>
                    <timezone>Australia/Sydney</timezone>
                    <dateformat>0</dateformat>
                    <timeformat>0</timeformat>
                    <defaultlist>123456</defaultlist>
                </settings>
            </rsp>
        ''')).once()
        play {
            def settings = instance.settingsGetList()
            assert settings instanceof Map : 'Expected Map return type, but got ' + settings.class.toString()
            assert settings['defaultlist'].equals('123456'): 'Expected default list "123456"'
        }
    }

    @Test void testTasksAdd() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksAdd('Get Bananas', null, false)
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.transactionId && task.transactionId.equals('123'): 'Expected transaction ID of "123"'
            assert task.name.equals('Get Bananas'): 'Expected name "Get Bananas"'
            assert task.listId.equals('987654321'): 'Expected list ID of "987654321"'
            assert task.taskSeriesId.equals('987654321'): 'Expected task series ID of "987654321"'
            assert task.taskId.equals('123456789'): 'Expected task ID of "123456789"'
            assert task.due.equals(''): 'Expected blank task due'
            assert !task.hasDueTime: 'Expected task has due time of "0" but got ' + task.hasDueTime
            assert !task.completed: 'Expected blank task completed'
            assert !task.deleted: 'Expected blank task deleted'
            assert task.estimate.equals(''): 'Expected blank task estimate'
            assert task.url.equals(''): 'Expected blank task url'
            assert task.locationId.equals(''): 'Expected blank task location'
            assert task.priority.equals('N'): 'Expected priority of "N"'
        }
    }

    @Test void testTasksAddAllParams() {
        def newTask = new Task(transactionId: '123', name: 'Get Bananas', listId: '100', taskSeriesId: '101',
                taskId: '102', due: '2009-05-07T10:19:54Z', priority: '2', hasDueTime: true, completed: false,
                deleted: false, estimate: '3 hrs', repeat: 'FREQ=WEEKLY;INTERVAL=1', url: 'http://eriwen.com',
                locationId: '234', tags: ['yay', 'tag'], participants: [], notes: [])
        mockGroovyRtm.tasksAdd('Get Bananas', '2', '2009-05-07T10:19:54Z', '3 hrs', 'every 1 week', 'yay, tag', '234',
                'http://eriwen.com', '100').returns(newTask).once()
        play {
            def task = instance.tasksAdd('Get Bananas', '2', '2009-05-07T10:19:54Z', '3 hrs', 'every 1 week',
                    'yay, tag', '234', 'http://eriwen.com', '100')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.transactionId.equals('123'): 'Expected transaction ID of "123"'
            assert task.name.equals('Get Bananas'): 'Expected name "Get Bananas"'
            assert task.listId.equals('100'): 'Expected list ID of "987654321"'
            assert task.taskSeriesId.equals('101'): 'Expected task series ID of "987654321"'
            assert task.taskId.equals('102'): 'Expected task ID of "123456789"'
            assert task.due.equals('2009-05-07T10:19:54Z'): 'Expected due date "2009-05-07T10:19:54Z"'
            assert task.hasDueTime: 'Expected task has due time of "1"'
            assert !task.completed: 'Expected blank task completed'
            assert !task.deleted: 'Expected blank task deleted'
            assert task.estimate.equals('3 hrs'): 'Expected estimate of "3 hrs"'
            assert task.repeat.equals('FREQ=WEEKLY;INTERVAL=1'): 'Expected repeat "FREQ=WEEKLY;INTERVAL=1"'
            assert task.url.equals('http://eriwen.com'): 'Expected URL "http://eriwen.com"'
            assert task.locationId.equals('234'): 'Expected location ID "234"'
            assert task.priority.equals('2'): 'Expected priority of "2"'
            assert task.tags.join(', ').equals('yay, tag'): 'Expected tags of "yay, tag"'
        }
    }

    @Test void testTasksAddTags() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags>
                      <tag>coffee</tag>
                      <tag>good</tag>
                      <tag>mmm</tag>
                    </tags>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksAddTags('987654321', '987654321', '123456789', 'coffee, good, mmm')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.tags.join(', ').equals('coffee, good, mmm'): 'Expected tags of "coffee, good, mmm"'
        }
    }

    @Test void testTasksComplete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="2009-05-07T10:26:21Z" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksComplete('987654321', '987654321', '123456789')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.completed: 'Expected task completed'
        }
    }

    @Test void testTasksDelete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="2009-05-07T10:26:21Z" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksDelete('987654321', '987654321', '123456789')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.deleted: 'Expected task deleted'
        }
    }

    @Test void testTasksGetListAllTasks() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
              <tasks>
                <list id="100">
                  <taskseries id="101" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="102" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                  <taskseries id="103" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Buy Coffee" source="api">
                    <tags>
                        <tag>mmm</tag>
                        <tag>tasty</tag>
                    </tags>
                    <participants/>
                    <notes/>
                    <task id="104" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="2" postponed="1" estimate="3 hrs"/>
                  </taskseries>
                </list>
                <list id="105">
                  <taskseries id="106" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Do Some Work" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="107" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="3" postponed="0" estimate="4 hrs"/>
                  </taskseries>
                </list>
              </tasks>
            </rsp>
        ''')).once()
        play {
            def tasks = instance.tasksGetList(null, null, null)
            assert tasks instanceof List<Task> : 'Expected List return type, but got ' + tasks.class.toString()
            assert tasks.size() == 3 : 'Expected 3 tasks returned but got ' + tasks.size()
            assert tasks[0].name.equals('Get Bananas') : 'Expected first task name "Get Bananas" but got ' + tasks[0].name
            assert tasks[0].listId.equals('100') : 'Expected first task list id of "100" but got ' + tasks[0].listId
            assert tasks[0].priority.equals('N') : 'Expected first task priority of "N" but got ' + tasks[0].priority
        }
    }
    
    @Test void testTasksMovePriority() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="2009-05-07T10:26:21Z" priority="3" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksMovePriority('987654321', '987654321', '123456789', 'up')
            assert task instanceof Task : 'Expected Task return type, but got ' + task.class.toString()
            assert task.priority.equals('3'): 'Expected task priority moved to "3"'
        }
    }

    @Test void testTasksMoveTo() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="349523984">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="2009-05-07T10:26:21Z" priority="3" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksMoveTo('987654321', '987654321', '123456789', '349523984')
            assert task instanceof Task : 'Expected Task return type, but got ' + task.class.toString()
            assert task.listId.equals('349523984'): 'Expected task list ID to be "349523984"'
        }
    }

    @Test void testTasksPostpone() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="2009-05-07T10:26:21Z" priority="3" postponed="1" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksPostpone('987654321', '987654321', '123456789')
            assert task instanceof Task : 'Expected Task return type, but got ' + task.class.toString()
            assert task.postponed: 'Expected task postponed'
        }
    }

    @Test void testTasksRemoveTags() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags>
                        <tag>mmm</tag>
                    </tags>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="2009-05-07T10:26:21Z" priority="3" postponed="1" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksRemoveTags('987654321', '987654321', '123456789', 'coffee')
            assert task instanceof Task : 'Expected Task return type, but got ' + task.class.toString()
            assert task.tags.join(', ').equals('mmm'): 'Expected task tags of "mmm"'
        }
    }

    @Test void testTasksSetDueDate() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="2009-05-08T10:19:54Z" has_due_time="1" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetDueDate('987654321', '987654321', '123456789', '2009-05-08T10:19:54Z', true, false)
            assert task instanceof Task : 'Expected Task return type, but got ' + task.class.toString()
            assert task.due.equals('2009-05-08T10:19:54Z'): 'Expected task tags of "2009-05-07T10:19:54Z"'
            assert task.hasDueTime : 'Expected task has due time but got ' + task.hasDueTime
        }
    }

    @Test void testTasksSetEstimate() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="2009-05-08T10:19:54Z" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate="2 hours"/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetEstimate('987654321', '987654321', '123456789', '2 hours')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.estimate.equals('2 hours'): 'Expected task tags of "2 hours"'
        }
    }

    @Test void testTasksSetLocation() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api" location_id="123456">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate="2 hours"/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetLocation('987654321', '987654321', '123456789', '123456')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.locationId.equals('123456'): 'Expected task location ID of "123456"'
        }
    }

    @Test void testTasksSetName() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetName('987654321', '987654321', '123456789', 'Get Bananas')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.name.equals('Get Bananas'): 'Expected task name "Get Bananas"'
        }
    }

    @Test void testTasksSetPriority() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="2" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetPriority('987654321', '987654321', '123456789', '2')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.priority.equals('2'): 'Expected task priority of "2"'
        }
    }

    @Test void testTasksSetRecurrence() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="387546">
                  <taskseries id="648042" created="2009-05-07T10:19:54Z" modified="2009-05-07T11:11:22Z"
                             name="Get Coffee" source="api">
                    <rrule every="1">FREQ=DAILY;INTERVAL=1</rrule>
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="811467" due="2009-05-09T14:00:00Z" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="3" postponed="1" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetRecurrence('387546', '648042', '811467', 'every 1 day')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.repeat.equals('FREQ=DAILY;INTERVAL=1'): 'Expected task repeat of "FREQ=DAILY;INTERVAL=1"'
        }
    }

    @Test void testTasksSetTags() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags>
                      <tag>coffee</tag>
                      <tag>good</tag>
                      <tag>mmm</tag>
                    </tags>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetTags('987654321', '987654321', '123456789', 'coffee, good, mmm')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.tags.join(', ').equals('coffee, good, mmm'): 'Expected tags of "coffee, good, mmm"'
        }
    }

    @Test void testTasksSetUrl() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api" location_id="123456" url="http://eriwen.com">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate="2 hours"/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksSetUrl('987654321', '987654321', '123456789', 'http://eriwen.com')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert task.url.equals('http://eriwen.com'): 'Expected task URL "http://eriwen.com"'
        }
    }

    @Test void testTasksUncomplete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="987654321">
                  <taskseries id="987654321" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api">
                    <tags/>
                    <participants/>
                    <notes/>
                    <task id="123456789" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="N" postponed="0" estimate=""/>
                  </taskseries>
                </list>
            </rsp>
        ''')).once()
        play {
            def task = instance.tasksUncomplete('987654321', '987654321', '123456789')
            assert task instanceof Task : 'Expected Map return type, but got ' + task.class.toString()
            assert !task.completed: 'Expected blank task completed'
        }
    }

    @Test void testTasksNotesAdd() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><note id="169624" created="2009-05-07T11:26:49Z" modified="" title="Note Title">Note Body</note></rsp>')).once()
        play {
            def note = instance.tasksNotesAdd('987','987','123','Note Title','Note Body')
            assert note instanceof Note : 'Expected Map return type, but got ' + note.class.toString()
            assert note.transactionId.equals('234') : 'Expected note transaction ID of "234"'
            assert note.id.equals('169624') : 'Expected note ID of "169624"'
            assert note.title.equals('Note Title') : 'Expected note title of "Note Title"'
            assert note.text.equals('Note Body') : 'Expected note text of "Note Body"'
        }
    }

    @Test void testTasksNotesDelete() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"/>')).once()
        play {
            def success = instance.tasksNotesDelete('123')
            assert success : 'Expected true for successful notes delete'
        }
    }

    @Test void testTasksNotesEdit() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><transaction id="234"/><note id="169624" created="2009-05-07T11:26:49Z" modified="2009-05-07T11:26:49Z" title="Note Title">Note Body</note></rsp>')).once()
        play {
            def note = instance.tasksNotesEdit('169624','Note Title','Note Body')
            assert note instanceof Note : 'Expected Map return type, but got ' + note.class.toString()
            assert note.transactionId.equals('234') : 'Expected note transaction ID of "234"'
            assert note.id.equals('169624') : 'Expected note ID of "169624"'
            assert note.title.equals('Note Title') : 'Expected note title of "Note Title"'
            assert note.text.equals('Note Body') : 'Expected note text of "Note Body"'
        }
    }

    @Test void testTimeConvert() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><time timezone="Australia/Sydney">2009-05-07T10:00:00</time></rsp>')).once()
        play {
            def time = instance.timeConvert('123', '234', null)
            assert time.equals('2009-05-07T10:00:00') : 'Expected date-time value of "2009-05-07T10:00:00"'
        }
    }

    @Test void testTimeParse() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><time precision="time">2009-05-10T07:00:00Z</time></rsp>')).once()
        play {
            def time = instance.timeParse('2009-05-07T10:00:00', null, false)
            assert time.equals('2009-05-10T07:00:00Z') : 'Expected date-time value of "2009-05-10T07:00:00Z"'
        }
    }

    @Test void testTimelinesCreate() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"><timeline>12741021</timeline></rsp>')).once()
        play {
            def timeline = instance.timelinesCreate()
            assert timeline.equals('12741021') : 'Expected timeline value of "12741021"'
        }
    }

    @Test void testTimezonesGetList() {
        mockGroovyRtm.execMethod(match{it}).returns(new XmlSlurper().parseText('''
            <rsp stat="ok">
                <timezones>
                  <timezone id="216" name="Asia/Hong_Kong" dst="0" offset="28800" current_offset="28800" />
                  <timezone id="217" name="Asia/Hovd" dst="1" offset="28800" current_offset="25200" />
                  <timezone id="226" name="Asia/Kashgar" dst="0" offset="28800" current_offset="28800" />
                  <timezone id="228" name="Asia/Krasnoyarsk" dst="1" offset="28800" current_offset="25200" />
                  <timezone id="229" name="Asia/Kuala_Lumpur" dst="0" offset="28800" current_offset="28800" />
                  <timezone id="230" name="Asia/Kuching" dst="0" offset="28800" current_offset="28800" />
                </timezones>
            </rsp>
        ''')).once()
        play {
            def timezones = instance.timezonesGetList()
            assert timezones instanceof List : 'Expected List return type, but got ' + timezones.class.toString()
            assert timezones.size() == 6 : 'Expected 6 timezones but got ' + timezones.size()
            assert timezones[0].id.equals('216') : 'Expected timezone ID "216" but got ' + timezones[0].id
            assert timezones[0].name.equals('Asia/Hong_Kong') : 'Expected timezone name "Asia/Hong_Kong" but got ' + timezones[0].name
        }
    }

    @Test void testTimezonesGetTimezoneByName() {
        mockGroovyRtm.timezonesGetList().returns([
            new Timezone(id: '216', name: 'Asia/Hong_Kong', dst: '0', offset: '28800', currentOffset: '28800'),
            new Timezone(id: '217', name: 'Asia/Hovd', dst: '1', offset: '28800', currentOffset: '25200'),
            new Timezone(id: '228', name: 'Asia/Krasnoyarsk', dst: '1', offset: '28800', currentOffset: '25200')
        ]).times(2)
        play {
            def timezone = instance.timezonesGetTimezoneByName('Asia/Hovd')
            assert timezone instanceof Timezone : 'expected Map returned but got ' + timezone.class.toString()
            assert timezone.id.equals('217') : 'Expected timezone ID 217'
            assert timezone.name.equals('Asia/Hovd'): 'Expected timezone name "Asia/Hovd"'

            timezone = instance.timezonesGetTimezoneByName('idontexist')
            assert timezone == null : 'timezone should be null'
        }
    }

    @Test void testTransactionsUndo() {
        mockGroovyRtm.execTimelineMethod(match{it}).returns(new XmlSlurper().parseText('<rsp stat="ok"/>')).once()
        play {
            def success = instance.transactionsUndo('123')
            assert success : 'Expected true for successful Undo'
        }
    }
}
