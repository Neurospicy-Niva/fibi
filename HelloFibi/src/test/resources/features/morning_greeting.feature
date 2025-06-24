Feature: Morning Greeting

  As a Friend of Fibi,
  I want to receive a warm good morning message at my specified wake-up time,
  So that I start my day feeling welcomed and in control.

  Scenario: Friend receives a morning greeting and is asked if they want to see their schedule
    Given a Friend
    And they send "I want to set up a morning routine" to Fibi
    Then they eventually receive a message containing "when"
    When they send a wake-up time 75 seconds ahead
    Then they eventually receive a message containing "Your morning routine is now configured"
    And they send their current time
    When the scheduled wake-up time is reached
    Then they eventually receive a message containing "Good morning"


  Scenario: Friend receives a morning greeting and is asked if they want to see their schedule
    Given a Friend
    And they have a registered calendar with 4 appointments
    And they send "I want to set up a morning routine" to Fibi
    Then they eventually receive a message containing "when"
    When they send a wake-up time 75 seconds ahead
    Then they eventually receive a message containing "Your morning routine is now configured"
    And the scheduled wake-up time is reached
    Then they eventually receive a message containing "Good morning"
    When they send "Good morning Fibi. I am feeling very well! Provide my appointments for today." to Fibi
    Then they eventually receive a list of their appointments
    When they send "Let's have a look at the tasks, too." to Fibi
    Then they eventually receive a list of the tasks
