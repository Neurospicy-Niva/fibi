Feature: Appointment Rework - Follow-Up Tasks

  As a Friend of Fibi,
  I want to be asked after an appointment if there are related follow-up tasks,
  So that I don't forget them.

  Scenario: Friend is asked for follow-up
    Given a Friend
    And they have a WebDAV calendar
    And they have the appointment "Pick up Miro from school" ending in 2 minutes
    When they send "Please remind me after picking up kids from school events to ask for his homework" to Fibi
    Then they eventually receive a message containing "Set reminder"
    And they send "Let's register my calendar" to Fibi
    Then they eventually receive a message containing "caldav"
    And they send their CalDAV URL to Fibi
    Then they eventually receive a calendar successfully added message containing appointments
    And the end of the appointment is reached
    Then they eventually receive a message containing "homework"
    When they send "Yes, I need to call the clinic. Add this task." to Fibi
    Then they eventually receive a task added confirmation
