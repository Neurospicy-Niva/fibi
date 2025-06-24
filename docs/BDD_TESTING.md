# 🎭 Behavior-Driven Development with Niva

Niva uses comprehensive BDD testing to ensure our AI assistant truly understands and serves the neurodivergent community. Our test suite validates real user conversations and scenarios using Cucumber and Gherkin.

## 📍 Overview

The [HelloFibi](../HelloFibi/) project contains our BDD test suite that:
- **Tests real user scenarios** in natural language
- **Validates conversation flows** with actual AI models
- **Ensures accessibility** for neurodivergent communication patterns
- **Documents expected behavior** through executable specifications

## 🏗 Test Architecture

```
HelloFibi/
├── src/test/resources/features/     # Gherkin feature files
├── src/test/java/.../step/         # Step definitions (Java)
├── src/main/container/             # Test infrastructure
└── pom.xml                         # Maven configuration
```

### **Test Infrastructure Components**

| Component | Purpose | Technology |
|-----------|---------|------------|
| **Signal Mock** | Simulates Signal messaging | Python Flask |
| **Calendar Server** | WebDAV/CalDAV testing | Radicale |
| **Database** | Data persistence testing | MongoDB + TestContainers |
| **Fibi App** | Full application under test | Spring Boot Container |

## 🎯 Feature Coverage

### **User Onboarding** (`registration_consent.feature`)
Tests the critical first-contact experience:

```gherkin
Scenario: Drew has ADHD and wants to use Fibi
  Given a user who is new
  When they send "Hello" to Fibi
  Then they eventually receive the initial welcome message
  When they send "Yes" to Fibi
  Then they eventually receive a message containing "Fantastic! I'm excited to help you."
```

**Key Validations:**
- ✅ Terms of service consent flow
- ✅ Neurodivergent-friendly language
- ✅ Clear opt-in/opt-out options
- ✅ Privacy-first approach

### **Task Management** (`task_management.feature`)
Validates natural language task operations:

```gherkin
Scenario Outline: Adding tasks using different wordings
  Given a Friend
  When they send "<taskAddingMessage>" to Fibi
  Then they eventually receive a task added confirmation

  Examples:
    | taskAddingMessage                                                   |
    | Add a task to organize my books                                     |
    | I just had an idea. Maybe I should read the book about monsters.    |
    | The attic is so dirty. I need a task to clean it in the next days. |
```

**Key Validations:**
- ✅ Flexible natural language input
- ✅ ADHD-friendly task breakdown
- ✅ Clear confirmation messages
- ✅ Task state management

### **Calendar Integration** (`calendar_adding.feature`)
Tests calendar connectivity and appointment management:

```gherkin
Scenario: Alex successfully connects a self-hosted Nextcloud calendar
  Given a Friend
  And they have a WebDAV calendar
  And they have 2 appointments today
  When they send "I'd like to connect my calendar" to Fibi
  Then they eventually receive a request to post a CalDAV URL
  When they send their CalDAV URL to Fibi
  Then they eventually receive a calendar successfully added message containing appointments
```

**Key Validations:**
- ✅ Self-hosted calendar support
- ✅ Secure credential handling
- ✅ Appointment synchronization
- ✅ Privacy-focused integration

### **Morning Routines** (`morning_greeting.feature`)
Validates daily routine support:

```gherkin
Scenario: Friend receives a morning greeting
  Given a Friend
  When they send "I want to set up a morning routine" to Fibi
  And they send a wake-up time 75 seconds ahead
  When the scheduled wake-up time is reached
  Then they eventually receive a message containing "Good morning"
```

**Key Validations:**
- ✅ Personalized wake-up times
- ✅ Routine establishment
- ✅ Gentle daily structure
- ✅ Motivation and encouragement

## 🚀 Running BDD Tests

### **Prerequisites**
```bash
# Java 21, Maven, Docker
java -version
mvn -version
docker --version
```

### **Build Signal Mock**
```bash
cd HelloFibi/src/main/container/signal-mock
docker build -t icu.neurospicy/mock-signal-cli:latest .
```

### **Build Fibi Application**
```bash
# From project root
./gradlew build
docker build -t icu.neurospicy/fibi:latest .
```

### **Run All Features**
```bash
cd HelloFibi
mvn test
```

### **Run Specific Feature**
```bash
mvn test -Dcucumber.features="src/test/resources/features/task_management.feature"
```

### **Run with Specific Tags**
```bash
mvn test -Dcucumber.filter.tags="@calendar"
```

## 🔍 Understanding Test Results

### **HTML Reports**
After running tests, view the detailed report:
```bash
open HelloFibi/target/results.html
```

### **Console Output**
Each test scenario shows:
- ✅ **Passed steps** in green
- ❌ **Failed steps** with detailed error messages
- ⚠️ **Pending steps** that need implementation
- 📊 **Timing information** for performance analysis

### **Test Artifacts**
- **Screenshots**: Captured on test failures
- **Logs**: Detailed application and container logs
- **Data**: Test database states and message histories

## 🎨 Writing New Features

### **Feature File Structure**
```gherkin
Feature: [Brief description of the feature]
  As a [type of user]
  I want [some goal]
  So that [some reason]

  Background:
    Given [common setup for all scenarios]

  Scenario: [Specific scenario name]
    Given [initial context]
    When [action taken]
    Then [expected outcome]

  Scenario Outline: [Template scenario]
    Given [context with <parameters>]
    When [action with <parameters>]
    Then [outcome with <parameters>]

    Examples:
      | parameter1 | parameter2 |
      | value1     | value2     |
```

### **Step Definition Guidelines**

```java
@Given("a Friend")
public void aFriend() {
    // Setup a registered user who accepted terms
    UserRepository.User user = userRepository.newUser();
    fibiSignalClient.sendMessageToFibi(user, "Hello");
    // ... consent flow
}

@When("they send {string} to Fibi")
public void theySendMessageToFibi(String message) {
    fibiSignalClient.sendMessageToFibi(userRepository.getCurrentUser(), message);
}

@Then("they eventually receive {string}")
public void theyEventuallyReceive(String expected) {
    await()
        .atMost(Duration.ofSeconds(120))
        .until(() -> fibiSignalClient.verifyFibiSentTextTo(expected, user));
}
```

### **Best Practices**

1. **User-Centric Language**: Write scenarios from the user's perspective
2. **Neurodivergent Considerations**: Include various communication styles
3. **Realistic Timing**: Use appropriate timeouts for AI processing
4. **Clear Assertions**: Verify both positive and negative cases
5. **Data Independence**: Each scenario should be self-contained

## 🧪 Test Categories

### **Functional Tests**
- Core feature validation
- User workflow testing
- Integration verification

### **Accessibility Tests**
- Communication pattern diversity
- Error handling for confusion
- Clear feedback mechanisms

### **Performance Tests**
- AI response times
- Container startup times
- Message processing throughput

### **Regression Tests**
- Previously fixed bugs
- Edge case handling
- System stability

## 🚨 Troubleshooting

### **Common Issues**

#### Container Startup Failures
```bash
# Check container logs
docker logs [container-name]

# Verify port availability
netstat -tulpn | grep [port]
```

#### Test Timeouts
```bash
# Increase timeout in step definitions
await().atMost(Duration.ofSeconds(300))

# Check AI model availability
curl http://localhost:11434/api/tags
```

#### Signal Mock Issues
```bash
# Rebuild mock container
cd HelloFibi/src/main/container/signal-mock
docker build --no-cache -t icu.neurospicy/mock-signal-cli:latest .
```

### **Debugging Steps**

1. **Verify Prerequisites**: Java 21, Maven, Docker
2. **Check Container Health**: All services running
3. **Review Logs**: Application and test logs
4. **Validate Data**: Database states and test fixtures
5. **Network Connectivity**: Container-to-container communication

## 📊 Metrics and Reporting

### **Test Coverage**
- **Feature Coverage**: All major user journeys
- **Scenario Coverage**: Happy path + edge cases
- **Integration Coverage**: All external systems

### **Quality Metrics**
- **Pass Rate**: Target 95%+ for all scenarios
- **Performance**: AI responses < 10 seconds
- **Reliability**: Consistent test execution

### **Continuous Integration**
```yaml
# GitHub Actions example
- name: Run BDD Tests
  run: |
    ./gradlew build
    docker build -t icu.neurospicy/fibi:latest .
    cd HelloFibi
    mvn test
```

## 💡 Contributing to BDD Tests

### **Adding New Features**
1. Create `.feature` file in appropriate category
2. Write scenarios in clear, user-friendly language
3. Implement step definitions in Java
4. Add necessary test infrastructure
5. Verify all scenarios pass

### **Improving Existing Tests**
1. Enhance scenario coverage
2. Add edge cases and error conditions
3. Improve test performance
4. Update documentation

### **Review Checklist**
- [ ] Scenarios are user-centric and realistic
- [ ] Language is accessible to neurodivergent users
- [ ] Tests are reliable and deterministic
- [ ] Error messages are clear and helpful
- [ ] Performance is within acceptable limits

---

**🎭 Remember**: Our BDD tests are not just validation—they're living documentation of how Niva serves our community. Every scenario should reflect real user needs and demonstrate our commitment to accessibility and empathy. 