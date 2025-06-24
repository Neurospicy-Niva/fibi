# Dockerfile
FROM python:3.11-slim

WORKDIR /app

# Install Flask or dependencies as needed
RUN pip install flask==2.3.3

COPY mock_signal_cli.py /app/mock_signal_cli.py

EXPOSE 8080
CMD ["python", "/app/mock_signal_cli.py"]