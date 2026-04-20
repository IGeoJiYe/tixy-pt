(function () {
    const CONTRACT = {
        endpoint: "/ws/support",
        publishPrefix: "/pub/support/v1",
        subscribePrefix: "/sub/support/v1",
        userQueuePrefix: "/user/queue/support/v1",
        supportApiPrefix: "/api/support/v1",
        adminApiPrefix: "/api/admin/support/v1"
    };

    const STOMP_SUBPROTOCOLS = ["v12.stomp", "v11.stomp", "v10.stomp"];
    const CONNECT_TIMEOUT_MS = 5000;
    const DEFAULT_ROOM_PAGE = 1;
    const DEFAULT_ROOM_SIZE = 50;
    const DEFAULT_MESSAGE_SIZE = 50;
    const STORAGE_REMEMBER_KEY = "support-chat-app:remember";
    const STORAGE_TOKEN_PREFIX = "support-chat-app:token:";

    const MODE_CONFIG = {
        user: {
            label: "고객",
            sessionTitle: "고객 세션",
            defaultScope: "my",
            scopes: [
                { id: "my", label: "내 문의" }
            ],
            canCreateRoom: true,
            canParticipate: true,
            autoQueue: false,
            autoUserRead: true
        },
        admin: {
            label: "상담원",
            sessionTitle: "상담원 세션",
            defaultScope: "queue",
            scopes: [
                { id: "queue", label: "대기열" },
                { id: "my", label: "내 담당" },
                { id: "closed", label: "종료 이력" }
            ],
            canCreateRoom: false,
            canParticipate: true,
            autoQueue: true,
            autoUserRead: true
        },
        super_admin: {
            label: "운영 관리자",
            sessionTitle: "운영 관리자 세션",
            defaultScope: "all",
            scopes: [
                { id: "all", label: "전체 문의" },
                { id: "queue", label: "대기열" },
                { id: "closed", label: "종료 이력" },
                { id: "stale", label: "장기 미응답" }
            ],
            canCreateRoom: false,
            canParticipate: false,
            autoQueue: true,
            autoUserRead: false
        }
    };

    const state = {
        socket: null,
        connected: false,
        connecting: false,
        connectTimeoutId: null,
        subscriptions: new Map(),
        subscriptionCounter: 1,
        roomRefreshTimerId: null,
        pendingAutoReadTimerId: null,
        rememberToken: false,
        mode: "user",
        scope: "my",
        sessionUserId: null,
        sessionRole: null,
        roomListLoading: false,
        rooms: [],
        activeRoomId: null,
        activeRoomSummary: null,
        activeRoom: null,
        messagesLoading: false,
        loadingOlderMessages: false,
        messages: [],
        hasOlderMessages: false,
        nextCursor: null,
        latestUnreadSync: null,
        lastAutoReadByRoom: {},
        activities: []
    };

    const elements = {
        jwtInput: document.getElementById("jwt-input"),
        rememberTokenToggle: document.getElementById("remember-token-toggle"),
        connectButton: document.getElementById("connect-button"),
        disconnectButton: document.getElementById("disconnect-button"),
        userModeButton: document.getElementById("user-mode-button"),
        adminModeButton: document.getElementById("admin-mode-button"),
        superAdminModeButton: document.getElementById("super-admin-mode-button"),
        connectionStatus: document.getElementById("connection-status"),
        sessionRoleLabel: document.getElementById("session-role-label"),
        sessionEndpointLabel: document.getElementById("session-endpoint-label"),
        roomPanelTitle: document.getElementById("room-panel-title"),
        roomScopeTabs: document.getElementById("room-scope-tabs"),
        refreshRoomsButton: document.getElementById("refresh-rooms-button"),
        createRoomButton: document.getElementById("create-room-button"),
        fetchRoomDetailButton: document.getElementById("fetch-room-detail-button"),
        roomList: document.getElementById("room-list"),
        roomEyebrow: document.getElementById("room-eyebrow"),
        roomTitle: document.getElementById("room-title"),
        roomSubtitle: document.getElementById("room-subtitle"),
        roomStatusChip: document.getElementById("room-status-chip"),
        counselorStatusChip: document.getElementById("counselor-status-chip"),
        toolbarGuide: document.getElementById("toolbar-guide"),
        requestCounselorButton: document.getElementById("request-counselor-button"),
        claimRoomButton: document.getElementById("claim-room-button"),
        releaseRoomButton: document.getElementById("release-room-button"),
        solveRoomButton: document.getElementById("solve-room-button"),
        closeRoomButton: document.getElementById("close-room-button"),
        loadOlderButton: document.getElementById("load-older-button"),
        messageEmptyState: document.getElementById("message-empty-state"),
        messageStream: document.getElementById("message-stream"),
        messageForm: document.getElementById("message-form"),
        messageInput: document.getElementById("message-input"),
        composerHint: document.getElementById("composer-hint"),
        sendMessageButton: document.getElementById("send-message-button"),
        detailRoomId: document.getElementById("detail-room-id"),
        detailCustomerUserId: document.getElementById("detail-customer-user-id"),
        detailCounselorUserId: document.getElementById("detail-counselor-user-id"),
        detailRequestedAt: document.getElementById("detail-requested-at"),
        detailLastMessageId: document.getElementById("detail-last-message-id"),
        detailLastMessageAt: document.getElementById("detail-last-message-at"),
        detailCreatedAt: document.getElementById("detail-created-at"),
        detailUpdatedAt: document.getElementById("detail-updated-at"),
        operatorCard: document.getElementById("operator-card"),
        operatorHint: document.getElementById("operator-hint"),
        activityFeed: document.getElementById("activity-feed")
    };

    initialize();

    function initialize() {
        elements.sessionEndpointLabel.textContent = CONTRACT.endpoint;
        restorePreferences();
        bindEvents();
        setMode("user", false);
        renderScopeTabs();
        refreshUi();
        pushActivity("info", "워크스페이스를 열었습니다.", "로컬 백엔드에 맞는 실제 채팅 흐름을 테스트할 수 있습니다.");
    }

    function restorePreferences() {
        state.rememberToken = window.localStorage.getItem(STORAGE_REMEMBER_KEY) === "true";
        elements.rememberTokenToggle.checked = state.rememberToken;
    }

    function bindEvents() {
        elements.connectButton.addEventListener("click", connect);
        elements.disconnectButton.addEventListener("click", disconnect);
        elements.userModeButton.addEventListener("click", function () { setMode("user", true); });
        elements.adminModeButton.addEventListener("click", function () { setMode("admin", true); });
        elements.superAdminModeButton.addEventListener("click", function () { setMode("super_admin", true); });
        elements.rememberTokenToggle.addEventListener("change", handleRememberTokenToggleChange);
        elements.refreshRoomsButton.addEventListener("click", function () {
            refreshRoomList(true);
        });
        elements.createRoomButton.addEventListener("click", createRoom);
        elements.fetchRoomDetailButton.addEventListener("click", function () {
            refreshActiveRoom(true);
        });
        elements.requestCounselorButton.addEventListener("click", requestCounselor);
        elements.claimRoomButton.addEventListener("click", claimRoom);
        elements.releaseRoomButton.addEventListener("click", releaseRoom);
        elements.solveRoomButton.addEventListener("click", solveRoom);
        elements.closeRoomButton.addEventListener("click", closeRoom);
        elements.loadOlderButton.addEventListener("click", loadOlderMessages);
        elements.messageForm.addEventListener("submit", handleMessageSubmit);
        elements.messageInput.addEventListener("keydown", handleComposerKeydown);
        document.addEventListener("visibilitychange", handleVisibilityChange);
    }

    function handleRememberTokenToggleChange() {
        state.rememberToken = !!elements.rememberTokenToggle.checked;
        window.localStorage.setItem(STORAGE_REMEMBER_KEY, String(state.rememberToken));

        if (state.rememberToken) {
            saveTokenForCurrentMode(normalizeToken(elements.jwtInput.value));
            pushActivity("success", "이 역할의 JWT 저장을 켰습니다.", "다음에 같은 역할로 들어오면 자동으로 채워집니다.");
            return;
        }

        deleteTokenForCurrentMode();
        pushActivity("info", "이 역할의 JWT 저장을 끘습니다.", "브라우저에 저장된 현재 역할 토큰도 함께 지웠습니다.");
    }

    function setMode(mode, logChange) {
        if (state.mode === mode && !logChange) {
            return;
        }

        if (state.connected || state.connecting) {
            disconnect(true);
        }

        state.mode = mode;
        state.scope = MODE_CONFIG[mode].defaultScope;
        state.sessionUserId = null;
        state.sessionRole = null;
        state.rooms = [];
        clearActiveRoom();
        loadSavedTokenForCurrentMode();
        renderScopeTabs();
        refreshUi();

        if (logChange) {
            pushActivity("info", MODE_CONFIG[mode].label + " 모드로 전환했습니다.", "역할에 맞는 방 목록과 액션만 남겨 두었습니다.");
        }
    }

    function renderScopeTabs() {
        clearNode(elements.roomScopeTabs);

        MODE_CONFIG[state.mode].scopes.forEach(function (scope) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "scope-tab" + (scope.id === state.scope ? " is-active" : "");
            button.textContent = scope.label;
            button.addEventListener("click", function () {
                selectScope(scope.id);
            });
            elements.roomScopeTabs.appendChild(button);
        });
    }

    async function selectScope(scopeId, preserveRoomId) {
        if (state.scope === scopeId && !preserveRoomId) {
            await refreshRoomList(true);
            return;
        }

        state.scope = scopeId;
        state.rooms = [];
        renderScopeTabs();
        refreshUi();
        await refreshRoomList(true, preserveRoomId || null);
    }

    async function connect() {
        if (state.connected) {
            pushActivity("info", "이미 실시간 연결이 열려 있습니다.", "");
            return;
        }

        if (state.connecting) {
            pushActivity("info", "이미 연결을 시도하고 있습니다.", "");
            return;
        }

        const token = normalizeToken(elements.jwtInput.value);
        if (!token) {
            pushActivity("error", "JWT가 필요합니다.", "연결 전에 현재 역할의 JWT를 입력해 주세요.");
            return;
        }

        if (state.rememberToken) {
            saveTokenForCurrentMode(token);
        }

        const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
        const socketUrl = protocol + "//" + window.location.host + CONTRACT.endpoint;

        resetSocketOnly();

        try {
            state.socket = new WebSocket(socketUrl, STOMP_SUBPROTOCOLS);
        } catch (error) {
            pushActivity("error", "WebSocket을 열지 못했습니다.", safe(error.message));
            return;
        }

        state.connecting = true;
        refreshUi();
        pushActivity("info", "실시간 연결을 시작했습니다.", socketUrl);

        state.connectTimeoutId = window.setTimeout(function () {
            if (state.connecting && !state.connected) {
                pushActivity("error", "STOMP CONNECT 응답이 늦습니다.", "JWT와 서버 로그를 확인해 주세요.");
                disconnect(true);
            }
        }, CONNECT_TIMEOUT_MS);

        state.socket.onopen = function () {
            const connectFrame = buildFrame("CONNECT", {
                host: window.location.host,
                "accept-version": "1.2",
                "heart-beat": "0,0",
                Authorization: "Bearer " + token
            });
            sendRawFrame(connectFrame);
        };

        state.socket.onmessage = function (event) {
            splitStompFrames(event.data).forEach(function (rawFrame) {
                handleFrame(parseFrame(rawFrame));
            });
        };

        state.socket.onerror = function () {
            pushActivity("error", "WebSocket 오류가 발생했습니다.", "서버가 연결을 끊기 전에 에러가 난 상태입니다.");
        };

        state.socket.onclose = function (event) {
            const wasConnected = state.connected || state.connecting;
            resetSocketOnly();
            refreshUi();
            if (wasConnected) {
                pushActivity("info", "실시간 연결이 닫혔습니다.", "close code=" + safe(event.code));
            }
        };
    }

    function disconnect(silent) {
        clearConnectTimeout();

        if (!state.socket) {
            state.connected = false;
            state.connecting = false;
            refreshUi();
            return;
        }

        if (state.connected) {
            try {
                sendRawFrame(buildFrame("DISCONNECT", {}));
            } catch (error) {
                // 소켓이 이미 닫히는 중이면 그대로 닫는다.
            }
        }

        try {
            state.socket.close();
        } catch (error) {
            // 이미 닫힌 소켓이면 조용히 넘어간다.
        }

        if (!silent) {
            pushActivity("info", "실시간 연결을 종료했습니다.", "");
        }
    }

    function resetSocketOnly() {
        clearConnectTimeout();
        if (state.pendingAutoReadTimerId) {
            window.clearTimeout(state.pendingAutoReadTimerId);
            state.pendingAutoReadTimerId = null;
        }
        state.connected = false;
        state.connecting = false;
        state.subscriptions.clear();
        state.socket = null;
    }

    function clearConnectTimeout() {
        if (state.connectTimeoutId) {
            window.clearTimeout(state.connectTimeoutId);
            state.connectTimeoutId = null;
        }
    }

    async function refreshRoomList(silent, preserveRoomId) {
        state.roomListLoading = true;
        refreshUi();

        try {
            const data = await requestJson(buildRoomListUrl(state.scope), "방 목록 불러오기", {}, {
                silentSuccess: true,
                silentError: !!silent
            });

            state.rooms = extractItems(data);
            renderRoomList();

            const roomIdToReopen = preserveRoomId != null ? Number(preserveRoomId) : state.activeRoomId;
            if (roomIdToReopen) {
                const matchedRoom = state.rooms.find(function (room) {
                    return Number(room.roomId) === roomIdToReopen;
                });

                if (matchedRoom) {
                    await selectRoom(matchedRoom, true);
                } else if (!preserveRoomId) {
                    clearActiveRoom();
                }
            }

            if (!silent) {
                pushActivity("success", "방 목록을 새로 불러왔습니다.", MODE_CONFIG[state.mode].scopes.find(function (scope) {
                    return scope.id === state.scope;
                }).label + " 기준입니다.");
            }
        } catch (error) {
            if (!silent) {
                pushActivity("error", "방 목록을 불러오지 못했습니다.", safe(error.message));
            }
        } finally {
            state.roomListLoading = false;
            refreshUi();
        }
    }

    async function createRoom() {
        if (!MODE_CONFIG[state.mode].canCreateRoom) {
            pushActivity("error", "이 역할은 새 문의를 만들 수 없습니다.", "고객 모드에서만 문의방을 시작할 수 있습니다.");
            return;
        }

        try {
            const data = await requestJson(CONTRACT.supportApiPrefix + "/rooms", "새 문의 시작", {
                method: "POST"
            });

            const roomId = data && data.roomId ? Number(data.roomId) : null;
            await refreshRoomList(true, roomId);
            if (roomId) {
                await openRoomById(roomId, true);
            }
        } catch (error) {
            pushActivity("error", "문의방 생성에 실패했습니다.", safe(error.message));
        }
    }

    async function refreshActiveRoom(notify) {
        if (!state.activeRoomId) {
            pushActivity("info", "먼저 방을 선택해 주세요.", "");
            return;
        }

        await loadActiveRoomDetail(notify);
        await loadMessages(true, notify);
        maybeAutoReadCurrentRoom("room-refresh");
    }

    async function openRoomById(roomId, notify) {
        const matchedRoom = state.rooms.find(function (room) {
            return Number(room.roomId) === Number(roomId);
        });

        if (matchedRoom) {
            await selectRoom(matchedRoom, notify);
            return;
        }

        state.activeRoomId = Number(roomId);
        state.activeRoomSummary = { roomId: Number(roomId) };
        state.messages = [];
        state.hasOlderMessages = false;
        state.nextCursor = null;
        refreshUi();
        await loadActiveRoomDetail(notify);
        await loadMessages(true, notify);
    }

    async function selectRoom(room, notify) {
        const roomId = Number(room.roomId);
        if (!roomId) {
            return;
        }

        state.activeRoomId = roomId;
        state.activeRoomSummary = room;
        state.activeRoom = buildFallbackRoomDetail(room);
        state.messages = [];
        state.hasOlderMessages = false;
        state.nextCursor = null;

        ensureRealtimeSubscriptions();
        refreshUi();

        if (!canOpenConversationForCurrentSelection()) {
            if (notify) {
                pushActivity("info", "대기열 방을 선택했습니다.", "상담원은 claim 후에만 상세 이력과 실시간 대화를 열 수 있습니다.");
            }
            return;
        }

        await loadActiveRoomDetail(false);
        await loadMessages(true, false);
        maybeAutoReadCurrentRoom("room-select");

        if (notify) {
            pushActivity("success", "문의방을 열었습니다.", "문의 #" + roomId + " 대화를 불러왔습니다.");
        }
    }

    function clearActiveRoom() {
        state.activeRoomId = null;
        state.activeRoomSummary = null;
        state.activeRoom = null;
        state.messages = [];
        state.hasOlderMessages = false;
        state.nextCursor = null;
        unsubscribeDestination("room-feed");
        unsubscribeDestination("room-read-feed");
        refreshUi();
    }

    async function loadActiveRoomDetail(notify) {
        if (!state.activeRoomId || !canOpenConversationForCurrentSelection()) {
            return;
        }

        const roomId = state.activeRoomId;
        try {
            const data = await requestJson(
                CONTRACT.supportApiPrefix + "/rooms/" + roomId,
                "방 정보 불러오기",
                {},
                { silentSuccess: true, silentError: !notify }
            );

            if (state.activeRoomId !== roomId) {
                return;
            }

            state.activeRoom = data;
            syncSummaryWithDetail(data);
            refreshUi();
        } catch (error) {
            if (notify) {
                pushActivity("error", "방 정보를 불러오지 못했습니다.", safe(error.message));
            }
        }
    }

    async function loadMessages(reset, notify) {
        if (!state.activeRoomId || !canOpenConversationForCurrentSelection()) {
            return;
        }

        const roomId = state.activeRoomId;
        const beforeMessageId = reset ? null : state.nextCursor;

        if (reset) {
            state.messagesLoading = true;
        } else {
            state.loadingOlderMessages = true;
        }
        refreshUi();

        try {
            const url = buildMessagesUrl(roomId, beforeMessageId);
            const data = await requestJson(url, "메시지 이력 불러오기", {}, {
                silentSuccess: true,
                silentError: !notify
            });

            if (state.activeRoomId !== roomId) {
                return;
            }

            const responseMessages = extractMessages(data);
            state.messages = reset
                ? normalizeMessages(responseMessages)
                : mergeMessages(responseMessages, state.messages);
            state.hasOlderMessages = !!(data && data.hasNext);
            state.nextCursor = data && data.nextCursor ? Number(data.nextCursor) : null;

            renderMessages(true);

            if (notify) {
                pushActivity("success", "메시지 이력을 불러왔습니다.", responseMessages.length + "건을 가져왔습니다.");
            }
        } catch (error) {
            if (notify) {
                pushActivity("error", "메시지 이력을 불러오지 못했습니다.", safe(error.message));
            }
        } finally {
            state.messagesLoading = false;
            state.loadingOlderMessages = false;
            refreshUi();
        }
    }

    async function loadOlderMessages() {
        if (!state.hasOlderMessages || !state.nextCursor || state.loadingOlderMessages) {
            return;
        }
        await loadMessages(false, true);
    }

    async function handleMessageSubmit(event) {
        event.preventDefault();

        if (!canSendMessage()) {
            pushActivity("error", "현재 상태에서는 메시지를 보낼 수 없습니다.", "연결, 방 선택, 역할 권한을 다시 확인해 주세요.");
            return;
        }

        const content = String(elements.messageInput.value || "").trim();
        if (!content) {
            pushActivity("error", "빈 메시지는 보낼 수 없습니다.", "내용을 입력해 주세요.");
            return;
        }

        sendJsonFrame(
            CONTRACT.publishPrefix + "/rooms/" + state.activeRoomId + "/messages",
            { content: content }
        );
        elements.messageInput.value = "";
        refreshUi();
        pushActivity("success", "메시지를 전송했습니다.", "문의 #" + state.activeRoomId + "로 보냈습니다.");
    }

    function handleComposerKeydown(event) {
        if (event.key !== "Enter") {
            return;
        }

        if (event.ctrlKey || event.metaKey) {
            event.preventDefault();
            elements.messageForm.requestSubmit();
        }
    }

    async function requestCounselor() {
        if (!canRequestCounselor()) {
            pushActivity("error", "상담원 연결 요청이 가능한 상태가 아닙니다.", "이미 상담원이 배정되었거나 요청이 접수된 방일 수 있습니다.");
            return;
        }

        try {
            const data = await requestJson(
                CONTRACT.supportApiPrefix + "/rooms/" + state.activeRoomId + "/counselor-request",
                "상담원 연결 요청",
                { method: "POST" }
            );

            if (data) {
                state.activeRoom = Object.assign({}, state.activeRoom || {}, {
                    roomId: data.roomId,
                    status: data.status,
                    counselorUserId: data.counselorUserId,
                    customerRequestedCounselorAt: data.customerRequestedCounselorAt
                });
                if (state.activeRoomSummary) {
                    state.activeRoomSummary.status = data.status;
                    state.activeRoomSummary.customerRequestedCounselorAt = data.customerRequestedCounselorAt;
                }
                refreshUi();
            }

            scheduleRoomListRefresh();
        } catch (error) {
            pushActivity("error", "상담원 연결 요청에 실패했습니다.", safe(error.message));
        }
    }

    async function claimRoom() {
        if (!state.activeRoomId) {
            pushActivity("error", "먼저 방을 선택해 주세요.", "");
            return;
        }

        try {
            const data = await requestJson(
                CONTRACT.adminApiPrefix + "/rooms/" + state.activeRoomId + "/claim",
                "방 배정",
                { method: "POST" }
            );

            const claimed = !!(data && data.claimed);
            if (claimed && state.mode === "admin") {
                await selectScope("my", state.activeRoomId);
                return;
            }

            await refreshActiveRoom(false);
            scheduleRoomListRefresh();
        } catch (error) {
            pushActivity("error", "방 배정에 실패했습니다.", safe(error.message));
        }
    }

    async function releaseRoom() {
        if (!state.activeRoomId) {
            pushActivity("error", "먼저 방을 선택해 주세요.", "");
            return;
        }

        try {
            await requestJson(
                CONTRACT.adminApiPrefix + "/rooms/" + state.activeRoomId + "/release",
                "배정 해제",
                { method: "POST" }
            );
            await refreshActiveRoom(false);
            scheduleRoomListRefresh();
        } catch (error) {
            pushActivity("error", "배정 해제에 실패했습니다.", safe(error.message));
        }
    }

    async function solveRoom() {
        if (!state.activeRoomId) {
            pushActivity("error", "먼저 방을 선택해 주세요.", "");
            return;
        }

        try {
            await requestJson(
                CONTRACT.adminApiPrefix + "/rooms/" + state.activeRoomId + "/solve",
                "해결 처리",
                { method: "POST" }
            );
            await refreshActiveRoom(false);
            scheduleRoomListRefresh();
        } catch (error) {
            pushActivity("error", "해결 처리에 실패했습니다.", safe(error.message));
        }
    }

    async function closeRoom() {
        if (!state.activeRoomId) {
            pushActivity("error", "먼저 방을 선택해 주세요.", "");
            return;
        }

        try {
            await requestJson(
                CONTRACT.adminApiPrefix + "/rooms/" + state.activeRoomId + "/close",
                "문의 종료",
                { method: "POST" }
            );
            await refreshActiveRoom(false);
            scheduleRoomListRefresh();
        } catch (error) {
            pushActivity("error", "문의 종료에 실패했습니다.", safe(error.message));
        }
    }

    function handleVisibilityChange() {
        if (document.visibilityState === "visible") {
            maybeAutoReadCurrentRoom("visibility");
        }
    }

    function handleFrame(frame) {
        if (!frame.command) {
            return;
        }

        if (frame.command === "CONNECTED") {
            state.connected = true;
            state.connecting = false;
            clearConnectTimeout();
            hydrateSessionFromToken();
            ensureRealtimeSubscriptions();
            refreshUi();
            pushActivity("success", "실시간 연결이 열렸습니다.", "현재 역할에 맞는 구독을 자동으로 붙였습니다.");
            refreshRoomList(true);
            maybeAutoReadCurrentRoom("connect");
            return;
        }

        if (frame.command === "MESSAGE") {
            handleMessageFrame(frame);
            return;
        }

        if (frame.command === "ERROR") {
            pushActivity("error", "STOMP ERROR frame을 받았습니다.", frame.body || "본문이 비어 있습니다.");
        }
    }

    function handleMessageFrame(frame) {
        const destination = frame.headers.destination || "";
        const payload = tryParseJson(frame.body);

        if (!payload) {
            return;
        }

        if (destination.indexOf(CONTRACT.userQueuePrefix + "/read") > -1) {
            handleUnreadSyncEvent(payload);
            return;
        }

        if (destination.indexOf("/queue") > -1) {
            handleQueueEvent(payload);
            return;
        }

        if (destination.indexOf("/read") > -1) {
            handleReadReceiptEvent(payload);
            return;
        }

        handleRoomMessageEvent(payload);
    }

    function handleUnreadSyncEvent(payload) {
        state.latestUnreadSync = payload;
        if (payload && payload.roomId) {
            state.lastAutoReadByRoom[payload.roomId] = payload.lastReadMessageId;
            patchRoomSummaryUnread(payload.roomId, payload.unreadCount);
        }

        pushActivity(
            "info",
            "개인 unread 동기화가 도착했습니다.",
            buildUnreadSyncSummary(payload)
        );
        refreshUi();
    }

    function handleReadReceiptEvent(payload) {
        pushActivity(
            "info",
            "읽음 이벤트가 도착했습니다.",
            buildReadReceiptSummary(payload)
        );
    }

    function handleQueueEvent(payload) {
        syncRoomStateFromQueueEvent(payload);
        scheduleRoomListRefresh();

        pushActivity(
            "info",
            "운영 큐 이벤트를 받았습니다.",
            buildQueueEventSummary(payload)
        );
    }

    function handleRoomMessageEvent(payload) {
        if (!payload || !payload.roomId) {
            return;
        }

        patchRoomSummaryFromMessageEvent(payload);
        scheduleRoomListRefresh();

        if (Number(payload.roomId) !== Number(state.activeRoomId)) {
            return;
        }

        upsertMessage(payload);
        syncRoomStateFromMessageEvent(payload);
        renderMessages(true);
        maybeAutoReadCurrentRoom("message-event");
    }

    function syncRoomStateFromQueueEvent(payload) {
        if (!payload || Number(payload.roomId) !== Number(state.activeRoomId)) {
            return;
        }

        if (!state.activeRoom) {
            state.activeRoom = buildFallbackRoomDetail(state.activeRoomSummary || payload);
        }

        if (payload.eventType === "REQUESTED") {
            state.activeRoom.customerRequestedCounselorAt = state.activeRoom.customerRequestedCounselorAt || new Date().toISOString();
        }

        if (payload.eventType === "CLAIMED") {
            state.activeRoom.counselorUserId = payload.counselorUserId || state.activeRoom.counselorUserId;
            state.activeRoom.customerRequestedCounselorAt = null;
        }

        if (payload.eventType === "RELEASED") {
            state.activeRoom.counselorUserId = null;
            state.activeRoom.customerRequestedCounselorAt = null;
        }

        if (payload.eventType === "SOLVED") {
            state.activeRoom.status = "SOLVED";
        }

        if (payload.eventType === "CLOSED") {
            state.activeRoom.status = "CLOSED";
            state.activeRoom.counselorUserId = null;
            state.activeRoom.customerRequestedCounselorAt = null;
        }

        if (state.activeRoomSummary) {
            state.activeRoomSummary.status = state.activeRoom.status;
            state.activeRoomSummary.customerRequestedCounselorAt = state.activeRoom.customerRequestedCounselorAt;
        }

        if (state.mode === "admin" && state.scope === "queue" && payload.eventType === "CLAIMED" && Number(payload.counselorUserId) === Number(state.sessionUserId)) {
            selectScope("my", state.activeRoomId);
        }

        refreshUi();
    }

    function syncRoomStateFromMessageEvent(payload) {
        if (!state.activeRoom) {
            state.activeRoom = buildFallbackRoomDetail(state.activeRoomSummary || payload);
        }

        state.activeRoom.lastMessageId = payload.messageId;
        state.activeRoom.lastMessageAt = payload.createdAt;

        if (payload.senderType === "SYSTEM" && payload.content) {
            const content = String(payload.content);
            if (content.indexOf("상담원 연결 요청이 접수") > -1) {
                state.activeRoom.customerRequestedCounselorAt = payload.createdAt || new Date().toISOString();
                state.activeRoom.status = "OPEN";
            } else if (content.indexOf("상담원이 연결") > -1) {
                state.activeRoom.customerRequestedCounselorAt = null;
                state.activeRoom.status = "OPEN";
            } else if (content.indexOf("답변이 완료") > -1) {
                state.activeRoom.status = "SOLVED";
            } else if (content.indexOf("다시 진행 상태") > -1) {
                state.activeRoom.status = "OPEN";
            } else if (content.indexOf("자동 종료") > -1 || content.indexOf("문의가 종료") > -1) {
                state.activeRoom.status = "CLOSED";
            }
        }

        syncSummaryWithDetail(state.activeRoom);
        refreshUi();
    }

    function ensureRealtimeSubscriptions() {
        if (!state.connected) {
            return;
        }

        if (MODE_CONFIG[state.mode].autoQueue) {
            subscribeDestination("queue-feed", CONTRACT.subscribePrefix + "/queue");
        } else {
            unsubscribeDestination("queue-feed");
        }

        if (MODE_CONFIG[state.mode].autoUserRead) {
            subscribeDestination("user-read-feed", CONTRACT.userQueuePrefix + "/read");
        } else {
            unsubscribeDestination("user-read-feed");
        }

        if (shouldSubscribeActiveRoom()) {
            subscribeDestination("room-feed", CONTRACT.subscribePrefix + "/rooms/" + state.activeRoomId);
            subscribeDestination("room-read-feed", CONTRACT.subscribePrefix + "/rooms/" + state.activeRoomId + "/read");
        } else {
            unsubscribeDestination("room-feed");
            unsubscribeDestination("room-read-feed");
        }
    }

    function shouldSubscribeActiveRoom() {
        if (!state.connected || !state.activeRoomId) {
            return false;
        }

        if (state.mode === "super_admin") {
            return false;
        }

        if (state.mode === "admin" && state.scope === "queue") {
            return false;
        }

        return canOpenConversationForCurrentSelection();
    }

    function subscribeDestination(key, destination) {
        const existing = state.subscriptions.get(key);
        if (existing && existing.destination === destination) {
            return;
        }

        if (existing) {
            unsubscribeDestination(key);
        }

        const id = "sub-" + state.subscriptionCounter++;
        state.subscriptions.set(key, { id: id, destination: destination });
        sendRawFrame(buildFrame("SUBSCRIBE", {
            id: id,
            destination: destination
        }));
    }

    function unsubscribeDestination(key) {
        const subscription = state.subscriptions.get(key);
        if (!subscription || !state.socket || state.socket.readyState !== WebSocket.OPEN) {
            state.subscriptions.delete(key);
            return;
        }

        state.socket.send(buildFrame("UNSUBSCRIBE", { id: subscription.id }));
        state.subscriptions.delete(key);
    }

    function maybeAutoReadCurrentRoom(source) {
        if (!canAutoReadCurrentRoom()) {
            return;
        }

        const latestMessage = state.messages[state.messages.length - 1];
        if (!latestMessage || !latestMessage.messageId) {
            return;
        }

        const roomId = state.activeRoomId;
        const lastReadMessageId = Number(latestMessage.messageId);
        if (Number(state.lastAutoReadByRoom[roomId]) === lastReadMessageId) {
            return;
        }

        if (state.pendingAutoReadTimerId) {
            window.clearTimeout(state.pendingAutoReadTimerId);
        }

        state.pendingAutoReadTimerId = window.setTimeout(function () {
            if (!canAutoReadCurrentRoom()) {
                return;
            }

            sendJsonFrame(
                CONTRACT.publishPrefix + "/rooms/" + roomId + "/read",
                { lastReadMessageId: lastReadMessageId }
            );
            state.lastAutoReadByRoom[roomId] = lastReadMessageId;
            pushActivity(
                "info",
                "읽음 위치를 자동 반영했습니다.",
                "message #" + lastReadMessageId + " · source=" + source
            );
        }, 220);
    }

    function canAutoReadCurrentRoom() {
        if (!state.connected || document.visibilityState !== "visible") {
            return false;
        }

        if (!MODE_CONFIG[state.mode].canParticipate) {
            return false;
        }

        if (!shouldSubscribeActiveRoom()) {
            return false;
        }

        if (!state.activeRoom || String(state.activeRoom.status) === "CLOSED") {
            return false;
        }

        return true;
    }

    function canSendMessage() {
        if (!MODE_CONFIG[state.mode].canParticipate || !state.connected || !state.activeRoomId) {
            return false;
        }

        if (!canOpenConversationForCurrentSelection()) {
            return false;
        }

        if (!state.activeRoom || String(state.activeRoom.status) === "CLOSED") {
            return false;
        }

        return true;
    }

    function canOpenConversationForCurrentSelection() {
        if (!state.activeRoomId) {
            return false;
        }

        if (state.mode === "admin" && state.scope === "queue") {
            return false;
        }

        return true;
    }

    function canRequestCounselor() {
        if (state.mode !== "user" || !state.activeRoomId || !state.activeRoom) {
            return false;
        }

        if (String(state.activeRoom.status) === "CLOSED") {
            return false;
        }

        if (state.activeRoom.counselorUserId != null) {
            return false;
        }

        return !state.activeRoom.customerRequestedCounselorAt;
    }

    function renderRoomList() {
        clearNode(elements.roomList);

        if (state.roomListLoading) {
            elements.roomList.appendChild(createEmptyBlock("방 목록을 불러오는 중입니다."));
            return;
        }

        if (!state.rooms.length) {
            elements.roomList.appendChild(createEmptyBlock(buildRoomListEmptyMessage()));
            return;
        }

        state.rooms.forEach(function (room) {
            const button = document.createElement("button");
            button.type = "button";
            button.className = "room-item" + (Number(room.roomId) === Number(state.activeRoomId) ? " is-selected" : "");
            button.addEventListener("click", function () {
                selectRoom(room, true);
            });

            const head = document.createElement("div");
            head.className = "room-item-head";

            const title = document.createElement("div");
            const titleHeading = document.createElement("h3");
            titleHeading.className = "room-item-title";
            titleHeading.textContent = "문의 #" + safe(room.roomId);
            const titleMeta = document.createElement("p");
            titleMeta.className = "room-item-meta";
            titleMeta.textContent = buildRoomMetaText(room);
            title.appendChild(titleHeading);
            title.appendChild(titleMeta);

            const unreadChip = document.createElement("span");
            unreadChip.className = "room-chip" + (Number(room.unreadCount) > 0 ? " is-brand" : "");
            unreadChip.textContent = Number(room.unreadCount) > 0 ? "unread " + room.unreadCount : "읽음 완료";

            head.appendChild(title);
            head.appendChild(unreadChip);

            const foot = document.createElement("div");
            foot.className = "room-item-foot";

            const badges = document.createElement("div");
            badges.className = "room-badges";
            badges.appendChild(createRoomChip(normalizeRoomStatus(room.status), statusModifier(normalizeRoomStatus(room.status))));

            if (room.customerRequestedCounselorAt) {
                badges.appendChild(createRoomChip("상담원 요청", "accent"));
            }

            if (state.mode === "admin" && state.scope === "queue") {
                badges.appendChild(createRoomChip("claim 후 입장", "danger"));
            }

            const time = document.createElement("span");
            time.className = "room-item-meta";
            time.textContent = room.lastMessageAt ? formatDateTime(room.lastMessageAt) : "최근 메시지 없음";

            foot.appendChild(badges);
            foot.appendChild(time);

            button.appendChild(head);
            button.appendChild(foot);
            elements.roomList.appendChild(button);
        });
    }

    function renderMessages(keepScrollAtBottom) {
        const shouldStayBottom = keepScrollAtBottom && isNearBottom(elements.messageStream);
        const previousScrollTop = elements.messageStream.scrollTop;
        clearNode(elements.messageStream);

        if (!state.activeRoomId) {
            elements.messageEmptyState.hidden = false;
            elements.messageEmptyState.textContent = "방을 선택하면 대화와 운영 상태가 여기에 표시됩니다.";
            return;
        }

        if (!canOpenConversationForCurrentSelection()) {
            elements.messageEmptyState.hidden = false;
            elements.messageEmptyState.textContent = "이 방은 아직 담당 전이라 대화창을 열지 않았습니다. claim 후 실제 채팅 화면으로 들어옵니다.";
            return;
        }

        if (state.messagesLoading) {
            elements.messageEmptyState.hidden = false;
            elements.messageEmptyState.textContent = "메시지 이력을 불러오는 중입니다.";
            return;
        }

        if (!state.messages.length) {
            elements.messageEmptyState.hidden = false;
            elements.messageEmptyState.textContent = "아직 메시지가 없습니다. 첫 메시지를 보내면 여기부터 대화가 시작됩니다.";
            return;
        }

        elements.messageEmptyState.hidden = true;

        state.messages.forEach(function (message) {
            const group = document.createElement("div");
            const appearance = resolveMessageAppearance(message);
            group.className = "message-group " + appearance.groupClass;

            const bubble = document.createElement("article");
            bubble.className = "message-bubble " + appearance.bubbleClass;

            const meta = document.createElement("div");
            meta.className = "message-meta";

            const sender = document.createElement("span");
            sender.className = "message-sender";
            sender.textContent = appearance.senderLabel;

            const time = document.createElement("span");
            time.textContent = formatTime(message.createdAt);

            meta.appendChild(sender);
            meta.appendChild(time);

            const body = document.createElement("p");
            body.className = "message-text";
            body.textContent = safe(message.content);

            bubble.appendChild(meta);
            bubble.appendChild(body);
            group.appendChild(bubble);
            elements.messageStream.appendChild(group);
        });

        if (shouldStayBottom) {
            scrollTimelineToBottom();
            return;
        }

        elements.messageStream.scrollTop = previousScrollTop;
    }

    function refreshUi() {
        renderConnectionStatus();
        renderModeButtons();
        renderScopeTabs();
        renderRoomList();
        renderConversationHeader();
        renderToolbar();
        renderComposer();
        renderMessages(false);
        renderRoomInfo();
        renderActivityFeed();
        renderOperatorCard();
        renderLoadOlderButton();
    }

    function renderConnectionStatus() {
        elements.connectionStatus.className = "connection-pill " + connectionStatusModifier();
        elements.connectionStatus.textContent = connectionStatusText();
        elements.sessionRoleLabel.textContent = buildSessionLabel();
        elements.connectButton.disabled = state.connected || state.connecting;
        elements.disconnectButton.disabled = !state.connected && !state.connecting;
    }

    function renderModeButtons() {
        elements.userModeButton.classList.toggle("is-active", state.mode === "user");
        elements.adminModeButton.classList.toggle("is-active", state.mode === "admin");
        elements.superAdminModeButton.classList.toggle("is-active", state.mode === "super_admin");
        elements.createRoomButton.classList.toggle("is-hidden", !MODE_CONFIG[state.mode].canCreateRoom);
        elements.fetchRoomDetailButton.classList.toggle(
            "is-hidden",
            !state.activeRoomId || !canOpenConversationForCurrentSelection()
        );
        elements.roomPanelTitle.textContent = resolveScopeLabel(state.scope);
    }

    function renderConversationHeader() {
        if (!state.activeRoomId) {
            elements.roomEyebrow.textContent = "ROOM";
            elements.roomTitle.textContent = "대화를 시작할 방을 선택해 주세요";
            elements.roomSubtitle.textContent = "현재 역할에 맞는 목록을 불러와 방을 고르면 메시지 이력과 실시간 흐름이 열립니다.";
            setStatusBadge(elements.roomStatusChip, "is-muted", "미선택");
            setStatusBadge(elements.counselorStatusChip, "is-muted", "상담원 미배정");
            return;
        }

        const room = state.activeRoom || buildFallbackRoomDetail(state.activeRoomSummary || {});
        elements.roomEyebrow.textContent = state.mode === "super_admin" ? "OPERATIONS VIEW" : "LIVE ROOM";
        elements.roomTitle.textContent = "문의 #" + safe(room.roomId);
        elements.roomSubtitle.textContent = buildRoomSubtitle(room);

        const status = normalizeRoomStatus(room.status);
        setStatusBadge(elements.roomStatusChip, statusBadgeModifier(status), status || "상태 미확인");

        if (room.counselorUserId != null) {
            setStatusBadge(elements.counselorStatusChip, "is-live", "상담원 #" + room.counselorUserId);
        } else if (room.customerRequestedCounselorAt) {
            setStatusBadge(elements.counselorStatusChip, "is-pending", "상담원 요청 접수");
        } else {
            setStatusBadge(elements.counselorStatusChip, "is-muted", "상담원 미배정");
        }
    }

    function renderToolbar() {
        elements.toolbarGuide.textContent = buildToolbarGuide();

        toggleAction(elements.requestCounselorButton, canRequestCounselor());
        toggleAction(elements.claimRoomButton, canClaimRoom());
        toggleAction(elements.releaseRoomButton, canReleaseRoom());
        toggleAction(elements.solveRoomButton, canSolveRoom());
        toggleAction(elements.closeRoomButton, canCloseRoom());
    }

    function renderComposer() {
        const visible = MODE_CONFIG[state.mode].canParticipate && canOpenConversationForCurrentSelection();
        elements.messageForm.classList.toggle("is-hidden", !visible);
        if (!visible) {
            return;
        }

        elements.sendMessageButton.disabled = !canSendMessage();
        elements.messageInput.disabled = !canSendMessage();
        elements.composerHint.textContent = buildComposerHint();
    }

    function renderRoomInfo() {
        const room = state.activeRoom || buildFallbackRoomDetail(state.activeRoomSummary || {});
        elements.detailRoomId.textContent = safe(room.roomId);
        elements.detailCustomerUserId.textContent = safe(room.customerUserId);
        elements.detailCounselorUserId.textContent = safe(room.counselorUserId);
        elements.detailRequestedAt.textContent = formatDateTime(room.customerRequestedCounselorAt);
        elements.detailLastMessageId.textContent = safe(room.lastMessageId);
        elements.detailLastMessageAt.textContent = formatDateTime(room.lastMessageAt);
        elements.detailCreatedAt.textContent = formatDateTime(room.createdAt);
        elements.detailUpdatedAt.textContent = formatDateTime(room.updatedAt);
    }

    function renderOperatorCard() {
        const visible = state.mode === "admin" || state.mode === "super_admin";
        elements.operatorCard.hidden = !visible;
        elements.operatorHint.textContent = state.mode === "super_admin"
            ? "SUPER_ADMIN은 전체 조회와 종료, 배정 해제 같은 운영 제어만 담당하고 직접 상담 메시지에는 참여하지 않습니다."
            : "ADMIN은 대기열에서 claim 한 문의방에 참여해 상담을 진행하고, 필요 시 배정 해제와 해결 처리까지 담당합니다.";
    }

    function renderActivityFeed() {
        clearNode(elements.activityFeed);

        if (!state.activities.length) {
            elements.activityFeed.appendChild(createActivityEmpty());
            return;
        }

        state.activities.forEach(function (activity) {
            const item = document.createElement("article");
            item.className = "activity-item " + activity.modifier;

            const head = document.createElement("div");
            head.className = "activity-head";

            const title = document.createElement("span");
            title.className = "activity-title";
            title.textContent = activity.title;

            const time = document.createElement("span");
            time.className = "activity-time";
            time.textContent = activity.time;

            const body = document.createElement("p");
            body.className = "activity-body";
            body.textContent = activity.body;

            head.appendChild(title);
            head.appendChild(time);
            item.appendChild(head);
            item.appendChild(body);
            elements.activityFeed.appendChild(item);
        });
    }

    function renderLoadOlderButton() {
        const visible = canOpenConversationForCurrentSelection() && state.messages.length > 0 && state.hasOlderMessages;
        elements.loadOlderButton.hidden = !visible;
        elements.loadOlderButton.disabled = state.loadingOlderMessages;
        elements.loadOlderButton.textContent = state.loadingOlderMessages ? "이전 대화를 불러오는 중..." : "이전 대화 더 보기";
    }

    function buildRoomListUrl(scope) {
        if (scope === "queue") {
            return CONTRACT.adminApiPrefix + "/queue?page=" + DEFAULT_ROOM_PAGE + "&size=" + DEFAULT_ROOM_SIZE;
        }

        if (scope === "closed") {
            return CONTRACT.adminApiPrefix + "/rooms/closed?page=" + DEFAULT_ROOM_PAGE + "&size=" + DEFAULT_ROOM_SIZE;
        }

        if (scope === "stale") {
            return CONTRACT.adminApiPrefix + "/rooms/stale?page=" + DEFAULT_ROOM_PAGE + "&size=" + DEFAULT_ROOM_SIZE;
        }

        return CONTRACT.supportApiPrefix + "/rooms/me?page=" + DEFAULT_ROOM_PAGE + "&size=" + DEFAULT_ROOM_SIZE;
    }

    function buildMessagesUrl(roomId, beforeMessageId) {
        const params = ["size=" + DEFAULT_MESSAGE_SIZE];
        if (beforeMessageId) {
            params.push("beforeMessageId=" + beforeMessageId);
        }
        return CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/messages?" + params.join("&");
    }

    async function requestJson(url, actionLabel, options, requestOptions) {
        const token = normalizeToken(elements.jwtInput.value);
        if (!token) {
            throw new Error("JWT를 먼저 입력해 주세요.");
        }

        const request = Object.assign({ method: "GET" }, options || {});
        const headers = Object.assign({}, request.headers || {}, {
            Authorization: "Bearer " + token
        });

        if (request.body && !headers["Content-Type"]) {
            headers["Content-Type"] = "application/json";
        }
        request.headers = headers;

        const response = await fetch(url, request);
        const rawText = await response.text();
        let body = null;

        try {
            body = rawText ? JSON.parse(rawText) : null;
        } catch (error) {
            body = rawText;
        }

        if (!response.ok) {
            throw new Error(extractErrorMessage(body, response.status));
        }

        if (!requestOptions || !requestOptions.silentSuccess) {
            pushActivity("success", actionLabel + "을 완료했습니다.", "");
        }

        return body && Object.prototype.hasOwnProperty.call(body, "data") ? body.data : body;
    }

    function sendJsonFrame(destination, payload) {
        sendRawFrame(buildFrame("SEND", {
            destination: destination,
            "content-type": "application/json"
        }, JSON.stringify(payload)));
    }

    function sendRawFrame(frame) {
        if (!state.socket || state.socket.readyState !== WebSocket.OPEN) {
            throw new Error("실시간 연결이 아직 열리지 않았습니다.");
        }
        state.socket.send(frame);
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
        return lines.join("\r\n") + "\r\n\0";
    }

    function splitStompFrames(payload) {
        return String(payload || "")
            .split("\0")
            .map(function (frame) { return frame.trim(); })
            .filter(function (frame) { return frame.length > 0; });
    }

    function parseFrame(rawFrame) {
        const normalized = String(rawFrame || "")
            .replace(/\0+$/, "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n");
        const lines = normalized.split("\n");
        const command = (lines.shift() || "").trim();
        const headers = {};
        let bodyIndex = lines.findIndex(function (line) { return line === ""; });

        if (bodyIndex < 0) {
            bodyIndex = lines.length;
        }

        lines.slice(0, bodyIndex).forEach(function (line) {
            const separatorIndex = line.indexOf(":");
            if (separatorIndex > -1) {
                headers[line.slice(0, separatorIndex).trim()] = line.slice(separatorIndex + 1).trim();
            }
        });

        return {
            command: command,
            headers: headers,
            body: bodyIndex < lines.length ? lines.slice(bodyIndex + 1).join("\n") : ""
        };
    }

    function pushActivity(type, title, body) {
        const modifier = type === "error"
            ? "is-error"
            : type === "success"
                ? "is-success"
                : "is-info";

        state.activities.unshift({
            modifier: modifier,
            title: title,
            body: body || "",
            time: formatTime(new Date().toISOString())
        });

        if (state.activities.length > 40) {
            state.activities.length = 40;
        }

        renderActivityFeed();
    }

    function scheduleRoomListRefresh() {
        if (state.roomRefreshTimerId) {
            return;
        }

        state.roomRefreshTimerId = window.setTimeout(function () {
            state.roomRefreshTimerId = null;
            refreshRoomList(true, state.activeRoomId);
        }, 350);
    }

    function buildSessionLabel() {
        const title = MODE_CONFIG[state.mode].sessionTitle;
        if (!state.sessionUserId) {
            return title;
        }
        return title + " · userId " + state.sessionUserId;
    }

    function hydrateSessionFromToken() {
        const token = normalizeToken(elements.jwtInput.value);
        const claims = decodeJwtClaims(token);
        state.sessionUserId = claims && claims.sub ? Number(claims.sub) : null;
        state.sessionRole = claims && claims.role ? String(claims.role) : null;
    }

    function loadSavedTokenForCurrentMode() {
        const savedToken = window.localStorage.getItem(STORAGE_TOKEN_PREFIX + state.mode);
        elements.jwtInput.value = state.rememberToken && savedToken ? savedToken : "";
    }

    function saveTokenForCurrentMode(token) {
        if (!token) {
            deleteTokenForCurrentMode();
            return;
        }
        window.localStorage.setItem(STORAGE_TOKEN_PREFIX + state.mode, token);
    }

    function deleteTokenForCurrentMode() {
        window.localStorage.removeItem(STORAGE_TOKEN_PREFIX + state.mode);
    }

    function decodeJwtClaims(token) {
        const normalized = normalizeToken(token);
        if (!normalized) {
            return null;
        }

        const parts = normalized.split(".");
        if (parts.length < 2) {
            return null;
        }

        try {
            const payload = parts[1]
                .replace(/-/g, "+")
                .replace(/_/g, "/");
            const decoded = window.atob(payload.padEnd(payload.length + (4 - payload.length % 4) % 4, "="));
            return JSON.parse(decoded);
        } catch (error) {
            return null;
        }
    }

    function patchRoomSummaryUnread(roomId, unreadCount) {
        state.rooms = state.rooms.map(function (room) {
            if (Number(room.roomId) !== Number(roomId)) {
                return room;
            }
            return Object.assign({}, room, { unreadCount: unreadCount });
        });
    }

    function patchRoomSummaryFromMessageEvent(payload) {
        state.rooms = state.rooms.map(function (room) {
            if (Number(room.roomId) !== Number(payload.roomId)) {
                return room;
            }

            return Object.assign({}, room, {
                lastMessageId: payload.messageId,
                lastMessageAt: payload.createdAt
            });
        });
    }

    function syncSummaryWithDetail(detail) {
        if (!detail || !state.activeRoomSummary) {
            return;
        }

        state.activeRoomSummary = Object.assign({}, state.activeRoomSummary, {
            roomId: detail.roomId,
            status: detail.status,
            customerRequestedCounselorAt: detail.customerRequestedCounselorAt,
            lastMessageId: detail.lastMessageId,
            lastMessageAt: detail.lastMessageAt
        });
    }

    function upsertMessage(message) {
        const normalized = normalizeMessage(message);
        const existingIndex = state.messages.findIndex(function (current) {
            return Number(current.messageId) === Number(normalized.messageId);
        });

        if (existingIndex > -1) {
            state.messages.splice(existingIndex, 1, normalized);
        } else {
            state.messages.push(normalized);
        }

        state.messages.sort(function (left, right) {
            return Number(left.messageId) - Number(right.messageId);
        });
    }

    function mergeMessages(olderMessages, newerMessages) {
        const mergedMap = new Map();

        normalizeMessages(olderMessages.concat(newerMessages)).forEach(function (message) {
            mergedMap.set(Number(message.messageId), message);
        });

        return Array.from(mergedMap.values()).sort(function (left, right) {
            return Number(left.messageId) - Number(right.messageId);
        });
    }

    function normalizeMessages(messages) {
        return (messages || []).map(normalizeMessage).sort(function (left, right) {
            return Number(left.messageId) - Number(right.messageId);
        });
    }

    function normalizeMessage(message) {
        return {
            messageId: Number(message.messageId),
            senderUserId: message.senderUserId == null ? null : Number(message.senderUserId),
            senderType: safe(message.senderType),
            messageType: safe(message.messageType),
            content: safe(message.content),
            createdAt: message.createdAt || null
        };
    }

    function buildFallbackRoomDetail(room) {
        return {
            roomId: room.roomId || null,
            customerUserId: room.customerUserId || null,
            counselorUserId: room.counselorUserId || null,
            customerRequestedCounselorAt: room.customerRequestedCounselorAt || null,
            status: room.status || null,
            lastMessageId: room.lastMessageId || null,
            lastMessageAt: room.lastMessageAt || null,
            createdAt: room.createdAt || null,
            updatedAt: room.updatedAt || null
        };
    }

    function extractItems(data) {
        return data && Array.isArray(data.items) ? data.items : [];
    }

    function extractMessages(data) {
        return data && Array.isArray(data.messages) ? data.messages : [];
    }

    function resolveMessageAppearance(message) {
        if (message.senderType === "SYSTEM") {
            return {
                groupClass: "is-center",
                bubbleClass: "is-system",
                senderLabel: "SYSTEM"
            };
        }

        if (message.senderType === "AI") {
            return {
                groupClass: "is-left",
                bubbleClass: "is-ai",
                senderLabel: "AI"
            };
        }

        if (message.senderUserId != null && state.sessionUserId != null && Number(message.senderUserId) === Number(state.sessionUserId)) {
            return {
                groupClass: "is-right",
                bubbleClass: "is-self",
                senderLabel: message.senderType === "COUNSELOR" ? "나" : "나"
            };
        }

        if (message.senderType === "COUNSELOR") {
            return {
                groupClass: "is-left",
                bubbleClass: "",
                senderLabel: "상담원"
            };
        }

        return {
            groupClass: "is-left",
            bubbleClass: "",
            senderLabel: "고객"
        };
    }

    function buildToolbarGuide() {
        if (!state.activeRoomId) {
            return "방을 고르면 역할에 맞는 액션만 오른쪽에 남기고, 필요한 경우 실시간 구독도 자동으로 붙입니다.";
        }

        if (state.mode === "admin" && state.scope === "queue") {
            return "대기열 방은 claim 전까지 대화창을 열지 않습니다. 먼저 방을 잡은 뒤 실시간 대화로 들어갑니다.";
        }

        if (state.mode === "super_admin") {
            return "운영 관리자 모드는 조회와 강제 조치 중심입니다. 메시지 전송과 읽음 처리는 열지 않습니다.";
        }

        return "메시지를 보고 있는 동안 최신 메시지는 자동 읽음 처리되고, 고객 모드에서는 필요 시 상담원 요청으로 전환할 수 있습니다.";
    }

    function buildComposerHint() {
        if (!state.activeRoomId) {
            return "먼저 방을 선택하면 대화 입력창이 열립니다.";
        }

        if (!state.connected) {
            return "실시간 연결이 열려 있지 않아도 이력은 볼 수 있지만, 메시지 전송은 연결 후에만 가능합니다.";
        }

        if (state.mode === "user") {
            return "고객 메시지 뒤에는 자동 AI 응답이 붙고, 사람이 필요하면 상담원 연결 요청으로 전환합니다.";
        }

        return "상담원 메시지를 보내면 같은 방 사용자에게 즉시 브로드캐스트되고, 방을 보고 있으면 읽음도 자동 반영됩니다.";
    }

    function buildRoomSubtitle(room) {
        if (state.mode === "admin" && state.scope === "queue") {
            return "이 방은 아직 대기열 상태입니다. claim 후에 메시지 이력과 실시간 대화창이 열립니다.";
        }

        if (String(room.status) === "CLOSED") {
            return "종료된 문의방입니다. 이력은 볼 수 있지만 새 메시지와 읽음 처리는 막혀 있습니다.";
        }

        if (String(room.status) === "SOLVED") {
            return "현재는 답변 완료 후 고객 추가 문의를 기다리는 상태입니다.";
        }

        if (room.customerRequestedCounselorAt && room.counselorUserId == null) {
            return "상담원 연결 요청이 접수되어 자동 AI 응답이 멈춘 상태입니다.";
        }

        if (room.counselorUserId != null) {
            return "현재 상담원이 배정된 문의방입니다. 실시간 메시지와 읽음 상태를 함께 확인할 수 있습니다.";
        }

        return "현재 진행 중인 문의방입니다. 메시지 전송과 자동 읽음 처리 흐름을 그대로 확인할 수 있습니다.";
    }

    function buildRoomMetaText(room) {
        const parts = [];

        if (room.lastMessageId) {
            parts.push("메시지 #" + room.lastMessageId);
        }

        if (room.lastMessageAt) {
            parts.push(formatDateTime(room.lastMessageAt));
        }

        if (!parts.length && room.createdAt) {
            parts.push("생성 " + formatDateTime(room.createdAt));
        }

        return parts.length ? parts.join(" · ") : "최근 대화 정보가 아직 없습니다.";
    }

    function buildRoomListEmptyMessage() {
        if (state.roomListLoading) {
            return "방 목록을 불러오는 중입니다.";
        }

        if (state.mode === "user") {
            return "아직 문의방이 없습니다. 새 문의 시작으로 바로 대화를 열 수 있습니다.";
        }

        if (state.scope === "queue") {
            return "현재 대기열에 새 문의방이 없습니다.";
        }

        return "이 범위에서는 아직 표시할 문의방이 없습니다.";
    }

    function buildUnreadSyncSummary(payload) {
        if (!payload) {
            return "payload 없음";
        }

        return "문의 #" + safe(payload.roomId)
            + " · lastReadMessageId=" + safe(payload.lastReadMessageId)
            + " · unread=" + safe(payload.unreadCount);
    }

    function buildReadReceiptSummary(payload) {
        if (!payload) {
            return "payload 없음";
        }

        return safe(payload.readerRole)
            + " user #" + safe(payload.readerUserId)
            + " · 문의 #" + safe(payload.roomId)
            + " · lastReadMessageId=" + safe(payload.lastReadMessageId);
    }

    function buildQueueEventSummary(payload) {
        if (!payload) {
            return "payload 없음";
        }

        return "문의 #" + safe(payload.roomId)
            + " · event=" + safe(payload.eventType)
            + (payload.counselorUserId != null ? " · counselor=" + payload.counselorUserId : "");
    }

    function canClaimRoom() {
        return isOperatorMode()
            && !!state.activeRoomId
            && !!state.activeRoomSummary
            && state.scope === "queue";
    }

    function canReleaseRoom() {
        return isOperatorMode()
            && !!state.activeRoomId
            && !!state.activeRoom
            && state.activeRoom.counselorUserId != null
            && String(state.activeRoom.status) !== "CLOSED";
    }

    function canSolveRoom() {
        return state.mode === "admin"
            && !!state.activeRoomId
            && !!state.activeRoom
            && String(state.activeRoom.status) === "OPEN";
    }

    function canCloseRoom() {
        return state.mode === "super_admin"
            && !!state.activeRoomId
            && !!state.activeRoom
            && String(state.activeRoom.status) !== "CLOSED";
    }

    function isOperatorMode() {
        return state.mode === "admin" || state.mode === "super_admin";
    }

    function toggleAction(element, visible) {
        element.classList.toggle("is-hidden", !visible);
        element.disabled = !visible;
    }

    function setStatusBadge(element, modifier, text) {
        element.className = "status-badge " + modifier;
        element.textContent = text;
    }

    function createRoomChip(text, modifier) {
        const chip = document.createElement("span");
        chip.className = "room-chip" + (modifier ? " is-" + modifier : "");
        chip.textContent = text;
        return chip;
    }

    function createEmptyBlock(text) {
        const block = document.createElement("div");
        block.className = "room-empty";
        block.textContent = text;
        return block;
    }

    function createActivityEmpty() {
        const block = document.createElement("div");
        block.className = "activity-empty";
        block.textContent = "아직 기록된 활동이 없습니다. 연결, 방 선택, 메시지 전송 같은 액션이 쌓이면 여기서 흐름을 볼 수 있습니다.";
        return block;
    }

    function normalizeRoomStatus(status) {
        return safe(status).toUpperCase();
    }

    function statusModifier(status) {
        if (status === "OPEN") {
            return "brand";
        }
        if (status === "SOLVED") {
            return "accent";
        }
        if (status === "CLOSED") {
            return "danger";
        }
        return "";
    }

    function statusBadgeModifier(status) {
        if (status === "OPEN") {
            return "is-live";
        }
        if (status === "SOLVED") {
            return "is-pending";
        }
        if (status === "CLOSED") {
            return "is-danger";
        }
        return "is-muted";
    }

    function connectionStatusText() {
        if (state.connected) {
            return "실시간 연결됨";
        }
        if (state.connecting) {
            return "연결 중";
        }
        return "오프라인";
    }

    function connectionStatusModifier() {
        if (state.connected) {
            return "is-live";
        }
        if (state.connecting) {
            return "is-pending";
        }
        return "is-muted";
    }

    function resolveScopeLabel(scopeId) {
        const scope = MODE_CONFIG[state.mode].scopes.find(function (item) {
            return item.id === scopeId;
        });
        return scope ? scope.label : "방 목록";
    }

    function extractErrorMessage(body, status) {
        if (body && body.error && body.error.message) {
            return body.error.message + " (status=" + status + ")";
        }

        if (typeof body === "string" && body.trim()) {
            return body;
        }

        return "status=" + status;
    }

    function isNearBottom(container) {
        return container.scrollHeight - container.scrollTop - container.clientHeight < 120;
    }

    function scrollTimelineToBottom() {
        requestAnimationFrame(function () {
            elements.messageStream.scrollTop = elements.messageStream.scrollHeight;
        });
    }

    function clearNode(node) {
        while (node.firstChild) {
            node.removeChild(node.firstChild);
        }
    }

    function safe(value) {
        return value == null ? "-" : String(value);
    }

    function normalizeToken(value) {
        return String(value || "").replace(/^Bearer\s+/i, "").trim();
    }

    function parsePositiveNumber(value) {
        const parsed = Number(value);
        return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
    }

    function tryParseJson(value) {
        try {
            return JSON.parse(value);
        } catch (error) {
            return value;
        }
    }

    function formatDateTime(value) {
        if (!value || value === "-") {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        return date.toLocaleString("ko-KR", {
            month: "2-digit",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        });
    }

    function formatTime(value) {
        if (!value || value === "-") {
            return "-";
        }

        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return String(value);
        }

        return date.toLocaleTimeString("ko-KR", {
            hour: "2-digit",
            minute: "2-digit"
        });
    }
})();