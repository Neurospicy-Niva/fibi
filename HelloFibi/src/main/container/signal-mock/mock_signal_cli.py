# A *basic* Python/Flask mock of the signal-cli JSON-RPC + SSE interface.
import json
import queue
import time
from flask import Flask, request, jsonify, Response

app = Flask(__name__)

incoming_messages_to_fibi = queue.Queue()
incoming_messages = queue.Queue()


@app.route('/signal/api/v1/rpc', methods=['POST'])
def json_rpc():
    data = request.get_json()
    if not data:
        return jsonify({"error": "Invalid JSON"}), 400

    jsonrpc = data.get("jsonrpc", "2.0")
    method = data.get("method", "")
    params = data.get("params", {})
    request_id = data.get("id", 1)

    if method == "send":
        recipient = params.get("recipient")
        message = params.get("message")
        if not recipient or not message:
            return jsonify({"error": "Missing recipient or message"}), 400
        timestamp = int(time.time() * 1000)
        msg = {
            "to": recipient,
            "message": message,
            "timestamp": timestamp,
            "type": "send"
        }

        incoming_messages.put(msg)

        return jsonify({
            "jsonrpc": jsonrpc,
            "id": request_id,
            "result": {
                "timestamp": timestamp
            }
        }), 200
    if method == "sendReceipt":
        recipient = params.get("recipient")
        timestamps = params.get("targetTimestamps")
        if not recipient or not timestamps:
            return jsonify({"error": "Missing recipient or targetTimestamps"}), 400

        msg = {
            "to": recipient,
            "timestamps": timestamps,
            "type": "sendReceipt"
        }

        incoming_messages.put(msg)

        return jsonify({
            "jsonrpc": jsonrpc,
            "id": request_id
        }), 200
    if method in ["updateProfile", "sendTyping", "sendReaction"]:
        # Catches all method which are not supported and shall not fail
        return jsonify({
            "jsonrpc": jsonrpc,
            "id": request_id
        }), 200

    return jsonify({
        "jsonrpc": jsonrpc,
        "id": request_id,
        "error": {
            "code": -32601,
            "message": f"Method '{method}' not implemented."
        }
    }), 404


@app.route('/signal/api/v1/events', methods=['GET'])
def sse_events():
    """
    SSE endpoint:
      - "typing" -> event: message, params: { "envelope": { "typingMessage": { "action": ... }, "source": ..., "account": ... } }
      - "message" -> event: message, params: { "envelope": { "dataMessage": { "message": ..., "expiresInSeconds": ..., "viewOnce": ... }, "source": ..., "sourceNumber": ..., "sourceName": ..., "sourceDevice": ..., "timestamp": ... }, "account": ... }
      - "reaction" -> event: message, params: { "envelope": { "reaction": { "emoji": ..., "targetAuthor": ..., "targetSentTimestamp": ... }, "source": ..., "timestamp": ... }, "account": ... }
    """

    def generate():
        while True:
            msg = incoming_messages_to_fibi.get(block=True)
            if msg["type"] == "typing":
                event_json = json.dumps({
                    "envelope": {
                        "source": msg.get("source_uuid"),
                        "typingMessage": {
                            "action": msg.get("action", "STARTED")  # STARTED, STOPPED
                        }
                    },
                    "account": msg.get("to", "+1337")
                })

            elif msg["type"] == "reaction":
                event_json = json.dumps({
                    "envelope": {
                        "source": msg.get("source"),
                        "reaction": {
                            "emoji": msg["reaction"]["emoji"],
                            "targetAuthor": msg["reaction"]["targetAuthor"],
                            "targetSentTimestamp": msg["reaction"]["targetSentTimestamp"]
                        },
                        "timestamp": msg["timestamp"]
                    },
                    "account": msg.get("to", "+1337")
                })

            elif msg["type"] == "message":
                event_json = json.dumps({
                    "envelope": {
                        "source": msg.get("source_uuid"),
                        "sourceNumber": msg.get("source_number"),
                        "sourceName": msg.get("source_name"),
                        "sourceDevice": msg.get("source_device"),
                        "dataMessage": {
                            "message": msg.get("message"),
                            "expiresInSeconds": 0,
                            "viewOnce": False
                        },
                        "timestamp": msg["timestamp"]
                    },
                    "account": msg.get("to", "+1337")
                })

            else:
                continue

            yield "event: message\n"
            yield f"data: {event_json}\n\n"

    return Response(generate(), mimetype="text/event-stream")


@app.route('/signal/all_events', methods=['GET'])
def mock_all_events():
    """
    Endpoint to receive all send messages.
    """

    def generate():
        while True:
            msg = incoming_messages.get(block=True)

            event_json = json.dumps(msg)
            yield f"event: {msg['type']}\n"
            yield f'data: {event_json}\n\n'

    return Response(generate(), mimetype="text/event-stream")


@app.route('/signal/send', methods=['POST'])
def mock_send_to_fibi():
    data = request.get_json()
    if not data:
        return jsonify({"error": "Invalid JSON"}), 400

    # Neue Felder aus dem Request extrahieren
    to = data.get("to")
    message = data.get("message")
    timestamp = data.get("timestamp")
    source_number = data.get("source")
    source_uuid = data.get("sourceUuid")
    source_name = data.get("sourceName", "Unknown")
    source_device = data.get("sourceDevice", 1)

    if not source_number or not source_uuid or not message:
        return jsonify({"error": "Missing required fields (from_number, from_uuid, message)"}), 400

    msg = {
        "type": "message",
        "to": to,
        "source_number": source_number,
        "source_uuid": source_uuid,
        "source_name": source_name,
        "source_device": source_device,
        "message": message,
        "timestamp": timestamp
    }

    incoming_messages_to_fibi.put(msg)

    return jsonify({"message": "success"}), 201


@app.route('/signal/react', methods=['POST'])
def react_to_message():
    data = request.get_json()
    to = data.get("to", "+1337")
    emoji = data.get("reaction", {}).get("emoji")
    target_author = data.get("reaction", {}).get("targetAuthor")
    target_sent_timestamp = data.get("reaction", {}).get("targetSentTimestamp")

    if not emoji or not target_author or not target_sent_timestamp:
        return jsonify({"error": "Invalid reaction format"}), 400

    reaction_event = {
        "type": "reaction",
        "to": to,
        "source": data["from"],
        "reaction": {
            "emoji": emoji,
            "targetAuthor": target_author,
            "targetSentTimestamp": target_sent_timestamp
        },
        "timestamp": int(time.time() * 1000)
    }
    incoming_messages_to_fibi.append(reaction_event)
    return jsonify({"status": "reaction received"}), 200


@app.errorhandler(404)
def page_not_found(e):
    return jsonify({"error": str(e), "path": str(request.path)})


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=8080, debug=False)
