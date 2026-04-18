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
    const STORAGE_SHOW_ALL_ACTIONS_KEY = "support-chat-debug-show-all-actions";
    const MODE_CONFIG = {
        user: {
            badge: "CUSTOMER FLOW",
            title: "고객 모드",
            description: "문의방 생성부터 메시지 전송, 읽음 처리, AI 선응답, 상담원 연결 요청까지 고객 입장에서 순서대로 검증합니다.",
            guideSummary: "고객 모드에서는 문의 시작, AI 보조, 사람 상담 요청 흐름이 자연스럽게 이어지는지 확인합니다.",
            step1Title: "JWT 입력 후 연결",
            step1Description: "고객 토큰을 넣고 STOMP 연결이 정상적으로 완료되는지 확인합니다.",
            step2Title: "문의방 생성과 구독",
            step2Description: "문의방을 만든 뒤 roomId를 넣고 실시간 구독을 시작합니다.",
            step3Title: "메시지와 읽음 검증",
            step3Description: "메시지 전송, 읽음 처리, AI 선응답을 차례대로 테스트합니다.",
            guide: [
                "문의방 생성 버튼으로 OPEN 방 생성 또는 기존 방 재사용을 확인합니다.",
                "생성된 roomId로 Subscribe 후 메시지와 읽음 이벤트가 들어오는지 봅니다.",
                "메시지 전송, 읽음 처리, AI 선응답 생성, 상담원 연결 요청까지 고객 관점 흐름을 검증합니다."
            ],
            scenarios: [
                {
                    title: "신규 문의 시작 시나리오",
                    summary: "처음 문의를 여는 고객 흐름이 자연스럽게 이어지는지 확인합니다.",
                    steps: "JWT 입력 → Connect → 문의방 생성 → 내 문의방 목록 조회 → roomId 확인 후 Subscribe",
                    expected: "OPEN 문의방이 생성되거나 기존 진행 중 방이 재사용되고, 구독 준비가 끝나야 합니다."
                },
                {
                    title: "실시간 메시지·읽음 시나리오",
                    summary: "고객이 대화에 참여하면서 메시지와 읽음 상태가 정상 반영되는지 봅니다.",
                    steps: "메시지 전송 → 타임라인 수신 확인 → lastReadMessageId 입력 → 읽음 이벤트 전송",
                    expected: "방 이벤트와 개인 unread sync가 함께 반영되고, 읽음 기준 메시지까지 상태가 맞아야 합니다."
                },
                {
                    title: "AI 선응답과 상담원 요청 시나리오",
                    summary: "AI로 먼저 보조하다가 필요하면 사람 상담으로 넘기는 흐름을 확인합니다.",
                    steps: "AI 설정 조회 → AI 선응답 생성 → 상담원 연결 요청 → 요청 상태 배지 확인",
                    expected: "현재 provider 상태가 보이고, 상담원 연결 요청 후에는 AI 버튼이 숨겨지며 요청 상태가 바로 보여야 합니다."
                }
            ],
            roleChip: "USER",
            fetchMyRoomsLabel: "내 문의방 목록 조회",
            fetchClosedRoomsLabel: "내 종료 이력 조회",
            fetchStaleRoomsLabel: "장기 미응답 방 조회",
            adminDescription: "운영자 모드에서만 대기열과 강제 조치 흐름을 검증할 수 있습니다.",
            messageHint: "고객은 본인 문의방에서 메시지 전송을 테스트할 수 있습니다.",
            aiHint: "고객은 현재 방에서 AI 선응답을 먼저 확인하고, 부족하면 상담원 연결 요청으로 전환할 수 있습니다.",
            readHint: "고객은 본인 문의방에서 마지막 읽은 메시지 기준으로 읽음 처리를 테스트할 수 있습니다."
        },
        admin: {
            badge: "COUNSELOR FLOW",
            title: "상담원 모드",
            description: "실제 상담원처럼 대기열 진입, 방 배정, 메시지 응대, 읽음 처리, 종료 흐름을 검증합니다.",
            guideSummary: "상담원 모드에서는 담당 방 기준 응대 흐름과 운영 액션을 함께 확인합니다.",
            step1Title: "JWT 입력 후 연결",
            step1Description: "상담원 토큰으로 STOMP 연결이 정상적으로 열리는지 확인합니다.",
            step2Title: "대기열 또는 담당 방 선택",
            step2Description: "대기열을 조회하거나 내 담당 방 목록에서 테스트할 roomId를 고릅니다.",
            step3Title: "응대와 운영 액션 실행",
            step3Description: "배정, 응답, 읽음, 종료 흐름을 역할 기준에 맞게 검증합니다.",
            guide: [
                "대기열 조회 또는 내 담당 문의방 조회로 테스트할 roomId를 선택합니다.",
                "필요하면 방 배정 또는 방 배정 해제를 한 뒤 해당 roomId로 Subscribe를 진행합니다.",
                "메시지 전송과 읽음 처리가 본인 담당 방에서만 동작하는지 확인합니다."
            ],
            scenarios: [
                {
                    title: "대기열 진입과 배정 시나리오",
                    summary: "상담원이 queue에서 방을 잡는 기본 운영 흐름을 확인합니다.",
                    steps: "Connect → 대기열 조회 → roomId 선택 → 방 배정(claim) → Subscribe",
                    expected: "선택한 방이 본인 담당 방으로 바뀌고, 이후 참여형 액션이 열려야 합니다."
                },
                {
                    title: "상담 응대 시나리오",
                    summary: "담당 방에서만 메시지와 읽음 처리가 가능한지 확인합니다.",
                    steps: "메시지 전송 → SYSTEM/일반 메시지 수신 확인 → 읽음 이벤트 전송",
                    expected: "본인이 claim한 방에서는 정상 동작하고, 담당이 아닌 방에서는 접근 제한이 유지되어야 합니다."
                },
                {
                    title: "해결 처리 시나리오",
                    summary: "응대 후 SOLVED 전환과 후속 상태 변화가 자연스럽게 이어지는지 봅니다.",
                    steps: "해결 처리(solve) → 방 상태 확인 → 필요 시 재오픈 또는 종료 흐름 확인",
                    expected: "방 상태가 SOLVED로 바뀌고, 후속 메시지나 운영 액션 정책이 현재 설계와 맞아야 합니다."
                }
            ],
            roleChip: "ADMIN",
            fetchMyRoomsLabel: "내 담당 문의방 조회",
            fetchClosedRoomsLabel: "내 종료 이력 조회",
            fetchStaleRoomsLabel: "장기 미응답 방 조회",
            adminDescription: "ADMIN 토큰으로 대기열 조회, 방 배정, 배정 해제 흐름을 검증합니다.",
            messageHint: "상담원은 본인이 claim한 방에서만 메시지 전송을 테스트할 수 있습니다.",
            aiHint: "상담원은 claim한 방 기준으로 AI 선응답 생성 결과를 확인할 수 있습니다.",
            readHint: "상담원은 본인이 맡은 방에서만 읽음 처리를 테스트할 수 있습니다."
        },
        super_admin: {
            badge: "OPERATIONS FLOW",
            title: "운영 관리자 모드",
            description: "전체 조회와 강제 조치 중심으로 검증하며, 실제 상담 참여 상태는 남기지 않는 운영 전용 흐름을 확인합니다.",
            guideSummary: "SUPER_ADMIN 모드에서는 운영 제어는 가능하지만 상담 참여형 액션은 막혀 있어야 합니다.",
            step1Title: "JWT 입력 후 연결",
            step1Description: "운영 관리자 토큰으로 연결한 뒤 운영 조회 API가 열리는지 확인합니다.",
            step2Title: "전체 목록 또는 큐 확인",
            step2Description: "전체 문의방, 대기열, 종료 이력에서 테스트할 roomId를 고릅니다.",
            step3Title: "운영 조치와 차단 정책 검증",
            step3Description: "강제 조치는 가능하고 메시지·읽음·AI는 막히는지 확인합니다.",
            guide: [
                "전체 문의방 조회, 대기열 조회, 종료 이력 조회, 장기 미응답 방 조회로 운영 화면 데이터 범위를 확인합니다.",
                "필요한 roomId를 선택해 claim, release, reassign, close 같은 운영 조치를 테스트합니다.",
                "메시지 전송, 읽음 처리, AI 선응답 생성이 비활성화되거나 차단되는지 확인합니다."
            ],
            scenarios: [
                {
                    title: "운영 대시보드 조회 시나리오",
                    summary: "운영 관리자가 전체 문의 현황을 빠르게 파악할 수 있는지 확인합니다.",
                    steps: "Connect → 전체 문의방 조회 → 종료 이력 조회 → 장기 미응답 방 조회",
                    expected: "운영 전용 목록이 역할에 맞게 열리고, 상담 참여 없이도 전체 현황 파악이 가능해야 합니다."
                },
                {
                    title: "강제 운영 조치 시나리오",
                    summary: "재배정과 종료 같은 운영 강제 액션이 정확히 제한되는지 확인합니다.",
                    steps: "roomId 선택 → reassign 또는 close 실행 → 결과 상태 반영 확인",
                    expected: "SUPER_ADMIN만 가능한 액션이 노출되고, 실행 후 방 상태와 목록 반영이 즉시 따라와야 합니다."
                },
                {
                    title: "참여 차단 정책 시나리오",
                    summary: "운영 전용 계정이 상담 참여 액션을 하지 못하게 막히는지 봅니다.",
                    steps: "메시지/읽음/AI 영역 확인 → 숨김 또는 차단 상태 점검",
                    expected: "운영 계정은 조회와 강제 조치 중심으로만 움직이고, 참여형 액션은 열리지 않아야 합니다."
                }
            ],
            roleChip: "SUPER_ADMIN",
            fetchMyRoomsLabel: "전체 문의방 조회",
            fetchClosedRoomsLabel: "전체 종료 이력 조회",
            fetchStaleRoomsLabel: "장기 미응답 방 조회",
            adminDescription: "SUPER_ADMIN 토큰으로 전체 조회와 운영 강제 조치 흐름을 검증합니다.",
            messageHint: "SUPER_ADMIN은 운영 전용 역할이므로 일반 상담 메시지 전송을 하지 않습니다.",
            aiHint: "SUPER_ADMIN은 운영 전용 역할이므로 AI 선응답 생성을 하지 않습니다.",
            readHint: "SUPER_ADMIN은 운영 전용 역할이므로 참여자 읽음 상태를 변경하지 않습니다."
        }
    };

    const state = {
        socket: null,
        connected: false,
        connecting: false,
        currentMode: "user",
        currentRoomStatus: null,
        currentCounselorAssigned: false,
        currentCounselorUserId: null,
        currentCounselorRequestPending: false,
        currentCounselorRequestedAt: null,
        aiConfig: null,
        showAllActions: false,
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
        modeBadge: document.getElementById("mode-badge"),
        modeTitle: document.getElementById("mode-title"),
        modeDescription: document.getElementById("mode-description"),
        modeGuideSummary: document.getElementById("mode-guide-summary"),
        modeGuideList: document.getElementById("mode-guide-list"),
        modeRoleChip: document.getElementById("mode-role-chip"),
        step1Label: document.getElementById("mode-step-1-label"),
        step1Title: document.getElementById("mode-step-1-title"),
        step1Description: document.getElementById("mode-step-1-description"),
        step2Label: document.getElementById("mode-step-2-label"),
        step2Title: document.getElementById("mode-step-2-title"),
        step2Description: document.getElementById("mode-step-2-description"),
        step3Label: document.getElementById("mode-step-3-label"),
        step3Title: document.getElementById("mode-step-3-title"),
        step3Description: document.getElementById("mode-step-3-description"),
        modeSwitch: document.querySelector(".mode-switch"),
        adminPanelDescription: document.getElementById("admin-panel-description"),
        messageModeHint: document.getElementById("message-mode-hint"),
        aiModeHint: document.getElementById("ai-mode-hint"),
        aiProviderStatus: document.getElementById("ai-provider-status"),
        aiProviderSummary: document.getElementById("ai-provider-summary"),
        aiRagStatus: document.getElementById("ai-rag-status"),
        aiRagSummary: document.getElementById("ai-rag-summary"),
        aiRagDocumentCount: document.getElementById("ai-rag-document-count"),
        aiRagTopK: document.getElementById("ai-rag-top-k"),
        aiRagThreshold: document.getElementById("ai-rag-threshold"),
        counselorRequestStatus: document.getElementById("counselor-request-status"),
        counselorRequestSummary: document.getElementById("counselor-request-summary"),
        readModeHint: document.getElementById("read-mode-hint"),
        messageDestinationPreview: document.getElementById("message-destination-preview"),
        readDestinationPreview: document.getElementById("read-destination-preview"),
        sendDestinationPreview: document.getElementById("send-destination-preview"),
        aiConfigDestinationPreview: document.getElementById("ai-config-destination-preview"),
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
        superAdminMode: document.getElementById("super-admin-mode-button"),
        loadJwt: document.getElementById("load-jwt-button"),
        deleteJwt: document.getElementById("delete-jwt-button"),
        subscribe: document.getElementById("subscribe-button"),
        unsubscribe: document.getElementById("unsubscribe-button"),
        subscribeQueue: document.getElementById("subscribe-queue-button"),
        unsubscribeQueue: document.getElementById("unsubscribe-queue-button"),
        sendMessage: document.getElementById("send-message-button"),
        fetchAiConfig: document.getElementById("fetch-ai-config-button"),
        createAiReply: document.getElementById("create-ai-reply-button"),
        requestCounselor: document.getElementById("request-counselor-button"),
        sendRead: document.getElementById("send-read-button"),
        createRoom: document.getElementById("create-room-button"),
        fetchMyRooms: document.getElementById("fetch-my-rooms-button"),
        fetchQueueRooms: document.getElementById("fetch-queue-rooms-button"),
        fetchClosedRooms: document.getElementById("fetch-closed-rooms-button"),
        fetchStaleRooms: document.getElementById("fetch-stale-rooms-button"),
        fetchRoomDetail: document.getElementById("fetch-room-detail-button"),
        fetchRoomMessages: document.getElementById("fetch-room-messages-button"),
        claimRoom: document.getElementById("claim-room-button"),
        releaseRoom: document.getElementById("release-room-button"),
        solveRoom: document.getElementById("solve-room-button"),
        reassignRoom: document.getElementById("reassign-room-button"),
        closeRoom: document.getElementById("close-room-button"),
        clearFriendlyLogs: document.getElementById("clear-friendly-logs-button"),
        clearErrorLogs: document.getElementById("clear-error-logs-button"),
        clearRawLogs: document.getElementById("clear-raw-logs-button"),
        clearRestOutput: document.getElementById("clear-rest-output-button")
    };

    const ui = {
        stepCards: Array.from(document.querySelectorAll(".mode-step-card")),
        connectPanel: buttons.connect.closest(".panel"),
        subscribePanel: buttons.subscribe.closest(".panel"),
        adminPanel: elements.adminModePanel,
        messagePanel: buttons.sendMessage.closest(".panel"),
        aiPanel: buttons.createAiReply.closest(".panel"),
        readPanel: buttons.sendRead.closest(".panel"),
        restPanel: buttons.createRoom.closest(".panel")
    };

    initialize();

    function initialize() {
        elements.endpointValue.textContent = CONTRACT.endpoint;
        elements.publishPrefixValue.textContent = CONTRACT.publishPrefix;
        elements.subscribePrefixValue.textContent = CONTRACT.subscribePrefix;
        elements.userQueuePrefixValue.textContent = CONTRACT.userQueuePrefix;

        restoreDisplayPreference();
        injectDisplayToggle();
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
        buttons.superAdminMode.addEventListener("click", function () { setMode("super_admin", true); });
        buttons.loadJwt.addEventListener("click", loadSavedToken);
        buttons.deleteJwt.addEventListener("click", deleteSavedToken);
        buttons.subscribe.addEventListener("click", subscribeRoom);
        buttons.unsubscribe.addEventListener("click", unsubscribeRoom);
        buttons.subscribeQueue.addEventListener("click", subscribeQueue);
        buttons.unsubscribeQueue.addEventListener("click", unsubscribeQueue);
        buttons.sendMessage.addEventListener("click", sendMessage);
        buttons.fetchAiConfig.addEventListener("click", fetchAiConfig);
        buttons.createAiReply.addEventListener("click", createAiReply);
        buttons.requestCounselor.addEventListener("click", requestCounselor);
        buttons.sendRead.addEventListener("click", sendReadReceipt);
        buttons.createRoom.addEventListener("click", createRoom);
        buttons.fetchMyRooms.addEventListener("click", fetchMyRooms);
        buttons.fetchQueueRooms.addEventListener("click", fetchQueueRooms);
        buttons.fetchClosedRooms.addEventListener("click", fetchClosedRooms);
        buttons.fetchStaleRooms.addEventListener("click", fetchStaleRooms);
        buttons.fetchRoomDetail.addEventListener("click", fetchRoomDetail);
        buttons.fetchRoomMessages.addEventListener("click", fetchRoomMessages);
        buttons.claimRoom.addEventListener("click", claimRoom);
        buttons.releaseRoom.addEventListener("click", releaseRoom);
        buttons.solveRoom.addEventListener("click", solveRoom);
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
        elements.adminModePanel.hidden = !isOperatorMode();
        buttons.userMode.classList.toggle("is-active", mode === "user");
        buttons.adminMode.classList.toggle("is-active", mode === "admin");
        buttons.superAdminMode.classList.toggle("is-active", mode === "super_admin");
        renderModeGuide();
        refreshUi();
        if (logChange) {
            addFriendlyLog("콘솔 모드를 " + mode.toUpperCase() + "로 전환했습니다.");
        }
    }

    function handleRoomIdChange() {
        refreshDestinationPreview();
        setCurrentRoomStatus(null, "roomId가 바뀌어서 방 상태를 다시 확인해야 합니다.", null);
        if (state.connected && !state.showAllActions) {
            revealNextStep(ui.subscribePanel, buttons.subscribe);
        }
    }

    function renderModeGuide() {
        const config = MODE_CONFIG[state.currentMode];

        elements.modeBadge.textContent = config.badge;
        elements.modeTitle.textContent = config.title;
        elements.modeDescription.textContent = config.description;
        elements.modeGuideSummary.textContent = config.guideSummary;
        elements.modeRoleChip.textContent = config.roleChip;
        elements.step1Label.textContent = "STEP 1";
        elements.step1Title.textContent = config.step1Title;
        elements.step1Description.textContent = config.step1Description;
        elements.step2Label.textContent = "STEP 2";
        elements.step2Title.textContent = config.step2Title;
        elements.step2Description.textContent = config.step2Description;
        elements.step3Label.textContent = "STEP 3";
        elements.step3Title.textContent = config.step3Title;
        elements.step3Description.textContent = config.step3Description;
        elements.adminPanelDescription.textContent = config.adminDescription;
        elements.messageModeHint.textContent = config.messageHint;
        elements.aiModeHint.textContent = config.aiHint;
        elements.readModeHint.textContent = config.readHint;
        buttons.fetchMyRooms.textContent = config.fetchMyRoomsLabel;
        buttons.fetchClosedRooms.textContent = config.fetchClosedRoomsLabel;
        buttons.fetchStaleRooms.textContent = config.fetchStaleRoomsLabel;

        clearNode(elements.modeGuideList);
        config.scenarios.forEach(function (scenario, index) {
            const item = document.createElement("article");
            item.className = "guide-item guide-scenario";

            const step = document.createElement("span");
            step.className = "guide-step";
            step.textContent = "0" + (index + 1);

            const body = document.createElement("div");
            const title = document.createElement("strong");
            title.textContent = scenario.title;

            const summary = document.createElement("p");
            summary.textContent = scenario.summary;

            const flow = document.createElement("p");
            flow.className = "guide-meta";
            flow.innerHTML = "<span>실행 순서</span>" + scenario.steps;

            const expected = document.createElement("p");
            expected.className = "guide-meta";
            expected.innerHTML = "<span>기대 결과</span>" + scenario.expected;

            body.appendChild(title);
            body.appendChild(summary);
            body.appendChild(flow);
            body.appendChild(expected);
            item.appendChild(step);
            item.appendChild(body);
            elements.modeGuideList.appendChild(item);
        });
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
                    host: window.location.hostname,
                    "accept-version": "1.2,1.1,1.0",
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
        if (!isSuperAdminMode()) {
            subscribeDestination("user-read", CONTRACT.userQueuePrefix + "/read");
        }
        updateSubscriptionStatus();
        revealNextActionPanel();
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
        if (!isOperatorMode()) {
            addErrorLog("큐 구독은 ADMIN 또는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

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
        if (!canUseParticipantActions()) {
            addErrorLog("현재 모드에서는 일반 상담 메시지 전송을 테스트할 수 없습니다.");
            return;
        }

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
        if (!canUseParticipantActions()) {
            addErrorLog("현재 모드에서는 AI 선응답 생성을 테스트할 수 없습니다.");
            return;
        }

        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        if (isRoomClosed()) {
            addErrorLog("CLOSED 문의방에서는 AI 선응답을 생성할 수 없습니다.");
            return;
        }

        if (isAiReplyBlockedByCounselorRequest()) {
            addErrorLog("상담원 연결 요청이 접수된 동안에는 AI 선응답을 생성할 수 없습니다.");
            return;
        }

        fetchJson(CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/ai-replies", "AI 선응답 생성", {
            method: "POST"
        }).then(function (result) {
            const fallback = !!(result.body && result.body.data && result.body.data.fallback);
            const provider = state.aiConfig ? state.aiConfig.provider : "UNKNOWN";
            addFriendlyLog("AI 선응답 생성이 성공했습니다. provider=" + provider + ", fallback=" + fallback);
        });
    }

    function fetchAiConfig() {
        fetchJson(CONTRACT.supportApiPrefix + "/ai/config", "AI 설정 조회")
            .then(function (result) {
                renderAiConfig(result.body && result.body.data ? result.body.data : null);
            });
    }

    function requestCounselor() {
        if (state.currentMode !== "user") {
            addErrorLog("상담원 연결 요청은 USER 모드에서만 테스트할 수 있습니다.");
            return;
        }

        const roomId = parseRoomId();
        if (!roomId) {
            return;
        }

        if (isRoomClosed()) {
            addErrorLog("CLOSED 문의방에서는 상담원 연결 요청을 보낼 수 없습니다.");
            return;
        }

        fetchJson(CONTRACT.supportApiPrefix + "/rooms/" + roomId + "/counselor-request", "상담원 연결 요청", {
            method: "POST"
        }).then(function (result) {
            const data = result.body && result.body.data ? result.body.data : null;
            if (!data) {
                return;
            }

            syncCurrentRoomMetadata(data);
            setCurrentRoomStatus(
                data.status || "OPEN",
                "상담원 연결 요청 결과를 현재 roomId 상태에 반영했습니다."
            );

            if (data.requested) {
                addFriendlyLog("상담원 연결 요청이 접수되었습니다. AI 선응답 버튼은 배정 전까지 숨겨집니다.");
                return;
            }

            if (data.alreadyAssigned) {
                addFriendlyLog("이미 상담원이 배정된 문의방이라 추가 요청을 보내지 않았습니다.");
                return;
            }

            if (data.alreadyRequested) {
                addFriendlyLog("이미 상담원 연결 요청이 접수된 문의방입니다.");
            }
        });
    }

    function sendReadReceipt() {
        if (!canUseParticipantActions()) {
            addErrorLog("현재 모드에서는 참여자 읽음 처리를 테스트할 수 없습니다.");
            return;
        }

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
        if (!canCreateCustomerRoom()) {
            addErrorLog("문의방 생성은 USER 모드에서만 테스트할 수 있습니다.");
            return;
        }

        fetchJson(CONTRACT.supportApiPrefix + "/rooms", "문의방 생성", {
            method: "POST"
        }).then(function (result) {
            const roomId = result.body && result.body.data ? result.body.data.roomId : null;
            if (roomId) {
                elements.roomIdInput.value = roomId;
                refreshDestinationPreview();
                setCurrentRoomStatus(
                    "OPEN",
                    "방을 새로 만들었으므로 현재 roomId 상태를 OPEN으로 반영했습니다.",
                    {
                        counselorUserId: null,
                        customerRequestedCounselorAt: null
                    }
                );
                addFriendlyLog("생성된 roomId를 입력칸에 반영했습니다. roomId=" + roomId);
                revealNextStep(ui.subscribePanel, buttons.subscribe);
            }
        });
    }

    function fetchMyRooms() {
        fetchJson(CONTRACT.supportApiPrefix + "/rooms/me", buttons.fetchMyRooms.textContent)
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, buttons.fetchMyRooms.textContent);
            });
    }

    function fetchQueueRooms() {
        if (!isOperatorMode()) {
            addErrorLog("대기열 조회는 ADMIN 또는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

        fetchJson(CONTRACT.adminApiPrefix + "/queue", "대기열 조회")
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, "대기열");
            });
    }

    function fetchClosedRooms() {
        if (!isOperatorMode()) {
            addErrorLog("종료 이력 조회는 ADMIN 또는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

        fetchJson(CONTRACT.adminApiPrefix + "/rooms/closed", buttons.fetchClosedRooms.textContent)
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, buttons.fetchClosedRooms.textContent);
            });
    }

    function fetchStaleRooms() {
        if (!isSuperAdminMode()) {
            addErrorLog("장기 미응답 방 조회는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

        fetchJson(CONTRACT.adminApiPrefix + "/rooms/stale", buttons.fetchStaleRooms.textContent)
            .then(function (result) {
                syncRoomStatusFromRoomList(result.body && result.body.data, buttons.fetchStaleRooms.textContent);
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
        if (!isSuperAdminMode()) {
            addErrorLog("방 재배정은 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

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
            markCounselorAssigned(targetCounselorUserId);
            setCurrentRoomStatus("OPEN", "방 재배정 후에도 방 상태는 OPEN으로 유지됩니다.");
        });
    }

    function closeRoom() {
        if (!isSuperAdminMode()) {
            addErrorLog("문의방 종료는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }
        mutateAdminRoom("close", "문의방 종료");
    }

    function solveRoom() {
        if (!isOperatorMode()) {
            addErrorLog("해결 처리는 ADMIN 또는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }
        mutateAdminRoom("solve", "해결 처리");
    }

    function mutateAdminRoom(action, label) {
        if (!isOperatorMode()) {
            addErrorLog(label + "은 ADMIN 또는 SUPER_ADMIN 모드에서만 가능합니다.");
            return;
        }

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
            fetchAiConfig();
            revealNextStep(ui.subscribePanel, elements.roomIdInput);
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

        if (payload && payload.messageType === "SYSTEM") {
            item.classList.add("is-system");
            syncRoomStatusFromSystemMessage(payload);
        }

        const meta = document.createElement("div");
        meta.className = "timeline-meta";
        meta.textContent = "messageId=" + safe(payload && payload.messageId)
            + " / senderType=" + safe(payload && payload.senderType)
            + " / messageType=" + safe(payload && payload.messageType);

        const body = document.createElement("pre");
        body.className = "timeline-body";
        body.textContent = JSON.stringify(payload, null, 2);

        item.appendChild(meta);
        item.appendChild(body);
        elements.messageTimeline.prepend(item);

        if (payload && payload.messageType === "SYSTEM") {
            addFriendlyLog("SYSTEM 안내를 수신했습니다. messageId=" + safe(payload && payload.messageId));
            return;
        }

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
        return lines.join("\r\n") + "\r\n\0";
    }

    function parseFrame(rawFrame) {
        const normalized = String(rawFrame)
            .replace(/\0+$/, "")
            .replace(/\r\n/g, "\n")
            .replace(/\r/g, "\n");
        const lines = normalized.split(/\n/);
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
        elements.aiConfigDestinationPreview.textContent = "GET " + CONTRACT.supportApiPrefix + "/ai/config";
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
        updateCounselorRequestStatus();
        updateAiProviderStatus();
        updateActionButtons();
        updateStepProgress();
        updatePanelVisibility();
        syncDisplayToggle();
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
        if (state.currentRoomStatus === "SOLVED") {
            setStatusChip(elements.roomStatus, "is-pending", "SOLVED");
            return;
        }
        if (state.currentRoomStatus === "CLOSED") {
            setStatusChip(elements.roomStatus, "is-closed", "CLOSED");
            return;
        }
        setStatusChip(elements.roomStatus, "is-muted", "상태 미확인");
    }

    function updateCounselorRequestStatus() {
        if (!hasCurrentRoomId()) {
            setStatusChip(elements.counselorRequestStatus, "is-muted", "미확인");
            elements.counselorRequestSummary.textContent = "roomId를 정한 뒤 문의방 상세 조회를 하면 상담원 연결 상태를 바로 확인할 수 있습니다.";
            return;
        }

        if (state.currentCounselorAssigned) {
            setStatusChip(elements.counselorRequestStatus, "is-connected", "배정됨");
            elements.counselorRequestSummary.textContent = "이미 상담원이 배정된 문의방입니다. 이 상태에서는 상담원 연결 요청 버튼이 보이지 않습니다.";
            return;
        }

        if (state.currentCounselorRequestPending) {
            setStatusChip(elements.counselorRequestStatus, "is-pending", "요청됨");
            elements.counselorRequestSummary.textContent = "상담원 연결 요청이 접수된 상태입니다. 배정 전까지 AI 선응답 버튼은 숨겨집니다.";
            return;
        }

        if (state.currentMode === "user" && state.currentRoomStatus && !isRoomClosed()) {
            setStatusChip(elements.counselorRequestStatus, "is-muted", "요청 가능");
            elements.counselorRequestSummary.textContent = "아직 상담원 연결 요청이 없는 문의방입니다. AI로 부족할 때 버튼으로 사람 상담을 요청할 수 있습니다.";
            return;
        }

        setStatusChip(elements.counselorRequestStatus, "is-muted", "대상 아님");
        elements.counselorRequestSummary.textContent = "현재 모드 또는 문의방 상태에서는 상담원 연결 요청을 직접 보내지 않습니다.";
    }

    function updateAiProviderStatus() {
        if (!state.aiConfig) {
            setStatusChip(elements.aiProviderStatus, "is-muted", "미확인");
            elements.aiProviderSummary.textContent = "현재 AI mode / provider / 모델 정보를 아직 조회하지 않았습니다.";
            setStatusChip(elements.aiRagStatus, "is-muted", "미확인");
            elements.aiRagSummary.textContent = "RAG 활성화 여부와 준비 상태를 아직 조회하지 않았습니다.";
            elements.aiRagDocumentCount.textContent = "-";
            elements.aiRagTopK.textContent = "-";
            elements.aiRagThreshold.textContent = "-";
            return;
        }

        const provider = safe(state.aiConfig.provider);
        const ready = isPreferredProviderReady(state.aiConfig);
        setStatusChip(
            elements.aiProviderStatus,
            ready ? "is-connected" : "is-pending",
            provider + (ready ? " 준비됨" : " fallback 가능")
        );
        elements.aiProviderSummary.textContent =
            "mode=" + safe(state.aiConfig.mode)
            + " / provider=" + provider
            + " / model=" + resolveProviderModel(state.aiConfig)
            + " / recentMessages=" + safe(state.aiConfig.recentMessageContextLimit);

        const ragEnabled = !!state.aiConfig.ragEnabled;
        const ragReady = !!state.aiConfig.ragAdvisorReady && !!state.aiConfig.ragVectorStoreReady;
        setStatusChip(
            elements.aiRagStatus,
            ragEnabled ? (ragReady ? "is-connected" : "is-pending") : "is-muted",
            ragEnabled ? (ragReady ? "RAG 준비됨" : "RAG 부분 준비") : "RAG 꺼짐"
        );
        elements.aiRagSummary.textContent = buildRagSummary(state.aiConfig, ragEnabled, ragReady);
        elements.aiRagDocumentCount.textContent = safe(state.aiConfig.ragDocumentCount);
        elements.aiRagTopK.textContent = safe(state.aiConfig.ragTopK);
        elements.aiRagThreshold.textContent = safe(state.aiConfig.ragSimilarityThreshold);
    }

    function buildRagSummary(config, ragEnabled, ragReady) {
        if (!ragEnabled) {
            return "현재는 RAG를 사용하지 않고, 최근 대화와 system prompt만으로 AI 호출을 진행합니다.";
        }

        if (ragReady) {
            return "RAG가 활성화되어 있고 Advisor와 VectorStore도 준비되었습니다. 현재 질문과 비슷한 정책 문서를 찾아 prompt에 함께 붙입니다.";
        }

        if (!!config.ragAdvisorReady || !!config.ragVectorStoreReady) {
            return "RAG는 켜져 있지만 준비 상태가 완전하지 않습니다. 일부 구성은 열려 있으나 실제 문서 검색이 빠질 수 있습니다.";
        }

        return "RAG는 켜져 있지만 아직 Advisor 또는 VectorStore가 준비되지 않았습니다. 이 경우 일반 AI 호출처럼 동작할 수 있습니다.";
    }

    function updateActionButtons() {
        const hasRoomId = hasCurrentRoomId();
        const roomClosed = isRoomClosed();
        const operatorMode = isOperatorMode();
        const participantMode = canUseParticipantActions();
        const customerCreateAllowed = canCreateCustomerRoom();
        const hasSavedToken = !!window.localStorage.getItem(STORAGE_TOKEN_KEY);

        setButtonVisibility(buttons.connect, !state.connected && !state.connecting);
        setButtonVisibility(buttons.disconnect, !!state.socket);
        setButtonVisibility(buttons.loadJwt, hasSavedToken);
        setButtonVisibility(buttons.deleteJwt, hasSavedToken);

        setButtonVisibility(buttons.subscribe, state.connected && hasRoomId);
        setButtonVisibility(buttons.unsubscribe, state.connected && hasRoomId);
        setButtonVisibility(buttons.subscribeQueue, state.connected && operatorMode);
        setButtonVisibility(buttons.unsubscribeQueue, state.connected && operatorMode);

        setButtonVisibility(buttons.sendMessage, state.connected && hasRoomId && !roomClosed && participantMode);
        setButtonVisibility(buttons.sendRead, state.connected && hasRoomId && !roomClosed && participantMode);
        setButtonVisibility(buttons.createAiReply, hasRoomId && !roomClosed && participantMode && !isAiReplyBlockedByCounselorRequest());
        setButtonVisibility(buttons.requestCounselor, canRequestCounselor());
        setButtonVisibility(buttons.fetchAiConfig, true);

        setButtonVisibility(buttons.createRoom, customerCreateAllowed);
        setButtonVisibility(buttons.fetchRoomDetail, hasRoomId);
        setButtonVisibility(buttons.fetchRoomMessages, hasRoomId);
        setButtonVisibility(buttons.fetchMyRooms, true);
        setButtonVisibility(buttons.fetchQueueRooms, operatorMode);
        setButtonVisibility(buttons.fetchClosedRooms, operatorMode);
        setButtonVisibility(buttons.fetchStaleRooms, isSuperAdminMode());

        setButtonVisibility(buttons.claimRoom, operatorMode && hasRoomId);
        setButtonVisibility(buttons.releaseRoom, operatorMode && hasRoomId);
        setButtonVisibility(buttons.solveRoom, operatorMode && hasRoomId && !roomClosed);
        setButtonVisibility(buttons.reassignRoom, isSuperAdminMode() && hasRoomId);
        setButtonVisibility(buttons.closeRoom, isSuperAdminMode() && hasRoomId);
    }

    function updateStepProgress() {
        const roomIdReady = hasCurrentRoomId();
        const roomSubscribed = roomIdReady && state.subscriptions.has("room-" + parseRoomId(false));
        const stepStates = [
            state.connected ? "complete" : (state.connecting ? "current" : "current"),
            !state.connected ? "locked" : (roomSubscribed ? "complete" : "current"),
            !state.connected ? "locked" : (!roomIdReady ? "locked" : "current")
        ];

        ui.stepCards.forEach(function (card, index) {
            const stepNumber = index + 1;
            const stateName = stepStates[index];

            card.classList.toggle("is-current", stateName === "current");
            card.classList.toggle("is-complete", stateName === "complete");
            card.classList.toggle("is-locked", stateName === "locked");

            if (stepNumber === 1) {
                elements.step1Label.textContent = stateName === "complete" ? "STEP 1 · 완료" : (state.connecting ? "STEP 1 · 연결 중" : "STEP 1 · 시작");
            }
            if (stepNumber === 2) {
                elements.step2Label.textContent = stateName === "complete" ? "STEP 2 · 완료" : (stateName === "locked" ? "STEP 2 · 대기" : "STEP 2 · 진행");
            }
            if (stepNumber === 3) {
                elements.step3Label.textContent = stateName === "complete" ? "STEP 3 · 완료" : (stateName === "locked" ? "STEP 3 · 대기" : "STEP 3 · 실행");
            }
        });

        elements.modeGuideSummary.textContent = buildModeProgressSummary(state.connected, roomIdReady, roomSubscribed);
    }

    function buildModeProgressSummary(connected, roomIdReady, roomSubscribed) {
        const config = MODE_CONFIG[state.currentMode];

        if (!connected) {
            return config.guideSummary + " 현재는 STEP 1에서 JWT 입력과 연결을 먼저 완료하면 됩니다.";
        }

        if (!roomIdReady) {
            return config.guideSummary + " 현재는 STEP 2에서 roomId를 정하고 구독 대상으로 삼을 방을 고르면 됩니다.";
        }

        if (!roomSubscribed) {
            return config.guideSummary + " roomId는 준비됐습니다. 이제 Subscribe로 실시간 흐름을 붙이면 STEP 3 액션이 자연스럽게 이어집니다.";
        }

        return config.guideSummary + " 연결과 구독이 끝났으니 이제 STEP 3에서 역할에 맞는 액션만 순서대로 실행하면 됩니다.";
    }

    function updatePanelVisibility() {
        const connected = state.connected;
        const hasRoomId = hasCurrentRoomId();
        const participantMode = canUseParticipantActions();
        const operatorMode = isOperatorMode();

        if (state.showAllActions) {
            togglePanel(ui.connectPanel, true);
            togglePanel(ui.subscribePanel, true);
            togglePanel(ui.adminPanel, operatorMode);
            togglePanel(ui.messagePanel, participantMode);
            togglePanel(ui.aiPanel, participantMode);
            togglePanel(ui.readPanel, participantMode);
            togglePanel(ui.restPanel, true);
            return;
        }

        togglePanel(ui.connectPanel, true);
        togglePanel(ui.subscribePanel, connected);
        togglePanel(ui.adminPanel, operatorMode);
        togglePanel(ui.messagePanel, connected && hasRoomId && participantMode);
        togglePanel(ui.aiPanel, connected && participantMode);
        togglePanel(ui.readPanel, connected && hasRoomId && participantMode);
        togglePanel(ui.restPanel, connected);
    }

    function togglePanel(panel, visible) {
        if (!panel) {
            return;
        }
        panel.classList.toggle("is-hidden", !visible);
    }

    function setButtonVisibility(button, visible) {
        button.disabled = !visible;
        button.classList.toggle("is-hidden", !state.showAllActions && !visible);
    }

    function revealNextActionPanel() {
        if (!state.connected || state.showAllActions) {
            return;
        }

        if (canUseParticipantActions()) {
            revealNextStep(ui.messagePanel, elements.messageInput);
            return;
        }

        if (isOperatorMode()) {
            revealNextStep(ui.adminPanel, buttons.claimRoom);
        }
    }

    function revealNextStep(panel, focusTarget) {
        if (!panel) {
            return;
        }

        requestAnimationFrame(function () {
            panel.scrollIntoView({ behavior: "smooth", block: "start" });

            if (!focusTarget || typeof focusTarget.focus !== "function") {
                return;
            }

            requestAnimationFrame(function () {
                try {
                    focusTarget.focus({ preventScroll: true });
                } catch (error) {
                    focusTarget.focus();
                }
            });
        });
    }

    function injectDisplayToggle() {
        const wrapper = document.createElement("label");
        wrapper.className = "view-toggle";
        wrapper.innerHTML = ""
            + "<input type=\"checkbox\" id=\"show-all-actions-toggle\">"
            + "<span class=\"view-toggle-copy\">"
            + "<strong>전체 액션 보기</strong>"
            + "<small>끄면 현재 단계에 필요한 버튼만 보이고, 켜면 숨겨진 액션도 비활성화 상태로 함께 봅니다.</small>"
            + "</span>";

        elements.modeSwitch.appendChild(wrapper);
        ui.showAllActionsToggle = wrapper.querySelector("#show-all-actions-toggle");
        ui.showAllActionsToggle.addEventListener("change", handleShowAllActionsToggleChange);
    }

    function restoreDisplayPreference() {
        state.showAllActions = window.localStorage.getItem(STORAGE_SHOW_ALL_ACTIONS_KEY) === "true";
    }

    function handleShowAllActionsToggleChange() {
        state.showAllActions = !!ui.showAllActionsToggle.checked;
        window.localStorage.setItem(STORAGE_SHOW_ALL_ACTIONS_KEY, String(state.showAllActions));
        refreshUi();
        addFriendlyLog(state.showAllActions ? "전체 액션 보기 모드로 전환했습니다." : "집중 보기 모드로 전환했습니다.");
    }

    function syncDisplayToggle() {
        if (ui.showAllActionsToggle) {
            ui.showAllActionsToggle.checked = state.showAllActions;
        }
    }

    function setStatusChip(element, modifier, text) {
        element.className = "status-chip " + modifier;
        element.textContent = text;
    }

    function setCurrentRoomStatus(status, summary, roomMeta) {
        state.currentRoomStatus = status || null;
        if (arguments.length >= 3) {
            syncCurrentRoomMetadata(roomMeta);
        }
        refreshUi();
        if (summary) {
            elements.roomStatusSummary.textContent = summary;
        }
    }

    function renderAiConfig(config) {
        state.aiConfig = config || null;
        refreshUi();
    }

    function isPreferredProviderReady(config) {
        if (!config) {
            return false;
        }
        if (config.provider === "OPENAI") {
            return !!config.openAiReady;
        }
        if (config.provider === "OLLAMA") {
            return !!config.ollamaReady;
        }
        return false;
    }

    function resolveProviderModel(config) {
        if (!config) {
            return "-";
        }
        if (config.provider === "OPENAI") {
            return safe(config.openAiModel);
        }
        if (config.provider === "OLLAMA") {
            return safe(config.ollamaModel);
        }
        return "-";
    }

    function syncCurrentRoomMetadata(room) {
        if (!room) {
            state.currentCounselorAssigned = false;
            state.currentCounselorUserId = null;
            state.currentCounselorRequestPending = false;
            state.currentCounselorRequestedAt = null;
            return;
        }

        state.currentCounselorUserId = room.counselorUserId == null ? null : Number(room.counselorUserId);
        state.currentCounselorAssigned = room.counselorUserId != null;
        state.currentCounselorRequestedAt = room.customerRequestedCounselorAt || null;
        state.currentCounselorRequestPending = !state.currentCounselorAssigned && !!state.currentCounselorRequestedAt;
    }

    function markCounselorRequested(requestedAt) {
        state.currentCounselorAssigned = false;
        state.currentCounselorUserId = null;
        state.currentCounselorRequestPending = true;
        state.currentCounselorRequestedAt = requestedAt || state.currentCounselorRequestedAt || null;
    }

    function markCounselorAssigned(counselorUserId) {
        state.currentCounselorAssigned = true;
        state.currentCounselorUserId = counselorUserId == null ? state.currentCounselorUserId : Number(counselorUserId);
        state.currentCounselorRequestPending = false;
        state.currentCounselorRequestedAt = null;
    }

    function clearCounselorAssignment() {
        state.currentCounselorAssigned = false;
        state.currentCounselorUserId = null;
        state.currentCounselorRequestPending = false;
        state.currentCounselorRequestedAt = null;
    }

    function syncRoomStatusFromRoomList(rooms, sourceLabel) {
        const roomId = parseRoomId(false);
        const items = extractRoomListItems(rooms);
        if (!roomId) {
            return;
        }

        if (!items.length) {
            setCurrentRoomStatus(null, safe(sourceLabel) + " 조회 결과가 비어 있어서 현재 roomId 상태를 확인하지 못했습니다.", null);
            return;
        }

        const matchedRoom = items.find(function (room) {
            return Number(room && room.roomId) === roomId;
        });

        if (!matchedRoom || !matchedRoom.status) {
            setCurrentRoomStatus(null, safe(sourceLabel) + " 조회 결과에 현재 roomId가 없어 상태를 확인하지 못했습니다.", null);
            return;
        }

        setCurrentRoomStatus(
            matchedRoom.status,
            "목록 조회 결과 기준 현재 roomId 상태를 " + matchedRoom.status + "로 반영했습니다.",
            matchedRoom
        );
    }

    function syncRoomStatusFromRoomDetail(room) {
        const roomId = parseRoomId(false);
        if (!roomId || !room || Number(room.roomId) !== roomId || !room.status) {
            return;
        }

        setCurrentRoomStatus(
            room.status,
            "상세 조회 결과 기준 현재 roomId 상태를 " + room.status + "로 반영했습니다.",
            room
        );
        if (state.connected && !state.showAllActions) {
            revealNextActionPanel();
        }
    }

    function syncRoomStatusFromAdminMutation(action, result) {
        const data = result && result.body ? result.body.data : null;
        if (action === "close" && data && data.closed) {
            clearCounselorAssignment();
            setCurrentRoomStatus("CLOSED", "문의방 종료가 반영되어 현재 roomId 상태를 CLOSED로 바꿨습니다.");
            return;
        }

        if (action === "solve" && data && data.solved) {
            setCurrentRoomStatus("SOLVED", "해결 처리가 반영되어 현재 roomId 상태를 SOLVED로 바꿨습니다.");
            return;
        }

        if (action === "claim") {
            markCounselorAssigned(data && data.counselorUserId);
            setCurrentRoomStatus("OPEN", "방 배정 후에도 방 상태는 OPEN으로 유지됩니다.");
            return;
        }

        if (action === "release") {
            clearCounselorAssignment();
            setCurrentRoomStatus("OPEN", "방 배정 해제 후에도 방 상태는 OPEN으로 유지됩니다.");
            return;
        }

        if (action === "reassign") {
            markCounselorAssigned(data && data.counselorUserId);
            setCurrentRoomStatus("OPEN", "방 재배정 후에도 방 상태는 OPEN으로 유지됩니다.");
        }
    }

    function syncRoomStatusFromQueueEvent(payload) {
        const roomId = parseRoomId(false);
        if (!roomId || !payload || Number(payload.roomId) !== roomId) {
            return;
        }

        if (payload.eventType === "CLOSED") {
            clearCounselorAssignment();
            setCurrentRoomStatus("CLOSED", "큐 이벤트 기준 현재 roomId 상태를 CLOSED로 반영했습니다.");
            return;
        }

        if (payload.eventType === "REQUESTED") {
            markCounselorRequested(state.currentCounselorRequestedAt);
            setCurrentRoomStatus("OPEN", "큐 이벤트 기준 현재 roomId가 상담원 연결 요청 상태로 반영되었습니다.");
            return;
        }

        if (payload.eventType === "CLAIMED") {
            markCounselorAssigned(payload.counselorUserId);
            setCurrentRoomStatus("OPEN", "큐 이벤트 기준 현재 roomId 상태를 OPEN으로 반영했습니다.");
            return;
        }

        if (payload.eventType === "RELEASED") {
            clearCounselorAssignment();
            setCurrentRoomStatus("OPEN", "큐 이벤트 기준 현재 roomId 상태를 OPEN으로 반영했습니다.");
        }
    }

    function syncRoomStatusFromSystemMessage(payload) {
        const content = payload && payload.content ? String(payload.content) : "";

        if (!content) {
            return;
        }

        if (content.indexOf("상담원 연결 요청이 접수") > -1) {
            markCounselorRequested(payload && payload.createdAt);
            setCurrentRoomStatus("OPEN", "SYSTEM 안내 기준 현재 roomId를 상담원 연결 요청 상태로 반영했습니다.");
            return;
        }

        if (content.indexOf("상담원이 연결") > -1) {
            markCounselorAssigned(null);
            setCurrentRoomStatus("OPEN", "SYSTEM 안내 기준 현재 roomId에 상담원이 배정된 상태를 반영했습니다.");
            return;
        }

        if (content.indexOf("다시 진행 상태") > -1) {
            setCurrentRoomStatus("OPEN", "SYSTEM 안내 기준 현재 roomId 상태를 OPEN으로 반영했습니다.");
            return;
        }

        if (content.indexOf("답변이 완료") > -1) {
            setCurrentRoomStatus("SOLVED", "SYSTEM 안내 기준 현재 roomId 상태를 SOLVED로 반영했습니다.");
            return;
        }

        if (content.indexOf("자동 종료") > -1 || content.indexOf("문의가 종료") > -1) {
            clearCounselorAssignment();
            setCurrentRoomStatus("CLOSED", "SYSTEM 안내 기준 현재 roomId 상태를 CLOSED로 반영했습니다.");
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

    function isSuperAdminMode() {
        return state.currentMode === "super_admin";
    }

    function isOperatorMode() {
        return isAdminMode() || isSuperAdminMode();
    }

    function canUseParticipantActions() {
        return state.currentMode === "user" || state.currentMode === "admin";
    }

    function canCreateCustomerRoom() {
        return state.currentMode === "user";
    }

    function canRequestCounselor() {
        return state.currentMode === "user"
            && hasCurrentRoomId()
            && !!state.currentRoomStatus
            && !isRoomClosed()
            && !state.currentCounselorAssigned
            && !state.currentCounselorRequestPending;
    }

    function isAiReplyBlockedByCounselorRequest() {
        return state.currentCounselorRequestPending && !state.currentCounselorAssigned;
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
