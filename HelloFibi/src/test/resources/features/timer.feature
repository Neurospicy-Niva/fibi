Feature: Timer Management
  As a Friend of Fibi,
  I want to set short, countdown-based timers,
  So that I get a notification when the time is up.

  Scenario: Friend sets a timer for tea
    Given a Friend
    When they send "Please set a timer for 1:22 minutes to 'Look for the tea'" to Fibi
    Then they eventually receive a set timer confirmation
    Then they eventually receive a message containing "Look for the tea"

  Scenario: Friend lists active timers
    Given a Friend
    When they send "Set a 30-minute timer with text 'Make cake for the birthday'" to Fibi
    Then they eventually receive a set timer confirmation
    When they send "Show my active timers in a listing with all unaltered information" to Fibi
    Then they eventually receive a message containing "cake"

  Scenario: Canceling a running timer
    Given a Friend
    When they send "Give me a 30-minute timer for 'Pizza is ready'" to Fibi
    Then they eventually receive a set timer confirmation
    When they send "Cancel the 'Pizza is ready' timer" to Fibi
    Then they eventually receive a message containing "Removed timer"

  Scenario: Friend modifies an active timer
    Given a Friend
    When they send "Set a 30-minute timer: 'Fly to the moon'" to Fibi
    Then they eventually receive a set timer confirmation
    When they send "Please change the timer from 'Fly to the moon' to 'Fly me to the moon'" to Fibi
    Then they eventually receive a message containing "Updated timer"
    When they send "Do I have any timers? Which one?" to Fibi
    Then they eventually receive a message containing "Fly me to the moon"
