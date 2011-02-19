/*
 *  Copyright 2009-2011 Eric Wendelin
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

import org.eriwen.rtm.model.*
import groovy.util.slurpersupport.GPathResult

/**
 * Utility class used to house RTM XML parsing methods
 * 
 * @author @author <a href="http://eriwen.com">Eric Wendelin</a>
 */
class RtmCollectionParser {

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing an
     * RTM Task. For instance, the XML:
     * 
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;transaction id="123"/&gt;
     *       &lt;list id="100"&gt;
     *         &lt;taskseries id="101" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
     *                    name="Get Bananas" source="api" location_id="234" url="http://eriwen.com"&gt;
     *           &lt;rrule every="1"&gt;FREQ=DAILY;INTERVAL=1&lt;/rrule&gt;
     *           &lt;tags&gt;&lt;tag&gt;yay&lt;/tag&gt;&lt;tag&gt;bananas&lt;/tag&gt;&lt;/tags&gt;
     *           &lt;participants/&gt;
     *           &lt;notes/&gt;
     *           &lt;task id="102" due="2009-05-07T10:19:54Z" has_due_time="1" added="2009-05-07T10:19:54Z"
     *                completed="" deleted="" priority="2" postponed="0" estimate="4 hours"/&gt;
     *         &lt;/taskseries&gt;
     *       &lt;/list&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *   
     * Will become:
     *
     * ['transaction_id':'123', 'name':'Get Bananas',
     *      'list_id':'100', 'taskseries_id':'101', 'task_id':'102',
     *      'priority': '2', 'due':'2009-05-07T10:19:54Z', 'has_due_time':'1',
     *      'completed':'', 'deleted':'', 'estimate':'4 hours', 'repeat':'FREQ=DAILY;INTERVAL=1',
     *      'url':'http://eriwen.com', 'location':'234', 'tags':'yay, bananas',
     *      'notes':'', 'participants':'']
     */
    public Task parseTask(GPathResult taskXml) {
        if (!taskXml) {
            return null
        }
        def taskSeries = taskXml.list.taskseries
        def task = taskSeries.task
        def transactionId = taskXml.transaction ? taskXml.transaction.@id.toString() : ''
        return new Task(
            transactionId: transactionId,
            name: taskSeries.@name.toString(),
            listId: taskXml.list.@id.toString(),
            taskSeriesId: taskSeries.@id.toString(),
            taskId: task.@id.toString(),
            due: task.@due.toString(),
            hasDueTime: task.@has_due_time.toString().equals('1'),
            completed: !task.@completed.toString().equals(''),
            priority: task.@priority.toString(),
            deleted: !task.@deleted.toString().equals(''),
            postponed: task.@postponed.toString().equals('1'),
            estimate: task.@estimate.toString(),
            repeat: taskSeries.rrule.text().toString(),
            url: taskSeries.@url.toString(),
            locationId: taskSeries.@location_id.toString(),
            tags: taskSeries.tags.tag.collect{ it.text() },
            notes: taskSeries.notes.collect{ new Note(id: it.@id.toString(), title: it.@title.toString(), text: it.text() ) }, 
            participants: taskSeries.participants.participant.collect{ it.text() }
        )
    }

    /**
     * Creates a <code>java.util.List</code> of LinkedHashMaps representing RTM
     * tasks from the XML returned by the RTM API. For example:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *     &lt;tasks&gt;
     *       &lt;list id="100"&gt;
     *         &lt;taskseries id="101" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
     *                    name="Get Bananas" source="api"&gt;
     *           &lt;tags/&gt;
     *           &lt;participants/&gt;
     *           &lt;notes/&gt;
     *           &lt;task id="102" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
     *                completed="" deleted="" priority="N" postponed="0" estimate=""/&gt;
     *         &lt;/taskseries&gt;
     *         &lt;taskseries id="103" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
     *                    name="Buy Coffee" source="api"&gt;
     *           &lt;tags&gt;
     *               &lt;tag&gt;mmm&lt;/tag&gt;
     *               &lt;tag&gt;tasty&lt;/tag&gt;
     *           &lt;/tags&gt;
     *           &lt;participants/&gt;
     *           &lt;notes/&gt;
     *           &lt;task id="104" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
     *                completed="" deleted="" priority="2" postponed="1" estimate="3 hrs"/&gt;
     *         &lt;/taskseries&gt;
     *       &lt;/list&gt;
     *       &lt;list id="105"&gt;
     *         &lt;taskseries id="106" created="2009-05-07T10:19:54Z" modified="2009-05-07T10:19:54Z"
     *                    name="Do Some Work" source="api"&gt;
     *           &lt;tags/&gt;
     *           &lt;participants/&gt;
     *           &lt;notes/&gt;
     *           &lt;task id="107" due="" has_due_time="0" added="2009-05-07T10:19:54Z"
     *                completed="" deleted="" priority="3" postponed="0" estimate="4 hrs"/&gt;
     *         &lt;/taskseries&gt;
     *       &lt;/list&gt;
     *     &lt;/tasks&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     *
     * [
     *     ['name':'Get Bananas', 'list_id':'100', 'taskseries_id':'101', 'task_id':'102', 'priority': 'N', 'due':'', 'has_due_time':'0', 'completed':'', 'deleted':'', 'estimate':'', 'repeat':'', 'url':'', 'location':'', 'tags':'', 'notes':'', 'participants':''],
     *     ['name':'Buy Coffee', 'list_id':'100', 'taskseries_id':'103', 'task_id':'104', 'priority': '2', 'due':'', 'has_due_time':'0', 'completed':'', 'deleted':'', 'estimate':'3 hrs', 'repeat':'', 'url':'', 'location':'', 'tags':'mmm, good', 'notes':'', 'participants':''],
     *     ['name':'Do Some Work', 'list_id':'105', 'taskseries_id':'106', 'task_id':'107', 'priority': '3', 'due':'', 'has_due_time':'0', 'completed':'', 'deleted':'', 'estimate':'4 hrs', 'repeat':'', 'url':'', 'location':'', 'tags':'', 'notes':'', 'participants':'']
     * ]
     */
    public List<Task> parseTaskList(GPathResult response) {
        if (!response) {
            return []
        }
        def tasksList = []
        response.tasks.list.each {
            def listId = it.@id.toString()
            it.taskseries.each {
                def taskSeries = it
                def taskName = taskSeries.@name.toString()
                def taskSeriesId = taskSeries.@id.toString()
                def repeat = taskSeries.rrule.text().toString()
                def url = taskSeries.@url.toString()
                def locationId = taskSeries.@location_id.toString()
                def tags = taskSeries.tags.tag.collect{ it.text() }
                def notes = null //taskSeries.notes.collect{ it.text() }
                def participants = taskSeries.participants.participant.collect{ it.text() }
                it.task.each {
                    def task = it
                    tasksList << new Task(
                        name: taskName,
                        listId: listId,
                        taskSeriesId: taskSeriesId,
                        repeat: repeat,
                        url: url, 
                        locationId: locationId,
                        tags: tags, 
                        notes: notes,
                        participants: participants,
                        taskId: task.@id.toString(),
                        due: task.@due.toString(),
                        hasDueTime: task.@has_due_time.toString().equals('1'),
                        completed: !task.@completed.toString().equals(''),
                        priority: task.@priority.toString(),
                        deleted: !task.@deleted.toString().equals(''),
                        postponed: task.@postponed.toString().equals('1'),
                        estimate: task.@estimate.toString()
                    )
                }
            }
        }
        return tasksList
    }

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing an
     * RTM Note. For instance, the XML:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;transaction id="234"/&gt;
     *       &lt;note id="169624" created="2009-05-07T11:26:49Z" modified="2009-05-07T11:26:49Z" title="Note Title"&gt;Note Body&lt;/note&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     *
     * ['transaction_id':'234', 'id':'169624', 'title':'Note Title', 'text':'Note Body']
     */
    public Note parseNote(GPathResult noteXml) {
        if (!noteXml) {
            return null
        }
        def note = noteXml.note
        return new Note(
            transactionId: noteXml.transaction.@id.toString(),
            id: note.@id.toString(),
            title: note.@title.toString(),
            text: note.text().toString()
        )
    }

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing an
     * RTM List. For instance, the XML:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;transaction id="234"/&gt;
     *       &lt;list id="123" name="New List" deleted="0" locked="0" archived="0" position="-1" smart="0"/&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     *
     * ['transaction_id':'234', 'id':'123', 'name':'New List', 'deleted':'0',
     *      'locked':'0', 'archived':'0', 'position':'-1', 'smart':'0']
     */
    public TaskList parseList(GPathResult listXml) {
        if (!listXml) {
            return null
        }
        def list = listXml.list
        return new TaskList(
            transactionId: listXml.transaction.@id.toString(),
            id: list.@id.toString(),
            name: list.@name.toString(),
            deleted: list.@deleted.toString().equals('1'),
            locked: list.@locked.toString().equals('1'),
            archived: list.@archived.toString().equals('1'),
            position: list.@position.toString(),
            smart: list.@smart.toString().equals('1')
        )
    }

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing an
     * RTM List. For instance, the XML:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;lists&gt;
     *       &lt;list id="123" name="New List" deleted="0" locked="0" archived="0" position="-1" smart="0"/&gt;
     *       &lt;list id="124" name="Other List" deleted="0" locked="0" archived="0" position="0" smart="1"/&gt;
     *       &lt;/lists&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     * [
     *      ['id':'123', 'name':'New List', 'deleted':'0', 'locked':'0', 'archived':'0', 'position':'-1', 'smart':'0']
     *      ['id':'124', 'name':'Other List', 'deleted':'0', 'locked':'0', 'archived':'0', 'position':'0', 'smart':'1']
     * ]
     */
    public List<TaskList> parseLists(GPathResult listsXml) {
        if (!listsXml) {
            return null
        }
        def listsList = []
        listsXml.lists.list.each {
            def list = it
            listsList << new TaskList(
                id: list.@id.toString(),
                name: list.@name.toString(),
                deleted: list.@deleted.toString().equals('1'),
                locked: list.@locked.toString().equals('1'),
                archived: list.@archived.toString().equals('1'),
                position: list.@position.toString(),
                smart: list.@smart.toString().equals('1')
            )
        }
        return listsList
    }

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing RTM
     * settings. For instance, the XML:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;settings&gt;
     *           &lt;timezone&gt;Australia/Sydney&lt;/timezone&gt;
     *           &lt;dateformat&gt;0&lt;/dateformat&gt;
     *           &lt;timeformat&gt;0&lt;/timeformat&gt;
     *           &lt;defaultlist&gt;123456&lt;/defaultlist&gt;
     *       &lt;/settings&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     *
     * ['timezone':'Australia/Sydney', 'dateformat':'0', 'timeformat': '0', 'defaultlist':'123456']
     */
    public LinkedHashMap<String, String> parseSettings(GPathResult settingsXml) {
        if (!(settingsXml && settingsXml.@stat.equals("ok"))) {
            return null
        }
        def settings = settingsXml.settings
        return [
            'timezone': settings.timezone.toString(),
            'dateformat': settings.dateformat.toString(),
            'timeformat': settings.timeformat.toString(),
            'defaultlist': settings.defaultlist.toString()
        ]
    }

    /**
     * Creates a <code>java.util.LinkedHashMap</code> from XML representing RTM
     * Timezones. For instance, the XML:
     *
     *   <pre>
     *   {@code
     *   &lt;rsp stat="ok"&gt;
     *       &lt;timezones&gt;
     *         &lt;timezone id="216" name="Asia/Hong_Kong" dst="0" offset="28800" current_offset="25200" /&gt;
     *         &lt;timezone id="217" name="Asia/Hovd" dst="1" offset="28800" current_offset="25200" /&gt;
     *       &lt;/timezones&gt;
     *   &lt;/rsp&gt;
     *   }
     *   </pre>
     *
     * Will become:
     *
     * [
     *     ['id':'216', 'name':'Asia/Hong_Kong', 'dst':'0', 'offset','28800', 'currentOffset';'25200'],
     *     ['id':'217', 'name':'Asia/Hovd', 'dst':'1', 'offset','28800', 'currentOffset';'25200']
     * ]
     */
    public List<Timezone> parseTimezones(GPathResult timezonesXml) {
        if (!(timezonesXml && timezonesXml.@stat.equals("ok"))) {
            return null
        }
        def timezones = []
        timezonesXml.timezones.timezone.each {
            timezones.push(new Timezone(id:it.@id.toString(), name:it.@name.toString(),
                    dst:it.@dst.toString(), offset:it.@offset.toString(),
                    currentOffset:it.@current_offset.toString()))
        }
        timezones
    }
}

