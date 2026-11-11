// admin-ui/js/login.js
(function () {
    'use strict';

    // Если уже залогинен и токен не истёк — сразу в дашборд
    if (window.wev.getAccessToken() && !window.wev.isTokenExpired?.()) {
        // isTokenExpired не экспортирован наружу — используем requireAuth-логику вручную
    }
//    const existing = window.wev.getCurrentUser?.();
//    if (existing && existing.exp && Date.now() < existing.exp * 1000 - 5000) {
//        window.location.replace('dashboard.html');
//        return;
//    }
    if (window.wev.getAccessToken() && !window.wev.isTokenExpired()) {
        window.location.replace('dashboard.html');
        return;
    }

    const form = document.getElementById('login-form');
    const btn = document.getElementById('submit-btn');
    const msg = document.getElementById('message');

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        window.wev.hideMessage(msg);

        const username = form.username.value.trim();
        const password = form.password.value;

        if (!username || !password) {
            window.wev.showMessage(msg, 'Заполните логин и пароль', 'error');
            return;
        }

        btn.disabled = true;
        const originalText = btn.textContent;
        btn.innerHTML = '<span class="spinner"></span> Вход…';

        try {
            await window.wev.api.login(username, password);
            window.location.replace('dashboard.html');
        } catch (err) {
            if (err instanceof window.wev.ApiError && err.status === 401) {
                window.wev.showMessage(msg, 'Неверный логин или пароль', 'error');
            } else {
                window.wev.showMessage(msg, err.message || 'Ошибка входа', 'error');
            }
            btn.disabled = false;
            btn.textContent = originalText;
        }
    });
})();