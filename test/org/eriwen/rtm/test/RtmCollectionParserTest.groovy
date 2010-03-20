package org.eriwen.rtm.test

import org.junit.After
import org.junit.Before
import org.junit.Test

import groovy.util.slurpersupport.GPathResult
import org.eriwen.rtm.RtmCollectionParser
import org.eriwen.rtm.model.*

/**
 * Unit test class for <code>org.eriwen.rtm.RtmService</code>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */

class RtmCollectionParserTest {
    private static RtmCollectionParser instance = null

    @Before void setUp() {
        instance = new RtmCollectionParser()
    }
    @After void tearDown() {
        instance = null
    }

    @Test void testParseTask() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="123"/>
                <list id="100">
                  <taskseries id="101" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
                             name="Get Bananas" source="api" location_id="234" url="http://eriwen.com">
                    <rrule every="1">FREQ=DAILY;INTERVAL=1</rrule>
                    <tags><tag>yay</tag><tag>bananas</tag></tags>
                    <participants/>
                    <notes/>
                    <task id="102" due="2009-05-07T10:19:54Z" has_due_time="1" added="2009-05-07T10:19:54Z"
                         completed="" deleted="" priority="2" postponed="0" estimate="4 hours"/>
                  </taskseries>
                </list>
            </rsp>
        ''')
        def task = instance.parseTask(resp)
        assert task instanceof Task : 'Expected Task returned but got ' + task.class.toString()
        assert task.transactionId.equals('123'): 'Expected transaction ID of "123"'
        assert task.name.equals('Get Bananas'): 'Expected name "Get Bananas"'
        assert task.listId.equals('100'): 'Expected list ID of "100"'
        assert task.taskSeriesId.equals('101'): 'Expected task series ID of "101"'
        assert task.taskId.equals('102'): 'Expected task ID of "102"'
        assert task.due.equals('2009-05-07T10:19:54Z'): 'Expected due date "2009-05-07T10:19:54Z"'
        assert task.hasDueTime: 'Expected task has due time'
        assert !task.completed: 'Expected blank task completed'
        assert !task.deleted: 'Expected blank task deleted'
        assert task.estimate.equals('4 hours'): 'Expected estimate of "4 hours"'
        assert task.repeat.equals('FREQ=DAILY;INTERVAL=1'): 'Expected repeat "FREQ=DAILY;INTERVAL=1"'
        assert task.url.equals('http://eriwen.com'): 'Expected URL "http://eriwen.com"'
        assert task.locationId.equals('234'): 'Expected location ID "234"'
        assert task.priority.equals('2'): 'Expected priority of "2"'
        assert task.tags.join(', ').equals('yay, bananas'): 'Expected tags of "yay, bananas"'
    }

    @Test void testParseTaskList() {
        GPathResult resp = new XmlSlurper().parseText('''
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
        ''')
        def tasks = instance.parseTaskList(resp)
        assert tasks instanceof List : 'Expected List return type but got ' + tasks.class.toString()
        assert tasks.size() == 3 : 'Expected 3 tasks returned but got ' + tasks.size()
        assert tasks[0].name.equals('Get Bananas') : 'Expected first task name "Get Bananas" but got ' + tasks[0].name
        assert tasks[0].listId.equals('100') : 'Expected first task list id of "100" but got ' + tasks[0].list_id
        assert tasks[0].taskSeriesId.equals('101'): 'Expected task series ID of "101" but got ' + tasks[0].taskseries_id
        assert tasks[0].taskId.equals('102'): 'Expected task ID of "102" but got ' + tasks[0].task_id
        assert tasks[0].due.equals('') : 'Expected blank due date but got ' + tasks[0].due
        assert !tasks[0].hasDueTime : 'Expected false hasDueTime but got ' + tasks[0].has_due_time
        assert tasks[0].estimate.equals('') : 'Expected blank estimate date but got ' + tasks[0].estimate
        assert tasks[0].priority.equals('N') : 'Expected first task priority of "N" but got ' + tasks[0].priority
    }

    @Test void testParseList() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="234"/>
                <list id="123" name="New List" deleted="0" locked="1" archived="0" position="-1" smart="0"/>
            </rsp>
        ''')
        def list = instance.parseList(resp)
        assert list instanceof TaskList : 'Expected TaskList returned but got ' + list.class.toString()
        assert list.transactionId == '234' : 'Expected transaction ID "234"'
        assert list.id.equals('123') : 'expected list ID "123"'
        assert list.name.equals('New List') : 'expected list name "New List"'
        assert !list.deleted : 'expected deleted of "0"'
        assert list.locked : 'expected locked of "1"'
        assert !list.archived : 'expected archived of "0"'
        assert list.position.equals('-1') : 'expected position of "-1"'
        assert !list.smart : 'expected smart of "0"'
    }

    @Test void testParseLists() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <lists>
                    <list id="123" name="Inbox" deleted="0" locked="1" archived="0" position="-1" smart="0"/>
                    <list id="124" name="Other List" deleted="0" locked="0" archived="0" position="0" smart="1"/>
                </lists>
            </rsp>
        ''')
        def lists = instance.parseLists(resp)
        assert lists instanceof List : 'Expected List returned but got ' + lists.class.toString()
        assert lists.size() == 2 : 'Expected 2 lists but got ' + lists.size()
        assert lists[1].id.equals('124') : 'expected list ID "124"'
        assert lists[1].name.equals('Other List') : 'expected list name "Other List"'
        assert !lists[1].deleted : 'expected list not deleted'
        assert !lists[1].locked : 'expected list not locked'
        assert !lists[1].archived : 'expected list not archived'
        assert lists[1].position.equals('0') : 'expected position of "0"'
        assert lists[1].smart : 'expected smart list'
    }

    @Test void testParseNote() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <transaction id="234"/>
                <note id="169624" created="2009-05-07T11:26:49Z" modified="2009-05-07T11:26:49Z" title="Note Title">Note Body</note>
            </rsp>
        ''')
        def note = instance.parseNote(resp)
        assert note : 'Note should not be null'
        assert note instanceof Note : 'Expected Note return type, but got ' + note.class.toString()
        assert note.transactionId.equals('234') : 'Expected note transaction ID of "234"'
        assert note.id.equals('169624') : 'Expected note ID of "169624"'
        assert note.title.equals('Note Title') : 'Expected note title of "Note Title"'
        assert note.text.equals('Note Body') : 'Expected note text of "Note Body"'
    }

    @Test void testParseSettings() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <settings>
                    <timezone>Australia/Sydney</timezone>
                    <dateformat>0</dateformat>
                    <timeformat>0</timeformat>
                    <defaultlist>123456</defaultlist>
                </settings>
            </rsp>
        ''')
        def settings = instance.parseSettings(resp)
        assert settings != null : 'Settings should not be null'
        assert settings instanceof LinkedHashMap : 'Expected LinkedHashMap return type, but got ' + settings.class.toString()
        settings.each { key,val ->
            assert val instanceof String : 'Expected String map value, but got ' + val.class.toString()
        }
        assert settings['timezone'].equals('Australia/Sydney'): 'Expected timezone "Australia/Sydney"'
        assert settings['dateformat'].equals('0'): 'Expected dateformat of "0"'
        assert settings['timeformat'].equals('0'): 'Expected timeformat of "0"'
        assert settings['defaultlist'].equals('123456'): 'Expected default list "123456"'
    }

    @Test void testParseTimezones() {
        GPathResult resp = new XmlSlurper().parseText('''
            <rsp stat="ok">
                <timezones>
                  <timezone id="216" name="Asia/Hong_Kong" dst="0" offset="28800" current_offset="25200" />
                  <timezone id="217" name="Asia/Hovd" dst="1" offset="28800" current_offset="25200" />
                  <timezone id="226" name="Asia/Kashgar" dst="0" offset="28800" current_offset="28800" />
                  <timezone id="228" name="Asia/Krasnoyarsk" dst="1" offset="28800" current_offset="25200" />
                  <timezone id="229" name="Asia/Kuala_Lumpur" dst="0" offset="28800" current_offset="28800" />
                  <timezone id="230" name="Asia/Kuching" dst="0" offset="28800" current_offset="28800" />
                </timezones>
            </rsp>
        ''')
        def timezones = instance.parseTimezones(resp)
        assert timezones instanceof List : 'Expected List return type, but got ' + timezones.class.toString()
        assert timezones.size() == 6 : 'Expected 6 timezones but got ' + timezones.size()
        assert timezones[0].id.equals('216') : 'Expected timezone ID "216" but got ' + timezones[0].id
        assert timezones[0].name.equals('Asia/Hong_Kong') : 'Expected timezone name "Asia/Hong_Kong" but got ' + timezones[0].name
        assert timezones[0].dst.equals('0') : 'Expected timezone DST "0" but got ' + timezones[0].dst
        assert timezones[0].offset.equals('28800') : 'Expected timezone offset "28800" but got ' + timezones[0].offset
        assert timezones[0].currentOffset.equals('25200') : 'Expected timezone current offset "25200" but got ' + timezones[0].currentOffset
    }
}

