// admin-ui/js/api.js
// Единая точка работы с API. Подключается на каждой странице ДО js/{page}.js.
// Экспортирует window.wev.{api, requireAuth, ...}.

(function () {
    'use strict';

    // ============ Config ============
    // - Через nginx (production): API_BASE = '' → fetch('/api/v1/...') уйдёт на тот же origin,
    //   nginx проксирует на backend.
    // - Локально (localhost / 127.0.0.1): API_BASE = 'http://localhost:8080'.
    // - Можно переопределить ДО подключения api.js: <script>window.WEV_API_BASE = 'http://...'</script>
    const API_BASE = (window.WEV_API_BASE !== undefined)
    ? window.WEV_API_BASE
    : (function () {
        const h = location.hostname;
        return (h === 'localhost' || h === '127.0.0.1') ? 'http://localhost:8080' : '';
    })();

    const STORAGE = {
        access: 'wev.accessToken',
        refresh: 'wev.refreshToken',
    };

    // ============ Token helpers ============
    function getAccessToken() { return localStorage.getItem(STORAGE.access); }
    function getRefreshToken() { return localStorage.getItem(STORAGE.refresh); }
    function setTokens(access, refresh) {
        if (access) localStorage.setItem(STORAGE.access, access);
        if (refresh) localStorage.setItem(STORAGE.refresh, refresh);
    }
    function clearTokens() {
        localStorage.removeItem(STORAGE.access);
        localStorage.removeItem(STORAGE.refresh);
    }

    function decodeJwt(token) {
        try {
            const payload = token.split('.')[1];
            const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
            return JSON.parse(decodeURIComponent(escape(json)));
        } catch (e) { return null; }
    }

    function getCurrentUser() {
        const t = getAccessToken();
        if (!t) return null;
        const p = decodeJwt(t);
        if (!p) return null;
        return { username: p.sub, role: p.role || null, exp: p.exp || null };
    }

    function isTokenExpired() {
        const u = getCurrentUser();
        if (!u || !u.exp) return true;
        return Date.now() >= u.exp * 1000 - 5000; // 5s safety margin
    }

    // ============ Navigation ============
    function redirectToLogin() {
        const here = window.location.pathname.split('/').pop() || 'index.html';
        if (here === 'login.html') return;
        window.location.href = 'login.html';
    }

    function requireAuth() {
        if (!getAccessToken() || isTokenExpired()) {
            clearTokens();
            redirectToLogin();
            return false;
        }
        return true;
    }

    function logout() {
        clearTokens();
        window.location.href = 'login.html';
    }

    // ============ Core fetch ============
    class ApiError extends Error {
        constructor(message, status, body) {
            super(message);
            this.name = 'ApiError';
            this.status = status;
            this.body = body;
        }
    }

    async function apiFetch(path, options = {}) {
        const headers = Object.assign({ 'Accept': 'application/json' }, options.headers || {});
        if (options.body && !headers['Content-Type']) {
            headers['Content-Type'] = 'application/json';
        }
        const token = getAccessToken();
        if (token) headers['Authorization'] = `Bearer ${token}`;

        const res = await fetch(API_BASE + path, Object.assign({}, options, { headers }));

        if (res.status === 401) {
            clearTokens();
            redirectToLogin();
            throw new ApiError('Сессия истекла, войдите заново', 401, null);
        }

        let data = null;
        const ct = res.headers.get('content-type') || '';
        if (ct.includes('application/json')) {
            data = await res.json().catch(() => null);
        } else {
            data = await res.text().catch(() => null);
        }

        if (!res.ok) {
            const message = (data && (data.message || data.error)) || `HTTP ${res.status}`;
            throw new ApiError(message, res.status, data);
        }
        return data;
    }

    // ============ Endpoints ============
    const api = {
        async login(username, password) {
            const data = await apiFetch('/api/v1/auth/login', {
                method: 'POST',
                body: JSON.stringify({ username, password }),
            });
            setTokens(data.accessToken, data.refreshToken);
            return data;
        },

        logout,

        // Runs
        listRuns: () => apiFetch('/api/v1/runs'),
        getRun: (id) => apiFetch(`/api/v1/runs/${encodeURIComponent(id)}`),
        createRun: (payload) => apiFetch('/api/v1/runs', {
            method: 'POST',
            body: JSON.stringify(payload),
        }),

        // API keys
        listApiKeys: () => apiFetch('/api/v1/api-keys'),
        createApiKey: (name) => apiFetch(`/api/v1/api-keys?name=${encodeURIComponent(name)}`, {
            method: 'POST',
        }),
        deleteApiKey: (id) => apiFetch(`/api/v1/api-keys/${encodeURIComponent(id)}`, {
            method: 'DELETE',
        }),
    };

    // ============ UI helpers ============
    function showMessage(container, message, type = 'info') {
        if (typeof container === 'string') container = document.querySelector(container);
        if (!container) return;
        container.innerHTML = '';
        const el = document.createElement('div');
        el.className = `alert alert-${type}`;
        el.textContent = message;
        container.appendChild(el);
    }

    function hideMessage(container) {
        if (typeof container === 'string') container = document.querySelector(container);
        if (!container) return;
        container.innerHTML = '';
    }

    function formatDate(iso) {
        if (!iso) return '—';
        try {
            return new Date(iso).toLocaleString('ru-RU', { dateStyle: 'short', timeStyle: 'medium' });
        } catch { return iso; }
    }

    function formatDuration(startIso, endIso) {
        if (!startIso || !endIso) return '—';
        const ms = new Date(endIso) - new Date(startIso);
        if (ms < 0 || isNaN(ms)) return '—';
        const s = Math.round(ms / 1000);
        if (s < 60) return `${s}с`;
        const m = Math.floor(s / 60);
        return `${m}м ${s % 60}с`;
    }

    // ============ Expose ============
    window.wev = {
        api, ApiError, apiFetch,
        getCurrentUser, getAccessToken, getRefreshToken,
        requireAuth, logout,
        showMessage, hideMessage, formatDate, formatDuration,
        API_BASE,
        isTokenExpired,
    };
})();