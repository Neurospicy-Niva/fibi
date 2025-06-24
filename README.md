# 🧠 Niva - Your AI-Powered Daily Life Assistant

> **Empowering neurodivergent individuals to structure their daily lives with compassionate AI assistance**

[![License: Dual](https://img.shields.io/badge/License-Dual%20(AGPL%20%2F%20Commercial)-blue.svg)](LICENSE)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9+-purple.svg)](https://kotlinlang.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.0+-green.svg)](https://spring.io/projects/spring-boot)

**🌐 Website:** [neurospicy.icu](https://neurospicy.icu)

---

## 🎯 **Why Niva?**

Living with ADHD, Autism, or other neurodivergent conditions often means struggling with:
- 📅 **Task organization and prioritization**
- ⏰ **Time management and reminders**
- 🔄 **Daily routine establishment**
- 🤝 **Consistent motivation and support**

**Niva changes that.** Built specifically for the neurodivergent community, Niva is an intelligent Signal-based chatbot that provides personalized, empathetic assistance to help structure your daily life.

## ✨ **Key Features**

### 🧩 **Intelligent Task Management**
- Natural language task creation and organization
- Context-aware priority suggestions
- ADHD-friendly reminders and notifications

### 📱 **Signal Integration**
- Works directly through Signal messaging
- Privacy-focused communication
- No additional apps to remember

### 🎭 **Neurodivergent-First Design**
- Built by and for the neurodivergent community
- Empathetic and patient interaction style
- Accommodates communication preferences

### 🔄 **Smart Routines**
- Customizable morning and daily routines
- Adaptive scheduling based on your patterns
- Gentle guidance without overwhelming pressure

### 📅 **Calendar Intelligence**
- Seamless calendar integration (Google, Nextcloud, etc.)
- Appointment reminders and preparation
- Daily planning assistance

### 🤖 **Advanced AI Conversations**
- Context-aware multi-turn conversations
- Goal clarification and refinement
- Intelligent intent recognition

---

## 🚀 **Quick Start**

### Prerequisites
- **Java 17+**
- **Docker & Docker Compose**
- **Signal CLI setup** (for Signal integration)

### 🐳 **Docker Deployment (Recommended)**

```bash
# Clone the repository
git clone https://github.com/Neurospicy-Niva/fibi.git
cd fibi

# Start with Docker Compose
docker-compose up -d

# The application will be available at http://localhost:8080
```

### 🛠 **Development Setup**

```bash
# Clone and build
git clone https://github.com/Neurospicy-Niva/fibi.git
cd fibi
./gradlew build

# Run tests
./gradlew test

# Start the application
./gradlew bootRun
```

### 📱 **Signal Configuration**

1. Set up Signal CLI on your server
2. Configure Signal webhook in `application.yaml`
3. Register your Signal number with Niva
4. Start chatting! 🎉

---

## 🏗 **Architecture Overview**

Niva is built with a sophisticated conversation orchestration system:

```
Friend Message → Intent Classification → Goal Refinement → Subtask Execution → Response
```

**Core Components:**
- 🧠 **ConversationOrchestrator**: Main conversation flow controller
- 🎯 **IntentClassifier**: AI-powered intent recognition
- 🎨 **GoalRefiner**: Context-aware goal management
- ⚡ **SubtaskHandlers**: Specialized action processors
- 💾 **MongoDB**: Persistent data storage
- 🤖 **Ollama**: Local LLM integration

📖 **[Detailed Architecture Documentation](docs/ARCHITECTURE.md)**

---

## 💼 **Licensing & Commercial Use**

Niva operates under a **dual licensing model**:

### 🆓 **Open Source (AGPL-3.0)**
- ✅ Perfect for personal use, learning, and community contributions
- ✅ Fork, modify, and distribute under AGPL terms
- ✅ Run your own instance for non-commercial purposes

### 💰 **Commercial License**
- 🏢 **Enterprise deployments** without AGPL restrictions
- 🔒 **Private modifications** and proprietary integrations
- 🛡 **Commercial support** and maintenance
- 📈 **Priority feature development**

**💡 Interested in commercial licensing?** Contact us at [licensing@neurospicy.icu](mailto:licensing@neurospicy.icu)

---

## 🤝 **Contributing**

We welcome contributions from the community! Whether you're:
- 🐛 **Reporting bugs**
- 💡 **Suggesting features**
- 🔧 **Improving code**
- 📚 **Enhancing documentation**
- 🌍 **Adding translations**

**[Contributing Guidelines](CONTRIBUTING.md)** | **[Code of Conduct](CODE_OF_CONDUCT.md)**

### 🛠 **Development Workflow**

```bash
# 1. Fork the repository
# 2. Create a feature branch
git checkout -b feature/amazing-feature

# 3. Make your changes
# 4. Run tests
./gradlew test

# 5. Submit a pull request
```

---

## 🧪 **Testing**

Niva includes comprehensive testing with a focus on behavior-driven development:

```bash
# Unit tests
./gradlew test

# Integration tests
./gradlew integrationTest

# AI-powered tests
./gradlew aiTest

# BDD Feature tests (Cucumber/Gherkin)
cd HelloFibi && mvn test
```

### **Behavior-Driven Development**
Our [BDD test suite](HelloFibi/) uses Cucumber and Gherkin to test real user scenarios:

```gherkin
Feature: Simple Task Management
  Scenario: Adding a new task
    Given a Friend
    When they send "Please add a task to call the clinic" to Fibi
    Then they eventually receive a task added confirmation
```

**🎭 View all user scenarios**: [Feature Files](HelloFibi/src/test/resources/features/)

---

## 📊 **Technology Stack**

| Component | Technology | Purpose |
|-----------|------------|---------|
| **Backend** | Kotlin + Spring Boot | Core application framework |
| **AI/ML** | Ollama + LLMs | Natural language processing |
| **Database** | MongoDB | Data persistence |
| **Messaging** | Signal CLI | User communication |
| **Scheduling** | Quartz | Task and reminder scheduling |
| **Containerization** | Docker | Deployment and distribution |

---

## 🌟 **Community & Support**

- 💬 **[Community Discord](https://discord.gg/neurospicy)**
- 📧 **Email**: [support@neurospicy.icu](mailto:support@neurospicy.icu)
- 🐛 **[Issue Tracker](https://github.com/Neurospicy-Niva/fibi/issues)**
- 📖 **[Documentation](https://docs.neurospicy.icu)**

---

## 🎗 **Our Mission**

At **neurospicy.icu**, we believe that neurodivergent individuals deserve technology that truly understands and supports their unique needs. Niva isn't just another productivity app—it's a companion designed with empathy, built by our community, for our community.

**Every license sold directly funds:**
- 🔬 **Advanced AI research** for neurodivergent assistance
- 🌍 **Community outreach** and accessibility improvements
- 🛠 **Open-source development** and maintenance
- 🎓 **Educational resources** and workshops

---

## 📜 **License**

This project is dual-licensed:
- **Open Source**: [AGPL-3.0](LICENSE-AGPL)
- **Commercial**: [Commercial License](LICENSE-COMMERCIAL)

See [licensing details](LICENSE.md) for more information.

---

## 🙏 **Acknowledgments**

Special thanks to:
- The neurodivergent community for inspiration and feedback
- Contributors who make Niva better every day
- Open-source projects that power our infrastructure

---

**Made with ❤️ by the neurodivergent community, for the neurodivergent community.**

*Niva is formerly known as "Fibi" in some code references during development.* 