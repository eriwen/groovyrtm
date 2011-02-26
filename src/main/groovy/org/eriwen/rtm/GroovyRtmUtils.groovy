/*
 *  Copyright 2010-2011 Eric Wendelin
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

import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.text.ParseException
import java.text.SimpleDateFormat
import org.apache.commons.codec.binary.Hex
import groovy.util.slurpersupport.GPathResult

/**
 * Utilities that are common to API elements without being tied to a specific
 * RTM API method.
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */
public class GroovyRtmUtils {
    private static MessageDigest digest
    private static final String encoding = 'UTF-8'
    private SimpleDateFormat rawDueDateFormat = new SimpleDateFormat('yyyy-MM-ddHH:mm:ss')
    private SimpleDateFormat defaultDateFmt = new SimpleDateFormat('MMM dd')
    private SimpleDateFormat dayOfWeekFmt = new SimpleDateFormat('EEEE')
    private SimpleDateFormat timeOfDayFmt = new SimpleDateFormat('HH:mm:ss')
    private SimpleDateFormat friendlyTimeFmt = new SimpleDateFormat('H:mm')
    int dayInMillis = 3600000 * 24
    int weekInMillis = dayInMillis * 7

    /*
    public def smartComparator = [
        compare: { Task o1, Task o2 ->
            if (o1.due == null || o1.due == "") return 1;
            if (o2.due == null || o2.due == "") return -1;
            if (o1.compareTo(o2) == 0) {
                if (o1.priority == "N") return 1;
                if (o2.priority == "N") return -1;
                return Integer.parseInt(o1.priority).compareTo(Integer.parseInt(o2.priority));
            }
            return o1.due.compareTo(o2.due)
        }
    ] as Comparator

    public def dueComparator = [
        compare: { Task o1, Task o2 ->
            if (o1.due == null || o1.due == "") return 1;
            if (o2.due == null || o2.due == "") return -1;
            return o1.due.compareTo(o2.due);
        }
    ] as Comparator

    public def priorityComparator = [
        compare: { Task o1, Task o2 ->
            if (o1.priority == "N") return 1;
            if (o2.priority == "N") return -1;
            //Higher number ~= lower priority
            return Integer.parseInt(o1.priority).compareTo(Integer.parseInt(o2.priority));
        }
    ] as Comparator

    public def nameComparator = [
        compare: { Task o1, Task o2 ->
            return o1.name.compareTo(o2.name)
        }
    ] as Comparator*/

    GroovyRtmUtils() {
        setMD5Digest()
    }

    /**
     * Gets the MD5 <code>MessageDigest</code> for signing API calls
     */
    private setMD5Digest() throws NoSuchAlgorithmException {
        digest = MessageDigest.getInstance('md5');
    }

    /**
     * Given a <code>List</code> of parameters, returns the RTM API signature
     *
     * @param params <code>List</code> of URL query parameters
     * @param secret the shared secret provided with an RTM API key
     * @return <code>String</code> API Signature
     */
    public String getApiSignature(List params, String secret) {
        digest.reset()
        digest.update(secret.getBytes(encoding))
        Collections.sort(params)
        params.each {
            //Remove '=' from parameters and decode them
            def param = URLDecoder.decode((it =~ /\=/).replaceAll(''), encoding)
            digest.update(param.getBytes(encoding))
        }
        return new String(Hex.encodeHex(digest.digest()))
    }

    /**
     * Given a URL, gets the response text or null if a server error occurred
     *
     * @param urlstr String representation of the URL to use for the call to the RTM REST API
     * @return String XML result from the RTM call
     * @throws GroovyRtmException when the HTTP request failed
     */
    public String getResponseText(String urlstr) throws GroovyRtmException {
        def connection = urlstr.toURL().openConnection()
        if (connection.responseCode == 200) {
            def responseText = connection.content.text
            return responseText
        } else if (connection.responseCode == -1) {
            throw new GroovyRtmException("Invalid URL: '${urlstr}' -- Ususally caused by not setting an API Key")
        }
        throw new GroovyRtmException("Server returned response code ${connection.responseCode}: ${connection.responseMessage}")
    }

    /**
     * Given a URL, gets the response text or throws an error if any problem,
     * including an error code response from RTM occurs
     *
     * @param urlstr String representation of the URL to use for the call to the RTM REST API
     * @return GPathResult XML result from the RTM call
     * @throws GroovyRtmException when RTM sends an error code
     */
    public GPathResult getRtmResponse(String urlstr) throws GroovyRtmException {
        String responseText = getResponseText(urlstr).trim()
        GPathResult resp = new XmlSlurper().parseText(responseText)
        if (!resp.@stat.equals("ok")) {
            throw new GroovyRtmException(resp.err.@msg.toString())
        }
        return resp
    }

    /**
     * Checks if RTM returned error-flagged XML
     *
     * @param result RTM REST API response XML
     * @return True if REST response indicates an error
     */
    public boolean isError(String responseText) {
        GPathResult resp = new XmlSlurper().parseText(responseText)
        !resp.@stat.equals("ok")
    }

    /**
     * Gets the error message from the RTM response
     *
     * @param result RTM REST API response XML
     * @return String message
     */
    public String getErrorMessage(String responseText) {
        GPathResult resp = new XmlSlurper().parseText(responseText)
        resp.err.@msg.toString()
    }

    /**
     * Trim the string if length is greater than specified length
     *
     * @param string The string to truncate
     * @param length The preferred length of the returned String
     */
    public String trimString(String string, int length) {
        if(!string) {
            return '';
        } else if(string.length() > length) {
            return string.substring(0, length).trim();
        }
        return string;
    }

    /**
     * Given a date in the form YYYY-MM-DDTHH:MM:SSZ, return a friendly date
     * like "Tuesday" or "Mar 9" or "11:00AM"
     *
     * @param dateStr the date to parse as a String
     * @return Friendly date like "Tuesday" or "Mar 9" or "11:00AM"
     */
    public String formatFriendlyDate(String dateStr, boolean hasDueTime, Integer timezoneOffset = 0) {
        long nowMillis = getCurrentDayMillisAtMidnight(timezoneOffset);
        Date taskDate;

        //Return 'Never' for nothing or error
        try {
            taskDate = rawDueDateFormat.parse(trimString(dateStr, 10) + dateStr.substring(11, 19));
        } catch (NullPointerException npe) {
            return 'Never';
        } catch (ParseException pe) {
            return 'Never';
        } catch (StringIndexOutOfBoundsException sioobe) {
            //Occurs with dates before 1990 or something
            return 'Never';
        }

        long taskMillis = taskDate.getTime();
        taskDate.setTime(taskMillis + (timezoneOffset * 1000));
        boolean isToday = taskMillis >= nowMillis && taskMillis < nowMillis + dayInMillis;
        String friendlyDate;
        if (taskMillis >= nowMillis && taskMillis < (nowMillis + weekInMillis)) {
            if (taskMillis == nowMillis || (isToday && !hasDueTime)) {
                friendlyDate = 'Today';
            } else if (isToday && hasDueTime) {
                friendlyDate = friendlyTimeFmt.format(taskDate);
            } else if (taskMillis >= nowMillis + dayInMillis && taskMillis < nowMillis + (2 * dayInMillis)) {
                friendlyDate = 'Tomorrow';
            } else {
                //Return day of week if within a week from now
                friendlyDate = dayOfWeekFmt.format(taskDate);
            }
        } else {
            //Otherwise return MMM dd
            friendlyDate = defaultDateFmt.format(taskDate);
        }
        return friendlyDate;
    }
//
//    public String formatFriendlyDate(String dateStr, boolean hasDueTime) {
//        return formatFriendlyDate(dateStr, hasDueTime, 0);
//    }

    /**
     * Given a String representing the RTM repeat, return a friendly repeat
     * String value
     *
     * @param the repeat string to format
     * @return formatted repeat string like "every 3 blahs"
     */
    public String formatFriendlyRepeat(String repeatStr) {
        //Format is FREQ=MONTHLY;INTERVAL=1 or INTERVAL=4;FREQ=DAILY
        if (!repeatStr) {
            return "";
        }
        def repeatTerms = repeatStr.split(";");
        def freq = "";
        def interval = 0;
        for (String repeatTerm : repeatTerms) {
            int equalsPosition = repeatTerm.indexOf("=");
            if (repeatTerm.startsWith("INTERVAL")) {
                interval = Integer.parseInt(repeatTerm.substring(equalsPosition + 1));
            } else {
                freq = repeatTerm.substring(5, repeatTerm.length() - 2).toLowerCase();
                //Everything but days can be converted this way. HOURLY, DAILY, WEEKLY, MONTHLY, YEARLY
                if (freq.equals("dai")) {
                    freq = "day";
                }
            }
        }
        String friendlyRepeat = "every ${interval} ${freq}"
        if (interval > 1) {
            friendlyRepeat += "s";
        }
        return friendlyRepeat;
    }

    /**
     * Given a date string, return if the date is overdue
     *
     * @param dateStr the date to check
     * @return True if the date is overdue (past today ignoring time)
     */
    public boolean isOverdue(String dateStr, int timezoneOffset) {
        //Check for null date
        if (!dateStr || dateStr.length() < 19) {
            return false;
        }
        Date taskDate;
        try {
            taskDate = rawDueDateFormat.parse(trimString(dateStr, 10) + dateStr.substring(11, 19));
            if ((getCurrentDayMillisAtMidnight(timezoneOffset) - taskDate.getTime()) > 0) {
                return true;
            }
            return false;
        } catch (ParseException pe) {
            pe.printStackTrace();
            return false;
        }
    }

    public long getCurrentDayMillisAtMidnight(long timezoneOffset) {
        Calendar now = Calendar.getInstance();
        now.set(Calendar.HOUR_OF_DAY, 0);
        now.set(Calendar.MINUTE, 0);
        now.set(Calendar.SECOND, 0);
        now.set(Calendar.MILLISECOND, 0);
        //Isn't java.util.Calendar great?
        long nowMillis = now.getTimeInMillis();
        nowMillis -= timezoneOffset * 1000;
        return nowMillis;
    }
}
