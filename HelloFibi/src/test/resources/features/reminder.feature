Feature: Reminder Management
  As a Friend of Fibi,
  I want to set time-based reminders,
  So that I get a notification at a specific time or date.

  Scenario: Friend sets a same-day reminder
    Given a Friend
    When they send "Remind me at <now plus 1:30 minutes> to 'Write a letter to Aunt Erna'" to Fibi
    Then they eventually receive a set reminder confirmation
    When wait till the reminder time is reached
    Then they eventually receive "Write a letter to Aunt Erna"

  Scenario: Friend lists active reminders
    Given a Friend
    When they send "Remind me at 15:00 to 'Call mom'" to Fibi
    Then they eventually receive a set reminder confirmation
    When they send "Show my reminders" to Fibi
    Then they eventually receive a message containing "Call mom"

  Scenario: Update a reminder
    Given a Friend
    When they send "Remind me at 21:00 to do the dishes" to Fibi
    Then they eventually receive a set reminder confirmation
    When they send "Update the reminder for doing the dishes to doing homework" to Fibi
    Then they eventually receive a message containing "Updated reminder"

  Scenario: Canceling a reminder
    Given a Friend
    When they send "Remind me at 21:00 to do the dishes" to Fibi
    Then they eventually receive a set reminder confirmation
    When they send "Cancel the 21:00 dishes reminder" to Fibi
    Then they eventually receive a message containing "Removed reminder"
