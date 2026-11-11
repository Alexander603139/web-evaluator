// admin-ui/js/dashboard.js
(function () {
    'use strict';

    if (!window.wev.requireAuth()) return;

    const REFRESH_MS = 5000;
    let refreshTimer = null;
    let isLoading = false;

    // ---------- Helpers ----------
    function escapeHtml(s) {
        if (s == null) return '';
        return String(s)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#39;');
    }

    function isActive(run) {
        return run && (run.status === 'PENDING' || run.status === 'RUNNING');
    }

    function statusClass(status) {
        if (status === 'SUCCESS') return 'badge-success';
        if (status === 'FAILED')  return 'badge-failed';
        return 'badge-pending';
    }

    // ---------- Header ----------
    function renderHeader() {
        const user = window.wev.getCurrentUser();
        const info = document.getElementById('user-info');
        if (user && info) {
            info.innerHTML =
            '<strong>' + escapeHtml(user.username || '—') + '</strong> ' +
            '<span class="text-muted">(' + escapeHtml(user.role || '—') + ')</span>';
        }
        document.getElementById('logout-btn').addEventListener('click', function () {
            if (confirm('Выйти из аккаунта?')) window.wev.logout();
        });
    }

    function updateRefreshStatus(hasActive) {
        const el = document.getElementById('refresh-status');
        if (!el) return;
        const t = new Date().toLocaleTimeString('ru-RU');
        if (hasActive) {
            el.innerHTML =
            '<span style="display:inline-block;width:6px;height:6px;border-radius:50%;' +
            'background:#F59E0B;vertical-align:middle;margin-right:6px;"></span>' +
            'Автообновление · ' + t;
        } else {
            el.textContent = 'Обновлено: ' + t;
        }
    }

    // ---------- Rendering ----------
    function renderRuns(runs) {
        const wrap = document.getElementById('runs-wrap');
        if (!wrap) return;

        if (!runs || runs.length === 0) {
            wrap.innerHTML =
            '<div class="card"><p class="text-muted" style="margin:0">' +
            'Задач пока нет. Нажмите «Запустить тест», чтобы создать первую.' +
            '</p></div>';
            return;
        }

        const rows = runs.map(function (run) {
            const jobId = run.jobId || run.id || '';
            const shortId = jobId ? jobId.substring(0, 8) : '—';
            const status = run.status || 'UNKNOWN';
            const createdAt = window.wev.formatDate(run.createdAt);
            const duration = window.wev.formatDuration(run.startedAt, run.finishedAt);

            return (
            '<tr>' +
            '<td class="mono">' +
            '<a href="job.html?id=' + encodeURIComponent(jobId) + '">' +
            escapeHtml(shortId) +
            '</a>' +
            '</td>' +
            '<td><span class="badge ' + statusClass(status) + '">' +
            escapeHtml(status) + '</span></td>' +
            '<td>' + escapeHtml(run.scenarioType || '—') + '</td>' +
            '<td class="mono" style="max-width:320px; overflow:hidden; text-overflow:ellipsis; white-space:nowrap;" ' +
            'title="' + escapeHtml(run.targetUrl || '') + '">' +
            escapeHtml(run.targetUrl || '—') +
            '</td>' +
            '<td class="text-muted text-sm">' + escapeHtml(createdAt) + '</td>' +
            '<td class="text-muted text-sm">' + escapeHtml(duration) + '</td>' +
            '</tr>'
            );
        }).join('');

        wrap.innerHTML =
        '<div class="table-wrap"><table class="table">' +
        '<thead><tr>' +
        '<th>ID</th>' +
        '<th>Статус</th>' +
        '<th>Сценарий</th>' +
        '<th>URL</th>' +
        '<th>Создана</th>' +
        '<th>Длительность</th>' +
        '</tr></thead>' +
        '<tbody>' + rows + '</tbody>' +
        '</table></div>';
    }

    // ---------- Data loading / auto-refresh ----------
    function scheduleRefresh(shouldRefresh) {
        if (refreshTimer) {
            clearTimeout(refreshTimer);
            refreshTimer = null;
        }
        if (shouldRefresh) {
            refreshTimer = setTimeout(loadRuns, REFRESH_MS);
        }
    }

    async function loadRuns() {
        if (isLoading) return;
        isLoading = true;
        try {
            const runs = await window.wev.api.listRuns();
            renderRuns(runs);
            window.wev.hideMessage('#message');
            const hasActive = (runs || []).some(isActive);
            updateRefreshStatus(hasActive);
            scheduleRefresh(hasActive);
        } catch (err) {
            // 401 уже обработан в api.js (редирект на login.html)
            if (err && err.status === 401) return;
            window.wev.showMessage(
                '#message',
                err.message || 'Не удалось загрузить задачи',
                'error'
            );
            updateRefreshStatus(false);
            // одна повторная попытка через 5с, чтобы пережить кратковременный сбой
            scheduleRefresh(true);
        } finally {
            isLoading = false;
        }
    }

    // ---------- Init ----------
    renderHeader();
    loadRuns();

    // При возврате на вкладку — обновить сразу, не ждать таймера
    window.addEventListener('visibilitychange', function () {
        if (document.visibilityState === 'visible') loadRuns();
    });
})();