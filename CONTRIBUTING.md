# Contributing to Niva

Thank you for your interest in contributing to Niva! We welcome contributions from everyone, especially from the neurodivergent community that Niva serves.

## 🌟 Ways to Contribute

### 🐛 Bug Reports
- Use the [issue tracker](https://github.com/Neurospicy-Niva/fibi/issues) to report bugs
- Include detailed steps to reproduce the issue
- Provide system information and error messages

### 💡 Feature Requests
- Describe the problem you're trying to solve
- Explain how the feature would help neurodivergent users
- Consider accessibility and sensory processing needs

### 🔧 Code Contributions
- Fork the repository and create a feature branch
- Follow the existing code style and conventions
- Write tests for new functionality
- Update documentation as needed

### 📚 Documentation
- Improve existing documentation
- Add examples and tutorials
- Help with translations

## 🛠 Development Setup

1. **Prerequisites**
   ```bash
   # Install Java 17+
   # Install Docker and Docker Compose
   # Install Git
   ```

2. **Clone and Setup**
   ```bash
   git clone https://github.com/Neurospicy-Niva/fibi.git
   cd fibi
   ./gradlew build
   ```

3. **Run Tests**
   ```bash
   # Unit tests
   ./gradlew test
   
   # Integration tests
   ./gradlew integrationTest
   
   # AI tests (requires Ollama)
   ./gradlew aiTest
   ```

4. **Local Development**
   ```bash
   # Start dependencies
   docker-compose up -d mongodb ollama
   
   # Run the application
   ./gradlew bootRun
   ```

## 📝 Code Style

### Kotlin Guidelines
- Use meaningful variable and function names
- Prefer data classes for simple data containers
- Use suspend functions for async operations
- Follow Kotlin conventions for naming

### Architecture Guidelines
- New features should integrate with the existing conversation orchestration
- Create subtask handlers for new functionality
- Use dependency injection for component management
- Keep LLM prompts in separate template files

### Testing Guidelines
- Write unit tests for business logic
- Use integration tests for component interactions
- Add AI tests for conversation flows
- Mock external dependencies in tests

## 🎯 Submitting Changes

### Pull Request Process
1. **Create a Feature Branch**
   ```bash
   git checkout -b feature/description-of-feature
   ```

2. **Make Your Changes**
   - Keep commits focused and atomic
   - Write clear commit messages
   - Update tests and documentation

3. **Test Your Changes**
   ```bash
   ./gradlew test integrationTest
   ```

4. **Submit Pull Request**
   - Provide a clear description of changes
   - Reference any related issues
   - Include screenshots for UI changes

### Commit Message Format
```
type(scope): brief description

Longer description if needed

Fixes #issue-number
```

Types: `feat`, `fix`, `docs`, `test`, `refactor`, `style`, `chore`

## 🤝 Community Guidelines

### Be Inclusive and Respectful
- Use inclusive language
- Respect different communication styles
- Be patient with neurodivergent contributors
- Consider accessibility in all contributions

### Neurodivergent-Friendly Practices
- Provide clear, step-by-step instructions
- Avoid sensory overwhelming elements (excessive colors, animations)
- Use plain language in documentation
- Respect different working styles and time zones

### Communication
- Be specific and direct in feedback
- Provide constructive suggestions
- Ask clarifying questions when needed
- Use content warnings for potentially triggering topics

## 📋 Issue Triage

### Labels We Use
- `bug`: Something isn't working
- `enhancement`: New feature or improvement
- `documentation`: Documentation updates
- `accessibility`: Accessibility improvements
- `neurodivergent`: Specifically affects neurodivergent users
- `good-first-issue`: Good for newcomers
- `help-wanted`: We need community help

### Priority Levels
- `critical`: Affects core functionality
- `high`: Important improvements
- `medium`: Nice to have features
- `low`: Minor improvements

## 🔒 Security

If you discover a security vulnerability, please:
1. **Do not** create a public issue
2. Email security@neurospicy.icu
3. Include detailed information about the vulnerability
4. Wait for confirmation before disclosure

## 📄 License

By contributing to Niva, you agree that your contributions will be licensed under the same dual license as the project (AGPL-3.0 for open source, commercial license available).

## 🙏 Recognition

All contributors will be recognized in our:
- Contributors section in README
- Release notes for significant contributions
- Annual contributor appreciation posts

## 📞 Getting Help

- 💬 [Community Discord](https://discord.gg/neurospicy)
- 📧 Email: [community@neurospicy.icu](mailto:community@neurospicy.icu)
- 📖 [Documentation](https://docs.neurospicy.icu)

---

Thank you for helping make Niva better for the neurodivergent community! 🧠💖 