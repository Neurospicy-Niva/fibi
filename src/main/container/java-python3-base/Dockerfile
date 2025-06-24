FROM openjdk:21-jdk-bookworm
RUN apt-get update && apt-get install -y python3 pipx \
    && pipx ensurepath \
    && pipx install vdirsyncer \
    && cp /root/.local/pipx/venvs/vdirsyncer/bin/vdirsyncer /usr/local/bin/vdirsyncer \
    && chmod a+rx /usr/local/bin/vdirsyncer
ENV PATH="$PATH:/usr/local/bin/vdirsyncer"