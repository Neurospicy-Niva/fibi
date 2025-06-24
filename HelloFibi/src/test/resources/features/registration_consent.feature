Feature: New users register for Fibi and consent to using the service

  Scenario: Drew has ADHD and wants to use Fibi to plan the day better
    Given a user who is new
    When they send "Hello" to Fibi
    Then they eventually receive the initial welcome message
    When they send "Yes" to Fibi
    Then they eventually receive a message containing "Fantastic! I'm excited to help you."

  Scenario: User confirms with an emoji
    Given a user who is new
    When they send "Hello" to Fibi
    Then they eventually receive the initial welcome message
    When they send "👍" to Fibi
    Then they eventually receive a message containing "Fantastic! I'm excited to help you."

  Scenario: User declines to use Fibi
    Given a user who is new
    When they send "Hello" to Fibi
    Then they eventually receive the initial welcome message
    When they send "No, I’m not interested." to Fibi
    Then they eventually receive the denial confirmation message

  Scenario: User asks for more info about terms of service before consenting
    Given a user who is new
    When they send "Hey Fibi" to Fibi
    Then they eventually receive the initial welcome message
    When they send "Hi, I'd like to know more about terms of use before I say yes." to Fibi
    Then they eventually receive a message containing "https://neurospicy.icu/tos"
    When they send "Yes, that's fine." to Fibi
    Then they eventually receive a message containing "Fantastic! I'm excited to help you."


  Scenario Outline: User asks for other things and is reminded to accept TOS
    Given a user who is new
    When they send "Hey Fibi" to Fibi
    Then they eventually receive the initial welcome message
    When they send "<otherThing>" to Fibi
    Then they eventually receive a message not containing "Fantastic! I’m excited to help you."
    When they send "Ok, I accept." to Fibi
    Then they eventually receive the confirmation message

    Examples:
      | otherThing                                |
      | Add 'Call mom' to my tasks                |
      | Remind me to call my mom tomorrow at 9 am |
      | Set a timer for 4 minutes                 |
      | How is the weather in Brisbane?           |
      | Where have all the flowers gone?          |
