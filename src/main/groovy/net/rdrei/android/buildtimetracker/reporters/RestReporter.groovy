package net.rdrei.android.buildtimetracker.reporters

import net.rdrei.android.buildtimetracker.Timing
import org.gradle.api.logging.Logger
import groovyx.net.http.HTTPBuilder
import org.gradle.internal.TrueTimeProvider

import java.text.DateFormat
import java.text.SimpleDateFormat

import static groovyx.net.http.ContentType.JSON
import static groovyx.net.http.Method.POST

class RestReporter extends AbstractBuildTimeTrackerReporter {
    RestReporter(Map<String, String> options, Logger logger) {
        super(options, logger)
    }

    List measurements(List<Timing> timings) {
        long timestamp = new TrueTimeProvider().getCurrentTime()

        TimeZone tz = TimeZone.getTimeZone("UTC")
        DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss,SSS'Z'")
        df.setTimeZone(tz)

        def hostname = "unknown"
        try {
            InetAddress localhost = InetAddress.getLocalHost()
            hostname = localhost.getHostName()
        } catch (UnknownHostException exception) {
            // do nothing
        }

        def info = new SysInfo()
        def osId = info.getOSIdentifier()
        def cpuId = info.getCPUIdentifier()
        def maxMem = info.getMaxMemory()
        def measurements = []

        timings.eachWithIndex { it, index ->
            measurements << [
                    timestamp: timestamp,
                    order    : index,
                    task     : it.path,
                    success  : it.success,
                    did_work : it.didWork,
                    skipped  : it.skipped,
                    ms       : it.ms,
                    date     : df.format(new Date(timestamp)),
                    cpu      : cpuId,
                    memory   : maxMem,
                    os       : osId,
                    hostname : hostname,
                    gitSha   : getOption("git", "")
            ]
        }

        return measurements
    }

    @Override
    def run(List<Timing> timings) {
        def measurements = measurements(timings)

        // write to server
        def url = getOption("url", null)
        def http = new HTTPBuilder(url)
        http.request(POST, JSON) { req ->
            body = measurements
            response.success = { resp, json ->
                logger.quiet "Posted results"
            }
            response.failure = {
                logger.quiet "Failed to post results"
            }
        }
    }
}
