package icu.neurospicy.fibi.application

import icu.neurospicy.fibi.domain.model.Channel
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.outgoing.signal.SignalMessageSender
import org.quartz.Job
import org.quartz.JobExecutionContext

class GeneratingMessageJob(
    private val signalMessageSender: SignalMessageSender
) : Job {
    override fun execute(context: JobExecutionContext) {
        val jobData = context.jobDetail.jobDataMap
        Channel.valueOf(jobData.getString("channel")).takeIf { it == Channel.SIGNAL }?.let {
            val receiver = FriendshipId(jobData.getString("receiver"))
            signalMessageSender.sendTyping(receiver)
        }
    }
}