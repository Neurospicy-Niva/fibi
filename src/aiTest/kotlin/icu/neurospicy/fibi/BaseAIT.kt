package icu.neurospicy.fibi

import icu.neurospicy.fibi.domain.model.AcceptedAgreement
import icu.neurospicy.fibi.domain.model.FriendshipId
import icu.neurospicy.fibi.domain.model.SignalId
import icu.neurospicy.fibi.domain.repository.*
import io.mockk.impl.annotations.SpyK
import io.mockk.junit5.MockKExtension
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.ApplicationEventPublisher
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import java.time.Instant.now
import java.util.*

@SpringBootTest(classes = [TestFibiApplication::class])
@ActiveProfiles("ai-test")
@ExtendWith(MockKExtension::class)
class BaseAIT {

    @Autowired
    @SpyK
    internal lateinit var eventPublisher: ApplicationEventPublisher

    @Autowired
    lateinit var friendshipLedger: FriendshipLedger

    @Autowired
    lateinit var taskRepository: TaskRepository

    @Autowired
    lateinit var reminderRepository: ReminderRepository

    @Autowired
    lateinit var conversationRepository: ConversationRepository

    @Autowired
    @SpyK
    lateinit var calendarRepository: CalendarRepository

    var friendshipId: FriendshipId = FriendshipId()

    @BeforeEach
    internal fun setUp() {
        // Ensure shared containers are initialized before each test
        SharedTestContainers.initialize()
        
        val ledgerEntry = friendshipLedger.addEntry(SignalId(UUID.randomUUID()), "Jane", "+0000123456789")
        friendshipId = ledgerEntry.friendshipId
        friendshipLedger.acceptTermsOfUse(friendshipId, AcceptedAgreement("TOS 1.2", now(), "Yes."))
    }

    @AfterEach
    internal fun tearDown() {
        friendshipLedger.findAllIds().forEach {
            friendshipLedger.deniedTermsOfUse(it)
        }
    }

    companion object {
        /**
         * Configure dynamic properties using the shared containers
         */
        @DynamicPropertySource
        @JvmStatic
        fun datasourceProperties(registry: DynamicPropertyRegistry) {
            // Use the shared containers to configure all properties
            SharedTestContainers.configureProperties(registry)
        }
    }
}
