package org.eriwen.rtm.test

import org.junit.After
import org.junit.Before
import org.junit.Test
import static org.junit.Assert.*

import org.eriwen.rtm.GroovyRtmUtils

/**
 * Unit test class for <code>org.eriwen.rtm.GroovyRtmUtils</code>
 *
 * @author <a href="http://eriwen.com">Eric Wendelin</a>
 */

class GroovyRtmUtilsTest {
    private static GroovyRtmUtils instance = null
    private static final String RAW_DATE_FORMAT = "yyyy-MM-dd"
    private static final String RAW_TIME_FORMAT = "HH:mm:ss"

    @Before void setUp() {
        instance = new GroovyRtmUtils()
    }
    @After void tearDown() {
        instance = null
    }

    @Test void testGetApiSignature() {
        def params = ["method=rtm.tasks.add", "api_key=1234", "auth_token=ABCD", "name=Test"]
        def secret = "4567"
        def apiSig = instance.getApiSignature(params, secret)
        assert apiSig instanceof String : 'Expected String but got ' + apiSig.class.toString()
        assert apiSig.equals('334cb5cd554cf5d1ffc593d3fa9252fe') : 'Wrong api signature'
    }

    @Test void testGetResponseText() {
        def response = instance.getResponseText('http://www.eriwen.com')
        assert response instanceof String : 'Expected String response but got ' + response.class.toString()
        assert response : 'Expected content returned but got nothing'
    }

    @Test void testIsError() {
        assert instance.isError('''
            <rsp stat="fail">
              <err code="98" msg="Login failed / Invalid auth token" />
            </rsp>
        ''')
        assert !instance.isError('''
            <rsp stat="ok">
              <frob>123456</frob>
            </rsp>
        ''')
        assert !instance.isError('<rsp stat="ok"/>')
    }

    @Test void testGetErrorMessage() {
        def errMsg = instance.getErrorMessage('''
            <rsp stat="fail">
              <err code="98" msg="Login failed / Invalid auth token" />
            </rsp>
        ''')
        assert errMsg instanceof String : 'Expected String return type but got ' + errMsg.class.toString()
        assert errMsg.equals('Login failed / Invalid auth token') : 'Wrong error message text'
    }

    @Test void testTrimString() {
        assertEquals 'Trim length is wrong', instance.trimString('12345', 3), '123'
        assertEquals 'Bad null processing', instance.trimString(null, 3), ''
        assertEquals 'Bad blank processing', instance.trimString('', 5), ''
    }

    @Test void testFormatFriendlyDate() {
        Date now = new Date()
        assertEquals 'Today', instance.formatFriendlyDate("${now.format(RAW_DATE_FORMAT)}T${now.format(RAW_TIME_FORMAT)}Z", false)
        assertEquals "${now.getHours().toString().padLeft(2, "0")}:${now.getMinutes().toString().padLeft(2, "0")}".toString(), instance.formatFriendlyDate("${now.format(RAW_DATE_FORMAT)}T${now.format(RAW_TIME_FORMAT)}Z", true)
        
        now++ //Add a day
        assertEquals 'Tomorrow', instance.formatFriendlyDate("${now.format(RAW_DATE_FORMAT)}T${now.format(RAW_TIME_FORMAT)}Z", false)

        Date janThird = new Date(1262502000000)
        assertEquals 'Jan 03', instance.formatFriendlyDate("${janThird.format(RAW_DATE_FORMAT)}T${janThird.format(RAW_TIME_FORMAT)}Z", false)

        assertEquals 'Never', instance.formatFriendlyDate(null, false)
    }

    @Test void testFormatFriendlyRepeat() {
        assertEquals 'every 1 month', instance.formatFriendlyRepeat('FREQ=MONTHLY;INTERVAL=1')
        assertEquals 'every 4 days', instance.formatFriendlyRepeat('INTERVAL=4;FREQ=DAILY')
        assertEquals 'every 2 weeks', instance.formatFriendlyRepeat('INTERVAL=2;FREQ=WEEKLY')
        assertEquals 'every 1 year', instance.formatFriendlyRepeat('FREQ=YEARLY;INTERVAL=1')
    }

    @Test void testIsOverdue() {
        Date now = new Date()
        assert !(instance.isOverdue("${now.format(RAW_DATE_FORMAT)}T${now.format(RAW_TIME_FORMAT)}Z", 0))

        now-- //Subtract a day
        assert instance.isOverdue("${now.format(RAW_DATE_FORMAT)}T${now.format(RAW_TIME_FORMAT)}Z".toString(), 0)
    }
}
