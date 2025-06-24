package icu.neurospicy.fibi.calendar.sync

import icu.neurospicy.fibi.domain.model.CalendarConfigId
import icu.neurospicy.fibi.domain.model.FriendshipId
import org.apache.camel.builder.RouteBuilder
import org.apache.camel.component.exec.ExecResult
import org.springframework.stereotype.Component


/**
 * VdirsyncerExecRoute listens on the "direct:vdirsyncerSync" endpoint.
 *
 * It ensures that a "discover" command is executed before running "sync".
 */
@Component
class VdirsyncerSyncExecRoute : RouteBuilder() {
    override fun configure() {
        from("direct:vdirsyncerSync")
            .routeId("vdirsyncerSyncExecRoute")
            .filter { it.`in`.body is CalendarSyncRequest }
            .process { exchange ->
                val calendarSyncRequest = exchange.message.body as CalendarSyncRequest
                exchange.setProperty("friendshipId", calendarSyncRequest.friendshipId)
                exchange.setProperty("calendarConfigId", calendarSyncRequest.calendarConfigId)
                exchange.setProperty("configFilePath", calendarSyncRequest.configFilePath)
                exchange.setProperty("discoveryShFilePath", calendarSyncRequest.discoveryShFilePath)
                exchange.setProperty("calendarsDir", calendarSyncRequest.calendarsDir)
                log.info("Executing vdirsyncer sync for: ${exchange.getProperty("calendarConfigId")}")
                exchange.message.setHeader("CamelExecCommandArgs", "-c ${exchange.getProperty("configFilePath")} sync")
            }
            .to("exec:vdirsyncer")
            .process { exchange ->
                val execResult = exchange.message.body as ExecResult
                exchange.setProperty("failed", execResult.exitValue != 0)
                log.info("vdirsyncer sync completed ${if (exchange.getProperty("failed") as Boolean) "with error" else "successfully"}.")
                log.debug(execResult.stdout?.readAllBytes()?.decodeToString())
                log.error(execResult.stderr?.readAllBytes()?.decodeToString())
                exchange.getIn().body = CalendarsConvertIcsRequest(
                    friendshipId = exchange.getProperty("friendshipId") as FriendshipId,
                    calendarConfigId = exchange.getProperty("calendarConfigId") as CalendarConfigId,
                    calendarsDir = exchange.getProperty("calendarsDir") as String
                )
            }
            .filter { !(it.getProperty("failed") as Boolean) }
            .to("direct:processCalendarDirectory")
    }
}