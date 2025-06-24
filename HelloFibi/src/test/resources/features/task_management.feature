Feature: Simple Task Management
  As a Friend of Fibi,
  I want to create tasks, mark them as completed, and view my task list,
  So that I can easily manage my daily to-dos via chat.

  Scenario: Adding a new task
    Given a Friend
    When they send "Please add a task to call the clinic because of a prescription." to Fibi
    Then they eventually receive a task added confirmation

  Scenario: Marking a task as completed
    Given a Friend
    And they add a task "Answer the letter of Ismael"
    When they send "I have completed task to answer the letter" to Fibi
    Then they eventually receive a task completed confirmation

  Scenario: Renaming a task
    Given a Friend
    And they add a task "Send mail to Clara"
    When they send "Ooops, it is not Clara, it is Larissa. Please, rename the task to 'Mail to Larissa'" to Fibi
    Then they eventually receive a task renamed confirmation
    When they send "Show me my tasks" to Fibi
    Then they eventually receive a message containing "Mail to Larissa"

  Scenario: Listing current tasks
    Given a Friend
    And they add a task "Clean dishes"
    And they add a task "Mail to Clara"
    And they add a task "Brush my teeth"
    When they send "Show all my tasks" to Fibi
    Then they eventually receive a list of the tasks

  Scenario: Cleaning up tasks
    Given a Friend
    And they add a task "Breakfast"
    And they add a task "Do Yoga"
    And they add a task "Do laundry"
    When they tell to mark task "Breakfast" completed
    When they send "Remove the completed tasks, please." to Fibi
    Then they eventually receive a message containing "?"
    When they send "Yes, clean up all tasks" to Fibi
    Then they eventually receive a cleaned-up tasks confirmation
    When they send "Show all my tasks" to Fibi
    Then they eventually receive a message not containing "breakfast"

  Scenario Outline: Adding tasks using different wordings
    Given a Friend
    When they send "<taskAddingMessage>" to Fibi
    Then they eventually receive a task added confirmation
    When they send "What is on my task list now?" to Fibi
    Then they eventually receive a list of the tasks

    Examples:
      | taskAddingMessage                                                                                 |
      | Add a task to organize my books                                                                   |
      | I just had an idea. Maybe I should read the book about monsters. Add a task for that.             |
      | The attic is so dirty. I need a task to clean it in the next days.                                |
      | Oh my gosh! I found my glasses which need to be repaired. That's another to do!                   |
      | I just had a meeting with my cooperation partner. Add a task to schedule a follow-up appointment. |

