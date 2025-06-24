Feature: Friends connect their calendar to Fibi
  As a Friend of Fibi,
  I add my calendar directly through the chat,
  So that I can receive immediate daily overviews and reminders.

  Scenario: Alex successfully connects a self-hosted Nextcloud calendar
    Given a Friend
    And they have a WebDAV calendar
    And they have 2 appointments today
    And they send "I'd like to connect my calendar" to Fibi
    Then they eventually receive a request to post a CalDAV URL
    When they send their CalDAV URL to Fibi
    Then they eventually receive a calendar successfully added message containing appointments

  Scenario: Alex successfully connects a self-hosted Nextcloud calendar without schedule for today
    Given a Friend
    And they have a WebDAV calendar
    And they send "I'd like to connect my calendar" to Fibi
    Then they eventually receive a request to post a CalDAV URL
    When they send their CalDAV URL to Fibi
    Then they eventually receive a calendar successfully added message without schedule

  Scenario: User cancels calendar connection before providing a link
    Given a Friend
    And they send "I'd like to connect my calendar" to Fibi
    Then they eventually receive a request to post a CalDAV URL
    When they send "I don't want to add a calendar anymore." to Fibi
    Then they eventually receive a calendar activity cancelled message