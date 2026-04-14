(function () {
    const CONTRACT = {
        endpoint: "/ws/support",
        publishPrefix: "/pub/support/v1",
        subscribePrefix: "/sub/support/v1",
        userQueuePrefix: "/user/queue/support/v1",
        adminApiPrefix: "/api/admin/support/v1",
        supportApiPrefix: "/api/support/v1"
    };

    const CONNECT_TIMEOUT_MS = 5000;
    const STOMP_SUBPROTOCOLS = ["v12.stomp", "v11.stomp", "v10.stomp"];
    const STORAGE_TOKEN_KEY = "support-chat-debug-jwt";
    const STORAGE_TOKEN_ENABLED_KEY = "support-chat-debug-jwt-enabled";

    const state = {
        socket: null,
        connected: false,
        connecting: false,
        currentMode: "user",
        currentRoomStatus: null,
        subscriptions: new Map(),
        subscriptionCounter: 1,
        connectTimeoutId: null,
        messageCount: 0
    };

    const elements = {
        endpointValue: document.getElementById("endpoint-value"),
        publishPrefixValue: document.getElementById("publish-prefix-value"),
        subscribePrefixValue: document.getElementById("subscribe-prefix-value"),
        userQueuePrefixValue: document.getElementById("user-queue-prefix-value"),
        connectionStatus: document.getElementById("connection-status"),
        subscriptionStatus: document.getElementById("subscription-status"),
        queueSubscriptionStatus: document.getElementById("queue-subscription-status"),
        roomStatus: document.getElementById("room-status"),
        roomStatusSummary: document.getElementById("room-status-summary"),
        jwtInput: document.getElementById("jwt-input"),
        saveJwtToggle: document.getElementById("save-jwt-toggle"),
        roomIdInput: document.getElementById("room-id-input"),
        reassignTargetUserIdInput: document.getElementById("reassign-target-user-id-input"),
        messageInput: document.getElementById("message-input"),
        lastReadMessageIdInput: document.getElementById("last-read-message-id-input"),
        beforeMessageIdInput: document.getElementById("before-message-id-input"),
        messageSizeInput: document.getElementById("message-size-input"),
        adminModePanel: document.getElementById("admin-mode-panel"),
        messageDestinationPreview: document.getElementById("message-destination-preview"),
        readDestinationPreview: document.getElementById("read-destination-preview"),
        sendDestinationPreview: document.getElementById("send-destination-preview"),
        aiReplyDestinationPreview: document.getElementById("ai-reply-destination-preview"),
        readSendDestinationPreview: document.getElementById("read-send-destination-preview"),
        messageTimeline: document.getElementById("message-timeline"),
        readEventList: document.getElementById("read-event-list"),
        friendlyLogList: document.getElementById("friendly-log-list"),
        errorLogList: document.getElementById("error-log-list"),
        rawLogOutput: document.getElementById("raw-log-output"),
        restOutput: document.getElementById("rest-output"),
        messageCount: document.getElementById("message-count")
    };

    const buttons = {
        connect: document.getElementById("connect-button"),
        disconnect: document.getElementById("disconnect-button"),
        userMode: document.getElementById("user-mode-button"),
        adminMode: document.getElementById("admin-mode-button"),
        loadJwt: document.getElementById("load-jwt-button"),
        deleteJwt: document.getElementById("delete-jwt-button"),
        subscribe: document.getElementById("subscribe-button"),
        unsubscribe: document.getElementById("unsubscribe-button"),
        subscribeQueue: document.getElementById("subscribe-queue-button"),
        unsubscribeQueue: document.getElementById("unsubscribe-queue-button"),
        sendMessage: document.getElementById("send-message-button"),
        createAiReply: document.getElementById("create-ai-reply-button"),
        sendRead: document.getElementById("send-read-button"),
        createRoom: document.getElementById("create-room-button"),
        fetchMyRooms: document.getElementById("fetch-my-rooms-button"),
        fetchQueueRooms: document.getElementById("fetch-queue-rooms-button"),
        fetchClosedRooms: document.getElementById("fetch-closed-rooms-button"),
        fetchRoomDetail: document.getElementById("fetch-room-detail-button"),
        fetchRoomMessages: document.getElementById("fetch-room-messages-button"),
        claimRoom: document.getElementById("claim-room-button"),
        releaseRoom: document.getElementById("release-room-button"),
        reassignRoom: document.getElementById("reassign-room-button"),
        closeRoom: document.getElementById("close-room-button"),
        clearFriendlyLogs: document.getElementById("clear-friendly-logs-button"),
        clearErrorLogs: document.getElementById("clear-error-logs-button"),
        clearRawLogs: document.getElementById("clear-raw-logs-button"),
        clearRestOutput: document.getElementById("clear-rest-output-button")
    };

    initialize();

    function initialize() {
        elements.endpointValue.textContent = CONTRACT.endpoint;
        elements.publishPrefixValue.textContent = CONTRACT.publishPrefix;
        elements.subscribePrefixValue.textContent = CONTRACT.subscribePrefix;
        elements.userQueuePrefixValue.textContent = CONTRACT.userQueuePrefix;

        wireEvents();
        restoreSavedTokenPreference();
        refreshDestinationPreview();
        setMode("user", false);
        refreshUi();
    }

    // Event binding
    function wireEvents() {
        buttons.connect.addEventListener("click", connect);
        buttons.disconnect.addEventListener("click", disconnect);
        buttons.userMode.addEventListener("click", function () { setMode("user", true); });
        buttons.adminMode.addEventListener("click", function () { setMode("admin", true); });
        buttons.loadJwt.addEventListener("click", loadSavedToken);
        buttons.deleteJwt.addEventListener("click", deleteSavedToken);
        buttons.subscribe.addEventListener("click", subscribeRoom);
        buttons.unsubscribe.addEventListener("click", unsubscribeRoom);
        buttons.subscribeQueue.addEventListener("click", subscribeQueue);
        buttons.unsubscribeQueue.addEventListener("click", unsubscribeQueue);
        buttons.sendMessage.addEventListener("click", sendMessage);
        buttons.createAiReply.addEventListener("click", createAiReply);
        buttons.sendRead.addEventListener("click", sendReadReceipt);
        buttons.createRoom.addEventListener("click", createRoom);
        buttons.fetchMyRooms.addEventListener("click", fetchMyRooms);
        buttons.fetchQueueRooms.addEventListener("click", fetchQueueRooms);
        buttons.fetchClosedRooms.addEventListener("click", fetchClosedRooms);
        buttons.fetchRoomDetail.addEventListener("click", fetchRoomDetail);
        buttons.fetchRoomMessages.addEventListener("click", fetchRoomMessages);
        buttons.claimRoom.addEventListener("click", claimRoom);
        buttons.releaseRoom.addEventListener("click", releaseRoom);
        buttons.reassignRoom.addEventListener("click", reassignRoom);
        buttons.closeRoom.addEventListener("click", closeRoom);
        buttons.clearFriendlyLogs.addEventListener("click", function () { clearNode(elements.friendlyLogList); });
        buttons.clearErrorLogs.addEventListener("click", function () { clearNode(elements.errorLogList); });
        buttons.clearRawLogs.addEventListener("click", function () { elements.rawLogOutput.textContent = ""; });
        buttons.clearRestOutput.addEventListener("click", function () { elements.restOutput.textContent = ""; });
        elements.roomIdInput.addEventListener("input", handleRoomIdChange);
        elements.saveJwtToggle.addEventListener("change", handleTokenStoragePreferenceChange);
    }

    function setMode(mode, logChange) {
        state.currentMode = mode;
        elements.adminModePanel.hidden = mode !== "admin";
        buttons.userMode.classList.toggle("is-active", mode === "user");
        buttons.adminMode.classList.toggle("is-active", mode === "admin");
        refreshUi();
        if (logChange) {
            addFriendlyLog("콘솔 모드를 " + mode.toUpperCase() + "로 전환했습니다.");
        }
    }

    function handleRoomIdChange() {
        refreshDestinationPreview();
        setCurrentRoomStatus(null, "roomId가 바뀌어서 방 상태를 다시 확인해야 합니다.");
    }

    // Connection / subscription
    function connect() {
        if (state.connected) {
            addFriendlyLog("이미 STOMP 연결이 완료된 상태입니다.");
            return;
        }

        if (state.connecting) {
            addFriendlyLog("이미 연결을 시도하고 있습니다.");
            return;
        }

        const token = normalizeToken(elements.jwtInput.value);
        if (!token) {
            addErrorLog("JWT를 입력한 뒤 Connect를 눌러 주세요.");
            return;
        }

        resetSocketState();

        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const socketUrl = protocol + "//" + window.location.host + CONTRACT.endpoint;
            // + "/websocket";

        try {
            state.socket = new WebSocket(socketUrl, STOMP_SUBPROTOCOLS);
        } catch (error) {
            addErrorLog("WebSocket 생성에 실패했습니다. " + safe(error.message));
            return;
        }

        state.connecting = true;
        refreshUi();
        addFriendlyLog("WebSocket 연결을 시작했습니다.");

        state.connectTimeoutId = window.setTimeout(function () {
            if (state.connecting && !state.connected) {
                addErrorLog("STOMP CONNECT 응답이 5초 안에 오지 않았습니다. JWT 또는 서버 로그를 확인해 주세요.");
                disconnect();
            }
        }, CONNECT_TIMEOUT_MS);

        state.socket.onopen = function () {
            addFriendlyLog("WebSocket handshake가 완료되었습니다. endpoint=" + socketUrl);
            addFriendlyLog("WebSocket subprotocol=" + (state.socket.protocol || "none"));

            sendRawFrame(
                buildFrame("CONNECT", {
                    host: window.location.host,
                    "accept-version": "1.2",
                    "heart-beat": "0,0",
                    Authorization: "Bearer " + token
                }),
                "CONNECT frame을 전송했습니다."
            );

            if (elements.saveJwtToggle.checked) {
                saveToken(token);
            }
        };

        state.socket.onmessage = function (event) {
            logRaw("RECV", event.data);
            splitStompFrames(event.data).forEach(function (rawFrame) {
                handleFrame(parseFrame(rawFrame));
            });
        };
        state.socket.onerror = function () {
            addErrorLog("WebSocket 레벨 오류가 발생했습니다. 서버가 STOMP CONNECT를 거부했는지 확인해 주세요.");
        };

        state.socket.onclose = function (event) {
            const wasConnecting = state.connecting;
            const wasConnected = state.connected;

            clearConnectTimeout();
            state.connected = false;
            state.connecting = false;
            state.subscriptions.clear();
            state.socket = null;
            refreshUi();

            if (wasConnecting && !wasConnected) {
                addErrorLog("CONNECT가 완료되기 전에 소켓이 종료되었습니다. close code=" + event.code);
                return;
            }

            if (wasConnected) {
                addFriendlyLog("소켓 연결이 종료되었습니다. close code=" + event.code);
            }
        };
    }

    function disconnect() {
        clearConnectTimeout();

        if (!state.socket) {
            state.connected = false;
            state.connecting = false;
            refreshUi();
            return;
        }

        if (state.connected) {
            sendRawFrame(buildFrame("DISCONNECT", {}), "DISCONNECT frame을 전송했습니다.");
        }

        state.socket.close();
    }

    function subscribeRoom() {
        if (!ensureConnected()) {
            return;
        }

        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        subscribeDestination("room-" + roomId, CONTRACT.subscribePrefix + "/rooms/" + roomId);
        subscribeDestination("room-read-" + roomId, CONTRACT.subscribePrefix + "/rooms/" + roomId + "/read");
        subscribeDestination("user-read", CONTRACT.userQueuePrefix + "/read");
        updateSubscriptionStatus();
    }

    function unsubscribeRoom() {
        const roomId = parseRoomId(false);
        if (!roomId) {
            return;
        }

        unsubscribeDestination("room-" + roomId);
        unsubscribeDestination("room-read-" + roomId);
        unsubscribeDestination("user-read");
        updateSubscriptionStatus();
    }

    function subscribeQueue() {
        if (!ensureConnected()) {
            return;
        }
        subscribeDestination("queue", CONTRACT.subscribePrefix + "/queue");
        updateQueueSubscriptionStatus();
    }

    function unsubscribeQueue() {
        unsubscribeDestination("queue");
        updateQueueSubscriptionStatus();
    }

    function subscribeDestination(key, destination) {
        if (state.subscriptions.has(key)) {
            addFriendlyLog("이미 구독 중인 경로입니다: " + destination);
            return;
        }

        const id = "sub-" + state.subscriptionCounter++;
        state.subscriptions.set(key, { id: id, destination: destination });
        sendRawFrame(buildFrame("SUBSCRIBE", { id: id, destination: destination }), "구독을 시작했습니다: " + destination);
    }

    function unsubscribeDestination(key) {
        const subscription = state.subscriptions.get(key);
        if (!subscription) {
            return;
        }

        sendRawFrame(buildFrame("UNSUBSCRIBE", { id: subscription.id }), "구독을 해제했습니다: " + subscription.destination);
        state.subscriptions.delete(key);
    }

    // User actions
    function sendMessage() {
        if (!ensureConnected()) {
            return;
        }

        const roomId = parseRoomId();
        const content = (elements.messageInput.value || "").trim();
        if (!roomId || !content) {
            addErrorLog("메시지를 전송하려면 올바른 roomId와 내용이 필요합니다.");
            return;
        }

        sendJsonFrame(
            CONTRACT.publishPrefix + "/rooms/" + roomId + "/messages",
            { content: content },
            "메시지 전송을 시도했습니다. roomId=" + roomId
        );
    }

    function createAiReply() {
        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        if (isRoomClosed()) {
            addErrorLog("CLOSED 문의방에서는 AI 선응답을 생성할 수 없습니다.");
            return;
        }

        fetchJson(CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/ai-replies", "AI 선응답 생성", {
            method: "POST"
        }).then(function (result) {
            const fallback = !!(result.body && result.body.data && result.body.data.fallback);
            addFriendlyLog("AI 선응답 생성이 성공했습니다. fallback=" + fallback);
        });
    }

    function sendReadReceipt() {
        if (!ensureConnected()) {
            return;
        }

        const roomId = parseRoomId();
        const lastReadMessageId = parseNumber(elements.lastReadMessageIdInput.value);
        if (!roomId || !lastReadMessageId) {
            addErrorLog("읽음 이벤트를 전송하려면 올바른 roomId와 lastReadMessageId가 필요합니다.");
            return;
        }

        sendJsonFrame(
            CONTRACT.publishPrefix + "/rooms/" + roomId + "/read",
            { lastReadMessageId: lastReadMessageId },
            "읽음 이벤트 전송을 시도했습니다. roomId=" + roomId + ", lastReadMessageId=" + lastReadMessageId
        );
    }

    function createRoom() {
        fetchJson(CONTRACT.supportApiPrefix + "/rooms", "문의방 생성", {
            method: "POST"
        }).then(function (result) {
            const roomId = result.body && result.body.data ? result.body.data.roomId : null;
            if (roomId) {
                elements.roomIdInput.value = roomId;
                refreshDestinationPreview();
                setCurrentRoomStatus("OPEN", "방을 새로 만들었으므로 현재 roomId 상태를 OPEN으로 반영했습니다.");
                addFriendlyLog("생성된 roomId를 입력칸에 반영했습니다. roomId=" + roomId);
            }
        });
    }

    function fetchMyRooms() {
        fetchJson(CONTRACT.supportApiPrefix + "/rooms/me", "내 문의방 목록 조회")
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, "내 문의방 목록");
            });
    }

    function fetchQueueRooms() {
        fetchJson(CONTRACT.adminApiPrefix + "/queue", "대기열 조회")
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, "대기열");
            });
    }

    function fetchClosedRooms() {
        fetchJson(CONTRACT.adminApiPrefix + "/rooms/closed", "내 종료 이력 조회")
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, "내 종료 이력");
            });
    }

    function fetchRoomDetail() {
        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        fetchJson(CONTRACT.supportApiPrefix + "/rooms/" + roomId, "문의방 상세 조회")
            .then(function (result) {
                syncRoomStatusFromRoomDetail(result.body && result.body.data);
            });
    }

    function fetchRoomMessages() {
        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        const params = new URLSearchParams();
        const beforeMessageId = parseNumber(elements.beforeMessageIdInput.value);
        const size = parseNumber(elements.messageSizeInput.value) || 30;
        if (beforeMessageId) {
            params.set("beforeMessageId", beforeMessageId);
        }
        params.set("size", size);

        fetchJson(CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/messages?" + params.toString(), "특정 방 메시지 이력 조회");
    }

    function claimRoom() {
        mutateAdminRoom("claim", "방 배정");
    }

    function releaseRoom() {
        mutateAdminRoom("release", "방 배정 해제");
    }

    function reassignRoom() {
        const roomId = parseRoomId();
        const targetCounselorUserId = parseNumber(elements.reassignTargetUserIdInput.value);
        if (!roomId || !targetCounselorUserId) {
            addErrorLog("방 재배정을 하려면 roomId와 targetCounselorUserId가 필요합니다.");
            return;
        }

        fetchJson(CONTRACT.adminApiPrefix + "/rooms/" + roomId + "/reassign", "방 재배정", {
            method: "POST",
            body: JSON.stringify({ targetCounselorUserId: targetCounselorUserId })
        }).then(function () {
            setCurrentRoomStatus("OPEN", "방 재배정 후에도 방 상태는 OPEN으로 유지됩니다.");
        });
    }

    function closeRoom() {
        mutateAdminRoom("close", "문의방 종료");
    }

    function mutateAdminRoom(action, label) {
        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        fetchJson(CONTRACT.adminApiPrefix + "/rooms/" + roomId + "/" + action, label, {
            method: "POST"
        }).then(function (result) {
            syncRoomStatusFromAdminMutation(action, result);
        });
    }

    // REST / STOMP handling
    async function fetchJson(url, actionLabel, options) {
        const token = normalizeToken(elements.jwtInput.value);
        if (!token) {
            addErrorLog(actionLabel + " 전에 JWT를 먼저 입력해 주세요.");
            throw new Error("missing jwt");
        }

        const requestOptions = Object.assign({ method: "GET" }, options || {});
        const headers = Object.assign({}, requestOptions.headers || {}, {
            Authorization: "Bearer " + token
        });

        if (requestOptions.body && !headers["Content-Type"]) {
            headers["Content-Type"] = "application/json";
        }

        requestOptions.headers = headers;

        const response = await fetch(url, requestOptions);
        const text = await response.text();
        let body = null;

        try {
            body = text ? JSON.parse(text) : null;
        } catch (error) {
            body = text;
        }

        const result = {
            status: response.status,
            ok: response.ok,
            body: body
        };

        elements.restOutput.textContent = JSON.stringify(result, null, 2);

        if (response.ok) {
            addFriendlyLog(actionLabel + "가 성공했습니다.");
            return result;
        }

        addErrorLog(actionLabel + "가 실패했습니다. " + formatRestErrorSummary(response.status, body));
        throw new Error(actionLabel + " failed");
    }

    function handleFrame(frame) {
        if (!frame.command) {
            return;
        }

        if (frame.command === "CONNECTED") {
            clearConnectTimeout();
            state.connected = true;
            state.connecting = false;
            updateConnectionStatus();
            updateActionButtons();
            addFriendlyLog("STOMP CONNECT가 성공했습니다.");
            return;
        }

        if (frame.command === "MESSAGE") {
            handleMessageFrame(frame);
            return;
        }

        if (frame.command === "ERROR") {
            addErrorLog("STOMP ERROR frame을 수신했습니다.\n" + (frame.body || "본문 없음"));
            return;
        }

        if (frame.command === "RECEIPT") {
            addFriendlyLog("RECEIPT frame을 수신했습니다. receipt-id=" + safe(frame.headers["receipt-id"]));
        }
    }

    function handleMessageFrame(frame) {
        const destination = frame.headers.destination || "";
        const payload = tryParseJson(frame.body);

        if (destination.includes(CONTRACT.userQueuePrefix + "/read")) {
            renderSystemEvent("개인 unread 동기화를 수신했습니다.", payload);
            return;
        }

        if (destination.includes("/read")) {
            renderSystemEvent("방 읽음 이벤트를 수신했습니다.", payload);
            return;
        }

        if (destination.includes("/queue")) {
            syncRoomStatusFromQueueEvent(payload);
            renderSystemEvent("대기열 이벤트를 수신했습니다.", payload);
            return;
        }

        renderMessage(payload);
    }

    // Rendering
    function renderMessage(payload) {
        state.messageCount += 1;
        elements.messageCount.textContent = state.messageCount + "건";

        const item = document.createElement("article");
        item.className = "timeline-item";

        const meta = document.createElement("div");
        meta.className = "timeline-meta";
        meta.textContent = "messageId=" + safe(payload && payload.messageId) + " / senderType=" + safe(payload && payload.senderType);

        const body = document.createElement("pre");
        body.className = "timeline-body";
        body.textContent = JSON.stringify(payload, null, 2);

        item.appendChild(meta);
        item.appendChild(body);
        elements.messageTimeline.prepend(item);

        addFriendlyLog("메시지를 수신했습니다. messageId=" + safe(payload && payload.messageId) + ", senderType=" + safe(payload && payload.senderType));
    }

    function renderSystemEvent(title, payload) {
        const item = document.createElement("article");
        item.className = "system-event-item";

        const heading = document.createElement("strong");
        heading.textContent = title;

        const body = document.createElement("pre");
        body.className = "timeline-body";
        body.textContent = JSON.stringify(payload, null, 2);

        item.appendChild(heading);
        item.appendChild(body);
        elements.readEventList.prepend(item);
    }

    function sendJsonFrame(destination, payload, successMessage) {
        sendRawFrame(
            buildFrame("SEND", {
                destination: destination,
                "content-type": "application/json"
            }, JSON.stringify(payload)),
            successMessage
        );
    }

    function sendRawFrame(frame, successMessage) {
        if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
            addErrorLog("먼저 WebSocket 연결을 완료해 주세요.");
            return;
        }

        state.socket.send(frame);
        logRaw("SEND", frame);
        if (successMessage) {
            addFriendlyLog(successMessage);
        }
    }

    function buildFrame(command, headers, body) {
        const lines = [command];
        Object.keys(headers || {}).forEach(function (key) {
            lines.push(key + ":" + headers[key]);
        });
        lines.push("");
        if (body) {
            lines.push(body);
        }
        return lines.join("\n") + "\n\0";
    }

    function parseFrame(rawFrame) {
        const trimmed = rawFrame.replace(/\0+$/, "");
        const lines = trimmed.split(/\n/);
        const command = lines.shift() || "";
        const headers = {};
        let bodyIndex = lines.findIndex(function (line) { return line === ""; });

        if (bodyIndex < 0) {
            bodyIndex = lines.length;
        }

        lines.slice(0, bodyIndex).forEach(function (line) {
            const separatorIndex = line.indexOf(":");
            if (separatorIndex > -1) {
                headers[line.slice(0, separatorIndex)] = line.slice(separatorIndex + 1);
            }
        });

        const body = bodyIndex < lines.length ? lines.slice(bodyIndex + 1).join("\n") : "";
        return { command: command, headers: headers, body: body };
    }

    function splitStompFrames(payload) {
        return String(payload)
            .split("\0")
            .map(function (frame) { return frame.trim(); })
            .filter(function (frame) { return frame.length > 0; });
    }

    function refreshDestinationPreview() {
        const roomId = elements.roomIdInput.value.trim() || "{roomId}";
        elements.messageDestinationPreview.textContent = CONTRACT.subscribePrefix + "/rooms/" + roomId;
        elements.readDestinationPreview.textContent = CONTRACT.subscribePrefix + "/rooms/" + roomId + "/read";
        elements.sendDestinationPreview.textContent = CONTRACT.publishPrefix + "/rooms/" + roomId + "/messages";
        elements.aiReplyDestinationPreview.textContent = "POST " + CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/ai-replies";
        elements.readSendDestinationPreview.textContent = CONTRACT.publishPrefix + "/rooms/" + roomId + "/read";
    }

    // State / UI sync
    // 연결 상태, 구독 상태, 방 상태, 버튼 가드는 항상 함께 움직이므로 한 번에 갱신한다.
    function refreshUi() {
        updateConnectionStatus();
        updateSubscriptionStatus();
        updateQueueSubscriptionStatus();
        updateRoomStatus();
        updateActionButtons();
    }

    function updateConnectionStatus() {
        if (state.connected) {
            setStatusChip(elements.connectionStatus, "is-connected", "연결됨");
            return;
        }
        if (state.connecting) {
            setStatusChip(elements.connectionStatus, "is-pending", "연결 중");
            return;
        }
        setStatusChip(elements.connectionStatus, "is-idle", "연결 안 됨");
    }

    function updateSubscriptionStatus() {
        const roomId = parseRoomId(false);
        const active = roomId && state.subscriptions.has("room-" + roomId);
        setStatusChip(elements.subscriptionStatus, active ? "is-connected" : "is-muted", active ? "구독 중" : "구독 없음");
    }

    function updateQueueSubscriptionStatus() {
        const active = state.subscriptions.has("queue");
        setStatusChip(elements.queueSubscriptionStatus, active ? "is-connected" : "is-muted", active ? "큐 구독 중" : "큐 미구독");
    }

    function updateRoomStatus() {
        if (state.currentRoomStatus === "OPEN") {
            setStatusChip(elements.roomStatus, "is-connected", "OPEN");
            return;
        }
        if (state.currentRoomStatus === "CLOSED") {
            setStatusChip(elements.roomStatus, "is-closed", "CLOSED");
            return;
        }
        setStatusChip(elements.roomStatus, "is-muted", "상태 미확인");
    }

    function updateActionButtons() {
        const hasRoomId = hasCurrentRoomId();
        const roomClosed = isRoomClosed();
        const adminMode = isAdminMode();

        buttons.connect.disabled = state.connected || state.connecting;
        buttons.disconnect.disabled = !state.socket;
        buttons.subscribe.disabled = !state.connected || !hasRoomId;
        buttons.unsubscribe.disabled = !state.connected || !hasRoomId;
        buttons.subscribeQueue.disabled = !state.connected || !adminMode;
        buttons.unsubscribeQueue.disabled = !state.connected || !adminMode;
        buttons.sendMessage.disabled = !state.connected || !hasRoomId || roomClosed;
        buttons.sendRead.disabled = !state.connected || !hasRoomId || roomClosed;
        buttons.createAiReply.disabled = !hasRoomId || roomClosed;
        buttons.fetchRoomDetail.disabled = !hasRoomId;
        buttons.fetchRoomMessages.disabled = !hasRoomId;
        buttons.claimRoom.disabled = !adminMode || !hasRoomId;
        buttons.releaseRoom.disabled = !adminMode || !hasRoomId;
        buttons.reassignRoom.disabled = !adminMode || !hasRoomId;
        buttons.closeRoom.disabled = !adminMode || !hasRoomId;
        buttons.fetchQueueRooms.disabled = !adminMode;
        buttons.fetchClosedRooms.disabled = !adminMode;
    }

    function setStatusChip(element, modifier, text) {
        element.className = "status-chip " + modifier;
        element.textContent = text;
    }

    function setCurrentRoomStatus(status, summary) {
        state.currentRoomStatus = status || null;
        refreshUi();
        if (summary) {
            elements.roomStatusSummary.textContent = summary;
        }
    }

    function syncRoomStatusFromRoomList(rooms, sourceLabel) {
        const roomId = parseRoomId(false);
        const items = extractRoomListItems(rooms);
        if (!roomId) {
            return;
        }

        if (!items.length) {
            setCurrentRoomStatus(null, safe(sourceLabel) + " 조회 결과가 비어 있어서 현재 roomId 상태를 확인하지 못했습니다.");
            return;
        }

        const matchedRoom = items.find(function (room) {
            return Number(room && room.roomId) === roomId;
        });

        if (!matchedRoom || !matchedRoom.status) {
            setCurrentRoomStatus(null, safe(sourceLabel) + " 조회 결과에 현재 roomId가 없어 상태를 확인하지 못했습니다.");
            return;
        }

        setCurrentRoomStatus(
            matchedRoom.status,
            "목록 조회 결과 기준 현재 roomId 상태를 " + matchedRoom.status + "로 반영했습니다."
        );
    }

    function syncRoomStatusFromRoomDetail(room) {
        const roomId = parseRoomId(false);
        if (!roomId || !room || Number(room.roomId) !== roomId || !room.status) {
            return;
        }

        setCurrentRoomStatus(
            room.status,
            "상세 조회 결과 기준 현재 roomId 상태를 " + room.status + "로 반영했습니다."
        );
    }

    function syncRoomStatusFromAdminMutation(action, result) {
        const data = result && result.body ? result.body.data : null;
        if (action === "close" && data && data.closed) {
            setCurrentRoomStatus("CLOSED", "문의방 종료가 반영되어 현재 roomId 상태를 CLOSED로 바꿨습니다.");
            return;
        }

        if (action === "claim") {
            setCurrentRoomStatus("OPEN", "방 배정 후에도 방 상태는 OPEN으로 유지됩니다.");
            return;
        }

        if (action === "release") {
            setCurrentRoomStatus("OPEN", "방 배정 해제 후에도 방 상태는 OPEN으로 유지됩니다.");
            return;
        }

        if (action === "reassign") {
            setCurrentRoomStatus("OPEN", "방 재배정 후에도 방 상태는 OPEN으로 유지됩니다.");
        }
    }

    function syncRoomStatusFromQueueEvent(payload) {
        const roomId = parseRoomId(false);
        if (!roomId || !payload || Number(payload.roomId) !== roomId) {
            return;
        }

        if (payload.eventType === "CLOSED") {
            setCurrentRoomStatus("CLOSED", "큐 이벤트 기준 현재 roomId 상태를 CLOSED로 반영했습니다.");
            return;
        }

        if (payload.eventType === "CLAIMED" || payload.eventType === "RELEASED" || payload.eventType === "TIMEOUT_RELEASED") {
            setCurrentRoomStatus("OPEN", "큐 이벤트 기준 현재 roomId 상태를 OPEN으로 반영했습니다.");
        }
    }

    // Token / input helpers
    function restoreSavedTokenPreference() {
        elements.saveJwtToggle.checked = window.localStorage.getItem(STORAGE_TOKEN_ENABLED_KEY) === "true";
        if (elements.saveJwtToggle.checked) {
            const token = window.localStorage.getItem(STORAGE_TOKEN_KEY);
            if (token) {
                elements.jwtInput.value = token;
            }
        }
    }

    function handleTokenStoragePreferenceChange() {
        window.localStorage.setItem(STORAGE_TOKEN_ENABLED_KEY, String(elements.saveJwtToggle.checked));
        if (elements.saveJwtToggle.checked) {
            const token = normalizeToken(elements.jwtInput.value);
            if (token) {
                saveToken(token);
            }
            return;
        }
        window.localStorage.removeItem(STORAGE_TOKEN_KEY);
        addFriendlyLog("브라우저 JWT 저장을 사용하지 않도록 바꿨습니다.");
    }

    function saveToken(token) {
        window.localStorage.setItem(STORAGE_TOKEN_KEY, token);
        addFriendlyLog("JWT를 브라우저에 저장했습니다.");
    }

    function loadSavedToken() {
        const token = window.localStorage.getItem(STORAGE_TOKEN_KEY);
        if (!token) {
            addErrorLog("브라우저에 저장된 JWT가 없습니다.");
            return;
        }
        elements.jwtInput.value = token;
        addFriendlyLog("저장된 JWT를 입력칸으로 불러왔습니다.");
    }

    function deleteSavedToken() {
        window.localStorage.removeItem(STORAGE_TOKEN_KEY);
        addFriendlyLog("저장된 JWT를 삭제했습니다.");
    }

    function ensureConnected() {
        if (state.connected) {
            return true;
        }
        addErrorLog("먼저 STOMP CONNECT를 완료하세요.");
        return false;
    }

    function parseRoomId(logError) {
        const roomId = parseNumber(elements.roomIdInput.value);
        if (!roomId && logError !== false) {
            addErrorLog("roomId를 먼저 입력해 주세요.");
        }
        return roomId;
    }

    function hasCurrentRoomId() {
        return !!parseRoomId(false);
    }

    function isRoomClosed() {
        return state.currentRoomStatus === "CLOSED";
    }

    function isAdminMode() {
        return state.currentMode === "admin";
    }

    function extractRoomListItems(rooms) {
        return rooms && Array.isArray(rooms.items) ? rooms.items : [];
    }

    function formatRestErrorSummary(status, body) {
        const errorCode = body && body.error ? body.error.code : null;
        const errorMessage = body && body.error ? body.error.message : null;
        return "status=" + status
            + (errorCode ? ", code=" + errorCode : "")
            + (errorMessage ? ", message=" + errorMessage : "");
    }

    function parseNumber(value) {
        const parsed = Number(value);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
    }

    function normalizeToken(value) {
        return String(value || "").replace(/^Bearer\s+/i, "").trim();
    }

    function clearConnectTimeout() {
        if (state.connectTimeoutId) {
            window.clearTimeout(state.connectTimeoutId);
            state.connectTimeoutId = null;
        }
    }

    function resetSocketState() {
        clearConnectTimeout();
        state.connected = false;
        state.connecting = false;
        state.subscriptions.clear();
        if (state.socket) {
            try {
                state.socket.close();
            } catch (error) {
                // 이미 닫힌 소켓이면 조용히 넘어갑니다.
            }
        }
        state.socket = null;
        refreshUi();
    }

    // Logging
    function addFriendlyLog(message) {
        prependLog(elements.friendlyLogList, message, false);
    }

    function addErrorLog(message) {
        prependLog(elements.errorLogList, message, true);
    }

    function prependLog(container, message, isError) {
        const item = document.createElement("article");
        item.className = isError ? "log-item error" : "log-item";

        const timestamp = document.createElement("span");
        timestamp.className = "log-time";
        timestamp.textContent = formatNow();

        const body = document.createElement("div");
        body.className = "log-message";
        body.textContent = message;

        item.appendChild(timestamp);
        item.appendChild(body);
        container.prepend(item);
    }

    function logRaw(direction, payload) {
        const visible = String(payload).replace(/Authorization:Bearer\s+[^\n\0]+/g, "Authorization:Bearer ****");
        const block = "[" + formatNow() + "] " + direction + "\n" + visible + "\n\n";
        elements.rawLogOutput.textContent = block + elements.rawLogOutput.textContent;
    }

    function tryParseJson(value) {
        try {
            return JSON.parse(value);
        } catch (error) {
            return value;
        }
    }

    function safe(value) {
        return value == null ? "-" : String(value);
    }

    function formatNow() {
        return new Date().toLocaleTimeString("ko-KR", {
            hour: "numeric",
            minute: "2-digit",
            second: "2-digit"
        });
    }

    function clearNode(node) {
        while (node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }
})();