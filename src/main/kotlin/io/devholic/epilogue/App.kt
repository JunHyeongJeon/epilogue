package io.devholic.epilogue

import com.amazonaws.services.lambda.runtime.Context
import com.amazonaws.services.lambda.runtime.RequestHandler
import io.devholic.epilogue.entity.Recipient
import io.devholic.epilogue.enum.NaverNewsCategory
import io.devholic.epilogue.repository.KATCRepository
import io.devholic.epilogue.repository.MessageRepository
import io.devholic.epilogue.repository.NaverNewsRepository
import io.devholic.epilogue.repository.SlackRepository
import io.reactivex.Single
import io.reactivex.functions.Function3
import java.io.InputStream


class App : RequestHandler<InputStream, Boolean> {

    companion object {
        const val slackAccessToken = "SLACK_ACCESS_TOKEN"
        const val slackChannelId = "SLACK_CHANNEL_ID"
        const val slackWebhookUrl = "SLACK_WEBHOOK_URL"
        const val slackUserId = "SLACK_USER_ID"
        const val slackUsername = "SLACK_USERNAME"
        const val recipientName = "RECIPIENT_NAME"
        const val recipientBirthday = "RECIPIENT_BIRTHDAY"
        const val recipientEnterDate = "RECIPIENT_ENTERDATE"
    }

    private val katcRepository = KATCRepository()
    private val messageRepository = MessageRepository(
        System.getenv(slackUsername)
    )
    private val naverNewsRepository = NaverNewsRepository()
    private val slackRepository = SlackRepository(
        System.getenv(slackAccessToken),
        System.getenv(slackWebhookUrl)
    )

    override fun handleRequest(input: InputStream, context: Context): Boolean {
        Single.zip(
            Single.zip(
                listOf(
                    NaverNewsCategory.IT,
                    NaverNewsCategory.ENTERTAINMENT,
                    NaverNewsCategory.SOCIETY,
                    NaverNewsCategory.WORLD,
                    NaverNewsCategory.LIFE
                ).map { naverNewsRepository.getHeadlineList(it) },
                {
                    it.map {
                        @Suppress("UNCHECKED_CAST")
                        it as List<String>
                    }.fold(emptyList<String>(), { acc, result -> acc + result })
                }
            ),
            katcRepository.getRecipients(
                System.getenv(recipientName),
                System.getenv(recipientBirthday),
                System.getenv(recipientEnterDate)
            ),
            slackRepository.getSenderId(
                System.getenv(slackChannelId),
                System.getenv(slackUserId)
            ),
            Function3 {
                newsList: List<String>, recipients: List<Recipient>, id: String ->
                Triple(newsList, recipients, id)
            }
        ).flatMapCompletable {
            messageRepository.create(
                it.second,
                it.first,
                it.third
            ).flatMapCompletable {
                slackRepository.send(it)
            }
        }.blockingGet()
        return true
    }
}
