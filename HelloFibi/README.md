## Requirements

- **docker** to run the dependencies, at least the signal-cli-mock
- **jdk 21**

## Running tests 

### Building docker image of signal-cli mock

```shell
docker build -t icu.neurospicy/mock-signal-cli:latest ./src/main/signal-mock/
```